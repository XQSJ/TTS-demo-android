# Third-party notices

## ONNX Runtime

- Project: Microsoft ONNX Runtime
- Dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.20.0`
- License: MIT

## Piper Plus Android G2P

- Project: `io.github.ayutaz:piper-plus-g2p-android:1.0.0`
- License declared by its Maven POM: MIT
- Used by builds containing `piper` or `openjtalk`

## eSpeak NG

- Source location: `../third_party/espeak-ng/`
- License: GNU General Public License v3.0
- Included only when `-PttsFrontends` contains `espeak`

The `training_espeak_jni.c` and `openjtalk_basic_jni.c` files are project JNI
adapters. They are not upstream eSpeak source files.
