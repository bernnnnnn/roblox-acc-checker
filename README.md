# Roblox Vault

A small **Android app for safely storing and logging into your own Roblox
accounts**. Paste your accounts in `account:pass` format, keep them in an
encrypted local vault behind a PIN/biometric lock, and log into any of them
through Roblox's real login page with one tap.

> **Scope, on purpose.** This is a personal credential manager + assisted
> login helper for accounts **you own** — not an automated credential tester.
> There is intentionally **no proxy rotation, no bulk combo grinding, no
> CAPTCHA/2FA bypass, and no "hit" harvesting**. Verification happens by
> opening Roblox's own login page and pre-filling the fields; **you** solve any
> CAPTCHA and complete 2FA. It works *with* Roblox's protections, not around
> them. Only use it with accounts that belong to you, and follow the Roblox
> Terms of Use.

## What it does

- **Encrypted local vault.** Accounts are stored with Android's
  `EncryptedSharedPreferences` (AES-256, key held in the Android Keystore).
  Nothing is uploaded anywhere — there is no backend and no network call except
  loading Roblox's own login page in a WebView. Cloud backup is disabled.
- **App lock.** Optional PIN (stored only as a salted SHA-256 hash) plus
  optional biometric unlock.
- **`account:pass` import.** Paste one `user:password` per line; the first
  colon splits the two, so passwords may contain colons. Duplicates are skipped.
- **Check.** Opens Roblox's real login in a fresh session with the credentials
  pre-filled. When a valid Roblox session cookie appears, the account is marked
  **Valid**; you can mark it **Invalid** yourself. CAPTCHA/2FA are solved by you
  on Roblox's page.
- **Open (quick login).** Same login flow but keeps the session, so you land
  logged in.
- **Copy** username, password, or `account:pass` to the clipboard.
- **Account Info tab** — reads each account's **creation date, RAP, Robux,
  premium status, and friends/followers** using that account's *own* login
  session (captured when you tap **Check**). Refresh one account or all at once,
  and copy any single field, a whole account's info, or every account's info at
  once. Accounts you haven't logged into yet show "log in first".

## Getting the APK

### Option A — download from Releases (easiest, phone-friendly)
Every push builds the APK and attaches it to a **prerelease** so it's one tap
to download — no desktop mode, works in the GitHub mobile app and browser:
1. Open the repo's **Releases** → **Roblox Vault (latest debug build)**.
2. Under **Assets**, tap **`roblox-vault.apk`**.
3. Tap the downloaded file to install (allow "install unknown apps" if asked).

### Option B — download the CI artifact
1. **Actions** tab → latest **Build APK** run → **Artifacts** →
   **`roblox-vault-debug-apk`** (a zip containing the APK).

Trigger a fresh build anytime via **Actions → Build APK → Run workflow**, or by
pushing any commit.

### Option B — build locally
Requires the Android SDK (e.g. via Android Studio).

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Open the folder in Android Studio and press **Run** to build and install onto a
connected device/emulator.

## Project layout

```
app/src/main/java/com/robloxvault/app/
├── MainActivity.kt            # lock gate + vault host, launches login
├── VaultViewModel.kt          # in-memory list backed by the encrypted store
├── data/
│   ├── Account.kt             # account model + CheckStatus
│   └── AccountStore.kt        # EncryptedSharedPreferences + combo parsing
├── security/LockManager.kt    # PIN hash + biometric flags
├── login/LoginActivity.kt     # real Roblox login WebView (assisted, no bypass)
└── ui/                        # Compose screens (Vault, Lock, Theme)
```

## Requirements

- Android 8.0 (API 26) or newer.
- Debug APKs are signed with the debug key — fine for personal use. For a
  release build, add your own signing config; never commit a keystore.

## Security notes

- Credentials live only on your device, encrypted at rest. Losing the device
  without a screen lock means someone could reinstall/inspect app data on a
  rooted device — set the in-app PIN and a device screen lock.
- The app never sends your `account:pass` to any server other than Roblox's own
  login endpoint, via the login WebView you can see.
