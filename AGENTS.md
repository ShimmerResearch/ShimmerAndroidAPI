# ShimmerAndroidAPI

Android wrapper over the Java driver — all but its Bluetooth streaming parser — plus eleven example
apps. Public repo; BETA per its README.

## It wraps the driver — except for the Bluetooth stream parser

`Shimmer` extends `ShimmerBluetooth`, `Shimmer4Android` extends `Shimmer4sdk`, and
`VerisenseDeviceAndroid` extends `VerisenseDevice` — all from `Shimmer-Java-Android-API`. Apart from
the parser below, what lives here is Android-specific: radio/transport drivers
(`androidradiodriver/`), a foreground `ShimmerService`, GUI fragments and plotting.

**`Shimmer.java` carries its own copy of the Bluetooth stream parser.** It overrides these
`ShimmerBluetooth` methods, and the copy has drifted from the driver's:

- `processPacket()` — resyncs a byte at a time, where the driver skips to the next packet
- `processWhileStreaming()` — reads everything available in one go, where the driver reads a byte
  at a time
- `processInstreamResponse()` and `clearSingleDataPacketFromBuffers()`
- `checkCrc()`, since DEV-1148 — a stopgap that its javadoc says can go once the driver pin
  includes DEV-1147

`readBattery()` and `readStatusLogAndStream()` are overridden too: while streaming they write the
command straight to the socket instead of queueing it.

