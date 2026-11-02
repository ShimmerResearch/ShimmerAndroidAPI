# Releasing ShimmerCapture

ShimmerCapture is this module, `shimmerServiceExample` (Play package
`com.shimmersensing.shimmerconnect`). A release is:

1. an **uprev PR** that raises the version in `build.gradle`, then
2. one run of the **Release ShimmerCapture** workflow
   ([`.github/workflows/release-shimmercapture.yml`](../../.github/workflows/release-shimmercapture.yml)),
   which builds and signs the APK, creates the GitHub pre-release and, if asked, puts a draft release
   in Google Play, then
3. **sending the draft for review** in Play Console, by hand.

The workflow never changes the version itself. It reads it from `build.gradle` on `master`, so
step 1 is not optional: without it, the run stops at its first check.

## 1. Uprev the version

Start a branch from `master`, named for the Jira ticket (e.g. `DEV-1234_shimmercapture_v1.3.4`).

In [`build.gradle`](build.gradle), raise both values in `defaultConfig`:

```groovy
versionCode 35        // +1 on the last release. Google Play rejects a code it has seen before
versionName "1.3.4"   // the version users see; also the tag, ShimmerCapture_v1.3.4
```

- `versionCode` goes up by **1** each release, whatever the change.
- `versionName` normally goes up by one patch (1.3.3 → 1.3.4). Raise the minor (1.3.x → 1.4.0) for
  a larger change.
- The last released values are in the newest
  [`ShimmerCapture_v…` release](https://github.com/ShimmerResearch/ShimmerAndroidAPI/releases)'s
  notes, and in `build.gradle` at its tag.

Check that `MainActivity` is the public build:

```java
public static APP_RELEASE_TYPE appReleaseType = APP_RELEASE_TYPE.PUBLIC;
```

`INTERNAL` and `TESTING` are for builds that do not go to Google Play. The workflow refuses to
release anything else.

Then, as before the workflow existed:

1. Sync Gradle in Android Studio, **Clean Project**, run the app on a device and check that it
   connects, configures, streams and plots.
2. Commit (`ShimmerCapture 1.3.4 (versionCode 35)`), push, and open a PR into `master`.
3. Wait for CI ("Build and test") to pass, get it reviewed, and merge.

## 2. Optional: a dry run

To check the signing and the version, and to get the list of changes for the release notes, before
publishing anything:

1. **Actions** → **Release ShimmerCapture** → **Run workflow**.
2. Branch `master`, tick **dry_run**, then **Run workflow**.

A dry run does everything up to the signed APK and stops. Open the run to check:

- The run's **summary** page lists every PR merged since the last ShimmerCapture release: the same
  list the GitHub release's notes will have. Write the Play release notes from it (see
  *Writing whats_new* below).
- **build** → *Collect and check the APK* prints the APK's `versionCode` and `versionName`, and its
  `certificate SHA-256 digest` (apksigner labels the line `V2 Signer:` or `Signer #1`). It must be
  the certificate every ShimmerCapture release has been signed with:

  ```
  CN=Shimmer
  SHA-256 5f353cb90d2452e2d83309837a57b72950003e1470d873899a573e2441123e19
  ```

  ShimmerCapture is **not** enrolled in Play App Signing, so Play has no separate upload key: it
  accepts an update only if it is signed by the same key as the APKs already published, and rejects
  it otherwise. To check against Play itself, download the current APK from Play Console →
  ShimmerCapture → *Test and release* → *App bundle explorer* → its version code → *Downloads*, and
  run `apksigner verify --print-certs` on it. Do not choose any option on *Protected with Play* →
  *Manage Play app signing* as part of a release: enrolling cannot be undone.
- The APK itself is on the run page under **Artifacts** (`shimmercapture-apk`), to install on a test
  device.

A dry run can be started from any branch, so it is also the way to try an uprev before merging it.

### Writing whats_new

The GitHub release gets its notes automatically: every PR since the last release. The Play release
notes (`whats_new`) are for users, at most 500 characters, so they are written by hand from that
list. Most PRs (CI, HIL tests, docs, release tooling) do not change the app: keep the ones users
notice, in plain words, one bullet each. For example, 1.3.4's:

```
• Connecting over Bluetooth LE no longer freezes the app while the link is set up
• A device is shown as ready only once it has fully connected
• More reliable streaming: fixes for device replies and acknowledgements that could be lost or misread
• Fixed a crash when a device disconnected while a command was being sent
• Updated Shimmer device library (0.11.12)
```

Without a dry run, the same list is one command away. It creates nothing; put in the version being
released and the last one:

```bash
gh api repos/ShimmerResearch/ShimmerAndroidAPI/releases/generate-notes -f tag_name=ShimmerCapture_v1.3.5 -f target_commitish=master -f previous_tag_name=ShimmerCapture_v1.3.4 --jq .body
```

## 3. Run the release

