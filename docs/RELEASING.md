# Releasing

For the maintainer. A release is a tag `vMAJOR.MINOR.PATCH` pushed to GitHub:
`.github/workflows/release.yml` runs the gates of every pull request, builds the APK with the tag's
version, signs it with the release key and publishes it as a GitHub release. Users install it by
hand (README, "Installing and updating").

## Versions

The tag is the version: `v0.1.0` gives `versionName` `0.1.0` and `versionCode` 100, and in general
`major * 10000 + minor * 100 + patch` (`build-logic/.../AppVersion.kt`). The workflow passes it to
Gradle as `-PappVersion=0.1.0`. Gradle refuses anything that would make the code go backwards or
collide: minor and patch 0-99, no leading zero, no suffix (no `-rc.1`), not `0.0.0`. Every other
build, local or CI, needs no property and is `0.1.0-dev`, code 1, signed with another key, so it
never installs over a release nor a release over it.

## The release key (once)

Make it outside the clone (`*.keystore` is git-ignored as well, but it does not belong there):

```sh
keytool -genkeypair -keystore petkit-local-release.keystore -storetype PKCS12 \
  -alias petkit-local -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=petkit-local Android"
```

keytool asks for the keystore password; a PKCS12 keystore uses it for the key too. Its
certificate's SHA-256 is what users compare (README); `apksigner` prints the same value in
lowercase without colons:

```sh
keytool -list -v -keystore petkit-local-release.keystore -alias petkit-local | grep SHA256
```

**Back it up now**: the keystore file and its password, in two places that do not fail together
(a password manager and an offline drive, say). GitHub secrets cannot be read back, so they are
not a copy. Android installs an update only when it is signed with the key of the installed app:
lose the key and no later release installs over an existing one, so every user has to uninstall
(losing the app's data) to move on. A leaked key is as bad: anyone can then sign an APK that
installs as an update.

## The secrets

`release.yml` reads four repository secrets, distinct from the CI key's below:

| Secret | What |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `ANDROID_RELEASE_KEYSTORE_PASSWORD` | its password |
| `ANDROID_RELEASE_KEY_ALIAS` | `petkit-local` |
| `ANDROID_RELEASE_KEY_PASSWORD` | optional: the key's password when it differs (JKS); empty means the keystore's |

Without the first three the workflow fails at its `apk` job and publishes nothing; it never
signs a release with another key. Set them from files, so no value lands in the shell history
(Git Bash on Windows, Linux; on macOS `base64 -i <keystore> -o keystore.b64`):

```sh
base64 -w0 petkit-local-release.keystore > keystore.b64
gh secret set ANDROID_RELEASE_KEYSTORE_BASE64 --repo Gamso/petkit-local-android < keystore.b64
# password.txt holds the password and nothing else, no final newline:
gh secret set ANDROID_RELEASE_KEYSTORE_PASSWORD --repo Gamso/petkit-local-android < password.txt
gh secret set ANDROID_RELEASE_KEY_ALIAS --repo Gamso/petkit-local-android --body petkit-local
rm keystore.b64 password.txt
gh secret list --repo Gamso/petkit-local-android      # names only, never values
```

### The CI key (already there, optional)

`e2e.yml`'s smoke job installs an R8 release build on an emulator, and signs it with the CI key
from four other secrets: `ANDROID_CI_KEYSTORE_BASE64`, `ANDROID_CI_KEYSTORE_PASSWORD`,
`ANDROID_CI_KEY_ALIAS` and `ANDROID_CI_KEY_PASSWORD` (optional, as above). Without them the job
makes a throwaway key for the run, and the test is the same. `gh secret list` shows whether they
are set. Never set them to the release key: e2e runs on every pull request, and `release.yml`
hands it only the CI key's secrets.

## Cutting a release

From the commit of `main` to release, once its checks are green:

```sh
git switch main && git pull --ff-only
git tag v0.1.0 && git push origin v0.1.0
```

Then follow Actions, "release" (about half an hour): `version` reads the tag; `ci` (static, unit,
compose, build) and `e2e` (e2e, then smoke) run as on a pull request while `apk` builds and signs;
`publish` creates the release, with notes generated from the merged pull requests, the APK
`petkit-local-0.1.0.apk`, its `petkit-local-0.1.0.apk.sha256`, and the two SHA-256s users check.
The run also keeps the R8 mapping file (`petkit-local-0.1.0-mapping.txt`, artifact `release`) for
90 days, to read an obfuscated stack trace. The smoke test runs on the same commit's R8 build
signed with the CI key, not on the published file itself.

When it fails, nothing is published:

- **Missing secrets**: set them, then "Re-run failed jobs" on the run.
- **A refused tag or a red gate**: delete the tag, fix on `main` through a pull request, and tag
  again; the version was never published, so it can be reused.

  ```sh
  git push origin :refs/tags/v0.1.0 && git tag -d v0.1.0
  ```

- **A release deleted by hand**, to publish again: Actions, "release", "Run workflow", and under
  "Use workflow from" pick the tag, not a branch.

`v0.1.0` is the first release: from it on, a database schema change needs a tested migration and
the destructive fallback goes (AGENTS.md, "No database migrations before the first release").
