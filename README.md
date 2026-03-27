# Homelab

> **Tailscale-native SSH client for Android.**  
> Tap a host. Connect. No IP addresses, no port numbers, no config files.

[![Build](https://github.com/SwedishLesbian/Homelab/actions/workflows/build.yml/badge.svg)](https://github.com/SwedishLesbian/Homelab/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/SwedishLesbian/Homelab)](https://github.com/SwedishLesbian/Homelab/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Android-8.0%2B-blue.svg)]()

---

## Download

**[→ Latest Release](../../releases/latest)**

Download `Homelab-vX.X.X.apk` and sideload it onto your Android device.

<details>
<summary>Sideload instructions</summary>

1. On your device open **Settings → Apps → Special app access → Install unknown apps**.
2. Enable installs from your browser or file manager.
3. Open the downloaded APK and tap **Install**.

</details>

---

## First-time setup (2 minutes)

### 1 — Create a Tailscale OAuth client

Every user brings their own OAuth client — nothing is hard-coded into the app.

1. Open [Tailscale Admin Console → Settings → OAuth](https://login.tailscale.com/admin/settings/oauth).
2. Click **Add OAuth client**.
3. Configure it:
   | Field | Value |
   |---|---|
   | **Scopes** | `devices:read` |
   | **Redirect URI** | `com.homelab.app://oauth` |
4. Click **Generate** and copy the **Client ID** (looks like `tskey-client-xxxx...`).

> The **client secret is not needed** — Homelab uses the OAuth PKCE flow, which is designed for mobile apps and never requires a secret.

### 2 — Sign in inside Homelab

1. Open Homelab.
2. Tap **Open Tailscale Admin Console** (or paste your Client ID directly).
3. Enter your Client ID and tap **Continue**.
4. Tap **Sign in with Tailscale** — a browser window opens.
5. Approve access. Done — your Tailscale devices appear automatically.

---

## Features

| Feature | Details |
|---|---|
| **Auto-discovery** | Pulls your Tailscale device list from the API every 60 s — no manual IP entry ever |
| **One-tap connect** | Favorites + Recents sections for ≤ 2-tap access to any host |
| **SSH key management** | Generate ed25519 keys backed by Android Keystore (TEE hardware) — private key never leaves the device |
| **Biometric unlock** | Keys are gated behind fingerprint / face auth |
| **Full terminal** | ANSI colour, 10 000-line scrollback, custom Ctrl/Alt/arrow toolbar |
| **Auto-reconnect** | Exponential back-off reconnect on WiFi → Cellular or network drops |
| **Background sessions** | Sessions survive app backgrounding via a foreground service |
| **Offline cache** | Last-known host list shown when offline |
| **Clipboard auto-clear** | Copied public keys erased from clipboard after 30 s |
| **Screenshot protection** | Terminal window excluded from screenshots (configurable) |

---

## Architecture

```
Android App (Kotlin + Jetpack Compose)
├── UI Layer
│   ├── Onboarding  (OAuth client ID entry + Tailscale sign-in)
│   ├── Home        (favorites / recents / all hosts, instant search)
│   ├── Terminal    (ANSI output, special-key toolbar, input bar)
│   ├── SSH Keys    (generate, copy public key, delete)
│   └── Settings    (session timeout, clipboard clear, screenshot lock)
├── Domain Layer
│   └── ViewModels + coroutine-based use-cases
├── Data Layer
│   ├── Tailscale API   Retrofit + Moshi → GET /api/v2/tailnet/{tailnet}/devices
│   ├── SSH Engine      sshj + AndroidKeystore KeyProvider (TEE-backed signing)
│   ├── Secure Storage  DataStore (tokens, client ID) + Android Keystore (SSH keys)
│   └── Local DB        Room (hosts, sessions, SSH key metadata)
└── Background
    ├── HostSyncWorker  WorkManager periodic 60 s sync
    └── SshSessionService  Foreground service — keeps sessions alive when backgrounded
```

**Tech stack:** Kotlin · Jetpack Compose · Hilt · Room · Retrofit · sshj · AppAuth · WorkManager · Android Keystore

---

## Building from source

```bash
git clone https://github.com/SwedishLesbian/Homelab
cd Homelab

# JDK 17 and Android SDK 34 required
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

No secrets needed to build. The OAuth client ID is entered by the user at runtime.

---

## CI / CD

| Workflow | Trigger | Output |
|---|---|---|
| `build.yml` | Every push / PR | Debug APK uploaded as artifact |
| `release.yml` | Git tag `v*.*.*` | GitHub Release created with signed APK attached |

### Publishing a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release workflow builds, signs (if keystore secrets are set), and publishes automatically.

### Optional signing secrets

Add these in **Settings → Secrets and variables → Actions** if you want signed APKs:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore (`base64 -i my.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Unsigned APKs from the debug build can always be sideloaded without signing.

---

## Contributing

PRs welcome. Please open an issue first for anything beyond small fixes.

1. Fork the repo and create a branch.
2. Build with `./gradlew assembleDebug` and verify it compiles.
3. Open a PR against `main`.

---

## License

MIT — see [LICENSE](LICENSE).
