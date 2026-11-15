<p align="center">
  <b>English</b> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="docs/images/icon.png" alt="Fractal icon" width="128" height="128">
</p>

<h1 align="center">Fractal</h1>

<p align="center">
  <b>Wii U emulation built for Android handhelds and phones.</b><br>
  A Cemu fork tuned for Adreno and Mali GPUs, with a new interface made for gamepads.
</p>

<p align="center">
  <a href="https://github.com/dfdx047/Fractal/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/dfdx047/Fractal/total?style=flat-square&color=FF2D95&label=downloads"></a>
  <a href="https://github.com/dfdx047/Fractal/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/dfdx047/Fractal?include_prereleases&style=flat-square&label=release&color=8A5CF6"></a>
  <a href="#requirements"><img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=flat-square&logo=android&logoColor=white"></a>
  <a href="#requirements"><img alt="ABI arm64-v8a" src="https://img.shields.io/badge/ABI-arm64--v8a-555555?style=flat-square"></a>
  <a href="#requirements"><img alt="Vulkan 1.1+" src="https://img.shields.io/badge/Vulkan-1.1%2B-AC162C?style=flat-square&logo=vulkan&logoColor=white"></a>
  <a href="LICENSE.txt"><img alt="License MPL-2.0" src="https://img.shields.io/badge/license-MPL--2.0-orange?style=flat-square"></a>
  <a href="docs/ROADMAP.md"><img alt="Status: public beta" src="https://img.shields.io/badge/status-public%20beta-FF2D95?style=flat-square"></a>
</p>

