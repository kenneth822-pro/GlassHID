# GlassHID

<p align="center">
  <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/project-banner.png" alt="GlassHID — your phone is the peripheral" width="100%">
</p>

A completely local keyboard, mouse, gamepad, and Anki study remote for Android.
The phone does not need Wi-Fi, mobile data, an account, or a cloud service. The
first development device is a Samsung Galaxy A05s. It works with Windows, macOS,
Linux, and Android tablets or phones (AnkiDroid), with nothing to install on the
other device. A setup guide walks through permissions and pairing on first launch
(**SET → SETUP GUIDE** reopens it).

The landscape-only interface uses a compact neo-brutalist keyboard. The full
keyboard occupies the canvas; **TRACKPAD ▾** opens the mouse surface from the
top-left without permanently taking space away from the keys. Shift, Caps, Ctrl,
Alt, and Win/Super are functional. Tap a modifier and then a key for a chord;
the active chord is shown beside the app title. Hold Win/Super to send the
Windows key by itself; a normal tap only latches it for a chord.

Tap **GAMEPAD** to replace the keyboard with a PS3-inspired Bluetooth controller,
then tap the centered **KEYS** button to return. Controller mode hides the normal
Trackpad, Tools, Settings, connection, and pairing toolbar to maximize usable
space. It provides two analog/clickable sticks, an eight-way D-pad, the
triangle/circle/cross/square cluster, L1/L2/R1/R2, Select, PS, and Start. Button
combinations and stick-plus-button input can be held simultaneously.
The persistent **SWAP PAD / STICKS** setting exchanges both outer and inner
control positions for players who prefer the analog sticks above the D-pad and
symbol cluster.

Both stick caps follow your finger and snap back to center, making live axis
movement visible on the phone. A stationary tap is an L3/R3 click from anywhere
on the pad; only movement after touchdown steers. The D-pad uses the classic
segmented cross silhouette with a direction triangle on each arm. Active arms
turn yellow and diagonal input highlights both arms. **SET → CONTROLLER LABELS**
cycles the controls between PlayStation names, Xbox-style names, and stable HID
numbers without cluttering the play surface or changing the reports.

Every control has visible input feedback: touch/click depresses the face into
its dark shadow, hover/focus lifts it, and phone touches produce haptic feedback.
**TOOLS ▾** keeps the function/navigation keys and System panel in one compact
drawer. **SET** opens persistent controls for drag-hold delay, pointer speed,
scroll speed, Backspace/key repeat speed, haptics, and laptop click sounds. The
controller position toggle lives at the bottom of this scrollable panel. The
default drag hold is 500 ms. Haptics use the phone vibrator directly, so they do
not depend on Android's global touch-feedback setting.

Shifted symbols are printed on their keys (`!`, `@`, `#`, and the rest). The
drawer's **F KEYS + NAV** opens F1–F12 plus Print Screen, Scroll Lock, Pause,
Insert, Delete, Page Up/Down, Home, and End. Holding Backspace repeats deletion.
On the wider trackpad, hold then
move—or double-tap and keep the second touch down—to drag. A dedicated vertical
scroll pad provides one-finger scrolling with haptic ticks; two-finger scrolling
on the main pad remains available. Three-finger swipes switch desktops left/right,
open Task View when swiping up, and show the desktop when swiping down. The
trackpad card's dock button moves it between the left and right edges.

## App tour

<p align="center">
  <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/keyboard.png" alt="Full GlassHID keyboard" width="100%">
</p>

| Wide trackpad and scroll strip | Compact tools drawer |
| --- | --- |
| <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/trackpad.png" alt="GlassHID trackpad" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/tools-drawer.png" alt="GlassHID tools drawer" width="100%"> |

| Function and navigation keys | Volume, brightness, and laptop battery |
| --- | --- |
| <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/function-keys.png" alt="Function key drawer" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/system-controls.png" alt="System controls" width="100%"> |

| Input tuning | Controller settings |
| --- | --- |
| <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/settings.png" alt="GlassHID input settings" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/settings-controller.png" alt="GlassHID controller settings" width="100%"> |

## Controller layouts

| PlayStation symbols | Xbox-style ABXY | HID numbers 1–4 |
| --- | --- | --- |
| <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-default.png" alt="PlayStation controller labels" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-abxy.png" alt="Xbox-style controller labels" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-1234.png" alt="Numbered controller labels" width="100%"> |

| Live stick position | Active diagonal cross | Swapped pad/sticks |
| --- | --- | --- |
| <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-input-live.png" alt="Left stick moving visibly" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-dpad-active.png" alt="D-pad diagonal arms highlighted" width="100%"> | <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/controller-swapped.png" alt="Swapped controller layout" width="100%"> |

Having trouble with Steam? Follow the [Steam Input setup and exact button map](docs/steam-input.md).

