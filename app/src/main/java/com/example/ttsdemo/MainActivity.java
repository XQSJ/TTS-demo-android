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

/**
 * 完整的 Java 集成示例：演示私有 TTS SDK（AAR）的加载、音色选择、合成播放与 WAV 保存流程，界面逻辑在本类，合成实现位于 SDK 内。 / Complete Java integration example: drives the private TTS SDK (AAR) through load, voice selection, synthesis/playback and WAV saving; synthesis implementation lives in the SDK AAR.
 */
public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AudioPlayer player = new AudioPlayer();
    private final List<TtsVoice> voices = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();
    private TtsClient tts;
    private TtsAudio latest;
    private Spinner voiceSpinner, sampleSpinner;
    private EditText textInput;
    private TextView status, metrics, speedText, noiseText, durationNoiseText;
    private Slider speedSlider, noiseSlider, durationNoiseSlider;
    private Button synthesizeButton, saveButton;

    /** 生命周期入口：绑定视图与事件后立即开始加载 SDK 模型。 / Lifecycle entry: binds views and actions, then kicks off SDK model loading. */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        bindViews();
        bindActions();
        loadSdk();
    }

    /** 查找并持有所有界面控件引用。 / Looks up and holds references to all UI widgets. */
    private void bindViews() {
        voiceSpinner = findViewById(R.id.voiceSpinner);
        sampleSpinner = findViewById(R.id.sampleSpinner);
        textInput = findViewById(R.id.textInput);
        status = findViewById(R.id.statusText);
        metrics = findViewById(R.id.metricsText);
        speedText = findViewById(R.id.speedText);
        speedSlider = findViewById(R.id.speedSlider);
        noiseText = findViewById(R.id.noiseText);
        noiseSlider = findViewById(R.id.noiseSlider);
        durationNoiseText = findViewById(R.id.durationNoiseText);
        durationNoiseSlider = findViewById(R.id.durationNoiseSlider);
        synthesizeButton = findViewById(R.id.synthesizeButton);
        saveButton = findViewById(R.id.saveWavButton);
    }

    /** 绑定各控件的事件回调：音色切换刷新示例文本、按钮触发合成/停止/保存/资源包操作。 / Wires widget callbacks: voice selection refreshes sample texts; buttons trigger synthesis, stop, save and pack operations. */
    private void bindActions() {
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                // 选中音色变化时按语言刷新示例，避免旧语言文本误导用户。 / Refresh samples by language on voice change so stale-language text does not mislead.
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
        // 程序内 setAdapter 也会触发监听，用 user 标记可区分用户拖动。 / Programmatic setAdapter also fires listeners; the user flag distinguishes real drags.
        speedSlider.addOnChangeListener((s, value, user) ->
                speedText.setText(String.format(Locale.US, "语速 / Speed: %.2f", value)));
        noiseSlider.addOnChangeListener((s, value, user) ->
                noiseText.setText(String.format(Locale.US, "噪声 / Noise: %.2f", value)));
        durationNoiseSlider.addOnChangeListener((s, value, user) ->
                durationNoiseText.setText(String.format(Locale.US, "节奏噪声 / Duration noise: %.2f", value)));
        synthesizeButton.setOnClickListener(v -> synthesize());
        findViewById(R.id.stopButton).setOnClickListener(v -> stop());
        saveButton.setOnClickListener(v -> saveWav());
        findViewById(R.id.downloadLanguageButton).setOnClickListener(v -> showPacks("languages"));
        findViewById(R.id.downloadVoiceButton).setOnClickListener(v -> showPacks("voices"));
    }

    /** 后台线程加载 SDK 模型，成功后回主线程更新音色列表与就绪状态。 / Loads the SDK model on a worker thread; on success updates the voice list and ready state on the UI thread. */
    private void loadSdk() {
        // 加载期间禁用合成，防止对未就绪的客户端发起请求。 / Disable synthesis while loading so requests never hit a not-ready client.
        synthesizeButton.setEnabled(false);
        status.setText("正在加载模型… / Loading model…");
        worker.execute(() -> {
            try {
                TtsClient loaded = TtsClient.create(getApplicationContext());
                List<TtsVoice> installed = loaded.voices();
                List<String> diagnostics = loaded.diagnostics();
                runOnUiThread(() -> {
                    // 重复 loadSdk（如安装包后）时先关闭旧实例，避免模型内存泄漏。 / Close the previous instance on repeated loadSdk (e.g. after pack install) to avoid leaking model memory.
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

    /** 按所选语言切换示例文本列表。 / Switches the sample text list to the selected language. */
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

    /** 后台线程执行合成并立即播放，主线程展示耗时与 RTF 指标。 / Runs synthesis on a worker thread and plays immediately; the UI thread shows latency and RTF metrics. */
    private void synthesize() {
        int i = voiceSpinner.getSelectedItemPosition();
        if (tts == null || i < 0 || i >= voices.size()) return;
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) return;
        TtsVoice voice = voices.get(i);
        // 先停止旧音频并禁用按钮，保证一次只有一个合成任务在跑。 / Stop stale audio and disable buttons so only one synthesis runs at a time.
        player.stop(); synthesizeButton.setEnabled(false); saveButton.setEnabled(false);
        status.setText("正在合成 / Synthesizing · " + voice); metrics.setText("");
        float speed = speedSlider.getValue();
        float noise = noiseSlider.getValue();
        float durationNoise = durationNoiseSlider.getValue();
        worker.execute(() -> {
            try {
                // 合成前应用进阶调优（滑杆实时值），线程数保持自动。 / Apply advanced tuning from the sliders before synthesizing; threads stay automatic.
                tts.configure(t -> t
                        .noiseScale(noise)
                        .durationNoiseScale(durationNoise));
                TtsAudio audio = tts.synthesize(text, voice.language(), voice.voice(), speed);
                // 后台合成完直接播放，避免再回主线程排队引入延迟。 / Play right from the worker to avoid extra UI-thread queuing latency.
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

    /** 查询可下载的语言/音色资源包并以对话框列表供选择。 / Queries downloadable language/voice packs and offers them in a dialog list. */
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

    /** 安装指定资源包，成功后重新加载 SDK 以纳入新音色。 / Installs the given pack, then reloads the SDK so the new voices become visible. */
    private void installPack(TtsPack pack) {
        status.setText("正在安装 / Installing " + pack);
        worker.execute(() -> {
            try {
                tts.install(pack);
                // 安装改变磁盘内容，必须重建客户端才能生效。 / Installation mutates disk state; the client must be rebuilt for it to take effect.
                runOnUiThread(() -> { status.setText("安装完成，正在重新加载…"); loadSdk(); });
            } catch (Exception e) { runOnUiThread(() -> showError("资源包安装失败", e)); }
        });
    }

    /** 将最近一次合成结果编码为 WAV 并写入 Downloads/TTSDemo。 / Encodes the latest synthesis result as WAV and writes it to Downloads/TTSDemo. */
    private void saveWav() {
        TtsAudio audio = latest;
        if (audio == null) return;
        saveButton.setEnabled(false);
        worker.execute(() -> {
            // 时间戳命名避免多次保存相互覆盖。 / Timestamped name avoids overwriting previous saves.
            String name = "tts-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".wav";
            try {
                Uri uri = WavSaver.save(getApplicationContext(), name, audio.samples(), audio.sampleRate());
                runOnUiThread(() -> { status.setText("已保存 / Saved: Downloads/TTSDemo/" + name); metrics.append("\n" + uri); saveButton.setEnabled(true); });
            } catch (Exception e) { runOnUiThread(() -> { saveButton.setEnabled(true); showError("保存 WAV 失败", e); }); }
        });
    }

    /** 停止当前播放并中断进行中的合成。 / Stops current playback and aborts any in-flight synthesis. */
    private void stop() { player.stop(); if (tts != null) tts.stop(); status.setText("已停止 / Stopped"); }
    /** 在状态栏与对话框中同时展示错误详情。 / Surfaces error details both in the status line and a dialog. */
    private void showError(String title, Throwable e) {
        status.setText(title + "\n" + e.getMessage());
        new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage())
                .setPositiveButton(android.R.string.ok, null).show();
    }
    /** 释放播放器、线程池与 SDK 客户端等原生资源。 / Releases the player, worker pool and SDK client native resources. */
    @Override protected void onDestroy() {
        player.close(); worker.shutdownNow(); if (tts != null) tts.close(); super.onDestroy();
    }
}
