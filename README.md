# AudioShare USB Companion

Native Android receiver for the customized AudioShare Windows host.

> **Bluetooth-routing modification (unofficial, self-built).** This build removes
> the original *enforced built-in-speaker* routing so received PC audio follows
> Android's normal media routing — including Bluetooth A2DP headsets, wired
> headsets, and USB audio — while keeping built-in-speaker playback when no other
> output is active. It is a derivative of
> [CodeinScrubs/AudioShare-Android](https://github.com/CodeinScrubs/AudioShare-Android)
> and is **not** an official release. See “Bluetooth output routing” below.

## Looking for the app to use?

Ordinary users should **not** clone or build this Android repository and do not
need Android Studio. Download the complete portable Windows ZIP from the
[AudioShare Windows releases](https://github.com/CodeinScrubs/AudioShare/releases).
It already includes the matching signed companion APK and installs it through
the Windows app's explicit **Install companion** button.

For beginner, step-by-step help, give your AI assistant both repository links
and ask it to read the
[AI setup brief](https://github.com/CodeinScrubs/AudioShare/blob/main/docs/AI_ASSISTANT_SETUP.md):

```text
Help me set up AudioShare USB as a beginner using the ready-made portable
Windows release, not source code or Android Studio:
https://github.com/CodeinScrubs/AudioShare
https://github.com/CodeinScrubs/AudioShare-Android
```

The companion is intentionally not a network application. The Windows host
launches it over an already-authorized USB ADB connection, creates an ADB
forward from a randomized Windows loopback port to a randomized Android
abstract Unix-domain socket, and streams framed PCM to a foreground media
playback service.

Windows RC5 intentionally bundles this unchanged **Android RC4 / version-code
7** companion. Download the complete Windows ZIP from the host repository;
you do not need to build or reinstall the phone app if the matching signed
companion is already present. For a connected-but-silent PC, follow the
[host troubleshooting guide](https://github.com/CodeinScrubs/AudioShare/blob/main/docs/TROUBLESHOOTING.md#connected-but-the-phone-is-silent).

Current phase: version-code 7 (`1.0.0-rc.4`) public release candidate. RC4
preserves nested playback failures, fixes a reproduced playback-thread startup
race, and causes the matching Windows host to stop once (rather than reconnect
in a loop) when phone-local media takes Android audio focus. The complete
Windows-to-USB-to-built-in-speaker path is hardware tested on a Samsung Galaxy
A52s running Android 14 with global-system capture, a 10 ms host queue
high-water mark, and zero visible host/Android drops in the initial audible
run. Screen-off endurance, cable reconnect cycling, measured latency, and the
two-hour run remain explicit manual gates before the release candidate is
promoted to stable. See [`docs/HARDWARE_TEST_RESULTS.md`](docs/HARDWARE_TEST_RESULTS.md).

## Security and privacy contract

- No `INTERNET`, microphone, camera, location, contacts, Bluetooth, or storage
  permissions.
- PCM remains in memory and is never recorded, uploaded, or logged.
- Playback service is not exported.
- The exported ADB launch bridge requires Android's shell-only `DUMP`
  permission, so ordinary installed apps cannot start receiver sessions.
- On Android 13 and newer, opening the companion once requests notification
  permission so the foreground Disconnect action remains visible. The USB ADB
  bridge never blocks first connection with a permission dialog. If permission
  is declined, playback still works and can be stopped from Windows or
  Android's foreground-service Task Manager.
- Playback leaves the AudioTrack's preferred device unset so Android's audio
  policy routes this media stream normally (built-in speaker, Bluetooth A2DP,
  wired headset, or USB). The actual route is still verified after writing
  starts: only a continuously missing route (no output device at all) is fatal,
  with a two-second grace window so normal Bluetooth/headset transitions do not
  terminate a long-running session.
- A playback watchdog observes write progress and the Android playback head;
  if PC audio remains pending while both stop advancing for two seconds, the
  session is terminated with an explicit playback-stalled error instead of
  reporting a healthy but silent connection.
- Playback requests media audio focus. Transient focus loss flushes queued PCM
  and resumes only at the live edge; ducking lowers the track volume; permanent
  loss is reported to Windows with its exact cause. The matching Windows host
  stops instead of reconnecting in a loop and tells the user to stop phone-local
  music/video before clicking Connect again.
- Disconnect interrupts any in-flight `AudioTrack` write and waits only for
  bounded worker termination. A replacement session cannot start until the old
  worker has conclusively stopped, preventing overlapping tracks and sockets.
- The receiver prefers Android's low-latency `AudioTrack` mode and retries with
  compatibility performance mode if an OEM rejects construction, buffer
  tuning, start threshold, or initial speaker routing.
- A partial wake lock is held only while a USB session is waiting or streaming,
  then released, so playback can continue reliably with the screen off.
- Each session uses a 256-bit nonce and a unique abstract socket name.
- Every protocol field and payload length is bounded. The transient playback
  queue keeps only the newest 40 ms of complete PCM chunks (or one indivisible
  chunk), with a separate 32-chunk hard memory bound, so a route or writer stall
  drops stale audio instead of becoming permanent playback lag.
- A launched service stops if the host does not connect or authenticate.
  Its connection watchdog closes only the listening socket and cannot race a
  client that was accepted at the deadline.
- If Android rejects the foreground playback service, the launch bridge records
  an explicit exception reason in Android/ADB diagnostics. Android does not
  guarantee that every vendor propagates an Activity exception through
  `am start -W`, so Windows also retains a bounded handshake timeout.

## Build

The project uses Android Gradle Plugin 9.3.2 and its built-in Kotlin support with
a checksum-pinned Gradle 9.7.1 wrapper. JDK 17 or newer is supported; CI uses
the AGP baseline of JDK 17:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug `
  --no-daemon --max-workers=1
```

`assembleRelease` and `lintRelease` require the external signing variables in
[`docs/SIGNING.md`](docs/SIGNING.md); an unsigned artifact is never produced by
the release task. The receiver uses a 40 ms target buffer without going below
Android's reported minimum and, on API 31+, a 20 ms playback start threshold.
The exact capacity, effective buffer, threshold, underruns, route, focus, media
  volume, queue high-water mark, write progress, cumulative playback head,
  play state, and actual performance mode are returned in STATS diagnostics.

The real app and ADB-forwarded protocol can be exercised on an authorized device
or emulator (the command sends a deliberately quiet test tone):

```powershell
python .\tools\device_protocol_smoke.py `
  --adb "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" `
  --serial '<adb-serial>' --duration 3
```

For a strict 60-second screen-off continuity run, use
`--duration 60 --screen-off-seconds 60`. The harness records the initial display state, turns
the display off and waits for that power transition before it launches the
receiver, verifies that the display is still off with the session wake lock
present, then restores the original display state during cleanup. Separating
the transition from the measured stream keeps the zero-drop assertion about
steady screen-off playback rather than about emulator power-transition timing.

An Android 16 emulator can complete cold install, playback-head
readiness, READY, STATS, heartbeat, system-managed output routing,
wake-lock visibility, STOP, and exact forward/service cleanup. Headless virtual
audio is not a zero-drop timing oracle: a fresh 10-second run completed the
protocol with route/focus state valid but reported virtual-audio underruns and
dropped frames. The initial physical Samsung run now proves audible output over
the actual USB cable with zero visible drops in its captured diagnostic snapshot.
Device-specific screen-off policy, repeated reconnect, measured latency, and
endurance remain open physical-device gates.
Locking an already-streaming cold emulator can briefly stall its virtual
`AudioTrack`; the bounded live-edge queue intentionally counts and discards stale
frames in that case instead of preserving permanent playback delay.

## Bluetooth output routing

The stock companion calls `AudioTrack.setPreferredDevice(built-in speaker)` and
fails the session if Android routes PC audio anywhere else. That deliberately
pins output to the phone speaker and prevents a connected Bluetooth headset from
playing PC audio.

This build removes that forced selection (`PlaybackWorker.kt` no longer calls
`setPreferredDevice`). The stream uses standard `USAGE_MEDIA` /
`CONTENT_TYPE_MUSIC` attributes, so Android's normal media routing applies:

- no other output → built-in speaker (unchanged behavior);
- Bluetooth A2DP headset connected and active for media → Bluetooth;
- wired headset / USB audio → that device.

`RouteGraceTracker` and the periodic route check now treat *any* concrete output
device as valid and only fail after a continuously missing route (no output at
all) for the two-second grace window. Route changes, available output devices,
AudioTrack initialization, and failures are logged under the
`AudioShareUsbCompanion` tag (`adb logcat -s AudioShareUsbCompanion`).

The USB wire protocol, audio format, handshake, package ID, and permissions are
unchanged. The Windows host still requires the installed companion `base.apk`
SHA-256 to match its bundled `android\audioshare-companion.apk`, so distributing
this build means bundling this APK in the Windows package (or relaxing that
host-side check).

Release APKs require an external stable signing key; see
[`docs/SIGNING.md`](docs/SIGNING.md). Tagged releases are built with the stable
project identity stored in GitHub Actions secrets; private signing material is
never committed. This project is licensed under LGPL-3.0-or-later; see `LICENSE`.

AudioShare USB custom edition was created by Shayan SalehiRad.
