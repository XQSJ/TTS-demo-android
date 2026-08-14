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
| 所有模型 | `tts-sdk-core.aar` | ONNX Runtime 1.22（官方 Maven 包） |
| `piper-plus-g2p` | `tts-frontend-piper.aar` | Kotlin、协程 |
| `openjtalk` | `tts-frontend-openjtalk.aar` 和 `tts-frontend-piper.aar` | Kotlin、协程 |

同一个多语言模型只需把用到的 provider 各添加一次。`tts-frontend-piper.aar`
已内置 Piper Plus 的 Java 类与重链接到 ONNX Runtime 1.22 的原生库，
`openjtalk` 复用同一份原生库。

`espeak-ng` 前端已在本版本移除：声明该 provider 的旧语言包（如本 Demo
内置的英文包）会得到“尚未接入前端 provider”的明确报错，等待训练侧导出
Piper 前端的英文模型后替换。

语言词典不在这些 AAR 中，而在模型资源中：

```text
composable/languages/<语言代码>/runtime/
```

所以复制了前端 AAR 以后，仍必须安装完整语言包，不能只复制 `model.onnx`。

### ONNX Runtime

统一使用 Microsoft 官方 Maven 包（1.22），同时覆盖 `arm64-v8a` 与
`armeabi-v7a`：

```gradle
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.22.0'
```

不再提供单独的 `tts-runtime-ort-v7a.aar`；Piper 前端原生库已重链接为仅依赖
外部 `libonnxruntime.so`（符号版本 `VERS_1.22.0`），动态链接器会强制版本匹配，
不会误绑旧版运行库。

### 复制需要的 AAR

将本项目中需要的 XQSJ 模块复制到应用的 `app/libs/`：

```text
app/libs/tts-sdk-core.aar
app/libs/tts-frontend-piper.aar        # 模型使用 piper-plus-g2p 时
app/libs/tts-frontend-openjtalk.aar    # 模型使用 openjtalk 时
```

到你的项目：

```text
your-app/app/libs/
```

### 配置 Gradle

以下是“ARM64/ARMv7 + Piper + OpenJTalk”的完整示例：

```gradle
dependencies {
    implementation files('libs/tts-sdk-core.aar')

    // 根据 composable/manifest.json 按需添加
    implementation files('libs/tts-frontend-piper.aar')
    implementation files('libs/tts-frontend-openjtalk.aar')

    // ONNX Runtime 1.22（arm64-v8a 与 armeabi-v7a）
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.22.0'

    // Piper/OpenJTalk 前端需要
    implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
}

android {
    defaultConfig {
        ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }
    }
}
```

### 常见组合

Piper 中文模型：

```gradle
implementation files('libs/tts-sdk-core.aar')
implementation files('libs/tts-frontend-piper.aar')
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.22.0'
implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.1.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0'
```

OpenJTalk 日语模型必须同时添加 Piper 适配器：

```gradle
implementation files('libs/tts-sdk-core.aar')
implementation files('libs/tts-frontend-piper.aar')
implementation files('libs/tts-frontend-openjtalk.aar')
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.22.0'
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
| `tts-frontend-piper.aar` | Piper Plus 适配器（含重链接原生库与 Java 类） | 按模型 |
| `tts-frontend-openjtalk.aar` | OpenJTalk 基础音素适配器（复用 Piper 原生库） | 按模型 |

Kotlin 和协程由 Gradle 从上游仓库解析；ONNX Runtime 1.22 由 Microsoft 官方
Maven 包提供。无需复制任何 C/C++ 文件到业务项目。

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

确认 App 没有过滤 `armeabi-v7a`，并检查最终 APK 中是否包含对应 `.so`。
ONNX Runtime 1.22 官方 Maven 包本身覆盖两种 ARM ABI。

### 提示尚未接入前端 provider

模型语言包声明了 `espeak-ng` 等已移除的前端。等待训练侧导出对应语言
Piper 前端模型后替换语言包即可，SDK 不会回退到错误发音。

### 如何缩小安装包

正式发布时建议使用 Android App Bundle，由应用商店按设备 ABI 下发原生库。语言包、
音色包和大模型也可以按需下载，不必全部放入基础 APK。

## 许可证

本项目使用的第三方组件具有各自的许可证。发布或再分发应用前，请阅读
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和 [`LICENSES/`](LICENSES/)，并履行
相应的许可证义务。
