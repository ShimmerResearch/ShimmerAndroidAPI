# Android HIL: the Android API on real Shimmers

Hardware-in-the-loop tests of the Android API. A phone on USB connects to real Shimmer3 and Shimmer3R
units, over Bluetooth Classic, BLE or both, through the same public API an app uses
(`ShimmerBluetoothManagerAndroid`, `ShimmerBluetooth`). One command builds the tests, installs them,
runs every scenario on every unit and transport, pulls the reports and prints pass or fail.

**Both transports matter, because on Android they are different code.**

| Transport | Device class | Parser | Pairing |
|---|---|---|---|
| Classic | the Android API's own `Shimmer` | the API's own copy, overriding the driver's | needed once |
| BLE | `Shimmer3BLEAndroid` | the Java driver's | none |

A defect in one transport says nothing about the other. DEV-1148 is Classic only, for example: the
driver has had its fix, DEV-1147, since 0.11.9_beta.

```powershell
.\tools\hil\run-android-hil.ps1
```

It exits 0 when everything passed, 1 when anything failed, errored, timed out or was skipped, and 2 when the bench isn't set up.

## Before every Android release (the gate)

**Release Android API** won't publish a commit that hasn't passed this on hardware. Each round:

1. Release the Java API, if the driver changed.
2. On the bench, on `master` with a clean working tree and every unit in the rig, run:
   ```powershell
   git checkout master; git pull
   .\tools\hil\run-android-hil.ps1 -Report
   ```
   With `-Report`, the result is posted to GitHub as the commit status
   **`android-hil (driver X)`** on that commit: passed or failed, with the phone and each unit's
   firmware. It refuses to report a partial run or a run of uncommitted code:
   - every scenario, at least one Shimmer3 and one Shimmer3R, and both transports, Classic and BLE;
   - the default rate, duration, link CRC and loss limit, and a fresh build (no `-SkipBuild`);
   - this checkout's library source;
   - a clean working tree, pushed.
3. Run **Release Android API** with the same driver (blank picks the newest, as the script does).
   It looks up that status for the commit it's releasing and the driver it builds against, and
   stops before building anything unless it passed. The release notes then say what was tested.

If anything merges after step 2, the commit changes, so test again. In an emergency, put a Jira key
in **hil_waiver** to release without a pass; the release notes record the waiver. Dry runs only warn.

To find regressions before release day, schedule step 2 nightly with Windows Task Scheduler, without
`-Report`.

## What it tests

