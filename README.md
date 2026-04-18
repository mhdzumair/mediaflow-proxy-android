# MediaFlow Proxy · Android

Native Android / Android TV app that runs the Rust
[mediaflow-proxy-light](https://github.com/mhdzumair/mediaflow-proxy-light) server
as a foreground service on-device.

## Architecture

The proxy binary is **bundled inside the APK** as a regular JNI library
(`libmediaflow-proxy.so`) for each supported Android ABI. At install time
Android extracts it to `nativeLibraryDir`, which is executable — the app's
`ProxyManager` then spawns the binary with `ProcessBuilder`.

```
mediaflow-android/
├── app/src/main/
│   ├── jniLibs/                         ← proxy binary per ABI
│   │   ├── arm64-v8a/libmediaflow-proxy.so       (not committed — see below)
│   │   ├── armeabi-v7a/libmediaflow-proxy.so
│   │   ├── x86_64/libmediaflow-proxy.so
│   │   └── x86/libmediaflow-proxy.so
│   └── java/com/mediaflow/proxy/
│       ├── MainActivity.kt              ← phone/tablet entry point
│       ├── ProxyService.kt              ← foreground service lifecycle
│       ├── ProxyManager.kt              ← subprocess launch / stdout log capture
│       ├── BootReceiver.kt              ← auto-start on boot
│       ├── ConfigRepository.kt          ← DataStore persistence
│       └── ui/
│           ├── MainViewModel.kt
│           ├── StatusFragment.kt        ← run/stop + logs
│           ├── ConfigFragment.kt        ← proxy settings
│           ├── MetricsFragment.kt
│           └── TvMainActivity.kt        ← Android TV (Leanback)
```

## Building

There are two paths — pick whichever matches what you need.

### Quick: use a pre-built proxy release

The `mediaflow-proxy-light` repo publishes stripped per-ABI tarballs on every
release (`mediaflow-proxy-light-android-<abi>.tar.gz`). Drop them into
`jniLibs/` and build the APK:

```bash
# Optional: pin a specific tag instead of "latest"
TAG=$(gh release view --repo mhdzumair/mediaflow-proxy-light --json tagName -q .tagName)

mkdir -p _proxy && cd _proxy
gh release download "$TAG" --repo mhdzumair/mediaflow-proxy-light \
    --pattern 'mediaflow-proxy-light-android-*.tar.gz'
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    mkdir -p "../app/src/main/jniLibs/$abi"
    tar -xzf "mediaflow-proxy-light-android-${abi}.tar.gz" \
        -C "../app/src/main/jniLibs/$abi"
done
cd .. && ./gradlew :app:assembleRelease
```

### From source: cross-compile the Rust proxy

Check out `mediaflow-proxy-light` as a sibling of this repo, then run its
build script — it auto-installs the binaries into this project's `jniLibs/`:

```bash
cd ../mediaflow-proxy-light
export ANDROID_NDK_HOME=$HOME/Library/Android/sdk/ndk/<version>
./tools/build-android.sh          # all four ABIs
# or: ABIS="arm64-v8a" ./tools/build-android.sh   (single ABI for fast iteration)

cd ../mediaflow-android
./gradlew :app:assembleRelease
```

`./gradlew :app:assembleDebug` builds a debug variant signed with the Android
default debug keystore — convenient for `adb install`, but not usable as a
release.

### Signing a release APK

Release signing is driven by env vars plus a keystore file at
`app/release.keystore` (gitignored). All four must be set together; if any are
missing, Gradle falls back to producing an **unsigned** release APK.

```bash
cp /path/to/your/mediaflow-release.keystore app/release.keystore
export SIGNING_KEY_STORE_PASSWORD='…'
export SIGNING_KEY_ALIAS='mediaflow'
export SIGNING_KEY_PASSWORD='…'
./gradlew :app:assembleRelease
```

Output APKs (one per ABI + one universal), all signed:

```
app/build/outputs/apk/release/
├── app-arm64-v8a-release.apk
├── app-armeabi-v7a-release.apk
├── app-x86_64-release.apk
├── app-x86-release.apk
└── app-universal-release.apk
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

| Workflow | Trigger | Produces |
|----------|---------|----------|
| `.github/workflows/test.yml`    | push / PR to `main`    | Debug APK (arm64 smoke build) as an artifact |
| `.github/workflows/release.yml` | GitHub Release created | Per-ABI + universal APKs, signed, attached to the release |

The release workflow:

1. Downloads the newest `mediaflow-proxy-light` release's Android tarballs
   (or a pinned tag via `workflow_dispatch`) and extracts them into `jniLibs/`.
2. Decodes the signing keystore from `SIGNING_KEY_STORE_BASE64` into
   `app/release.keystore`.
3. Runs `./gradlew :app:assembleRelease` with the signing env vars — Gradle
   produces five signed APKs (one per ABI + universal).
4. Renames and uploads all five to the triggering release:
   `mediaflow-proxy-<abi>.apk` and `mediaflow-proxy-universal.apk`.

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
