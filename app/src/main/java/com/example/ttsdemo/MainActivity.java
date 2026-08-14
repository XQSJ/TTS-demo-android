package com.example.ttsdemo;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xqsj.tts.TtsAudio;
import com.xqsj.tts.TtsClient;
import com.xqsj.tts.TtsVoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal public Java example; all TTS implementation lives in the AAR. */
public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<TtsVoice> voices = new ArrayList<>();
    private TtsClient tts;
    private Spinner voiceSpinner;
    private EditText textInput;
    private TextView status;
    private Button speakButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("TTS Android SDK Demo");
        title.setTextSize(24);
        voiceSpinner = new Spinner(this);
        textInput = new EditText(this);
        textInput.setText("你好，欢迎使用离线语音系统。");
        speakButton = new Button(this);
        speakButton.setText("合成并播放 / Speak");
        speakButton.setEnabled(false);
        Button stopButton = new Button(this);
        stopButton.setText("停止 / Stop");
        status = new TextView(this);
        status.setText("正在加载模型… / Loading model…");

        content.addView(title);
        content.addView(voiceSpinner);
        content.addView(textInput);
        content.addView(speakButton);
        content.addView(stopButton);
        content.addView(status);
        setContentView(content);

        speakButton.setOnClickListener(view -> synthesize());
        stopButton.setOnClickListener(view -> {
            if (tts != null) tts.stop();
        });
        loadSdk();
    }

    private void loadSdk() {
        worker.execute(() -> {
            try {
                TtsClient loaded = TtsClient.create(getApplicationContext());
                List<TtsVoice> installed = loaded.voices();
                runOnUiThread(() -> {
                    tts = loaded;
                    voices.clear();
                    voices.addAll(installed);
                    voiceSpinner.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item, voices));
                    speakButton.setEnabled(!voices.isEmpty());
                    status.setText("模型已加载 / Ready · voices=" + voices.size());
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(
                        "模型加载失败 / Load failed\n" + error.getMessage()));
            }
        });
    }

    private void synthesize() {
        int selected = voiceSpinner.getSelectedItemPosition();
        if (tts == null || selected < 0 || selected >= voices.size()) return;
        TtsVoice voice = voices.get(selected);
        String text = textInput.getText().toString();
        speakButton.setEnabled(false);
        status.setText("正在合成 / Synthesizing…");
        worker.execute(() -> {
            try {
                TtsAudio audio = tts.speak(text, voice.language(), voice.voice());
                runOnUiThread(() -> {
                    status.setText(String.format(Locale.US,
                            "完成 / Done · %.2fs · %dms · RTF %.3f",
                            audio.durationSeconds(), audio.inferenceMillis(),
                            audio.realTimeFactor()));
                    speakButton.setEnabled(true);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("合成失败 / Failed\n" + error.getMessage());
                    speakButton.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (tts != null) tts.close();
        super.onDestroy();
    }
}
