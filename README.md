# 🛡 CloakDroid

**Anti-detect multi-profile Android browser** — run isolated, fingerprint-spoofed browsing sessions with per-profile proxy, timezone, locale, GPS and hardware fingerprints.

## ✨ Features
- 🧅 **Per-profile proxy**: SOCKS5 (remote DNS / socks5h), HTTP, HTTPS, Direct
- 🎭 **Fingerprint spoofing**: Canvas/Audio noise, hardware mocks, synchronized timezone+locale+GPS
- 🌐 **GeckoView engine** (Firefox) with WebRTC leak protection
- 🔒 **Zero-leak design**: only INTERNET + NETWORK_STATE permissions, no GPS access
- 🗂 **Sandboxed storage** per profile (cookies, IndexedDB, cache — 50MB auto-prune)
- 🎨 **Soft Dark / Spotify aesthetic** with Material 3 Expressive typography, metallic gradient headlines & glassmorphic cards

## 🏗 Tech Stack
Kotlin 100% · Jetpack Compose M3 · GeckoView · Hilt · Room (KSP) · OkHttp · kotlinx-serialization · JDK 17 · arm64-v8a

## 📥 Download
APK (debug, arm64-v8a) is built automatically by GitHub Actions on every push — see the **Actions** tab → latest run → **Artifacts**.

## 🚀 Build
```bash
./gradlew assembleDebug
```

## 📱 Screens
- **Profile List** — dashboard with search, filter chips, one-tap launch
- **Profile Editor** — 3-tab wizard (General / Network / Spoofing) with live proxy tester (latency, IP, country, ISP)
- **Browser** — GeckoView session with privacy HUD

---
Built with [Hermes Agent](https://github.com/NousResearch/hermes-agent) 🤖