## Bluetooth mode

Bluetooth mode uses Android's native HID Device profile, so Windows sees the
phone as an ordinary composite keyboard, mouse, and standard HID gamepad. No
Windows companion is required for input.
Volume and mute also work directly over Bluetooth. Laptop brightness and the
laptop battery status use the optional USB cable helper described below because
Bluetooth HID has no return-data channel.

1. Open **GlassHID** on the phone and allow Nearby devices.
2. Tap **Make phone visible**, then pair `Galaxy A05s` in Windows Bluetooth settings.
3. Back in the app, tap the button for the paired computer and select **Bluetooth**.
   The app remembers this computer and active mode, then attempts to reconnect
   after a dropped HID session or app restart.

Windows' generic “Connected” label only confirms the Bluetooth bond. The app's
top bar says **INPUT ✓** only after Android reports that the keyboard/mouse HID
session is genuinely open. The Pair card also distinguishes **PAIRED · INPUT
OFFLINE** from **INPUT CONNECTED ✓**.

After upgrading from a keyboard-only build, remove and pair `Galaxy A05s` once
in Windows Bluetooth settings. Windows caches a paired device's HID collections,
so re-pairing is required once before its Game Controllers panel can see the new
gamepad collection. This is a standard HID/DirectInput controller; games that
accept only Xbox/XInput devices may require a local mapper.

`windows/Pair-Bluetooth.ps1` is an optional local Windows pairing helper. Run it
while the phone is visible if the normal Add device screen does not find the phone.

Keep the app in the foreground while using the phone as a trackpad.

If a connection attempt hangs, the app drops the stuck request after about ten
seconds and retries; if it keeps hanging, it re-registers its HID service
automatically. Tapping **CONNECT INPUT** also clears a stuck attempt.

### Android tablets and phones as the host

The same Bluetooth input works with another Android device, because Android
accepts Bluetooth keyboards and mice natively. Nothing needs to be installed
on the tablet.

1. In GlassHID, tap **PAIR → MAKE PHONE VISIBLE**.
2. On the tablet, open **Settings → Bluetooth → Pair new device** and pick the phone.
   Confirm the pairing code on both devices.
3. A computer or tablet paired within five minutes of **MAKE PHONE VISIBLE**
   becomes the active host automatically. Otherwise tap **CONNECT INPUT · tablet**
   in the Pair card.

The phone serves one host at a time. The Pair card lists the live host first,
then the last-used one, then other computers and tablets. Tap a different device
to switch: the phone drops the old link before connecting the new one. While
the last-used host is away, reconnect attempts slow from every 3.5 seconds to at
most every 10 seconds, so another host can reach the phone in between.

If the tablet pairs but input never connects, open the phone's entry in the
tablet's Bluetooth settings and make sure **Input device** is switched on.

### Background input

In Bluetooth mode a foreground service keeps the connection alive while GlassHID
is in the background or the screen is off. An ongoing notification shows the host
and has a **Disconnect** button. The service stops itself after 10 minutes in the
background without a connected host, and starts again when you reopen the app.
Turn it off with **ANKI → SET ▾ → BACKGROUND INPUT**. On Android 13 and newer the
notification needs the Notifications permission; the service runs either way.

## Anki review mode

Tap **ANKI** (or **TOOLS ▾ → ANKI REVIEW**) for a dedicated flashcard remote.
Opening it switches an **Off** mode to Bluetooth.

**Header.** The status chip shows the connection, the host's name, and the
target app; tap it for the Pair card. Then **UNDO**, **REPLAY**, **MARK**,
**MORE**, **STEALTH**, **POCKET**, **SET ▾**, and **EXIT**. Portrait splits the
header over two rows.

**Study bar.** Today's reviews with a goal ring, this session's count, pace,
and Again rate, your streak, and the focus timer. With live Anki desktop info it
also shows the deck's new, learning, and due counts. Tap it for the study tools.
**SET ▾ → STUDY BAR** hides it.

**Review buttons.** **SET ▾ → LAYOUT** cycles three layouts:

- **CENTER**: **FLIP / SPACE** on top, **AGAIN · 1** and **GOOD · 3**, then
  **HARD · 2** and **EASY · 4**.
- **SPLIT** (landscape): Flip on one side, the grades on the other.
- **SWIPE**: one big pad. Tap to flip; swipe left for Again, right for Good, up
  for Easy, down for Hard; hold to undo.

**SET ▾ → HAND: LEFT** mirrors the screen: the TAP/SCROLL column moves to the
left and Good and Easy sit on the left. On the right (or left), the **TAP** pad
clicks at the computer's or tablet's pointer (drag it to aim), and the scroll
strip below it scrolls long cards.

### Volume keys

