<div align="center">

# 🎧 0Sound

### Experimental Active Noise Cancellation for **any** headphones

![version](https://img.shields.io/badge/version-0.1-00C896?style=for-the-badge)
![platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![status](https://img.shields.io/badge/status-test%20build-FFC107?style=for-the-badge)

</div>

---

## ✨ What is it?

0Sound turns **non-ANC earbuds and headphones into "ANC-ish" ones**.
The phone's built-in microphone listens to the ambient noise, the app inverts the waveform and plays the **anti-noise as ordinary media audio** to your headphones.

```
 🎤 phone mic ──▶ DC blocker ──▶ low-pass ──▶ phase invert (−1×) ──▶ soft limiter ──▶ 🎧 media output
```

## 📱 Features

| | |
|---|---|
| 🟢 **Big ON/OFF slider** | Tap or drag. Always **OFF** after the app starts. |
| 🎚️ **Tuning** | Anti-noise strength and low-pass cut-off. |
| 🌙 **Themes** | Light, dark or follow system. |
| 🔄 **Background mode** | Optional foreground service keeps ANC alive with the screen off. |
| 🛡️ **Safety guard** | Refuses to start (and stops) without headphones, to avoid speaker feedback. |

## ⚠️ Read this first

> ⚠️ The ANC provided by this app is **completely different** from the solutions fine-tuned by official headphone manufacturers and is **phone-dependent**.
>
> ❗ **Use at your own risk.** Keep the volume low. Not suitable for traffic, safety-critical situations or hearing protection.

**Known physical limits (test build):**
- Phone-side loop latency means only **low-frequency, steady noise** (engines, hum, AC) can be reduced; speech and sharp sounds cannot.
- **Wired / USB** headphones work best. Bluetooth adds 100+ ms of delay, which makes cancellation largely ineffective.
- Results vary strongly with microphone position, phone model and earbud fit.

## 🛠️ Build

Requirements: JDK 17+, Android SDK 34.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease        # app/build/outputs/apk/release/
./gradlew exportReleaseApk       # copies to Release/0Sound-v<version>.apk
```

## 🔐 Signing

Builds are signed with a **test certificate** (`app/keystore/0sound-test.jks`, password `0soundtest`) that is public in this repo. It is for testing only and must **not** be used for a production/store release.

## 📦 Releases

APKs live in [`Release/`](Release) and carry the version in their name: `0Sound-v0.1.apk`.

## 🧪 Version

**0.1** – first test build.