> [!WARNING]
> **Fractal is in public beta (v0.1-beta).** Many games run, many don't yet, and the same game can behave differently
> from one device or GPU driver to another. Keep backups of your saves and please
> [report what you find](#reporting-bugs).

## Contents

- [Highlights](#highlights)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting started](#getting-started)
- [GPU drivers](#gpu-drivers)
- [Controls](#controls)
- [What Fractal fixes](#what-fractal-fixes)
- [Reporting bugs](#reporting-bugs)
- [Roadmap and changelog](#roadmap-and-changelog)
- [Building from source](#building-from-source)
- [Credits](#credits)
- [License](#license)

## Highlights

- **Made for handhelds.** Every screen works with a gamepad, with a one-line button legend and layouts that fit short
  landscape screens (Retroid Pocket, Anbernic, AYN) as well as portrait phones.
- **Five themes:** Solid, Liquid Glass, Blur, Material You and Synthwave, with static or animated wallpapers (or your own
  image) and an intro animation for each theme.
- **A real game library:** grid, carousel or list; play time, last played, favorites and sorting; per-game settings with
  **Resolution & FPS** options detected from the community graphic packs; save and shader cache export/import.
- **GPU drivers in one tap:** a catalog of Turnip builds for Adreno (MrPurple666, StevenMXZ, faux123, K11MCH1 and more)
  and experimental Mali drivers, selectable globally or per game, with automatic fallback to the system driver if a
  driver fails to start.
- **A renderer tuned for mobile GPUs:** fewer render pass breaks on tile-based GPUs, framebuffer compression (UBWC/AFBC)
  kept on, workarounds for Qualcomm driver bugs and a fix for Turnip's blocking fence checks.
- **Faster ARM64 recompiler:** more PowerPC instructions recompiled instead of interpreted, and less overhead per block.
- **On-screen controls** with profiles, macros, turbo, toggle and slide between buttons; physical controllers are
  mapped automatically; hotkeys and an in-game side menu.
- **AMD FSR 1 and Snapdragon GSR** upscaling with RCAS sharpening.
- **Built-in debug report:** every session keeps a light log, and one tap packs everything a bug report needs.
- **English, Português (Brasil) and Español**, picked from the device language on first run.

## Screenshots

<!-- Captured on a Retroid Pocket 5 (1920x1080, landscape) with the app in English. -->

**Themes**

<table>
  <tr>
    <td align="center"><img src="docs/images/screens/theme-solid.png" alt="Solid theme, library grid" width="400"><br><sub>Solid</sub></td>
    <td align="center"><img src="docs/images/screens/theme-liquid-glass.png" alt="Liquid Glass theme, library carousel" width="400"><br><sub>Liquid Glass</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/screens/theme-blur.png" alt="Blur theme, library list" width="400"><br><sub>Blur</sub></td>
    <td align="center"><img src="docs/images/screens/theme-material-you.png" alt="Material You theme, library grid" width="400"><br><sub>Material You</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="2"><img src="docs/images/screens/theme-synthwave.png" alt="Synthwave theme, library carousel" width="400"><br><sub>Synthwave</sub></td>
  </tr>
</table>

**Interface**

<table>
  <tr>
    <td align="center"><img src="docs/images/screens/welcome-setup.png" alt="Welcome setup" width="400"><br><sub>Welcome setup</sub></td>
    <td align="center"><img src="docs/images/screens/game-settings.png" alt="Per-game settings" width="400"><br><sub>Per-game settings</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/screens/drivers.png" alt="GPU drivers page" width="400"><br><sub>GPU drivers</sub></td>
    <td align="center"><img src="docs/images/screens/settings.png" alt="Settings home" width="400"><br><sub>Settings</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="2"><img src="docs/images/screens/ingame-menu.png" alt="In-game side menu" width="400"><br><sub>In-game menu</sub></td>
  </tr>
</table>

**Controls**

<table>
  <tr>
    <td align="center"><img src="docs/images/screens/controls-overlay.png" alt="On-screen controls in game" width="400"><br><sub>On-screen controls</sub></td>
    <td align="center"><img src="docs/images/screens/controls-edit.png" alt="On-screen controls edit mode" width="400"><br><sub>Edit mode</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="2"><img src="docs/images/screens/controls-mapping.png" alt="Physical controller mapping" width="400"><br><sub>Controller mapping</sub></td>
  </tr>
</table>

## Requirements

| | Minimum | Notes |
|---|---|---|
| Android | **11** (API 30) | |
| CPU | **64-bit ARM (arm64-v8a)** | No 32-bit or x86 builds. |
| GPU | **Vulkan 1.1** | Adreno 6xx/7xx is the main target. Mali works, with more issues (see [GPU drivers](#gpu-drivers)). |
| RAM | No fixed minimum | Tested down to 4 GB (Mi A3). Devices under 6 GB compile shaders on one thread. |
| Storage | Space for your games plus shader caches | |

Fractal is developed and tested on:

- **Retroid Pocket 5**: Snapdragon 865, Adreno 650, Android 13
- **Anbernic RG Cube**: Unisoc T820, Mali-G57 MC4, Android 13
- **Xiaomi Mi A3**: Snapdragon 665, Adreno 610 (low-end phone reference)

Wii U emulation is heavy. Expect full speed in lighter games on Snapdragon 865-class devices and lower frame rates in the
heaviest ones (Breath of the Wild, Xenoblade Chronicles X). Weaker GPUs, Mali in particular, will be slower.

## Installation

1. Download the APK from the [Releases](https://github.com/dfdx047/Fractal/releases) page.
2. Open it on your device and allow installing apps from that source when Android asks.
3. Open **Fractal**. The welcome setup walks you through language, theme, wallpaper, game folder, keys, graphic packs
   and GPU driver. Every step can be skipped and reopened later from **Options › Welcome setup**.

Fractal installs as its own app (`dev.dfd.fractal`) and does not touch other Cemu builds you may have installed.

## Getting started

### Your games

Fractal does **not** include or download games, keys or firmware. Use dumps of games you own, made from your own
console and discs. Do not ask for or share copyrighted files in this repository.

- Pick the folder with your games in the welcome setup or in **Options › Game folders**. Fractal also looks in the
  folders inside it.
- Supported formats: **.wua**, **.wux**, **.wud**, **.iso** and **.rpx** (and folders with `code/content/meta`).
- **Decrypted games** (.wua, or `code/content/meta` folders) work **without keys**.
- **Encrypted games** (.wud, .wux) need a `keys.txt` with the disc key of each game in the Fractal folder.
- Updates and DLC: use a .wua that contains them, or install them with **Options › Title manager**. The title manager can
  also convert installed content to .wua.
- Online features need `otp.bin` and `seeprom.bin` dumped from **your** console.

The **Options** menu is in the library's top bar (or **Start** on a controller). The Fractal folder is
`Android/data/dev.dfd.fractal/files`; **Options › Open Fractal folder** opens it in a file manager.

### Graphic packs

The community graphic packs add resolution and frame rate options and fix problems in some games. Download them in the
welcome setup or in **Options › Graphic packs**; they are strongly recommended. Some community fixes that were checked on
the test devices are turned on by default (for example the Need for Speed: Most Wanted U Bloom Fix) and can still be
turned off.

### Per-game settings

Press **Y** on a selected game (or pick **Edit game profile** in its menu) for its settings: Resolution & FPS, CPU,
graphics, GPU driver, graphic packs, saves and shader cache. Long-press a game (or press **Select**) for its menu:
export/import save, export/import shader cache, remove shader caches, create a home screen shortcut.

## GPU drivers

On Android the GPU driver matters as much as the emulator. Fractal lets you pick one globally or for each game.

**Adreno (Snapdragon)**

- **System driver (Qualcomm):** works out of the box. Some games hit bugs in its shader compiler; Fractal works around
  the ones found so far.
- **Turnip (Mesa):** an open-source driver that is often faster and more compatible. **Options › Drivers** lists builds
  by developer and installs them with one tap. MrPurple666's T-series for Adreno 6xx/7xx is listed first and is a good
  starting point.
- On Turnip, Fractal renders in system memory by default (a per-game option), which avoids GPU hangs seen with tiled
  rendering.

**Mali**

- The system driver is the default. Custom drivers (PanVK builds and malisx2) can be installed from the same page but
  are **experimental**.

**If a driver fails** to start a game, Fractal runs it on the system driver, and on the next start switches the setting
that chose that driver back to the system driver and tells you why. Changing drivers clears only that driver's pipeline
cache; your shader cache is kept.

If a game shows broken graphics or crashes, trying another driver is the first thing to do, and the driver you used is
the most useful detail in a bug report.

## Controls

- **On-screen controls** show by default when no controller is connected. In the in-game menu you can edit them:
  move, resize, snap to a grid, change opacity, style and shape per button, save layouts as profiles, make a button a
  toggle or turbo, add macros (button combos or timed sequences) and shortcut buttons that run emulator actions.
- **Physical controllers** (built-in handheld controls, Bluetooth and USB gamepads) are mapped to an empty emulated
  controller automatically when connected. Player 1 is a Wii U GamePad by default. Mappings can be changed in
  **Settings › Controls**, and the Wii U HOME button maps to the controller's Mode/Guide button.
- **Hotkeys:** pause, screenshot, swap TV/GamePad screen, show or hide the on-screen controls, FPS counter, aspect ratio,
  upscale filter and motion.
- **In-game menu:** GamePad screen, TV/GamePad swap, scaling, FPS counter, upscale filter and sharpness, VSync,
  on-screen controls, controller mapping, hotkeys, pause and screenshots (saved to `Pictures/Fractal`).

## What Fractal fixes

Fractal started from SSimco's Android port of Cemu and has since added about 160 commits of its own, plus a merge of
the official Cemu `main` branch. Some fixes are in the emulator core that the PC version shares, so they also
address problems that exist in official Cemu:

| Game | Problem | Fixed by |
|---|---|---|
| The Amazing Spider-Man | Never shows a picture (listed as "no display" on the Cemu compatibility wiki): the game copies its frames into the scan buffer with `GX2CopySurface` | Frames copied that way are presented. Playable on the Fractal PC build |
| Watch Dogs | Crashes at boot and in gameplay, frozen picture from the prologue on | Guest memory reserved with the alignment the game's allocator needs (official Cemu has the same problem), streamout results copied back to guest memory, indexed streamout draws in index order, no face culling for rect primitives, a boot race patched |
| Injustice: Gods Among Us | GPU hang, stretched fighters | Shader loop constants emulated, streamout offsets corrected |
| Need for Speed: Most Wanted U | Engine sound pops and crackles | `AXDecodeAdpcmData` implemented, full-precision biquad filter history |
| Every game | Shaders precompiled at boot were built with the wrong state, so they were compiled again in game | Pipeline state built from the cached registers (official Cemu has the same bug) |

And many fixes for Android GPUs and drivers: Tekken Tag Tournament 2, Assassin's Creed IV, Ninja Gaiden 3: Razor's
Edge, Xenoblade Chronicles X, Bayonetta 2, Pokkén Tournament, Breath of the Wild and more. The full list, with the
commits behind each item, is in the [roadmap](docs/ROADMAP.md).

## Reporting bugs

Bug reports with a debug report attached are the fastest way to get a game fixed.

1. Reproduce the problem (crash, freeze or graphics bug), then open Fractal again.
2. In the library, open **Options › Share debug report**. Fractal builds `debug_report.zip` with `log.txt`, the log of
   the previous session (useful after a crash), the last two game sessions (device, driver, settings, frame times and
   problems found), `settings.xml` and the profiles of those games.
3. Save or send the zip, then open a [new issue](https://github.com/dfdx047/Fractal/issues/new/choose) and attach it.

Include the device, SoC, Android version, GPU driver, the game with its **Title ID** and version, and the steps to
reproduce. `settings.xml` contains the paths of your game folders; remove anything you don't want to share.

**Please do not report Fractal bugs to the Cemu team.** If a game also fails the same way in official Cemu on PC, say so
in the issue.

## Roadmap and changelog

- [Roadmap](docs/ROADMAP.md): what is done, what is being worked on, what is planned
- [Changelog](CHANGELOG.md)

## Building from source

See [BUILD.md](BUILD.md#android). The Android project is in `src/android` (Gradle, NDK). Contributions are welcome; read
[CONTRIBUTING.md](CONTRIBUTING.md) first.

## Credits

Fractal stands on the work of:

- **[Cemu](https://github.com/cemu-project/Cemu)** by the Cemu team: the emulator core (PowerPC recompiler, Latte GPU
  emulation, Cafe OS, audio, input) and most of the code in this repository.
- **[Cemu Android port](https://github.com/SSimco/Cemu)** by SSimco: the Android build, JNI layer, Kotlin/Jetpack
  Compose app and ARM64 work that Fractal is forked from.
- **[Cemu Plus](https://github.com/danielcamilo1/cemu-plus)** by danielcamilo1: AMD FSR 1 and Snapdragon GSR upscaling
  with RCAS sharpening, the misaligned 64-bit atomics fix (Star Fox Zero), crash details in the log, and two texture
  and logging fixes.
- **GPU driver projects** listed in the app's drivers page, each credited with its name and link. Fractal does not
  bundle any driver.
- **[Cemu graphic packs](https://github.com/cemu-project/cemu_graphic_packs)** community.
- **Fonts:** Space Grotesk and Inter (SIL Open Font License 1.1).

Details are in [NOTICE.md](NOTICE.md).

## License

Fractal is licensed under the [Mozilla Public License 2.0](LICENSE.txt), like Cemu. Files in `dependencies/` and some
files in `src/` keep the licenses stated in their headers. See [NOTICE.md](NOTICE.md).

Fractal is not affiliated with or endorsed by Nintendo. Wii U is a trademark of Nintendo. Fractal is not an official
Cemu release.
