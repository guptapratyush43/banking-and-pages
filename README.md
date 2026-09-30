# Banking and Pages

A cute, private Android app that keeps every bank page you ever need in one place, each under its own bank logo.

- **Banks**: account holder, account number, IFSC (branch and MICR fill in by themselves), account type, customer ID, registered mobile and email, UPI ID, net banking user ID, login / transaction / profile passwords, security questions, notes
- **Photos**: cancelled cheque, passbook front page, debit and credit cards, captured with Google's document scanner. A card's front and back are kept as one picture with a gap between them. Open them in the app with pinch-zoom, **save to gallery** or **share**
- **Share account details** in one tap from the home screen (name, number, IFSC, branch and its address, phone; never passwords), or copy them all with one button
- **Documents**: Aadhaar, PAN, voter ID, passport, driving licence scanned in the app or imported as PDF or photo. Hold a card to drag it into a new place. Locked e-Aadhaar PDFs work. View in the app, **save to phone** or **share**
- **155 Indian banks** to search and pick from (public, private, small finance, payments, foreign, regional rural, co-operative), with **"Add another bank"** for anything missing or new
- **Logos in neat circles**, never cropped. 107 ship in the app; any other bank's logo is fetched the moment you save it (retrying in the background until online). **Once a day, silently**, the app checks whether your banks changed their logos and updates them
- **4-digit PIN** on every launch, set up during the first-run intro. **Fingerprint unlock** is optional (Settings), with an honest warning
- **Google Drive backup** to Drive's hidden app folder, locked with your PIN. Restore on a new phone with the same PIN

## Privacy

Everything is encrypted on the phone with a key in Android's hardware keystore. Screenshots and the recent-apps preview are blocked by default. Android's own cloud backup is switched off, so data only leaves the phone through your own Drive backup, which is encrypted before upload. Internet is used only for bank logos, IFSC branch lookups (Razorpay's public IFSC API) and Drive.

## Build

Android Studio's JDK 17, then:

```bash
./gradlew assembleDebug
```

Debug and release are both signed with `signing/banking-pages.jks` (git-ignored, with its passwords in `signing/keystore.properties`), so the SHA-1 Google sign-in needs never changes:

```
SHA-1   E6:73:9C:D5:32:92:DF:62:26:7C:1C:5D:58:2A:72:28:00:01:EF:05
Package com.bankingpages
```

**Keep a copy of `signing/` somewhere safe.** Lose it and future updates can't install over the old app.

## Google Drive sign-in setup (one time)

In Google Cloud Console, in a project with the **Google Drive API** enabled:

1. OAuth consent screen: External, Testing, add your Gmail as a test user, scope `.../auth/drive.appdata`
2. Credentials → Create OAuth client ID → **Android**, package `com.bankingpages`, the SHA-1 above

Until then, "Sign in with Google" shows "isn't set up for this build yet (code 10)". Everything else works offline.

## Bank logos

`tools/fetch_logos.py` rebuilds `app/src/main/assets/banks.json` and `assets/logos/` from `tools/banks.py`. Sources, best first: the open vector symbols of [praveenpuglia/indian-banks](https://github.com/praveenpuglia/indian-banks) (rendered with resvg into `tools/repo-symbols/`), each bank's own site icons, then the Wikidata logo. Each logo's source URL and SHA-256 are recorded, so the app can tell when a bank changes its logo.

Bank names and logos are trademarks of their banks. The indian-banks repo has no licence file, so check with its author before publishing those symbols widely.
