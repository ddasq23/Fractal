<p align="center">
  <b>English</b> · <a href="ROADMAP.pt-BR.md">Português (Brasil)</a> · <a href="ROADMAP.es.md">Español</a>
</p>

# Fractal roadmap

Status for **v0.1-beta** (November 2026). Every fix listed under *Done* links to the commit that made it, and the
numbers come from the measurements written in those commits.

## Where Fractal comes from

- **Base:** SSimco's Android port of Cemu (`android-port`, fork point `8759323e`, 2026-04-27).
- **Official Cemu merged:** the 220 commits of `cemu-project/Cemu` `main` up to `3b103161` (2026-11-04), in `7c02ce75`.
- **Fractal's own work:** about 160 commits since 2026-11-08: interface, drivers, input, renderer, recompiler, game
  fixes and debugging tools.

Test devices: **Retroid Pocket 5** (Snapdragon 865, Adreno 650), **Anbernic RG Cube** (Unisoc T820, Mali-G57 MC4),
**Xiaomi Mi A3** (Snapdragon 665, Adreno 610), plus a PC build of the same code used to compare with official Cemu.

---

## Done

### Game fixes in the shared emulator core

These bugs are in code that Fractal shares with the PC version of Cemu, so they are not specific to Android.

| Game | What was wrong | Fix | Commit |
|---|---|---|---|
| The Amazing Spider-Man | No picture at any time ("no display" on the Cemu compatibility wiki). The game copies the finished frame into the scan buffer with `GX2CopySurface` instead of `GX2CopyColorBufferToScanBuffer` | Copies into the TV/GamePad scan buffer ranges are presented. Playable on the Fractal PC build (intro, title, chapter 1) | [`79f2f691`](https://github.com/dfdx047/Fractal/commit/79f2f691) |
| Watch Dogs | Random crashes at boot: the game's allocator needs a 128 KiB aligned arena and got 16 KiB alignment. Official Cemu has the same problem (64 KiB on Windows) | Guest memory reserved at a 4 GB aligned host address | [`cb5321e6`](https://github.com/dfdx047/Fractal/commit/cb5321e6) |
| Watch Dogs | Crash at boot: a thread polls one manager and uses another that the main thread creates microseconds later | Game patch: the thread waits for the right manager | [`d316661a`](https://github.com/dfdx047/Fractal/commit/d316661a) |
| Watch Dogs | Gameplay crash: the game runs GPU visibility jobs with transform feedback and reads the results on the CPU; Cemu only kept them on the GPU | Streamout results copied back to guest memory before the CPU can observe them; streamout offset sources follow the packet | [`8b69778e`](https://github.com/dfdx047/Fractal/commit/8b69778e) |
| Watch Dogs | Gameplay crash: indexed streamout draws wrote outputs at the index value instead of in index buffer order | Indexed streamout draws drawn from vertices gathered in index order | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Watch Dogs | Picture frozen from the prologue on (checkerboard, stacked subtitles, frozen "Shoot Maurice" tutorial): the final upscale and FXAA rects were culled | No face culling for rect primitives | [`6d80adae`](https://github.com/dfdx047/Fractal/commit/6d80adae) |
| Watch Dogs | 38-68 s freeze entering gameplay; streamout shaders compiled again for every bound pixel shader | Vertex shaders with discarded rasterization don't depend on the pixel shader; large shaders cached by source. Now 9 s | [`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b), [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Watch Dogs | Freeze after long sessions (around 1000 s on the RP5, 1250 s on PC): the game checks that the GPU is parked on a wait for about 10 µs, then fills the next slot of its GPU job list. The command processor had already passed the wait and ran the slot empty, so a visibility job never finished. Official Cemu has the same race | The command processor checks the wait again right before its first effect; release/acquire fences around display lists. 3000 s without a freeze on PC and on the RP5 | [`210b613e`](https://github.com/dfdx047/Fractal/commit/210b613e) |
| Injustice: Gods Among Us | GPU hang: shader loops without a break became unbounded | Loop constants emulated (`IT_SET_LOOP_CONST`, loop variables in uniforms) | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Injustice: Gods Among Us | Stretched fighters (GPU skinning through streamout) | Streamout offsets taken from the packet, counted in DWORDs, without the draw's base vertex | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Need for Speed: Most Wanted U | Engine sound pops and crackles while accelerating | `AXDecodeAdpcmData` implemented (it was missing), full-precision biquad history, ADPCM loop context, low-pass filter overflow | [`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b) |
| Every game | Pipelines precompiled at boot were built with the GPU state of that moment, so their driver cache never matched and they were compiled again in game. Official Cemu has the same bug | Pipeline state built from the registers passed to the compiler | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Every game | Buffer cache: a uniform buffer with size register `0xFFFFFFFF` became a zero-sized node that corrupted the range tree | Size checked | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |

Also in shared code: the recompiler now handles `psq_lx`/`psq_stx` and their update forms, which official Cemu sends to
the interpreter ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b); in The Amazing Spider-Man 2 these
are the audio sample converters that kept a core near 100 % during loading).

**Assassin's Creed IV: Black Flag:** in our tests the official Cemu 2.6 release (AppImage) stopped at "loading..." with
no CPU use, while the Fractal PC build and the Retroid Pocket 5 play the opening cutscene and the ship deck. We have not
identified which change makes the difference.

### Game fixes for Android, ARM64 and mobile GPU drivers

| Game | What was wrong | Fix | Commit |
|---|---|---|---|
| Ninja Gaiden 3: Razor's Edge | Black screen after a few minutes on Android: a race in the game's own event flag code left two threads waiting forever | The game's event flag "set" no longer resets an event the other thread just signalled | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Ninja Gaiden 3: Razor's Edge | Crash: functions of more than 1 MB of ARM64 code put branches out of range | Cold code emitted between segments; an encoding error leaves the function interpreted instead of aborting | [`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf) |
| Ninja Gaiden 3: Razor's Edge | Turnip: the whole picture blurred by the depth of field reading a stale depth buffer | Barrier at the end of each render pass on Turnip | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Ninja Gaiden 3: Razor's Edge | Crash in its own allocator with zlib-ng as the host zlib | zlib state allocated from the default heap | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Tekken Tag Tournament 2 | Qualcomm system driver crashed compiling a 4x4 gather shader on the first fight | Indexed GPR writes emitted as a switch instead of a dynamically indexed GPR array | [`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d) |
| Assassin's Creed IV: Black Flag | Qualcomm system driver: green/magenta/blue blocks over particles, loading screens and cutscenes. The bloom samples the texture it renders to, which is undefined without `VK_EXT_attachment_feedback_loop_layout` | Such draws sample a copy of the texture | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Watch Dogs | White stadium and bloom on Adreno: Adreno returns NaN from min/max/clamp when one operand is NaN | NaN-safe min/max/clamp on by default on Qualcomm GPUs | [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223) |
| Xenoblade Chronicles X | Turnip: GPU hang about 36 s into a new game, every run | `R8G8B8A8_UNORM` images kept mutable (no UBWC) on Turnip | [`2a72a16b`](https://github.com/dfdx047/Fractal/commit/2a72a16b) |
| Xenoblade Chronicles X | Adreno 650: command buffer submission failed after ~160 s (copies between compressed images of different formats) | Those copies go through a staging buffer | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Bayonetta 2 | Crash 3 s after boot (RG Cube), hang on the boot screen (RP5): the ARM64 backend never loaded the core index (`mfspr UPIR`) | SPRs above 998 loaded by name | [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b) |
| Pokkén Tournament | Crash every time it left a menu: thread-local storage read on one host thread and used on another (dynamic TLS on Android) | Scheduler and fiber TLS read on the thread that runs the code. Reaches battles at 60 fps on the RP5 | [`8ea62197`](https://github.com/dfdx047/Fractal/commit/8ea62197) |
| Need for Speed: Most Wanted U | Crash entering the city: descriptor sets exhausted (over 160K texture/sampler combinations) | Descriptor pools grow on demand, sets unused for 600 frames are freed | [`1feba3d6`](https://github.com/dfdx047/Fractal/commit/1feba3d6) |
| Need for Speed: Most Wanted U | White, blown-out foliage and lights | The community "Bloom Fix" graphic pack is on by default (root cause still open) | [`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86) |
| The Legend of Zelda: Breath of the Wild | Turnip: GPU hang at boot with tiled rendering | Turnip renders in system memory by default (per-game option) | [`525a6907`](https://github.com/dfdx047/Fractal/commit/525a6907), [`7f8d5a0f`](https://github.com/dfdx047/Fractal/commit/7f8d5a0f) |
| The Legend of Zelda: Breath of the Wild | Render thread waited 15.6 ms per frame for a full sync at every `GX2DrawDone` | Per-game option, off in BotW's default profiles: RP5 9.4 → 11.1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| Star Fox Zero | SIGBUS on misaligned 64-bit atomics on ARM64 | Lock for misaligned atomics (from Cemu Plus) | [`c35755ec`](https://github.com/dfdx047/Fractal/commit/c35755ec) |
| Mario Kart 8 | Crash (sound mixer state) | `snd_user` reset brought in from official Cemu before the full merge | [`c62e6977`](https://github.com/dfdx047/Fractal/commit/c62e6977) |
| Games with an error dialog | Stuck on handhelds: the error dialog buttons could not be focused without a mouse | A and B close the dialog | [`5879e1f5`](https://github.com/dfdx047/Fractal/commit/5879e1f5) |
| All games | Settings and the game list became zeros after a reboot shortly after saving | Files are synced to storage before replacing the old ones | [`9db92543`](https://github.com/dfdx047/Fractal/commit/9db92543) |

### Renderer (Vulkan) for tile-based mobile GPUs

Adreno and Mali store and reload every attachment when a render pass ends, so most of this work avoids breaking passes.

- Buffer uploads and occlusion queries no longer end the render pass; the buffer cache lives in host-visible memory on
  unified memory GPUs ([`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac)).
- Vertex work keeps overlapping across render target barriers; per-attachment self-dependency tracking
  ([`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f), [`d9606be8`](https://github.com/dfdx047/Fractal/commit/d9606be8)).
- Busy buffer cache nodes move to new memory instead of ending the pass
  ([`ec46fba8`](https://github.com/dfdx047/Fractal/commit/ec46fba8), [`a74fbb56`](https://github.com/dfdx047/Fractal/commit/a74fbb56)).
- Texture uploads go into a prologue command buffer instead of ending the pass
  ([`2eeceaea`](https://github.com/dfdx047/Fractal/commit/2eeceaea)).
- Framebuffer compression (UBWC on Adreno, AFBC on Mali) kept on color images
  ([`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef)).
- Turnip: `vkGetFenceStatus` blocks on KGSL, so finished command buffers are found with a completion marker
  ([`65047547`](https://github.com/dfdx047/Fractal/commit/65047547)).
- Depth clears through a render pass instead of `vkCmdClearDepthStencilImage`, which costs ~3.5 ms per 1080p clear on
  the Adreno 610 driver ([`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76)).
- One driver pipeline cache per driver, kept when switching drivers
  ([`2e43b0b7`](https://github.com/dfdx047/Fractal/commit/2e43b0b7)).
- Pipelines compiled on 1-2 threads on Android instead of up to 7, which cut cold-cache stutter
  ([`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72)).
- Swapchain picks a supported composite alpha (Mesa PanVK) ([`c1f4cd8e`](https://github.com/dfdx047/Fractal/commit/c1f4cd8e)).

### PowerPC → ARM64 recompiler

- Cycle counter kept in a register, functions packed in one code arena, shorter jumps and immediates, flag branches,
  zero-offset memory access without an add ([`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251)).
- `mfspr UPIR` ([`d61aee38`](https://github.com/dfdx047/Fractal/commit/d61aee38), [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b)),
  `ps_nabs`, `dcbt` ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)), `psq_lx`/`psq_stx`/`psq_lux`/`psq_stux`,
  `eieio`, `dcbi` ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b)) no longer fall back to the interpreter.
- Huge functions stay within ARM64 branch range ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)).
- Flush-to-zero actually enabled on Android ([`b385304f`](https://github.com/dfdx047/Fractal/commit/b385304f)).

### Shader decompiler

- Loop constants ([`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5)).
- Indexed GPR writes as a switch ([`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d)).
- R600 NaN rules for min/max/clamp on Adreno ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Streamout vertex shaders independent from the bound pixel shader, SPIR-V cache by source for large shaders
  ([`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b)).

### System and audio

- AX audio fixes ([`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b)).
- The main core and the GPU thread sleep instead of spinning when idle; the main core sleeps while the game is paused
  ([`ff38f5b4`](https://github.com/dfdx047/Fractal/commit/ff38f5b4), [`bf0944a4`](https://github.com/dfdx047/Fractal/commit/bf0944a4)).
- `GX2Init` argument parsing ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).

### Interface

- Five themes, design tokens, bundled fonts, glass surfaces, static and animated wallpapers
  ([`73fd0c96`](https://github.com/dfdx047/Fractal/commit/73fd0c96)); intro animation per theme
  ([`4489bc72`](https://github.com/dfdx047/Fractal/commit/4489bc72)); Sierpinski app icon
  ([`a59f82f8`](https://github.com/dfdx047/Fractal/commit/a59f82f8)).
- Library with grid, carousel and list, welcome setup, appearance screen
  ([`80712467`](https://github.com/dfdx047/Fractal/commit/80712467)); play time, last played, sorting, save and shader
  cache export/import, per-game screen ([`ecf40b3b`](https://github.com/dfdx047/Fractal/commit/ecf40b3b)).
- Resolution & FPS options detected from any game's graphic packs
  ([`d95f0191`](https://github.com/dfdx047/Fractal/commit/d95f0191), [`9a359b76`](https://github.com/dfdx047/Fractal/commit/9a359b76)).
- Settings grouped in sections, legacy screens in the new style
  ([`b7428b86`](https://github.com/dfdx047/Fractal/commit/b7428b86), [`383af9d2`](https://github.com/dfdx047/Fractal/commit/383af9d2)).
- Gamepad navigation and a compact button legend on every screen (`4008a538` to `572b8a9e`), focus restored when going
  back (`eb7e614e` to `7dc5303c`), portrait phone layout ([`94e60896`](https://github.com/dfdx047/Fractal/commit/94e60896)).
- Themed in-game side menu ([`d6bba2e5`](https://github.com/dfdx047/Fractal/commit/d6bba2e5)).
- English, Português (Brasil) and Español for every app string
  ([`c9d8a940`](https://github.com/dfdx047/Fractal/commit/c9d8a940), [`a74e2a35`](https://github.com/dfdx047/Fractal/commit/a74e2a35)).

### GPU drivers

- Driver catalog from GitHub releases, one-tap install, per-driver cache clearing
  ([`3927b698`](https://github.com/dfdx047/Fractal/commit/3927b698)); experimental Mali drivers, more sources, automatic
  fallback to the system driver ([`7ce49626`](https://github.com/dfdx047/Fractal/commit/7ce49626),
  [`9e8cf0c3`](https://github.com/dfdx047/Fractal/commit/9e8cf0c3)); page by developer with MrPurple666's Turnip first,
  drivers and graphic packs in the welcome setup ([`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86)).

### Input

- On-screen controls: resolution-independent layouts, profiles, macros, turbo, toggle, slide, edit mode, theme colors,
  styles and shortcut buttons ([`c813042e`](https://github.com/dfdx047/Fractal/commit/c813042e),
  [`9c15535f`](https://github.com/dfdx047/Fractal/commit/9c15535f), [`5af1e981`](https://github.com/dfdx047/Fractal/commit/5af1e981)).
- Physical controllers mapped automatically ([`841f97a3`](https://github.com/dfdx047/Fractal/commit/841f97a3),
  [`e1698c63`](https://github.com/dfdx047/Fractal/commit/e1698c63)); HOME on the Mode/Guide button
  ([`b343cab3`](https://github.com/dfdx047/Fractal/commit/b343cab3)); hotkeys ([`571c9d87`](https://github.com/dfdx047/Fractal/commit/571c9d87)).
- Quick taps on on-screen buttons no longer lost ([`8929b786`](https://github.com/dfdx047/Fractal/commit/8929b786)).

### Upscaling

- AMD FSR 1 and Snapdragon GSR, RCAS sharpening (from Cemu Plus)
  ([`2c5dba03`](https://github.com/dfdx047/Fractal/commit/2c5dba03), [`8328081a`](https://github.com/dfdx047/Fractal/commit/8328081a)).

### Debugging

- Debug report: a light performance log for every session, `events.txt` with each problem found, a freeze watchdog,
  crash notes naming what the thread was doing, the previous log kept, and **Share debug report** in the app
  ([`be081807`](https://github.com/dfdx047/Fractal/commit/be081807)).
- Crash and freeze logs with guest code, host registers and thread stacks
  ([`c7923402`](https://github.com/dfdx047/Fractal/commit/c7923402), [`fcb9863e`](https://github.com/dfdx047/Fractal/commit/fcb9863e),
  [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Performance log, scripted benchmark scenes and profiling tools (`tools/perf`).

### Measured gains

From the A/B measurements in the commit messages (same scene, same device, warm shader cache). Conditions differ
between commits (driver, CPU profile), so compare rows only within themselves.

| Change | Device | Before → after | Commit |
|---|---|---|---|
| No render pass end for buffer uploads / occlusion queries | RG Cube, Mario Kart 8 | 16.0 → 23.0 fps | [`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac) |
| Vertex work overlaps render target barriers | RG Cube, Mario Kart 8 | 24.3 → 30.2 fps | [`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f) |
| Framebuffer compression kept | RP5, Super Mario 3D World | 57.0 → 59.8 fps, 1 % low 30 → 44 | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Recompiler: cycle counter in a register, code arena | RP5, Mario Kart 8 | 47.8 → 50.0-50.4 fps | [`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251) |
| Depth clear through a render pass | Mi A3, Shovel Knight | 34 → 52 fps | [`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76) |
| Per-game `GX2DrawDone` sync | RP5, Breath of the Wild | 9.4 → 11.1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| 1-2 pipeline compile threads | Mi A3, SM3DW, cold cache | 17.0 s → 7.5 s in frames over 100 ms | [`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72) |
| Precompiled pipelines match the driver cache | Watch Dogs | 38-68 s → 9 s freeze entering gameplay | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |

---

## In progress

- **Watch Dogs on Turnip:** a GPU page fault in a streamout write can hang the GPU after about 25 minutes. The system
  driver is not affected.
- **Watch Dogs overexposed on Android:** from the Maurice room on, the auto exposure can lock too bright in about half
  of the sessions, on Turnip and on the system driver; the PC build is not affected. Ruled out so far: NaN/Inf in pixel
  shaders, skipped draws during async pipeline compilation and texture readback.
- **Watch Dogs on Mali** (RG Cube): the render thread gets stuck compiling the very large streamout shaders; blocky menu
  and black intro.
- **Assassin's Creed IV:** comparing cutscenes with the PC build; the long swords hang straight down the same way on PC
  and on the RP5, not yet confirmed as a bug.
- **Ninja Gaiden 3 on Turnip:** dark floating heads and a blown-out sky remain after the depth of field fix.
- **Need for Speed: Most Wanted U:** finding the root cause of the white foliage (the community pack hides it).
- **Xenoblade Chronicles X on the Qualcomm system driver:** characters drawn as white silhouettes (driver bug); use
  Turnip for now.
- **Mali in general:** the RG Cube, kept in its economy CPU mode, is the reference for weaker Mali devices.

## Planned

- Wider compatibility testing on the main Wii U games and a public compatibility list.
- An in-game "mark a problem" button that saves a screenshot and a marker in the log for bug reports.
- Streamout on Mali drivers without vertex pipeline stores (for example through a compute pass).
- GPU features found missing while testing: compute draw calls, alpha-to-mask (`GX2SetAlphaToMaskReg`),
  `GX2ExpandDepthBuffer`, and `SQ_VTX_SEMANTIC_CLEAR`, which is never applied.
- More CPU-side performance: Breath of the Wild and Mario Kart 8 on the RP5 are limited by the main PowerPC core and the
  render thread, not by the GPU.
- Keep following official Cemu `main`.
