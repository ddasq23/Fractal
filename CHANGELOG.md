# Changelog

All notable changes to Fractal. Commit references point to `github.com/dfdx047/Fractal/commit/<hash>`; the
[roadmap](docs/ROADMAP.md) has the full list with the problem behind each fix.

Translations: [Português (Brasil)](docs/release-notes/v0.1-beta.md#português-brasil) ·
[Español](docs/release-notes/v0.1-beta.md#español) (release notes).

## v0.1-beta (2026-11-15) - first public beta

Based on SSimco's Android port of Cemu (`8759323e`) with official Cemu `main` merged up to `3b103161` (2026-11-04).

### Added

- **New interface:** five themes (Solid, Liquid Glass, Blur, Material You, Synthwave), static and animated wallpapers or
  a custom image, intro animation per theme, Sierpinski app icon (`73fd0c96`, `4489bc72`, `a59f82f8`).
- **Library:** grid, carousel and list views, play time, last played, sorting, favorites; welcome setup; appearance
  screen (`80712467`, `ecf40b3b`).
- **Per-game settings:** Resolution & FPS options detected from the community graphic packs, driver, graphic packs, saves
  and shader cache sections; save and shader cache export/import (`ecf40b3b`, `d95f0191`, `9a359b76`).
- **GPU drivers page:** catalog of Turnip builds for Adreno and experimental PanVK/malisx2 drivers for Mali from their
  GitHub releases, one-tap install, global or per-game choice, automatic fallback to the system driver
  (`3927b698`, `7ce49626`, `9e8cf0c3`, `0386ec86`).
- **On-screen controls:** profiles, macros, turbo, toggle, slide between buttons, edit mode, theme colors, per-button
  style, shortcut buttons (`c813042e`, `9c15535f`, `5af1e981`).
- **Controllers:** automatic mapping of connected gamepads, HOME on Mode/Guide, hotkeys for pause, screenshot, TV/PAD
  swap, overlay, FPS, aspect ratio, upscale filter and motion (`841f97a3`, `e1698c63`, `b343cab3`, `571c9d87`).
- **In-game side menu** with display, graphics, controls, pause and screenshots (`d6bba2e5`).
- **Gamepad navigation** and a compact button legend on every screen (`4008a538` to `572b8a9e`).
- **Languages:** English, Português (Brasil) and Español, picked from the device on first run (`c9d8a940`, `a74e2a35`).
- **Upscaling:** AMD FSR 1, Snapdragon GSR and RCAS sharpening, from Cemu Plus (`2c5dba03`, `8328081a`).
- **Debug report:** light performance log for every session, `events.txt`, freeze watchdog, crash notes, previous log
  kept, **Share debug report** in the library options (`be081807`).
- Performance log, benchmark scenes and profiling tools in `tools/perf`.

### Changed

- Vulkan renderer tuned for tile-based mobile GPUs: fewer render pass breaks for buffer uploads, occlusion queries,
  texture uploads and barriers; framebuffer compression kept; one pipeline cache per driver; 1-2 pipeline compile
  threads on Android (`2c1895ac`, `7b36890f`, `d9606be8`, `ec46fba8`, `2eeceaea`, `017be0ef`, `2e43b0b7`, `9893cf72`).
- Turnip: completion marker instead of blocking fence checks; system memory rendering by default
  (`65047547`, `7f8d5a0f`).
- ARM64 recompiler: cycle counter in a register, code arena, shorter code; more instructions recompiled
  (`23b10251`, `a26051bf`, `9107127b`).
- The main core and the GPU thread sleep when idle and while paused (`ff38f5b4`, `bf0944a4`).
- Settings home grouped in sections; older screens restyled (`b7428b86`, `383af9d2`).

### Fixed

- **The Amazing Spider-Man:** no picture; frames copied into the scan buffer with `GX2CopySurface` are now shown (`79f2f691`).
- **Watch Dogs:** crashes at boot and in gameplay, frozen picture from the prologue, long freeze entering gameplay, white
  stadium on Adreno, freeze after long sessions (`cb5321e6`, `d316661a`, `8b69778e`, `2eef8ff5`, `6d80adae`, `9b6ee09b`,
  `69ac8320`, `f8dbb223`, `210b613e`).
- **Injustice: Gods Among Us:** GPU hang and stretched fighters (`2eef8ff5`).
- **Need for Speed: Most Wanted U:** engine sound pops, crash entering the city; Bloom Fix pack on by default
  (`44cdd26b`, `1feba3d6`, `0386ec86`).
- **Ninja Gaiden 3: Razor's Edge:** freeze after a few minutes, recompiler crash, blurred picture on Turnip, allocator
  crash (`8eaae97a`, `a26051bf`, `64c4b75c`).
- **Tekken Tag Tournament 2:** Qualcomm driver crash on the first fight (`1293cd7d`).
- **Assassin's Creed IV:** colored blocks on the Qualcomm driver (`64c4b75c`).
- **Xenoblade Chronicles X:** Turnip GPU hang, Adreno 650 submission failure (`2a72a16b`, `017be0ef`).
- **Bayonetta 2:** crash/hang at boot (`613dc67b`).
- **Pokkén Tournament:** crash leaving menus (`8ea62197`).
- **Breath of the Wild:** Turnip hang at boot; per-game `GX2DrawDone` sync off (`525a6907`, `951eb1e7`).
- **Star Fox Zero:** SIGBUS on misaligned atomics, from Cemu Plus (`c35755ec`).
- **Mario Kart 8:** sound mixer crash, from official Cemu (`c62e6977`).
- **All games:** precompiled pipelines built with the wrong state (`69ac8320`); settings and game list lost after a
  reboot (`9db92543`); error dialogs that could not be closed with a gamepad (`5879e1f5`); quick on-screen taps lost
  (`8929b786`); flush-to-zero not enabled on Android (`b385304f`).

### Known issues

See [In progress](docs/ROADMAP.md#in-progress) in the roadmap.
