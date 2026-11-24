# AKSdown Mobile

Android video & audio downloader. **COPY LINK → PASTE → MP3 or MP4 → DOWNLOAD → DONE.**
Everything runs on the phone: no servers, no accounts, no ads, no tracking.

## تثبيت التطبيق (Installation)

1. انقل الملف `AKSdown.apk` إلى هاتف Samsung (أو نزّله مباشرة على الهاتف).
2. افتحه من «ملفاتي» (My Files). سيطلب Android السماح بتثبيت التطبيقات من هذا المصدر لمرة واحدة: اضغط «الإعدادات» ثم فعّل «السماح من هذا المصدر» وارجع.
3. اضغط «تثبيت». قد يعرض Play Protect فحصاً لتطبيق غير معروف لأنه ليس من متجر Play؛ اختر «فحص التطبيق» ثم «تثبيت» إذا كان الفحص سليماً.
4. افتح AKSdown، الصق الرابط، اختر MP4 أو MP3، واضغط «تنزيل». الملفات تُحفظ في `Internal Storage/Download/AKSdown/`.

Share from YouTube, Instagram, Facebook, TikTok, X… → choose **AKSdown** in the share sheet.

## How it works

| Component | Version | Role |
|---|---|---|
| yt-dlp (official zipimport build, includes yt-dlp-ejs) | 2026.11.23 | Extraction, format selection, download |
| CPython for Android (youtubedl-android) | 3.11 | Runs yt-dlp on the device |
| FFmpeg / FFprobe for Android (youtubedl-android) | 0.17.2 package | Merge video+audio, MP3 320 kbps, thumbnails |
| QuickJS-NG (cross-compiled with the NDK) | v0.18.0 | Solves YouTube's JavaScript challenges on-device |
| Kotlin + Jetpack Compose + Material 3 | — | App UI |

Native programs are packaged as `lib*.so` files and extracted to the app's `nativeLibraryDir`
(`extractNativeLibs=true`), the only app location Android allows to execute (W^X since Android 10).

**Automatic highest quality.** MP4 uses `-f "bv*+ba/b" -S "res,fps,hdr:12,acodec:aac"`: the highest
resolution and frame rate always win (8K/4K/1440p/1080p…), streams are merged into MP4 without
re-encoding (`--merge-output-format mp4`, `--remux-video mp4`). AAC audio is preferred over Opus only
because Opus inside MP4 is not playable in every player. If the best streams can't be remuxed into MP4,
the app re-encodes once (`--recode-video mp4`). MP3 extracts the best audio and encodes 320 kbps
(this cannot improve on the source quality) with embedded title and cover art.

**Storage.** Files are written through MediaStore into `Download/AKSdown/` (Android 10+), or the public
Download folder with `WRITE_EXTERNAL_STORAGE` on Android 8–9, so they appear in My Files, Gallery and
music players. Duplicate names get ` (1)`, ` (2)`… and nothing is overwritten. A success notification is
only shown after the file passes a container-signature and MediaMetadataRetriever check.

**Limits.** No DRM bypass, no private/login-only content, no cookies. Websites change; when one breaks,
a new yt-dlp version must be bundled (change `engine/versions.env`, rebuild). The app never updates
itself from the internet.

## Build

Builds run on GitHub Actions (`.github/workflows/build.yml`): fetches and SHA-256-verifies yt-dlp,
cross-compiles QuickJS-NG, builds and signs the release, verifies the signature, then runs a second job
that installs a debug build on an Android emulator and performs real downloads.

Local build (JDK 17, Android SDK 36, NDK, cmake, ninja):

```bash
scripts/fetch-engine.sh
ANDROID_NDK_HOME=/path/to/ndk scripts/build-quickjs.sh arm64-v8a
AKS_KEYSTORE=/path/aksdown-release.jks AKS_KEYSTORE_PASSWORD=… AKS_KEY_ALIAS=aksdown AKS_KEY_PASSWORD=… \
  ./gradlew :app:assembleRelease
```

**Signing key.** Every future version must be signed with the same `aksdown-release.jks`, or Android
will refuse to update the installed app. Keep the key and its password in a safe place (it is not
stored in this repository; CI reads it from encrypted repository secrets).

## Licenses

See `app/src/main/res/raw/licenses.txt`. Because youtubedl-android is GPL-3.0, AKSdown Mobile is GPL-3.0.
