package com.example.ttsdemo;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.slider.Slider;
import com.xqsj.tts.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Complete Java integration example; synthesis implementation lives in the SDK AAR. */
public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AudioPlayer player = new AudioPlayer();
    private final List<TtsVoice> voices = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();
    private TtsClient tts;
    private TtsAudio latest;
    private Spinner voiceSpinner, sampleSpinner;
    private EditText textInput;
    private TextView status, metrics, speedText;
    private Slider speedSlider;
    private Button synthesizeButton, saveButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        loadSdk();
    }

    private void bindViews() {
        voiceSpinner = findViewById(R.id.voiceSpinner);
        sampleSpinner = findViewById(R.id.sampleSpinner);
        textInput = findViewById(R.id.textInput);
        status = findViewById(R.id.statusText);
        metrics = findViewById(R.id.metricsText);
        speedText = findViewById(R.id.speedText);
        speedSlider = findViewById(R.id.speedSlider);
        synthesizeButton = findViewById(R.id.synthesizeButton);
        saveButton = findViewById(R.id.saveWavButton);
    }

    private void bindActions() {
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (i >= 0 && i < voices.size()) updateSamples(voices.get(i).language());
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        sampleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (i >= 0 && i < samples.size()) textInput.setText(samples.get(i));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        speedSlider.addOnChangeListener((s, value, user) ->
                speedText.setText(String.format(Locale.US, "语速 / Speed: %.2f", value)));
        synthesizeButton.setOnClickListener(v -> synthesize());
        findViewById(R.id.stopButton).setOnClickListener(v -> stop());
        saveButton.setOnClickListener(v -> saveWav());
        findViewById(R.id.downloadLanguageButton).setOnClickListener(v -> showPacks("languages"));
        findViewById(R.id.downloadVoiceButton).setOnClickListener(v -> showPacks("voices"));
    }

    private void loadSdk() {
        synthesizeButton.setEnabled(false);
        status.setText("正在加载模型… / Loading model…");
        worker.execute(() -> {
            try {
                TtsClient loaded = TtsClient.create(getApplicationContext());
                List<TtsVoice> installed = loaded.voices();
                List<String> diagnostics = loaded.diagnostics();
                runOnUiThread(() -> {
                    if (tts != null && tts != loaded) tts.close();
                    tts = loaded;
                    voices.clear(); voices.addAll(installed);
                    voiceSpinner.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, voices));
                    synthesizeButton.setEnabled(!voices.isEmpty());
                    String ready = "模型已加载 / Ready · voices=" + voices.size();
                    if (!diagnostics.isEmpty()) ready += "\n诊断 / Diagnostics:\n- " + String.join("\n- ", diagnostics);
                    status.setText(ready);
                });
            } catch (Exception e) { runOnUiThread(() -> showError("模型加载失败", e)); }
        });
    }

    private void updateSamples(String lang) {
        samples.clear();
        switch (lang) {
            case "zh": samples.addAll(Arrays.asList("你好，欢迎使用离线语音系统。", "今天天气不错，我们一起出去走走吧。")); break;
            case "en": samples.addAll(Arrays.asList("Hello, welcome to the offline speech system.", "This demonstration runs entirely on your Android device.")); break;
            case "ja": samples.addAll(Arrays.asList("こんにちは、音声システムへようこそ。", "今日はとてもいい天気ですね。")); break;
            case "ko": samples.add("안녕하세요. 오프라인 음성 시스템에 오신 것을 환영합니다."); break;
            case "fr": samples.add("Bonjour et bienvenue dans le système vocal hors ligne."); break;
            case "es": samples.add("Hola, bienvenido al sistema de voz sin conexión."); break;
            case "pt": samples.add("Olá, bem-vindo ao sistema de voz offline."); break;
            default: samples.add("Enter text for language: " + lang);
        }
        sampleSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, samples));
    }

    private void synthesize() {
        int i = voiceSpinner.getSelectedItemPosition();
        if (tts == null || i < 0 || i >= voices.size()) return;
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) return;
        TtsVoice voice = voices.get(i);
        player.stop(); synthesizeButton.setEnabled(false); saveButton.setEnabled(false);
        status.setText("正在合成 / Synthesizing · " + voice); metrics.setText("");
        float speed = speedSlider.getValue();
        worker.execute(() -> {
            try {
                TtsAudio audio = tts.synthesize(text, voice.language(), voice.voice(), speed);
                player.play(audio.samples(), audio.sampleRate());
                runOnUiThread(() -> {
                    latest = audio;
                    status.setText("合成完成，正在播放 / Playing");
                    metrics.setText(String.format(Locale.US,
                            "推理 %d ms · %d 段 · 音频 %.2f s · RTF %.3f · %d Hz",
                            audio.inferenceMillis(), audio.chunkCount(), audio.durationSeconds(),
                            audio.realTimeFactor(), audio.sampleRate()));
                    synthesizeButton.setEnabled(true); saveButton.setEnabled(true);
                });
            } catch (Exception e) { runOnUiThread(() -> { synthesizeButton.setEnabled(true); showError("合成失败", e); }); }
        });
    }

    private void showPacks(String type) {
        if (tts == null) return;
        worker.execute(() -> {
            try {
                List<TtsPack> packs = tts.availablePacks(type);
                runOnUiThread(() -> {
                    if (packs.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("没有可安装的资源包")
                                .setMessage("资源包均已安装，或模型没有配置下载目录。")
                                .setPositiveButton(android.R.string.ok, null).show();
                        return;
                    }
                    String[] labels = new String[packs.size()];
                    for (int i = 0; i < packs.size(); i++) labels[i] = packs.get(i).toString();
                    new AlertDialog.Builder(this).setTitle("选择资源包 / Select pack")
                            .setItems(labels, (d, which) -> installPack(packs.get(which)))
                            .setNegativeButton(android.R.string.cancel, null).show();
                });
            } catch (Exception e) { runOnUiThread(() -> showError("读取资源包失败", e)); }
        });
    }

    private void installPack(TtsPack pack) {
        status.setText("正在安装 / Installing " + pack);
        worker.execute(() -> {
            try {
                tts.install(pack);
                runOnUiThread(() -> { status.setText("安装完成，正在重新加载…"); loadSdk(); });
            } catch (Exception e) { runOnUiThread(() -> showError("资源包安装失败", e)); }
        });
    }

    private void saveWav() {
        TtsAudio audio = latest;
        if (audio == null) return;
        saveButton.setEnabled(false);
        worker.execute(() -> {
            String name = "tts-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".wav";
            try {
                Uri uri = WavSaver.save(getApplicationContext(), name, audio.samples(), audio.sampleRate());
                runOnUiThread(() -> { status.setText("已保存 / Saved: Downloads/TTSDemo/" + name); metrics.append("\n" + uri); saveButton.setEnabled(true); });
            } catch (Exception e) { runOnUiThread(() -> { saveButton.setEnabled(true); showError("保存 WAV 失败", e); }); }
        });
    }

    private void stop() { player.stop(); if (tts != null) tts.stop(); status.setText("已停止 / Stopped"); }
    private void showError(String title, Throwable e) {
        status.setText(title + "\n" + e.getMessage());
        new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage())
                .setPositiveButton(android.R.string.ok, null).show();
    }
    @Override protected void onDestroy() {
        player.close(); worker.shutdownNow(); if (tts != null) tts.close(); super.onDestroy();
    }
}
