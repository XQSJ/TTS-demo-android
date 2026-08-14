# Android 离线多语言 TTS Demo

这是一个可以直接运行和集成的 Android 离线 TTS 示例项目，使用 Java 编写，支持：

- 完全离线语音合成
- 多语言模型、语言包和音色包
- `arm64-v8a` 与 `armeabi-v7a`
- 从 Android `assets` 自动加载模型
- 核心 SDK 与文本前端按需组合

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

### 先确认模型需要哪些前端

不要按语言名称猜测依赖。打开导出模型中每个语言的：

```text
composable/languages/<语言代码>/manifest.json
```

查看：

```json
{
  "frontend": {
    "provider": "piper-plus-g2p"
  }
}
```

也可以在 macOS/Linux 中一次列出：

```bash
grep -R '"provider"' composable/languages/*/manifest.json
```

根据出现过的 provider 选择模块：

| provider | 复制到 `app/libs/` | Gradle 上游依赖 |
|---|---|---|
| 所有模型 | `tts-sdk-core.aar` | ONNX Runtime，见下文 ABI 选择 |
| `espeak-ng` | `tts-frontend-espeak.aar` | 无额外 Maven 前端依赖 |
| `piper-plus-g2p` | `tts-frontend-piper.aar` | Piper Plus、Kotlin、协程 |
| `openjtalk` | `tts-frontend-openjtalk.aar` 和 `tts-frontend-piper.aar` | Piper Plus、Kotlin、协程 |

同一个多语言模型只需把用到的 provider 各添加一次。例如中英日模型通常同时需要
Piper、eSpeak 和 OpenJTalk；仅英文模型通常只需要 eSpeak。最终以语言包 manifest 为准。

语言词典不在这些 AAR 中，而在模型资源中：

```text
composable/languages/<语言代码>/runtime/
```

所以复制了前端 AAR 以后，仍必须安装完整语言包，不能只复制 `model.onnx`。

### 选择 ONNX Runtime

二选一，不要同时添加：

| 设备范围 | 使用方式 |
|---|---|
| 仅 `arm64-v8a` | Microsoft 官方 Maven 依赖 |
| 同时支持 `armeabi-v7a` | 本项目的 `tts-runtime-ort-v7a.aar` |

仅 ARM64：

```gradle
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'
```

包含旧 ARMv7：

```gradle
implementation files('libs/tts-runtime-ort-v7a.aar')
```

`tts-runtime-ort-v7a.aar` 同时包含 ARM64 与兼容 ARMv7 运行库，因此使用它以后不要再
添加 Microsoft ORT Maven AAR。

### 复制需要的 AAR

将本项目中需要的 XQSJ 模块复制到应用的 `app/libs/`：

```text
app/libs/tts-sdk-core.aar
app/libs/tts-frontend-espeak.aar       # 模型使用 espeak-ng 时
app/libs/tts-frontend-piper.aar        # 模型使用 piper-plus-g2p 时
app/libs/tts-frontend-openjtalk.aar    # 模型使用 openjtalk 时
```

到你的项目：

```text
your-app/app/libs/
```

### 配置 Gradle

以下是“ARM64 + eSpeak + Piper + OpenJTalk”的完整示例：

```gradle
dependencies {
    implementation files('libs/tts-sdk-core.aar')

    // 根据 composable/manifest.json 按需添加
    implementation files('libs/tts-frontend-espeak.aar')
    implementation files('libs/tts-frontend-piper.aar')
    implementation files('libs/tts-frontend-openjtalk.aar')

    // 仅 ARM64：使用 Microsoft 官方 ORT
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'

    // 仅 Piper/OpenJTalk 前端需要
    implementation 'io.github.ayutaz:piper-plus-g2p-android:1.0.0'
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
}

android {
    defaultConfig {
        ndk { abiFilters 'arm64-v8a' }
    }

    // Piper Plus 上游 AAR 也携带 libonnxruntime.so。
    // 明确让应用选择前面声明的 ORT，避免重复 Native 文件。
    packaging {
        jniLibs { pickFirsts += ['**/libonnxruntime.so'] }
    }
}
```

如果模型没有 Piper/OpenJTalk，不要添加 Piper Plus、Kotlin、协程以及对应的
`pickFirsts`。如果需要 ARMv7，则把 Microsoft ORT 那一行换成：

```gradle
implementation files('libs/tts-runtime-ort-v7a.aar')
```

并把 ABI 改为：

```gradle
ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }
```

### 常见组合

仅 eSpeak 模型、只支持 ARM64：

```gradle
implementation files('libs/tts-sdk-core.aar')
implementation files('libs/tts-frontend-espeak.aar')
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'
```

Piper 中文模型、只支持 ARM64：

```gradle
implementation files('libs/tts-sdk-core.aar')
implementation files('libs/tts-frontend-piper.aar')
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'
implementation 'io.github.ayutaz:piper-plus-g2p-android:1.0.0'
implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
```

OpenJTalk 日语模型必须同时添加 Piper 适配器：

```gradle
implementation files('libs/tts-sdk-core.aar')
implementation files('libs/tts-frontend-piper.aar')
implementation files('libs/tts-frontend-openjtalk.aar')
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'
implementation 'io.github.ayutaz:piper-plus-g2p-android:1.0.0'
implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
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

## AAR 模块说明

| AAR | 内容 | 是否必需 |
|---|---|---|
| `tts-sdk-core.aar` | `com.xqsj.tts` API、模型和资源包管理 | 必需 |
| `tts-frontend-espeak.aar` | eSpeak 训练兼容适配器 | 按模型 |
| `tts-frontend-piper.aar` | Piper Plus 训练兼容适配器 | 按模型 |
| `tts-frontend-openjtalk.aar` | OpenJTalk 基础音素适配器 | 按模型 |
| `tts-runtime-ort-v7a.aar` | ARM64 + 旧 ARMv7 兼容 ORT | 仅兼容旧设备时 |

Piper Plus、Kotlin 和协程由 Gradle 从上游仓库解析，不属于本项目源码。无需复制任何
C/C++ 文件到业务项目。

模型主体、语言资源和音色包不包含在 AAR 中，由 TTSTRAINER 导出并按产品需要安装。

## 使用要求

- Android 8.0（API 26）或更高版本
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

确认使用的是 `tts-runtime-ort-v7a.aar`，而不是 Microsoft 官方 ORT AAR；确认 App
没有过滤 `armeabi-v7a`，并检查最终 APK 中是否包含对应 `.so`。

### 提示重复的 libonnxruntime.so

Piper Plus 上游 AAR 自带 ORT Native 库。按上面的示例添加：

```gradle
packaging {
    jniLibs { pickFirsts += ['**/libonnxruntime.so'] }
}
```

同时确认 Microsoft ORT 和 `tts-runtime-ort-v7a.aar` 没有一起添加。

### 如何缩小安装包

正式发布时建议使用 Android App Bundle，由应用商店按设备 ABI 下发原生库。语言包、
音色包和大模型也可以按需下载，不必全部放入基础 APK。

## 许可证

本项目使用的第三方组件具有各自的许可证。发布或再分发应用前，请阅读
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和 [`LICENSES/`](LICENSES/)，并履行
相应的许可证义务。