So a driver fix to parsing, CRC or ACK handling **never reaches `Shimmer`, even after a republish
and pin bump** (below). Check every such fix against `Shimmer.java` and expect to port it by hand:
DEV-1147's driver fix (ShimmerResearch/Shimmer-Java-Android-API#319) had to be ported as DEV-1148
(#121).

Decoding is still the driver's: `Shimmer.java` hands each packet to `processDataPacket()` →
`buildMsg()`, so calibration and the device timestamp are upstream, and a fix there needs no port.
Nothing else here overrides the parser — `Shimmer4Android` and `VerisenseDeviceAndroid` override
only callbacks, and `Shimmer3BLEAndroid` only BLE transport, state and callbacks, so it uses the
driver's parser.

## But upstream fixes do not arrive for free

The driver comes in as **published Maven artifacts pinned to an exact version**, from GitHub
Packages — not as source, a submodule or a sibling project. The version is set once in the repo-root
`ShimmerAndroidInstrumentDriver/build.gradle` and injected into *every* subproject:

```groovy
def shimmerDriverVersion = findProperty('driverVersion') ?: '0.11.11_beta'
allprojects { subprojects { afterEvaluate { dependencies {
    implementation (group: 'com.shimmerresearch', name: 'shimmerbluetoothmanager', version: shimmerDriverVersion)
    implementation (group: 'com.shimmerresearch', name: 'shimmerdriver',           version: shimmerDriverVersion)
} } } }
```

- The default is what CI, PR dry runs and local builds use.
- **Release Android API** passes `-PdriverVersion`, by default the newest Java API release, and
  records it in the release notes.
- Each Java API release (`API_v…`) publishes both artifacts at the same version, so they always
  move together.

So an upstream fix reaches this repo only once it is **in a Java API release** — and a
stream-parser fix not even then (above). The default moves with each Java API release, so CI builds
what a release would: Release Java API (Shimmer-Java-Android-API's `release.yml`, job
`update-android`) opens a PR here on a `driver_<version>` branch. The PR checklist covers the
steps that stay manual: porting parser fixes, the Android HIL at master's new commit, and Release
Android API.

## Build

**Build from `ShimmerAndroidInstrumentDriver/`, not the repo root** — that is where `settings.gradle`
is. A same-named folder nests inside it (`ShimmerAndroidInstrumentDriver/ShimmerAndroidInstrumentDriver/`)
and is the library module, not the build root.

```
cd ShimmerAndroidInstrumentDriver && ./gradlew :ShimmerAndroidInstrumentDriver:assembleRelease
```

AGP 8.2.2, Gradle 8.5, `compileSdk`/`targetSdk` 33, `minSdk` 14. Java 11 is the source and target
level only: AGP 8 needs Gradle itself to run on JDK 17 or later.

Both wrapper directories carry a wrapper, at **different versions** — 8.5 at the build root and
8.14.3 in the module. Gradle walks upward for `settings.gradle`, so invoking the inner one still
builds the same project with a different Gradle. Use the build root's.

### A token is required before anything resolves

The driver artifacts are private packages, so Gradle needs a GitHub PAT with **`read:packages`**.
Put it in `~/.gradle/gradle.properties` (not in the repo):

```
gpr.usr=<github-username>
gpr.key=<personal-access-token>
```

Without it the build fails at dependency resolution, which reads like a network or repository fault
rather than a missing credential. Note the property is `gpr.usr` — a commented-out block in the
module's `build.gradle` says `gpra.usr`, which is dead code.

### `android.useAndroidX=true` is required, and the repo does not set it

The build root's `gradle.properties` is gitignored, so a fresh clone has none. The library depends
on AndroidX (`androidx.appcompat`, `androidx.documentfile`), and without the property AGP stops:
"contains AndroidX dependencies, but the `android.useAndroidX` property is not enabled". Create
`ShimmerAndroidInstrumentDriver/gradle.properties` containing

```
android.useAndroidX=true
```

or pass `-Pandroid.useAndroidX=true` on the command line. Leave `android.enableJetifier` off: it
breaks the unit-test classpath on a transitive `jackson-core` 2.18.2 with "Unsupported class file
major version 65".

### Unit tests

Since DEV-1148 the library module has JVM unit tests in its `src/test/` (JUnit 4). They run against
the stub `android.jar`, with `unitTests.returnDefaultValues = true` so the stubs return defaults
instead of throwing — `Shimmer`'s constructor and its logging call into them. From
`ShimmerAndroidInstrumentDriver/`:

```
./gradlew -Pandroid.useAndroidX=true :ShimmerAndroidInstrumentDriver:test
```

That runs both the debug and release variants. CI runs the debug variant (`testDebugUnitTest`).

Some tests pin down what the driver queues, not only what `Shimmer.java` does, so a driver pin bump
can fail them with nothing wrong here: adapt them to the driver's new behaviour in the bump PR. The
first case was driver 0.11.12_beta and DEV-1337 (Shimmer-Java-Android-API#339): the driver no longer
writes the clock to a streaming Shimmer3R, so `startSDLogging()` on one queues only
`START_LOGGING_ONLY_COMMAND`. The 0.11.12_beta bump adapted AAPI_00002, 00003 and 00005 to that, as
#339 did the driver's own tests. Where a test needs a command queued behind the one in flight, it
uses `readDirectoryName()`: while streaming, `readBattery()` here writes straight to the socket
rather than queueing.

## CI

`.github/workflows/ci.yml` runs on every PR into master and every push to it. It builds the library
and every example (`assembleDebug`), then runs the library's unit tests, against the default driver.
No check is required to merge yet, so look at the result. `release.yml` is the dispatch-only Release
Android API workflow; it dry-runs on any PR that changes it.

## Modules

`ShimmerAndroidInstrumentDriver` is the library. The rest are examples, and are the practical
documentation: `shimmerBasicExample`, `bluetoothManagerExample`, `shimmerServiceExample`,
`shimmerLegacyExample`, `shimmer3BLEBasicExample`, `verisenseBLEBasicExample`,
`multiverisenseblebasicexample`, `efficientDataArrayExample`, `shimmer3DOrientationExample`,
`shimmerConnectionTest`, `shimmerspeedtest`.

`libs/` holds three checked-in jars — `ShimmerBiophysicalProcessingLibrary_Rev_0_11.jar` (closed
source), `AndroidBluetoothLibrary.jar` and `androidplot-core-0.5.0`.

## README links point at a dead org

Every link in the README — wiki, releases, quick-start, Shimmer3R notes — targets
`ShimmerEngineering/ShimmerAndroidAPI`. That org no longer exists; the links survive only on
GitHub's rename redirect. Repoint them if you are editing the README anyway.
