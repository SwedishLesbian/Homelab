# Homelab

**A Tailscale-native SSH client for Android.**  
Tap a host. Connect. No IP addresses, no port numbers, no config files.

---

## Download

Grab the latest APK from the [**Releases**](../../releases/latest) page.

> **Minimum Android version:** 8.0 (API 26)

### Sideload instructions
1. Download `Homelab-vX.X.X.apk` from Releases.
2. On your device: *Settings → Apps → Special app access → Install unknown apps* — enable for your browser or file manager.
3. Open the APK and tap **Install**.

---

## Setup (2 steps)

### Step 1 — Create a Tailscale OAuth client

1. Open [Tailscale Admin Console → Settings → OAuth](https://login.tailscale.com/admin/settings/oauth).
2. Click **Add OAuth client**.
3. Set **Scopes** → `devices:read`.
4. Set **Redirect URI** → `com.homelab.app://oauth`
5. Click **Generate** and copy the **Client ID** (starts with `tskey-client-...`).

> The client secret is **not needed** by the app — Homelab uses the PKCE flow.

### Step 2 — Sign in inside the app

1. Open Homelab.
2. Paste your **Client ID** and tap **Continue**.
3. Tap **Sign in with Tailscale** — a browser window opens.
4. Approve the access request.
5. Your Tailscale devices appear. Tap **Connect** on any online host.

Every user brings their own client ID. Nothing is hard-coded into the build.

---

## Features

| | |
|---|---|
| **Auto-discovery** | Reads your Tailscale device list via the API — no manual IP entry |
| **One-tap connect** | Favorites + Recents for ≤ 2-tap access |
| **SSH key management** | Generate ed25519 keys backed by Android Keystore (TEE) |
| **Full terminal** | ANSI colour, 10k-line scrollback, Ctrl/Alt/arrow toolbar |
| **Auto-reconnect** | Exponential back-off when WiFi → Cellular or network drops |
| **Session persistence** | Sessions survive app backgrounding |
| **Security** | Keys never leave the device, biometric-gated, clipboard auto-clears after 30 s |

---

## Building from source

```bash
git clone https://github.com/SwedishLesbian/Homelab
cd Homelab

# Copy and fill in your client ID (optional — can also be entered at runtime)
echo "TAILSCALE_CLIENT_ID=" >> local.properties

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires **JDK 17** and **Android SDK 34**.

### CI / GitHub Actions

The workflow at `.github/workflows/build.yml` builds on every push.  
No secrets are required — the OAuth client ID is entered by the user at runtime.

For **signed release builds** add these repository secrets:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Tagging a commit `vX.Y.Z` triggers `.github/workflows/release.yml` which creates a GitHub Release and attaches the APK automatically.

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Architecture

```
Android App
├── UI Layer          Jetpack Compose + Material 3
├── Domain Layer      ViewModels, UseCases
├── Data Layer
│   ├── Tailscale API  Retrofit + Moshi  →  /api/v2/tailnet/{tailnet}/devices
│   ├── SSH Engine     sshj + Android Keystore
│   ├── Secure Storage DataStore (tokens) + Keystore (SSH private keys)
│   └── Local DB       Room
└── Background        WorkManager (60s host sync), Foreground Service (sessions)
```

---

## License

MIT
