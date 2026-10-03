# GlassHID

<p align="center">
  <img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/project-banner.png" alt="GlassHID — your phone is the peripheral" width="100%">
</p>

A completely local keyboard, mouse, and gamepad for Android. The phone does not
need Wi-Fi, mobile data, an account, or a cloud service. The first development
device is a Samsung Galaxy A05s.

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

## Anki review mode

Tap **ANKI** (or **TOOLS ▾ → ANKI REVIEW**) for a dedicated flashcard remote.
**FLIP / SPACE** reveals the answer, then **AGAIN · 1**, **HARD · 2**,
**GOOD · 3**, and **EASY · 4** grade the card. The phone's volume keys work too:
Volume Down flips and then grades Good, and Volume Up grades Again. The header
shows the live connection (tap it to open the Pair card) plus Undo, Replay, Mark,
More, Stealth, and Exit. The scroll strip on the right scrolls long cards.

**SET ▾** holds the Center/Split layout, rotation (Auto, Portrait, Landscape),
OLED black, haptics, and sound. Portrait always uses the Center layout.
Rotating the phone keeps the Bluetooth session open. **STEALTH** blacks out the
screen: the volume keys still grade cards and swiping anywhere scrolls.

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

## Build

This project intentionally uses only Android platform APIs and the Python
standard library, so its runtime has no third-party dependencies. The Android
code is split by responsibility: Bluetooth HID, USB transport, feedback,
neo-brutalist styling, key mapping, controller layout, trackpad gestures, and the
scroll strip are separate components; `MainActivity` coordinates the screen.

## Contributing

<img src="https://raw.githubusercontent.com/MooketsiMagwaza/GlassHID/main/docs/images/contributions-welcome.png" alt="Contributions welcome" width="100%">

Bug fixes, device reports, layouts, accessibility work, documentation, and
translations are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening
a pull request, use the issue templates for reproducible reports, and disclose
input or pairing vulnerabilities through the process in [SECURITY.md](SECURITY.md).

GlassHID is available under the [MIT License](LICENSE). Community participation
is governed by [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
