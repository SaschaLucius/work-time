# Arbeitszeit

An Android app for tracking daily work hours according to German labor law (Arbeitszeitgesetz – ArbZG).

## Features

- **Timer** – Start and stop a live work session timer
- **Calculator** – Enter start and end times to calculate net working hours
- **Home Screen Widget** – Glance widget that displays current work time with live updates
- **Automatic Break Deduction** – Calculates mandatory breaks per ArbZG:
  - ≤ 6 h gross → no break
  - > 6 h to ≤ 9:30 h → 30-minute break
  - > 9:30 h → 45-minute break
- **Midnight Reset** – Resets the daily session automatically at midnight
- **Boot-resilient** – Reschedules alarms after device restart

## Tech Stack

| Area        | Technology                   |
| ----------- | ---------------------------- |
| Language    | Kotlin 2.0                   |
| UI          | Jetpack Compose + Material 3 |
| Widget      | Glance AppWidget             |
| State       | ViewModel + Coroutines       |
| Persistence | DataStore Preferences        |
| Navigation  | Navigation Compose           |
| Min SDK     | 26 (Android 8.0)             |

## Building Locally

```bash
./gradlew assembleDebug
```

For a signed release build, create `keystore.properties` in the project root:

```properties
storeFile=app/release.keystore
storePassword=<your-keystore-password>
keyAlias=<your-key-alias>
keyPassword=<your-key-password>
```

Then copy your keystore file to `app/release.keystore` and run:

```bash
./gradlew assembleRelease
```

## Releases via GitHub Actions

Pushing a version tag triggers the [release workflow](.github/workflows/release.yml), which:

1. Generates a changelog from git commits since the last tag
2. Builds a signed release APK
3. Creates a GitHub Release and attaches the APK

### Triggering a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The version name is taken from the tag (`v1.0.0`). The version code is automatically computed as the total number of `v*` tags in the repository.

### Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions** in your repository and add the following secrets:

| Secret              | Description                                              |
| ------------------- | -------------------------------------------------------- |
| `KEYSTORE_BASE64`   | The keystore file encoded as a single-line Base64 string |
| `KEYSTORE_PASSWORD` | Password for the keystore                                |
| `KEY_ALIAS`         | Alias of the signing key inside the keystore             |
| `KEY_PASSWORD`      | Password for the signing key                             |

#### How to get these values

**Create a new keystore** (skip if you already have one):

```bash
keytool -genkeypair \
  -keystore release.keystore \
  -alias my-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You will be prompted to set the keystore password, key alias password, and your identity details. Remember the values you enter — you will need them for the secrets.

**Encode the keystore as Base64** for `KEYSTORE_BASE64`:

```bash
base64 -i release.keystore | tr -d '\n'
```

Copy the entire output (it will be a long single line) and paste it as the `KEYSTORE_BASE64` secret value.

The other three secrets (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are the values you chose when creating the keystore.

> Keep your keystore file backed up securely. Losing it means you can no longer publish updates to the same app identity on the Play Store.
