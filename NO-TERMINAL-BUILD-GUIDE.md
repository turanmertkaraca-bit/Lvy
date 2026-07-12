# How to build AnimeStream without a terminal

You don't need Project IDX, you don't need a PC, you don't need to type any commands.
**GitHub will build the APK for you in the cloud for free.**

Total time: ~90 minutes (mostly waiting for the build).

---

## Step 1 — Create a free GitHub account (5 minutes)

1. Open https://github.com/signup in your phone browser
2. Enter an email, pick a username and password
3. Solve the puzzle, verify your email
4. Skip the "personalization" questions (click "Skip")

You now have a GitHub account.

---

## Step 2 — Create a new empty repository (2 minutes)

1. Go to https://github.com/new
2. **Repository name:** type `animestream`
3. **Visibility:** must be **Public** (Private repos don't get free Actions minutes for heavy builds)
4. **Don't** check "Add a README"
5. **Don't** check "Add .gitignore"
6. Click **"Create repository"**

You're now on an empty repo page.

---

## Step 3 — Upload the project files (10 minutes)

1. **Unzip** `animestream-mpv-fork.zip` on your phone (use any file manager — Files by Google, Solid Files, etc.). You should get a folder called `mpv-android` with lots of files inside.

2. On your GitHub repo page, click the **"uploading an existing file"** link (it's in the "...or push an existing repository from the command line" section, the second hyperlink).

3. **Drag the CONTENTS of the `mpv-android` folder** (not the folder itself) into the GitHub upload area. This is the slow part — there are ~330 files. Just drag them all.
   - If GitHub refuses because there are too many files, you'll need to upload in batches. The folders are: `.github/`, `app/`, `buildscripts/`, `docs/`, `fastlane/`, `gradle/`, plus the loose files at the top (`README.md`, `build.gradle`, `gradlew`, etc.)

4. At the bottom, in **"Commit changes"**, type `initial upload` in the title box.

5. Click **"Commit changes"**.

You now have all the code on GitHub.

---

## Step 4 — Trigger the build (1 minute)

1. In your repo, click the **"Actions"** tab at the top.

2. On the left sidebar, click **"build-apk"**.

3. Click the **"Run workflow"** button on the right side. A small dropdown appears.

4. Click the green **"Run workflow"** button inside the dropdown.

5. Wait. You'll see a yellow dot appear next to the workflow run. The build takes about **60–90 minutes**. You can close the browser and come back later.

---

## Step 5 — Download the APK (2 minutes)

1. When the build is done, the yellow dot turns into a **green checkmark** ✅. Click on that workflow run (the title says something like "build-apk · 1" or your commit message).

2. Scroll down to the **"Artifacts"** section at the bottom of the page.

3. You'll see two files:
   - `animestream-arm64-apk` — **this is the one you want** (smaller, optimized for your Reno 11F)
   - `animestream-universal-apk` — backup, works on any device

4. Click **`animestream-arm64-apk`** to download. It's a `.zip` file containing the APK.

5. Open the downloaded zip with your file manager — extract the `.apk` file inside.

---

## Step 6 — Install on your phone (2 minutes)

1. On your Reno 11F, open **Settings → Security → "Install unknown apps"** (or "More settings → Security"). Allow your file manager to install apps.

2. Open the extracted `.apk` file in your file manager.

3. Tap **"Install"**. If you see "For your security, your phone is not allowed to install unknown apps", tap **"Settings"** on that popup and enable the toggle.

4. After install, you'll see **"mpv"** in your app drawer. Open it once to grant permissions.

---

## Step 7 — Use it!

1. Open the **YouTube app** on your phone.
2. Find an anime episode.
3. Tap **Share** → scroll to **mpv** (you might need to tap "More" first).
4. Wait ~3–5 seconds — the app shows "Resolving YouTube stream…" then starts playing.
5. By default it fetches 480p and upscales to your screen with Anime4K Mode A.

### Adjust settings
Open **mpv** → tap the gear icon → **Settings**:
- **General → YouTube base resolution:** change 480p to 360p / 720p / etc.
- **General → Upscaling shader preset:** switch between Anime4K A/B/C, RAVU, etc.
- **Developer → Run Stress Bar benchmark:** play a video first, then run this to see if your GPU can handle the current shader. Green/Yellow/Red result + recommended preset.

---

## Troubleshooting

### Build failed (red X on the workflow run)
1. Click the failed workflow run.
2. Click the `build` job.
3. Scroll to find the red error text.
4. Take a screenshot or copy-paste the error text.
5. Send it to me and I'll patch the file.

### Can't see "Actions" tab
Your repo might be Private. Make it Public: Settings → scroll to bottom → "Change visibility" → Public.

### Build succeeded but no APK to download
The build only uploads artifacts if the APK file exists. If `buildall.sh -n` failed silently, the artifacts step is skipped. Check the build logs.

### "App not installed" on phone
Either:
- The APK is for the wrong architecture (you installed `universal` instead of `arm64`)
- Your phone doesn't allow unknown apps (Step 6.1)
- You have an older version of mpv-android installed with a conflicting signature — uninstall it first.

### YouTube sharing doesn't show "mpv" in the share sheet
Open the YouTube app → Share → tap "More" or the three dots → you should see mpv in the full list.

### "Failed to extract YouTube stream" toast
YouTube changed their cipher. I need to bump the NewPipeExtractor version. Send me a message and I'll patch it.

---

## Why this works

GitHub gives every public repository **2,000 free Actions minutes per month**. A full build takes ~90 minutes, so you can rebuild ~22 times per month for free. The build runs on a real Linux VM in GitHub's cloud — same as Project IDX, just free for public repos.

The `build.yml` file in `.github/workflows/` tells GitHub exactly what to do:
1. Install Java + NDK + build tools
2. Compile ffmpeg, libplacebo, libmpv from source (for arm64 = your phone's CPU)
3. Compile the Kotlin/Java app and package it into an APK
4. Upload the APK as a downloadable artifact

You just push code and wait. No terminal needed.