1. **Actions** → **Release ShimmerCapture** → **Run workflow**.
2. Fill in:

   | Field | Value |
   |---|---|
   | Use workflow from | `master` (a release refuses any other branch) |
   | google_play | ticked, to put a draft release in Google Play. Unticked = GitHub release only |
   | whats_new | the Play release notes, in English, at most 500 characters. Blank = "Bug fixes and improvements." See *Writing whats_new* above |
   | dry_run | unticked |

3. **Run workflow**, and wait for the three jobs, about 10 minutes:
   - **build**: checks the version, then builds and signs the APK.
   - **release**: tags `ShimmerCapture_v<versionName>` and creates the GitHub **pre-release**, with
     the APK and notes listing every PR since the last ShimmerCapture release. It is a pre-release
     so the Android API release stays *Latest* on the releases page.
   - **play**: uploads the APK to Play's **Production** track as a **draft**. Skipped when
     google_play is unticked.

The run's summary page links the new release.

## 4. Send it for review in Play Console

The workflow leaves the Play release as a draft on purpose, so a person checks it before it goes
out.

1. [Play Console](https://play.google.com/console) → **ShimmerCapture** → **Test and release** →
   **Production**.
2. Open the draft release. It is named after the version, e.g. `1.3.4`. Check the APK's version
   code and the release notes, then **Next**.
3. Ignore any page you have no changes for, then **Save** → **Go to Publishing overview**.
4. **Send changes for review**.

Google's review usually takes from a few hours to a few days. Play Console emails the account when
the release is live.

Not uploading through the workflow? Download the APK from the GitHub release, and in step 2 use
**Create new release** → **Upload** instead.

## 5. Afterwards

- Move the ticket on in Jira.
- If the release changes what the app does, update this module's [README](README.md).

## When a run fails

**Re-run failed jobs** on the run page re-runs only what failed, so a GitHub release that was
already created is not created twice.

| Message | Cause and fix |
|---|---|
| `A release must run on master` | Started from another branch. Pick `master`, or tick dry_run |
| `… is already released as ShimmerCapture_v…` | No uprev was merged. Do step 1 |
| `versionCode N is not higher than M` | `versionName` was raised but `versionCode` wasn't. Raise it in a new uprev PR |
| `MainActivity.appReleaseType is …, not PUBLIC` | Set it back to `PUBLIC` in a PR |
| `No SHIMMERCAPTURE_KEYSTORE_BASE64 secret` | The signing secrets are missing. See *Secrets* below |
| `The APK does not carry versionName …` | The build didn't pick up `build.gradle`'s values. Check for a version override in Gradle files |
| **play**: `No GOOGLE_PLAY_SERVICE_ACCOUNT_JSON secret` | The Play secret is missing. The GitHub release is done; upload by hand (step 4), or add the secret and re-run the job |
| **play**: `The caller does not have permission` | The service account lacks access to ShimmerCapture in Play Console, or was just added: new permissions can take a day. Re-run the play job later |
| **play**: `APK specifies a version code that has already been used` | That `versionCode` reached Play before, e.g. by hand. Uprev again |

## Secrets

Set once, under the repository's **Settings** → **Secrets and variables** → **Actions**. Setting
them needs admin rights on the repo. Values cannot be read back, so keep the originals in the team's
password store.

| Secret | What it is |
|---|---|
| `SHIMMERCAPTURE_KEYSTORE_BASE64` | The upload keystore, base64-encoded |
| `SHIMMERCAPTURE_KEYSTORE_PASSWORD` | Its store password |
| `SHIMMERCAPTURE_KEY_ALIAS` | The key's alias (`keytool -list -keystore <file>` shows it) |
| `SHIMMERCAPTURE_KEY_PASSWORD` | The key's password; often the same as the store password |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | JSON key of a Google Cloud service account that Play Console's *Users and permissions* gives *Release to production*, *Release apps to testing tracks* and *View app information* on ShimmerCapture. Only the play job needs it |

To replace the keystore secret, from PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("<path to keystore>")) | gh secret set SHIMMERCAPTURE_KEYSTORE_BASE64 --repo ShimmerResearch/ShimmerAndroidAPI
```

Run `gh secret set <NAME> --repo ShimmerResearch/ShimmerAndroidAPI` with no value for the passwords:
it asks for them without echoing them.

For the Play secret, create a JSON key for the service account in Google Cloud, store it, then
delete the file:

```powershell
Get-Content -Raw <key.json> | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON --repo ShimmerResearch/ShimmerAndroidAPI
```

## Building a release by hand

If the workflow is unavailable, Android Studio still does the same job: **Build** → **Generate
Signed App Bundle / APK** → **APK** → module `shimmerServiceExample`, the upload keystore, build
variant **release**. The APK lands in `shimmerServiceExample/release/`. Do the uprev (step 1) first
all the same, then upload as in step 4.
