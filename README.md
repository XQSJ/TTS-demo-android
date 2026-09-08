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

### 界面调优滑杆

除语速外，Demo 提供两个进阶滑杆（通过 `TtsClient.configure` 实时生效）：

| 滑杆 | 默认 | 作用 |
|---|---|---|
| 噪声 / Noise | 0.67 | VITS 合成噪声：调低更稳定一致，调高更自然多变，过大出现杂音。常用 [0.2, 0.9] |
| 节奏噪声 / Duration noise | 0.35 | 时长预测随机度：0 为每次完全相同的节奏。常用 [0.1, 0.8] |

这两个值即训练导出的推荐默认（模型 `manifest.json` 的
`scales_default: [0.667, 1.0, 0.35]`）；滑杆在合成前自动应用当前值，
拖动后无需重新加载模型。接入方代码中的等价写法：

```java
tts.configure(t -> t
        .noiseScale(0.7f)
        .durationNoiseScale(0.4f)
        .numThreads(4));  // 0 = 自动（核心数一半，上限 4）
```

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
| `espeak-ng`（旧模型兼容） | `tts-frontend-espeak.aar` | 无 |

同一个多语言模型只需把用到的 provider 各添加一次。`tts-frontend-piper.aar`
已内置 Piper Plus 的 Java 类与重链接到 ONNX Runtime 1.22 的原生库，
`openjtalk` 复用同一份原生库。

`espeak-ng` 前端只为兼容旧语言包（如本 Demo 内置的英文包）保留，后续新
模型不再使用。它是 GPLv3 eSpeak NG 代码的唯一载体：不需要旧模型兼容时，
删除 `tts-frontend-espeak.aar` 及其一行依赖即可彻底解除 GPL 义务，其余
AAR 与核心库均不含 eSpeak 代码（核心通过反射装载前端，缺失时报
“缺少前端模块”的明确错误）。

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

Piper 前端原生库已重链接为仅依赖外部 `libonnxruntime.so`（符号版本
`VERS_1.22.0`），动态链接器会强制版本匹配，不会误绑旧版运行库。

### 旧 ARMv7 真机 SIGBUS 兼容包

官方 1.22 的 `armeabi-v7a` 库在加载模型时仍会对未对齐地址做双字读取，
严格对齐的旧 ARMv7 真机会直接 SIGBUS（上游修复要到 ORT 1.23）。需要兼容
这类设备时，额外引入本项目重新构建的严格对齐覆盖包 `tts-runtime-ort-v7a.aar`：

```gradle
implementation files('libs/tts-runtime-ort-v7a.aar')

android {
    packaging {
        jniLibs { pickFirsts += ['**/libonnxruntime.so'] }
    }
}
```

覆盖包只含一份自建 v7a `libonnxruntime.so`（脚本 `setup_ort_v7a.sh` 以
`-mno-unaligned-access` 加对齐补丁构建），arm64 仍使用官方库。强烈建议
照搬本 Demo 的 `verifyV7aOrtOverride` 任务，在打包后校验 v7a 命中的是
严格对齐版本，避免 pickFirsts 静默选错。

### 复制需要的 AAR

将本项目中需要的 XQSJ 模块复制到应用的 `app/libs/`：

```text
app/libs/tts-sdk-core.aar
app/libs/tts-frontend-piper.aar        # 模型使用 piper-plus-g2p 时
app/libs/tts-frontend-openjtalk.aar    # 模型使用 openjtalk 时
app/libs/tts-frontend-espeak.aar       # 兼容旧 espeak-ng 语言包时（GPLv3，可选）
app/libs/tts-runtime-ort-v7a.aar       # 需兼容旧 ARMv7 真机时
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
    // 旧 espeak-ng 语言包兼容，可选（不删也不影响新模型）
    implementation files('libs/tts-frontend-espeak.aar')

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

### ARMv7 设备启动失败 / 加载模型即闪退

确认 App 没有过滤 `armeabi-v7a`；若闪退日志包含 `SIGBUS`/`BUS_ADRALN`，
说明打包进了官方 v7a 库 —— 引入 `tts-runtime-ort-v7a.aar` 覆盖包并配置
`pickFirsts`，再用 `verifyV7aOrtOverride` 任务确认命中覆盖版本。

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