Volume Down shows the answer, then grades Good. Volume Up grades Again. Holding
a key runs a shortcut chosen in **SET ▾ → HOLD VOL UP / HOLD VOL DOWN** (Undo
and Flag red by default; also Mark, Bury, Suspend, Replay, Hard, Easy, Edit, or
Off). With a hold shortcut set, a short press acts when you let go of the key;
with **OFF** it acts as soon as you press.

### Pocket mode

Tap **POCKET** to keep reviewing with the screen off or another app open: the
volume keys stay an Anki remote and each action buzzes (a short tick for flip,
a double tick for Good, a long buzz for Again). The notification shows pocket mode
and has **Exit pocket mode**; **POCKET ✓** in the remote also ends it, and it ends
by itself after 20 minutes without a press. It needs Bluetooth mode. While it runs,
the phone's volume keys do not change the volume, and GlassHID holds a wake lock
so every key press is released on time. If another app is playing audio, Android
may give the volume keys to that app instead.

### Stealth

**STEALTH** blacks out the screen. The volume keys still work, swiping anywhere
scrolls, and a double-tap anywhere clicks at the pointer.

### Study tools

**SET ▾ → STUDY TOOLS** (or a tap on the study bar) opens:

- This session: cards, minutes, pace, the Again/Hard/Good/Easy split, and the
  Again rate. Study time caps each gap at one minute, like Anki, so a break does
  not drag the pace down. A session ends after 30 minutes without an action.
- Today against your **daily goal** (50 to 1000 cards, or off), your streak, your
  best day, and a 14-day chart. Reaching the goal buzzes once.
- A **focus timer**: a focus block (10 to 60 minutes) followed by a break (3 to 20
  minutes), with a buzz at the end of each. It starts with your first review after
  you set it, or with **START FOCUS**.
- An **answer nudge**: a gentle buzz when a question has been showing longer than
  your limit (8 to 60 seconds, or off).
- **NEW SESSION** and **CLEAR HISTORY** (tap twice to confirm).

Counts come from remote presses that reached the computer or tablet, so they
track Anki closely but are not Anki's own records. With live Anki desktop info,
"today" uses Anki's own count. Everything stays on the phone; history keeps the
last 120 days.

### Keys

If you changed Anki's or AnkiDroid's shortcuts, **SET ▾ → KEYS…** tells the remote
which key each action sends. Pick the app at the top, tap an action, choose
modifiers and a key, and **SAVE**. A dot marks a changed key; **DEFAULT** and
**RESET ALL** restore the stock shortcuts.

### Other settings

**SET ▾** also holds rotation (Auto, Portrait, Landscape), OLED black, haptics,
and sound. Rotating the phone keeps the Bluetooth session open.

### AnkiDroid on a tablet or second phone

The remote drives Anki desktop on a PC or AnkiDroid on another Android device.
**SET ▾ → TARGET** chooses between them. **AUTO** (the default) picks AnkiDroid
when the connected host reports itself as a phone or tablet, and Anki desktop
otherwise. USB mode always drives the PC. The header chip shows the host and
the target in use, for example `Galaxy Tab S9 · ANKIDROID (AUTO)`.

Both apps share the same default review shortcuts, so every review button works
the same way:

| Button | Key sent | Anki desktop | AnkiDroid |
| --- | --- | --- | --- |
| FLIP / SPACE, Volume Down | Space | Show answer, then Good | Show answer, then Good |
| AGAIN / HARD / GOOD / EASY | 1 / 2 / 3 / 4 | Grade | Grade |
| UNDO | Ctrl+Z | Undo | Undo |
| REPLAY | R | Replay audio | Replay audio |
| MARK | * | Mark note | Mark note |
| Scroll strip | Mouse wheel | Scroll card | Scroll card |
| MORE | M (desktop) | More menu | — |
| MORE ▾ (AnkiDroid) | -, =, @, !, Ctrl+1–4, E, Ctrl+Shift+Z | — | Bury, suspend, flag, edit, redo |

AnkiDroid has no **M** "More" shortcut, so with the AnkiDroid target **MORE ▾**
opens a panel on the phone. It offers bury card/note, suspend card/note, red,
orange, green, and blue flags, edit note, and redo. These are AnkiDroid's default
shortcuts and work in both its classic and new study screens.

Notes for AnkiDroid:

- Scrolling uses the mouse wheel, so the tablet may briefly show a mouse pointer.
  The card scrolls wherever that pointer sits, which starts at the screen centre.
- The `*`, `@`, `!`, `=`, and `-` shortcuts assume the tablet's physical
  keyboard layout is English (US), which is Android's default.
- If you changed AnkiDroid's key bindings (**Settings → Controls**), the remote
  sends the default keys listed above.
- On "type in the answer" cards, keys go to the answer field until it is submitted.

