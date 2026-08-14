# TTS Android Demo

可直接运行的 Java 离线多语言 TTS 示例。这个公开仓库只包含编译好的 SDK AAR、
示例代码和模型安装脚本，不包含 SDK 私有实现源码。

## 直接运行

### 1. 放入训练模型

将 TTSTRAINER 导出的完整 `composable` 目录复制到：

```text
app/src/main/assets/tts/composable/
```

也可以使用脚本：

```bash
./scripts/install_composable_model.sh \
  /path/to/TTSTRAINER/artifacts/my_model
```

### 2. 构建 Demo

```bash
./gradlew :app:assembleDebug
```

APK：

```text
app/build/outputs/apk/debug/app-universal-debug.apk
```

Demo 支持 `arm64-v8a` 和 `armeabi-v7a`。没有安装模型时 App 可以启动，但会明确
提示模型资源不存在。

## 接入自己的 Android 项目

复制：

```text
app/libs/tts-android-sdk.aar
```

到你的项目：

```text
your-app/app/libs/tts-android-sdk.aar
```

在 `app/build.gradle` 中添加：

```gradle
dependencies {
    implementation files('libs/tts-android-sdk.aar')
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
}
```

模型仍放在：

```text
app/src/main/assets/tts/composable/
```

Java 调用：

```java
import com.xqsj.tts.TtsAudio;
import com.xqsj.tts.TtsClient;

ExecutorService worker = Executors.newSingleThreadExecutor();
worker.execute(() -> {
    try (TtsClient tts = TtsClient.create(context)) {
        TtsAudio audio = tts.speak(
                "你好，欢迎使用离线语音系统。",
                "zh",
                "voice_01");
    } catch (Exception error) {
        Log.e("TTS", "Speech synthesis failed", error);
    }
});
```

合成是同步操作，必须在工作线程调用。完整示例见
[MainActivity.java](app/src/main/java/com/example/ttsdemo/MainActivity.java)。

## SDK 包含内容

`tts-android-sdk.aar` 已整合：

- 公共 Java API：`com.xqsj.tts`
- ONNX Runtime 1.20
- Piper Plus G2P
- eSpeak NG
- OpenJTalk JNI
- ARM64 和 ARMv7 原生运行库
- ARMv7 严格对齐修复

不需要复制 C/C++ 源码，也不需要配置 `pickFirst`。

模型主体、语言资源和音色包不包含在 AAR 中，由 TTSTRAINER 导出并按产品需要安装。

## 项目关系

```text
TTSTRAINER              训练并导出 composable 模型
TTS-Android-SDK 私有库  维护源码并构建 AAR
本仓库                  公开分发 AAR 和 Java Demo
```

## 许可证

公开 AAR 包含 eSpeak NG GPL-3.0 组件。发布和再分发时必须遵守对应许可证义务。
第三方许可证随 SDK Release 一起提供。
