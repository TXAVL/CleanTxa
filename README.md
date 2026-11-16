# CleanTxa

CleanTxa is an Android app that cleans an Android TV / Google TV from your phone over Wi-Fi (ADB): you pick which
manufacturer apps to turn off, the app backs up first so everything can be restored, and it can install **CleanTxa Home**,
a lighter home screen you can customise.

CleanTxa là app Android giúp dọn Android TV / Google TV từ điện thoại qua Wi-Fi (ADB): bạn tự chọn app của hãng cần tắt,
app sao lưu trước để khôi phục được, và có thể cài **CleanTxa Home**, màn hình chính nhẹ và tuỳ chỉnh được.

## Build

Needs JDK 17 and the Android SDK.

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. The home screen app lives in the `launcher` module; to rebuild it
and bundle it into the app, run `tools/build-txahome.ps1`.

## License and credit

The source code is released under the [MIT License](LICENSE). If you copy, modify or redistribute it, you must keep the
copyright notice and the licence text (that is how credit is given under MIT).

The names "CleanTxa" and "TXA", the logo and the artwork are not covered by that licence: if you publish a modified build,
please use your own name and artwork.

Open-source libraries used by the app, and their licences, are listed inside the app (Open-source licenses).