**Images and sketches.** Many note types enlarge an image when the mouse hovers
over it on a PC, or when you tap it on a tablet. Android only updates hover when
the pointer moves, so after each scroll with the AnkiDroid target, the remote
nudges the tablet's pointer by one pixel and back. The image now under the
pointer reacts the same way it does on the PC. For a specific image, use the
**TAP** pad above the scroll strip: drag it to aim the tablet's pointer, then tap
to click, which is the same as tapping the image. In **STEALTH**, double-tap
anywhere to click. If AnkiDroid tap gestures are enabled (**Settings → Controls**),
a click on an empty part of the card also triggers that gesture.

## System controls and laptop battery

Open **TOOLS ▾ → SYSTEM** for volume down/mute/up and brightness down/up controls.
With `windows/Start-UsbInput.ps1` running, the top bar and System card show only
the laptop battery percentage and charging state. The helper talks only over the ADB
USB loopback tunnel; the phone still does not use Wi-Fi. It can stay running
while the app's active input mode is **Bluetooth**.

The helper also plays a local Windows click sound for phone button presses when
**LAPTOP CLICKS** is enabled. This sound needs the USB helper because Bluetooth
HID itself has no phone-to-helper feedback channel.

Brightness is intentionally cable-only for safety and compatibility. System
commands are written through a background queue, so a stale cable connection
cannot freeze the Android interface.

The launcher icon uses the same mint, cream, coral, heavy-outline, and offset-
shadow visual language as the app.

## USB mode

USB mode sends a tiny line-based protocol through `adb forward`. Its TCP sockets
exist only on each device's loopback interface; the cable carries the data and
the phone never joins a network.

1. Keep USB debugging enabled and connect the cable.
2. Open **GlassHID** and select **USB**.
3. Run `windows/Start-UsbInput.ps1` on Windows. Press Ctrl+C to stop it. The same
   helper also enables laptop battery status and reliable laptop-panel brightness in
   Bluetooth mode.

Run `python windows/usb_input_host.py --check` to verify the cable path without
injecting any keyboard or mouse events.

### Live Anki desktop info

With the free [AnkiConnect](https://ankiweb.net/shared/info/2055492159) add-on
installed in Anki desktop (**Tools → Add-ons → Get Add-ons**, code `2055492159`),
the USB helper relays what Anki is doing once a second:

- The deck name and its new, learning, and due counts in the study bar.
- The next interval for each answer on the grade buttons, for example `GOOD · 3  4d`.
- Anki's own reviewed-today count.
- When the card changes, the remote's flip/grade state resets, so Volume Down
  always shows the next answer first.

It talks to AnkiConnect on `127.0.0.1` only. Start the helper with `--no-anki` to
turn the relay off, or `--anki-url` for a different AnkiConnect address. Live info
needs the USB helper; Bluetooth HID cannot carry data back to the phone.

## Install and update

Download the APK from the latest run under **Actions** (artifact `GlassHID-debug`)
or from **Releases**. Since 2.0 every build is signed with the same key, so a new
APK installs over the previous one and keeps your settings and study history.
Updating from a 1.x build needs one uninstall first, because those builds were
signed with a different key each time.

Pushing a tag such as `v2.0.0` builds the app and publishes a GitHub release with
the APK attached. To sign releases with your own key, create one with
`keytool -genkeypair -keystore release.jks -alias glasshid -keyalg RSA -keysize 2048 -validity 10000`
and add these repository secrets: `GLASSHID_KEYSTORE_BASE64` (the file, base64-encoded),
`GLASSHID_KEYSTORE_PASSWORD`, `GLASSHID_KEY_ALIAS`, and `GLASSHID_KEY_PASSWORD`. Switching
an installed phone from the debug key to your release key needs one uninstall.

## Build

This project intentionally uses only Android platform APIs and the Python
standard library, so its runtime has no third-party dependencies. The Android
code is split by responsibility: Bluetooth HID, USB transport, feedback,
neo-brutalist styling, key mapping, controller layout, trackpad gestures, and the
scroll strip are separate components; `MainActivity` coordinates the screen. The
Anki remote lives in `AnkiRemote`, backed by small Android-free classes for keys,
review state, volume gestures, and study statistics. `GlassHid` holds the state the
activity, the background service, and pocket mode share.

CI runs `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: unit
tests, Robolectric tests that drive every screen, popup, layout, and mode, and a
lint check that rejects calls to APIs newer than the minimum SDK. The Windows
helper's AnkiConnect relay is tested with
`python -m unittest discover -s windows -p "test_*.py"`.

## Contributing

<img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/contributions-welcome.png" alt="Contributions welcome" width="100%">

Bug fixes, device reports, layouts, accessibility work, documentation, and
translations are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening
a pull request, use the issue templates for reproducible reports, and disclose
input or pairing vulnerabilities through the process in [SECURITY.md](SECURITY.md).

GlassHID is available under the [MIT License](LICENSE). Community participation
is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
