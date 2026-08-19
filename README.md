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
- **Copy** username or password to the clipboard when you'd rather paste.

## Getting the APK

### Option A — download from CI (no local setup)
Every push builds the APK on GitHub Actions:
1. Open the **Actions** tab → the latest **Build APK** run.
2. Download the **`roblox-vault-debug-apk`** artifact.
3. Unzip and install `app-debug.apk` on your phone (allow "install unknown
   apps" for your browser/file manager).

You can also trigger it manually via **Actions → Build APK → Run workflow**.

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
