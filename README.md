<div align="center">

# 🎧 0Sound

### Experimental Active Noise Cancellation for **any** headphones

![version](https://img.shields.io/badge/version-0.3-00C896?style=for-the-badge)
![platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![status](https://img.shields.io/badge/status-test%20build-FFC107?style=for-the-badge)

</div>

---

## ✨ What is it?

0Sound turns **non-ANC earbuds and headphones into "ANC-ish" ones**.
The phone's built-in microphone listens to the ambient noise, the app inverts the waveform and plays the **anti-noise as ordinary media audio** to your headphones.

```
 🎤 phone mic ──▶ DC blocker ──▶ low-pass ──▶ adaptive predictor ──▶ invert (−1×) ──▶ soft limiter ──▶ 🎧 media output
```

## 🧠 How it works (v0.2)

A phone-side loop has **20–60 ms latency**, far more than half a period of a hum, so a plain inverted copy arrives at a *random phase* (in simulation it even made things slightly worse).
Steady, periodic noise such as a **PC fan hum** is predictable, so 0Sound:

1. **Learns the noise** with an NLMS adaptive predictor and plays the *predicted* noise, inverted, so it arrives in phase at your ear.
2. **Measures the exact loop latency** with a one-time calibration (noise burst + cross-correlation, ~0.02 ms accuracy). A 1 ms error already ruins cancellation of higher harmonics, so this step matters.
3. **Fine trim** (±3 ms) lets you tune by ear.

Simulated fan hum: ~9 dB reduction with exact latency, ~6 dB with 0.2 ms error. Broadband noise and speech are not predictable and stay untouched.

### 🎯 Getting the best result
1. Connect **wired/USB** headphones.
2. *Settings ▸ Calibrate now*: press an ear cup tightly against the phone microphone (not on your ears!), wait for the burst.
3. Switch ON, then adjust *Strength*, the media volume and the *Fine trim* by ear.
4. Recalibrate whenever you change headphones or connection.

## 📱 Features

| | |
|---|---|
| 🟢 **Big ON/OFF slider** | Tap or drag. Always **OFF** after the app starts. |
| 🧠 **Adaptive ANC** | Predictor + latency calibration for steady hum (fans, AC, engines). |
| 🎚️ **Tuning** | Strength, low-pass cut-off, latency trim. |
| 🌙 **Themes** | Light, dark or follow system. |
| 🔄 **Background mode** | Optional foreground service keeps ANC alive with the screen off. |
| 🎙️ **Multi-mic** | Phone mic plus optional camera mics (rear / front direction), averaged. Device-dependent. |
| 🛡️ **Output guard** | Only **USB, 3.5 mm jack or Bluetooth media (A2DP)** outputs. The actual audio route is checked continuously; ANC stops if audio lands on the phone speaker. |

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

APKs live in [`Release/`](Release) and carry the version in their name: `0Sound-v<version>.apk`.

## 🧪 Version

**0.3** – multiple microphones (incl. camera mics), output-device recognition and live route guard.
**0.2** – adaptive predictor, latency calibration, trim, algorithm selector.
**0.1** – first test build.
