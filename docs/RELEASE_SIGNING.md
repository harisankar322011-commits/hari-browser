# Hari Browser release signing

Hari Browser must be signed with one stable release certificate before you install or distribute a release APK. The CI release job deliberately emits an **unsigned validation APK** when signing is not configured. Do not install or distribute that unsigned artifact.

## Create a stable key once

Run this locally on a trusted computer. Keep the keystore and passwords private and make an offline backup.

```bash
keytool -genkeypair -v -keystore hari-release.jks -alias hari \
  -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore hari-release.jks -alias hari
```

Record the certificate's SHA-256 fingerprint from the second command. It can be supplied with or without colons; CI normalizes it before comparison.

## Configure GitHub Actions secrets

In the repository's **Settings → Secrets and variables → Actions**, create these five repository secrets:

- `HARI_RELEASE_KEYSTORE_BASE64` — base64-encoded bytes of `hari-release.jks` (Linux: `base64 -w0 hari-release.jks`).
- `HARI_RELEASE_KEY_ALIAS` — `hari`, if you used the example command.
- `HARI_RELEASE_KEY_PASSWORD` — the key password.
- `HARI_RELEASE_STORE_PASSWORD` — the keystore password.
- `HARI_RELEASE_CERT_SHA256` — the SHA-256 certificate fingerprint recorded above.

Never commit the keystore, its base64 contents, or passwords to GitHub. Do not paste them into chat or issue comments. The workflow signs only the release variant on `main`, verifies the APK signature, and fails rather than publishing a signed APK if the certificate fingerprint does not match the pinned value.

## Why a stable key matters

Android uses the signing certificate to authorize app updates. If two APKs are signed by different keys, Android normally refuses an in-place update. Older Hari Browser 2.3.0 artifacts were found to use different self-signed “One-Time Release” certificates; the owner should not assume those keys are reusable or interchangeable. Since a signing-key change may require uninstalling the old app (which deletes its app data), preserve the stable key securely for all future builds.

The fingerprint check does not establish that the app is malware-free. Continue to inspect the exact APK hash, scan it with reputable tools, and test it on a spare device. If Android device protection reports a threat, do not bypass the warning.