| Scenario | What it does | Fails when |
|---|---|---|
| `streamRest` | Connects, writes an accel-only config at `-Rate` (default 256 Hz), streams for `-Seconds` (default 30) with the unit lying still, then stops and disconnects. | It can't connect or configure; more than `-MaxLossPercent` (1 %) of packets are lost; a timestamp doesn't increase, or leaps more than a second ahead of when it arrived (a bad unwrap, not loss); the timestamps give no rate, or one more than 1 % off the configured rate; the stream goes silent for over a second before the test stops it; accel is more than 1 m/s² from 9.81; or the API logs a "Packet syncing problem". |
| `midStreamSet` | While streaming, starts and stops SD logging and reads the battery, three times. | One of those calls fails, or a battery reply isn't decoded within 5 s; logging doesn't start or stop (the DEV-1148 symptom with a link CRC on); a packet is lost around a battery read, or more than `-MaxLossPercent` away from the SD transitions; the timestamps fail the same rate and silence checks as `streamRest`; or there's a parser error. Packets missing within 1 s of an SD logging start or stop are reported, not failed: the unit itself pauses while it opens its file, which is for the firmware HIL bench to judge. A start counts from both the command and the unit confirming it (here and in `inStream`): the pause comes up to 0.71 s after the confirmation, which can be more than 1 s after the command. Needs an SD card in the unit, off the dock and USB. |
| `reconnectLoop` | Connects, streams for 5 s and disconnects, five times. | Any cycle fails to connect within 30 s, loses packets, fails the rate and silence checks, or logs a parser error. |
| `inStream` | While streaming, three times: starts SD logging, stops it, and reads the battery and the status every 40 ms for 400 ms, so some reads land in the ~100 ms the unit pauses its data packets while it closes its file (DEV-1291). Then, three times, stops and starts logging back to back, so the queued start goes out in that pause. | A battery read isn't answered, or fewer status replies than reads are decoded; logging doesn't stop or restart, or a command stays queued; a command times out; there's a parser error; more than `-MaxLossPercent` of packets are lost away from the SD transitions and the reads; or the timestamps fail the same rate and silence checks as `streamRest`. Loss near the reads is reported, not failed: a Shimmer3 now and then skips a sample around a battery read itself. The report's `repliesInAPause` counts the replies that came with no data packet before them. Needs an SD card in the unit, off the dock and USB. |
| `connectReady` | Connects to the unit idle, starts SD logging, disconnects and connects again while it logs, then stops logging. | The unit is reported ready (a CONNECTED or SDLOGGING state message, or that state in the API) more than 250 ms before `NOTIFICATION_SHIMMER_FULLY_INITIALIZED`, which marks the end of the connect sequence (DEV-1275); or the connect doesn't end in CONNECTED, or SDLOGGING while logging. Needs an SD card in the unit, off the dock and USB. |
| `queueStress` | Connected and idle, with the API's timers on as in an app: four threads queue battery and status reads in tight loops for 60 s, keeping the queue under 8. Each queued read takes the device's lock and then the queue's while the IO thread fetches the next instruction, the window DEV-1266's lock-order deadlock needs. Idle, because while streaming Android writes these reads straight out, unqueued. | A read doesn't return within 10 s, or no read returns for 10 s (the report then holds every thread's stack); a read throws; or the queue doesn't drain within 15 s after the reads stop. No SD card needed. On driver 0.11.7_beta, which has the deadlock, this is the scenario to reproduce it. |
| `samplingRates` | Writes 51.2, 256 and 512 Hz in turn, the way the examples configure, and streams 5 s at each; then writes the unit's own rate back. Every configure but the first comes straight after a `stopStreaming()`, as an app that changes the rate does: up to driver 0.11.10_beta the stop's reply then retired the configuration's first chunk, the one holding the sampling rate (DEV-1306). | A configuration doesn't complete within 20 s, or the rate read back from the unit isn't the one written; or a stream fails `streamRest`'s loss, timestamp, rate and silence checks, or logs a parser error. No SD card needed. |

`-Scenario resetUnit` is not a check and is not part of a default run: it connects, stops SD logging if a run left the unit logging, and disconnects.

Every scenario also fails if an API call doesn't return within 10 s. The report then holds every thread's stack, which a phone without root can't otherwise give, and the scenario stops. If the whole test process still never finishes, the runner force-stops it after `-ScenarioTimeoutSeconds` (600) and records TIMEOUT.

The packet checks use the device's own timestamps, so a loss is a gap in the device's sequence, not
phone scheduling. They follow the firmware HIL harness in log-and-stream-common `Test/hil` (DEV-1165),
so this can later run there as a tier.

### What it does not test
Over BLE the tests run the driver's own streaming parser, the one the PC and MATLAB use. Over Classic
they run the Android API's copy of it (`Shimmer.java` overrides `processPacket`,
`processWhileStreaming`, `processInstreamResponse`, `clearSingleDataPacketFromBuffers`, `readBattery`
and `readStatusLogAndStream`). Neither covers:

- `shimmerdriverpc`: the serial port, BLE gRPC, and Shimmer3R USB;
- the SD and file parsers.

Both cover the driver's connect sequence, configuration, calibration, decoding and timestamp unwrap,
since Android uses those unchanged.

## Choosing what to test

