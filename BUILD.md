# Building NOVA from Termux (no computer required)

Running the full Android Gradle build *inside* Termux is unreliable — it needs a real
Android SDK, several GB of disk, and a lot of RAM that most phones don't have to spare.

So the setup here is:

- **Termux** → just pushes your code to GitHub (git only, nothing heavier).
- **GitHub Actions** → does the actual build, on GitHub's servers, using a real Android SDK.
- **You** → download the finished `.apk` from GitHub and install it.

This repo has no `gradlew` committed (it needs a binary wrapper jar that can't be created
offline) — the CI workflow (`.github/workflows/build-apk.yml`) installs Gradle itself on the
runner instead, so you don't need one locally either.

---

## 1. One-time Termux setup

```sh
pkg update -y
pkg install git gh -y
```

`gh` is GitHub's official CLI — it lets you create the repo and log in without leaving the
terminal.

```sh
gh auth login
```

Follow the prompts (choose GitHub.com → HTTPS → login with a web browser — it'll give you a
one-time code to enter at github.com/login/device).

```sh
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## 2. Get this project into Termux

If you downloaded this zip to your phone's Downloads folder:

```sh
termux-setup-storage        # one-time, grants Termux access to shared storage
cd ~
unzip /sdcard/Download/NOVA-Phase3-Private-Detective.zip -d nova-src
cd nova-src/NOVA
```

(Adjust the zip filename/path if yours differs.)

## 3. Push it to GitHub

```sh
git init
git add .
git commit -m "NOVA Phase 3 — Private + Detective"

gh repo create nova-app --private --source=. --remote=origin --push
```

`gh repo create ... --push` creates the GitHub repo *and* pushes in one step. If you'd rather
make the repo in a browser first, just run:

```sh
git remote add origin https://github.com/<your-username>/nova-app.git
git branch -M main
git push -u origin main
```

## 4. Let GitHub build the APK

Pushing to `main` automatically triggers the workflow. To watch it from Termux:

```sh
gh run watch
```

Or open `github.com/<your-username>/nova-app/actions` in a browser — you'll see "Build NOVA
APK" running, usually finishing in 5–10 minutes.

## 5. Download the APK

**Easiest (recommended): tag a release.**

```sh
git tag v0.3.0
git push origin v0.3.0
```

This also builds, but additionally attaches the `.apk` directly to a GitHub Release. Go to
`github.com/<your-username>/nova-app/releases` in your phone's browser and tap the `.apk` —
it downloads straight to your phone.

**Alternative: grab it from the build artifact** (works even without tagging):

```sh
gh run download --name nova-debug-apk
```

This pulls the APK into your current Termux directory. Move it somewhere your phone's file
manager can reach (e.g. `mv *.apk /sdcard/Download/`) and open it from there.

## 6. Install it

Tap the downloaded `.apk` in your file manager or Downloads app. Android will prompt to allow
installs from that source ("Install unknown apps") the first time — this is a **debug build**,
signed with a throwaway debug key, so it's only ever meant for sideloading onto your own
device, not for the Play Store.

---

## Making further changes

Edit files in `~/nova-src/NOVA` with any Termux text editor (`nano`, `vim`, or `pkg install
code-server` for a browser-based VS Code), then:

```sh
git add .
git commit -m "describe your change"
git push
```

Every push to `main` rebuilds automatically — check the Actions tab (or `gh run watch`) and
repeat step 5 to grab the new APK.
