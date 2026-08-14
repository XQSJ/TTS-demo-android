# Android 离线多语言 TTS Demo

这是一个可以直接运行和集成的 Android 离线 TTS 示例项目，使用 Java 编写，支持：

- 完全离线语音合成
- 多语言模型、语言包和音色包
- `arm64-v8a` 与 `armeabi-v7a`
- 从 Android `assets` 自动加载模型
- 通过一个 AAR 快速接入现有项目

## 三步运行 Demo

### 第一步：准备模型

使用 TTSTRAINER 导出模型后，将完整的 `composable` 目录复制到：

```text
app/src/main/assets/tts/composable/
```

也可以使用脚本：

```bash
./scripts/install_composable_model.sh \
  /path/to/TTSTRAINER/artifacts/my_model
```

目录应类似：

```text
app/src/main/assets/tts/composable/
├── manifest.json
├── model/
├── languages/
└── voices/
```

实际文件以导出模型中的 `manifest.json` 为准，不要只复制 `model.onnx`。

### 第二步：构建 Demo

```bash
./gradlew :app:assembleDebug
```

APK：

```text
app/build/outputs/apk/debug/app-universal-debug.apk
```

Demo 支持 `arm64-v8a` 和 `armeabi-v7a`。没有安装模型时 App 可以启动，但会明确
提示模型资源不存在。

### 第三步：安装运行

连接 Android 设备后执行：

```bash
./gradlew :app:installDebug
```

打开 App，选择模型支持的语言和音色，然后输入文本进行合成。

## 接入自己的 Android 项目

将本项目中的：

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

然后将 TTSTRAINER 导出的完整模型放入：

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

        // audio.samples() 返回合成后的 float PCM 数据；
        // 具体播放方式请参考 Demo 中的 MainActivity。
    } catch (Exception error) {
        Log.e("TTS", "Speech synthesis failed", error);
    }
});
```

合成是同步操作，必须在工作线程调用。完整示例见
[MainActivity.java](app/src/main/java/com/example/ttsdemo/MainActivity.java)。

## 模型、语言和音色

模型采用可组合目录结构：

- `model/`：所有语言和音色共享的 TTS 主模型
- `languages/`：各语言需要的文本前端、词典和配置
- `voices/`：音色资源和音色映射
- `manifest.json`：描述可用语言、音色及资源位置

应用应通过 `manifest.json` 识别模型能力。增加或删除语言、替换音色时，应安装或
移除对应资源包并同步更新清单，不要直接修改 ONNX 输入编号。

## AAR 包含内容

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

## 使用要求

- Android 7.0（API 24）或更高版本
- Java 8 或更高版本
- 模型必须由兼容版本的 TTSTRAINER 导出
- 合成操作必须放在后台线程，不能阻塞 UI 线程
- 模型需要包含目标语言对应的前端资源

## 常见问题

### 提示找不到模型

确认存在以下文件：

```text
app/src/main/assets/tts/composable/manifest.json
```

并重新安装 App。Android Studio 的增量安装有时不会刷新大体积 assets，必要时先卸载旧版。

### 提示语言或 G2P 不匹配

不要只复制 ONNX 文件。重新复制完整 `composable` 目录，确保 `languages/` 和
`manifest.json` 来自同一次导出。

### ARMv7 设备启动失败

确认 App 没有在打包阶段过滤 `armeabi-v7a`，并检查最终 APK 中是否包含对应 `.so`。

### 如何缩小安装包

正式发布时建议使用 Android App Bundle，由应用商店按设备 ABI 下发原生库。语言包、
音色包和大模型也可以按需下载，不必全部放入基础 APK。

## 许可证

本项目使用的第三方组件具有各自的许可证。发布或再分发应用前，请阅读
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和 [`LICENSES/`](LICENSES/)，并履行
相应的许可证义务。
