# AnimeStream — mpv-android fork for anime watching on YouTube

This is a customized fork of [mpv-android](https://github.com/mpv-android/mpv-android)
that adds the features you wanted for low-bandwidth anime streaming on your
OPPO Reno 11F 5G.

## 🚀 Easiest way to build — NO TERMINAL NEEDED

**[Read NO-TERMINAL-BUILD-GUIDE.md](NO-TERMINAL-BUILD-GUIDE.md) — it walks you through building the APK for free using GitHub Actions. No Project IDX, no PC, no terminal.**

The short version: upload this folder to a public GitHub repo → the included `.github/workflows/build.yml` builds the APK automatically → download it from the Actions tab.

## What's been added on top of stock mpv-android

| Feature | Where | Notes |
|---|---|---|
| **YouTube Share Intent receiver** | `MPVActivity.kt` + `YouTubeResolver.kt` | Share a YouTube URL → app fetches the low-res stream and plays it |
| **YouTube base resolution picker** | Settings → General → "YouTube base resolution" | 144p / 240p / 360p / **480p (default)** / 720p / 1080p |
| **NewPipeExtractor integration** | `YouTubeResolver.kt` | Extracts direct stream URLs (video + audio separately for A/V sync) |
| **Bundled anime shaders** | `app/src/main/assets/shaders/` | Anime4K v3 Modes A/B/C + RAVU Lite r3/r4 |
| **Shader preset picker** | Settings → General → "Upscaling shader preset" | Choose preset; applied at player init |
| **Stress Bar benchmark** | Settings → Developer → "Run Stress Bar benchmark" | 10-second benchmark using mpv's `estimated-vf-fps`, `frame-drop-count`, `vo-delayed-frame-count`. Green/Yellow/Red + recommended preset |
| **Brightness sensitivity tuned** | `TouchGestures.kt` | Left-side swipe is now 1.67× more sensitive (default 1.5 → 2.5) |
| **Volume sensitivity tuned** | `TouchGestures.kt` | Right-side swipe is now 1.875× less sensitive (default 1.5 → 0.8) — won't deafen you |
| **Double-tap seek defaults** | `strings.xml` | Left double-tap = -10s, right double-tap = +10s (was "none" by default) |
| **Aspect ratio lock** | `MPVView.kt` | `keepaspect=yes` + `keepaspect-window=no` enforced |

Features that mpv-android *already had* that you also wanted — no work needed:
- Resume playback (`save-position-on-quit` + watch-later files)
- Bottom playback bar with elapsed / total / drag-to-seek
- Hardware decoding toggle (Settings → General → "Hardware decoding")
- Built-in scalers (spline36, ewa_lanczossharp, etc.) — Settings → Video

---

## How to build this in Project IDX

> **TL;DR — three steps:** (1) upload the zip, (2) run `./buildscripts/buildall.sh --arch arm64 mpv && ./buildscripts/buildall.sh -n`, (3) download `app/build/outputs/apk/default/debug/app-default-universal-debug.apk` and install on your phone.

### Step 0 — what you need
- A free [Project IDX](https://idx.dev) account (Google login).
- Your OPPO Reno 11F 5G with USB debugging enabled (Settings → About phone → tap "Version" 7 times → Developer options → USB debugging).

### Step 1 — Upload the project to IDX
1. Download the zip of this project (animestream-mpv-fork.zip).
2. Go to https://idx.dev and sign in.
3. Click **"Import from GitHub"** → switch to **"Upload"** tab → drag the zip in.
4. IDX will detect it as an Android project. Wait for the workspace to provision (~2 min).

### Step 2 — Build the native libmpv (one-time, ~30–60 min on free tier)

The libmpv native libraries (`.so` files) must be compiled before the Kotlin
build can succeed. This is the slowest step.

Open the IDX terminal (Ctrl+`) and run:

```bash
# 1. Install SDK + NDK + download ffmpeg/mpv/libplacebo source (~2 GB)
cd /home/user/project/mpv-android
bash buildscripts/download.sh

# 2. Build native libs for arm64 (your Reno 11F is arm64-v8a)
# This compiles ffmpeg, libplacebo, libmpv. Takes 30-60 min on free tier.
bash buildscripts/buildall.sh --arch arm64 mpv

# 3. Build the Kotlin/Java app + package APK
bash buildscripts/buildall.sh -n
```

If the build fails with "out of memory" or OOM, try running it in two halves:
```bash
bash buildscripts/buildall.sh --arch arm64 ffmpeg
bash buildscripts/buildall.sh --arch arm64 libplacebo
bash buildscripts/buildall.sh --arch arm64 mpv
bash buildscripts/buildall.sh -n
```

### Step 3 — (Optional) Download extra shaders

The repo already bundles Anime4K v3 (Modes A/B/C) + RAVU Lite r3/r4.
If you also want FSRCNNX, ArtCNN, KrigBilateral, SSimDownscaler:

```bash
bash fetch_extra_shaders.sh
# Then rebuild:
bash buildscripts/buildall.sh -n
```

### Step 4 — Get the APK onto your phone

The APK lives at:
```
app/build/outputs/apk/default/debug/app-default-universal-debug.apk
```

In IDX you can right-click this file → "Download". Then transfer it to your
phone and tap to install (you'll need to allow "Install unknown apps" for
your file manager).

Alternatively, use `adb` if you've connected your phone via USB:
```bash
adb install -r app/build/outputs/apk/default/debug/app-default-universal-debug.apk
```

---

## How to use

1. Open the YouTube app (or browser) on your phone.
2. Find an anime episode you want to watch.
3. Tap **Share** → select **mpv** (the app's name is still "mpv" — you can rename it later).
4. The app shows "Resolving YouTube stream…" for ~3-5 seconds, then plays.
5. By default it fetches 480p and upscales to your screen with Anime4K Mode A.

### Adjust settings
- **Settings → General → YouTube base resolution** — change 480p to 360p/720p/etc.
- **Settings → General → Upscaling shader preset** — switch between Anime4K / RAVU / etc.
- **Settings → Developer → Run Stress Bar benchmark** — play a video, then run this to see how your device handles the current shader. Green/Yellow/Red + recommended preset.

### Gestures (all already tuned to your spec)
| Action | Gesture |
|---|---|
| Seek back 10s | Double-tap left side |
| Seek forward 10s | Double-tap right side |
| Brightness (high sensitivity) | Swipe vertically on left half |
| Volume (low sensitivity, won't deafen) | Swipe vertically on right half |
| Seek to position | Drag the bottom scrubber |
| Show/hide controls | Single tap (center) |

---

## Troubleshooting

**"YouTube extraction failed" toast when sharing.**
- YouTube probably changed its cipher. Run `bash buildscripts/buildall.sh -n` after bumping `NewPipeExtractor` to the latest version (edit `app/build.gradle`, change `v0.24.3` to the latest tag from https://github.com/TeamNewPipe/NewPipeExtractor/releases).

**Video plays but no audio.**
- The audio stream URL is being passed via `audio-file` option. Some videos (music videos with DRM) won't extract properly. Try a regular anime episode.

**Stuttering / dropped frames.**
- Run the Stress Bar benchmark. If Red, switch to a lighter preset (Anime4K A → RAVU Lite → None).
- Lower the base resolution (480p → 360p).
- Make sure Hardware decoding is ON (Settings → General).

**Build failed at ffmpeg step.**
- Make sure you ran `download.sh` first. If it's an out-of-memory error, restart IDX and try the per-component build sequence shown above.

---

## File map (what I modified/added)

```
app/src/main/java/is/xyz/mpv/
├── YouTubeResolver.kt        ← NEW: NewPipeExtractor wrapper
├── ShaderPresets.kt          ← NEW: bundled shader preset definitions
├── StressBenchmark.kt        ← NEW: 10s benchmark using mpv property observers
├── MPVLib.kt                 ← MODIFIED: added isCreated() / markCreated() / markDestroyed()
├── BaseMPVView.kt            ← MODIFIED: calls markCreated/markDestroyed
├── MPVView.kt                ← MODIFIED: applies shader preset + keepaspect, observes fps/drops
├── MPVActivity.kt            ← MODIFIED: YouTube URL detection + async resolution in onCreate/onNewIntent
├── TouchGestures.kt          ← MODIFIED: brightness=2.5 (more sensitive), volume=0.8 (less sensitive)
└── preferences/
    └── PreferenceActivity.kt ← MODIFIED: Stress Bar benchmark click handler + result dialog

app/src/main/res/
├── values/arrays.xml         ← MODIFIED: added yt_resolution + shader_preset arrays
├── values/strings.xml        ← MODIFIED: new strings, tap defaults = "seek"
├── xml/pref_general.xml      ← MODIFIED: added YT res + shader preset ListPreferences
└── xml/pref_developer.xml    ← MODIFIED: added Stress Bar benchmark Preference

app/src/main/assets/shaders/  ← NEW: 16 bundled .glsl/.hook files
├── Anime4K_Clamp_Highlights.glsl
├── Anime4K_Denoise_Bilateral_Mode.glsl
├── Anime4K_Restore_CNN_{S,M,L,VL}.glsl
├── Anime4K_Upscale_CNN_x2_{S,M,L,VL}.glsl
├── Anime4K_Upscale_Denoise_CNN_x2_{S,M,L,VL}.glsl
├── ravu-lite-r3.hook
└── ravu-lite-r4.hook

app/build.gradle              ← MODIFIED: added NewPipeExtractor, coroutines, OkHttp deps
build.gradle                  ← MODIFIED: added JitPack repo
fetch_extra_shaders.sh        ← NEW: optional script to fetch FSRCNNX/ArtCNN/Krig/SSim
README.md                     ← this file
```

---

## License

mpv-android is GPLv2+. This fork inherits that license. NewPipeExtractor is GPLv3.
Use this for personal use only — redistributing it (especially on the Play Store)
would violate both YouTube's ToS and potentially the GPL if source isn't provided.
