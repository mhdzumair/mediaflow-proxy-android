# MediaFlow Proxy · Android

Native Android / Android TV app that runs the Rust
[mediaflow-proxy-light](https://github.com/mhdzumair/mediaflow-proxy-light) server
as a foreground service on-device.

## Installation & Play Protect warning

When you side-load the APK, Google Play Protect will flag it as
**"Harmful app blocked"**. This is a false positive — tap **More details →
Install anyway** to proceed.

The combination that trips Play Protect's heuristics is:

- The app spawns a native subprocess (`libmediaflow-proxy.so` via `ProcessBuilder`).
- It opens a TCP listener on `0.0.0.0:8888` for local HTTP access.
- It requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` so streams don't stall when backgrounded.
- The APK is signed with a self-published certificate, not one linked to a
  Google Play publisher account.

Each is necessary for the proxy to work (see the architecture section
below) and nothing is uploaded or exfiltrated. Once the app has an
established install base / is reviewed by Google, Play Protect stops
flagging it.

If you want to audit what the spawned binary does, the source is at
[mediaflow-proxy-light](https://github.com/mhdzumair/mediaflow-proxy-light)
and every release is reproducibly built from tagged commits by the
companion CI.

## Architecture

The proxy binary is **bundled inside the APK** as a regular JNI library
(`libmediaflow-proxy.so`) for each supported Android ABI. At install time
Android extracts it to `nativeLibraryDir`, which is executable — the app's
`ProxyManager` then spawns the binary with `ProcessBuilder`.

The UI ships in **two product flavors**:

| Flavor   | Package id              | Form factor     | UI toolkit                                    | Entry point             |
|----------|-------------------------|-----------------|-----------------------------------------------|-------------------------|
| `mobile` | `com.mediaflow.proxy`   | phone / tablet  | Material 3 + bottom nav                       | `MainActivity`          |
| `tv`     | `com.mediaflow.proxy.tv` | Android TV      | Leanback `BrowseSupportFragment` + GuidedStep | `tv.TvMainActivity`     |

Each flavor has a different `applicationId`, so users can install both on the
same device without collision. Play Store-style discovery filters correctly —
the TV flavor declares `<uses-feature android:software.leanback required="true">`
so phone stores hide it, and the mobile flavor is the one shown everywhere else.

```
mediaflow-android/
├── app/src/
│   ├── main/                              ← shared across both flavors
│   │   ├── jniLibs/<abi>/libmediaflow-proxy.so   (not committed; see Building)
│   │   ├── java/com/mediaflow/proxy/
│   │   │   ├── ProxyService.kt            ← foreground service
│   │   │   ├── ProxyManager.kt            ← subprocess + stdout capture
│   │   │   ├── ConfigRepository.kt        ← DataStore
│   │   │   ├── BootReceiver.kt            ← auto-start on boot
│   │   │   └── ui/MainViewModel.kt        ← shared VM
│   │   ├── AndroidManifest.xml            ← permissions, service, receiver
│   │   └── res/drawable, mipmap, xml      ← icons, network config
│   │
│   ├── mobile/                            ← phone / tablet UI
│   │   ├── AndroidManifest.xml            ← MainActivity + LAUNCHER category
│   │   ├── java/com/mediaflow/proxy/
│   │   │   ├── MainActivity.kt
│   │   │   └── ui/{StatusFragment, ConfigFragment, MetricsFragment}.kt
│   │   └── res/                           ← Material 3 layouts + theme
│   │
│   └── tv/                                ← Android TV UI
│       ├── AndroidManifest.xml            ← TvMainActivity + LEANBACK_LAUNCHER
│       ├── java/com/mediaflow/proxy/tv/
│       │   ├── TvMainActivity.kt          ← BrowseSupportFragment
│       │   ├── TvPortGuidedStep.kt        ← remote-friendly port editor
│       │   └── TvPasswordGuidedStep.kt    ← remote-friendly password editor
│       └── res/                           ← Leanback theme + tv banner
```

## Building

There are two paths — pick whichever matches what you need.

### Quick: use a pre-built proxy release

The `mediaflow-proxy-light` repo publishes stripped per-ABI tarballs on every
release (`mediaflow-proxy-light-android-<abi>.tar.gz`). Drop them into
`jniLibs/` and build the APK for whichever flavor you want:

```bash
TAG=$(gh release view --repo mhdzumair/mediaflow-proxy-light --json tagName -q .tagName)

mkdir -p _proxy && cd _proxy
gh release download "$TAG" --repo mhdzumair/mediaflow-proxy-light \
    --pattern 'mediaflow-proxy-light-android-*.tar.gz'
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    mkdir -p "../app/src/main/jniLibs/$abi"
    tar -xzf "mediaflow-proxy-light-android-${abi}.tar.gz" \
        -C "../app/src/main/jniLibs/$abi"
done
cd ..

./gradlew :app:assembleMobileRelease       # Material phone/tablet APKs
./gradlew :app:assembleTvRelease           # Leanback Android TV APKs
./gradlew :app:assembleRelease             # both flavors in one go
```

### From source: cross-compile the Rust proxy

Check out `mediaflow-proxy-light` as a sibling of this repo, then run its
build script — it auto-installs the binaries into this project's `jniLibs/`:

```bash
cd ../mediaflow-proxy-light
export ANDROID_NDK_HOME=$HOME/Library/Android/sdk/ndk/<version>
./tools/build-android.sh

cd ../mediaflow-android
./gradlew :app:assembleRelease
```

`./gradlew :app:assembleMobileDebug` / `:app:assembleTvDebug` produce debug
APKs signed with the Android default debug keystore — convenient for
`adb install`, but not suitable for public distribution.

### Signing a release APK

Release signing is driven by env vars plus a keystore file at
`app/release.keystore` (gitignored). All four must be set together; if any are
missing, Gradle falls back to producing **unsigned** release APKs.

```bash
cp /path/to/your/mediaflow-release.keystore app/release.keystore
export SIGNING_KEY_STORE_PASSWORD='…'
export SIGNING_KEY_ALIAS='mediaflow'
export SIGNING_KEY_PASSWORD='…'
./gradlew :app:assembleRelease
```

Per flavor, Gradle emits 5 APKs (4 per-ABI + 1 universal). All are signed with
the same key:

```
app/build/outputs/apk/mobile/release/
├── app-mobile-arm64-v8a-release.apk
├── app-mobile-armeabi-v7a-release.apk
├── app-mobile-x86_64-release.apk
├── app-mobile-x86-release.apk
└── app-mobile-universal-release.apk

app/build/outputs/apk/tv/release/
├── app-tv-arm64-v8a-release.apk
├── app-tv-armeabi-v7a-release.apk
├── app-tv-x86_64-release.apk
├── app-tv-x86-release.apk
└── app-tv-universal-release.apk
```

**Keystore generation (one time):**

```bash
keytool -genkeypair -v \
  -keystore mediaflow-release.keystore \
  -storetype PKCS12 \
  -alias mediaflow \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=MediaFlow Proxy, OU=Release, O=mhdzumair, L=Unknown, ST=Unknown, C=US"
```

Back up the resulting file offline. Losing it means future releases can't be
signed under the same identity, and users on old versions won't be able to
install the updates.

## Requirements

- Android Studio Hedgehog or later
- Android SDK 35 (`compileSdk`), min SDK 21
- JDK 17
- Building from source only: Android NDK r26+ and the four Rust Android targets
  (`rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android`)

## Continuous integration

| Workflow                        | Trigger                 | Produces                                                                 |
|---------------------------------|-------------------------|--------------------------------------------------------------------------|
| `.github/workflows/test.yml`    | push / PR to `main`     | Debug APKs (both flavors, arm64 smoke build) as workflow artifacts       |
| `.github/workflows/release.yml` | GitHub Release created  | Per-ABI + universal APKs for **both** flavors, signed, attached to release |

Release-workflow steps:

1. Downloads the newest `mediaflow-proxy-light` release's Android tarballs
   (or a pinned tag via `workflow_dispatch`) and extracts them into `jniLibs/`.
2. Decodes the signing keystore from `SIGNING_KEY_STORE_BASE64` into
   `app/release.keystore`.
3. Runs `./gradlew :app:assembleMobileRelease :app:assembleTvRelease` with the
   signing env vars — Gradle produces 10 signed APKs (5 per-ABI × 2 flavors).
4. Renames and uploads each to the triggering release:
   `mediaflow-proxy-mobile-<abi>.apk` and `mediaflow-proxy-tv-<abi>.apk`.

Sequence your releases accordingly — publish `mediaflow-proxy-light` first so
its tarballs exist when the Android workflow runs.

### Required GitHub repo secrets (for signed APKs)

| Secret                         | Description |
|--------------------------------|-------------|
| `SIGNING_KEY_STORE_BASE64`     | `base64 -i release.keystore` (macOS) or `base64 -w0 release.keystore` (Linux) |
| `SIGNING_KEY_STORE_PASSWORD`   | Keystore password |
| `SIGNING_KEY_ALIAS`            | Key alias (e.g. `mediaflow`) |
| `SIGNING_KEY_PASSWORD`         | Key password (same as keystore password in a single-key store) |

Without these, the workflow still completes — it produces unsigned APKs that
users would have to sign themselves before installing.