| | Default | Option |
|---|---|---|
| Android API | this checkout's library source | `-AndroidApiVersion 3.2.10_beta`: the published `.aar`, as a customer app gets it |
| Java driver | the newest `API_v` release | `-DriverVersion 0.11.8_beta` |
| Units | every unit in the rig file | `-Unit s3r-15bc` |
| Transports | every transport each unit lists | `-Transport ble` |
| Scenarios | all five | `-Scenario midStreamSet` |
| Link CRC | ask for 2 bytes (the driver may settle on 1 or off) | `-Crc off`, `-Crc 1` |
| Pause between runs | 10 s | `-SettleSeconds 60` |
| Record the result for the release gate | no | `-Report` (full run on a clean, pushed commit only) |
| Save the bytes exchanged with each unit | no | `-CaptureBytes` (see [Capturing the bytes](#capturing-the-bytes)) |

## One-time setup

1. **Phone.** Turn on Developer options, then USB debugging, and "Stay awake while charging".
   Connect it to the PC and accept the debugging prompt. On Xiaomi phones also turn on "Install via
   USB". On Android 12 and later also turn on "USB debugging (Security settings)", which lets the
   tests be granted `BLUETOOTH_CONNECT`. Up to Android 11 nothing needs granting: connecting to a
   paired unit uses install-time permissions only.
2. **Pair each unit you test over Classic** with the phone, in Settings → Bluetooth, PIN `1234`. BLE
   needs no pairing. A Shimmer3R usually bonds over BLE instead (DEV-970). The phone then reaches
   the unit, but the serial channel is refused, and the Classic test reports that it "did not
   connect" within 2 s. Unpair it, then either pair again with the unit's BLE turned off, or connect
   once from a classic serial-terminal app so that Android makes a Bluetooth Classic bond. Until
   then, test that Shimmer3R over BLE only.
3. **Prepare each unit.**
   - **SD sync and Bluetooth disable off.** Either one keeps Bluetooth off (DEV-1228). Check in
     Consensys.
   - **SD card in, and off the dock and USB,** for `midStreamSet`, `inStream` and `connectReady`.
     Docked, or with USB plugged into a Shimmer3R, the firmware ACKs a start of SD logging and then
     ignores it (LogAndStream v1.01.002 on Shimmer3, v1.01.004 on Shimmer3R). The API still shows
     the unit logging, so these scenarios fail at once, saying the unit is docked or on USB, rather
     than pass without logging anything.
   - **Lying still** during `streamRest`.
4. **List the units:** copy `rig-android.example.json` to `rig-android.json` and fill in each name,
   MAC, `type` (`Shimmer3` or `Shimmer3R`) and `transports` (`["classic"]`, `["ble"]` or both;
   Classic if left out). That file is gitignored. You can read the MACs of paired units off the
   phone:
   ```powershell
   adb shell dumpsys bluetooth_manager | Select-String -Context 0,6 'Bonded devices'
   ```
5. **Credentials for GitHub Packages,** where the driver comes from. Either set `gpr.usr` and
   `gpr.key` in `%USERPROFILE%\.gradle\gradle.properties`, or let the script use your `gh` login
   after granting it the packages scope once:
   ```powershell
   gh auth refresh -h github.com -s read:packages
   ```
6. **JDK 17 or newer.** The script uses `JAVA_HOME` if it qualifies, otherwise the JDK Android Studio
   ships in its `jbr` folder. It also finds the Android SDK (`ANDROID_HOME`, or Android Studio's default
   location) when the checkout has no `local.properties`.

A Shimmer holds one Bluetooth link at a time. Don't run this while Consensys, the PC HIL harness or
another phone is connected to the same unit.

## Output

The run's folder, `tools/hil/out/<timestamp>/`, holds:
- `summary.json` and the table the script prints;
- `reports/`: per scenario and unit, a JSON report (versions, CRC mode actually in use, loss,
  rate, accel, parser errors, a timeline of events and the failures) and, for `streamRest` and
  `midStreamSet`, a CSV of every sample;
- `*.logcat.txt` and `*.instrument.txt`: the phone's log and the test runner's output for each run;
- with `-CaptureBytes`, `*.bytes.log` for each run and `btsnoop/`, the phone's snoop log they came from;
- `build.log`.

## Capturing the bytes

When a run finds something, the bytes that caused it are what a fix needs: replayed, they turn the
finding into a byte-level test that runs anywhere, with no phone or unit. With `-CaptureBytes`, the
script saves each run's bytes as `<unit>_<transport>_<scenario>.bytes.log`, in the trace format
shimmer-logandstream's replay tests read (`tests/data/*.bytes.log`, loaded by `load_session` in
`tests/common/mod.rs`):

```
14:33:05.120  TX h17 [0x07]
14:33:05.163  RX h14 [0xFF 0x00 0x12 0x34 ...]
```

TX is phone to unit, RX unit to phone. Over BLE, each line is one ATT write or notification, and
`h<n>` is its attribute handle. Over Classic, each line is one RFCOMM frame's payload and `r<n>` its
channel. Those are kept as the radio delivered them, not split into packets, so a bug that depends
on how bytes arrive replays the same way. The write that turns notifications on is left out, as in
the recorded traces.

**One-time setup:**
- On the phone: Developer options → **Enable Bluetooth HCI snoop log**. Choose "Enabled", not
  "Filtered", if it asks.
- **Then turn Bluetooth off and on.** The log only starts when Bluetooth does. adb can do neither step.
  The script checks with the Bluetooth service (`mSnoopLogSettingAtEnable` in
  `adb shell dumpsys bluetooth_manager`), and refuses to start until it reports `full`.
- Python 3 on the PATH.

**How it works:** the script notes the phone's clock before and after each run. After the last run
it takes one bug report (about 3 minutes), because that is the only way adb can read the snoop log
on a phone that isn't rooted. It keeps `btsnoop_hci.log` and deletes the bug report, which holds the
phone's whole state, from the PC and the phone. Then `btsnoop_to_trace.py` cuts out each run's bytes,
by the unit's MAC and the run's time window. Android stamps the snoop log in the phone's local time,
so the script passes the phone's UTC offset along. Each run's window and that offset are saved in
`summary.json` (`captureWindows`, `phoneUtcOffsetMin`), so the bytes can be cut out again later.

None of this changes a verdict: if it fails, the script says so and carries on.

**Limits:**
- Android keeps only the current snoop log and the one before it, so a long session can lose its
  first runs. Capture a few runs at a time, with `-Unit` and `-Scenario`.
- `btsnoop/` holds all the phone's Bluetooth traffic, not only the unit's. It stays in the run's
  folder, which is gitignored.

To extract by hand, or from a snoop log taken some other way:

```powershell
python tools\hil\btsnoop_to_trace.py btsnoop_hci.log --mac EC:06:D8:0D:86:F8 --utc-offset-min 480 --out run.bytes.log
```

Leave out `--mac` to keep every device's traffic; add `--from-ms` and `--to-ms` (Unix time, from
`captureWindows`) to keep one run. Its tests run without a phone:
`cd tools\hil; python -m unittest test_btsnoop_to_trace`.

## Running a single test by hand

```powershell
cd ShimmerAndroidInstrumentDriver
.\gradlew :shimmerHilTest:assembleDebug :shimmerHilTest:assembleDebugAndroidTest -PdriverVersion=0.11.9_beta -Pandroid.useAndroidX=true
adb install -r -g shimmerHilTest\build\outputs\apk\debug\shimmerHilTest-debug.apk
adb install -r -g shimmerHilTest\build\outputs\apk\androidTest\debug\shimmerHilTest-debug-androidTest.apk
adb shell am instrument -w -e class com.shimmerresearch.hiltest.HilScenariosTest#streamRest -e mac 00:06:66:XX:XX:XX com.shimmerresearch.hiltest.test/androidx.test.runner.AndroidJUnitRunner
```

Add `-e transport ble -e type Shimmer3R` to run the same test over BLE against a Shimmer3R.

Without `-e mac`, every test is skipped. That's how CI can compile the tests without hardware.
