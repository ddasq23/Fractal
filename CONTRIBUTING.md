# Contributing to Fractal

Thanks for helping! Português e español são bem-vindos nas issues / Portugués y español son bienvenidos en los issues.

## Testing and reporting

The most useful contribution right now is testing games on real devices:

- **Bug reports:** use the *Bug report* form and attach the debug report (**Options › Share debug report** in the app,
  right after the problem). See [Reporting bugs](README.md#reporting-bugs).
- **Compatibility reports:** use the *Game compatibility report* form, also for games that work well.
- Always include the device, the GPU driver and the game's Title ID and version. Trying a second driver before
  reporting saves a lot of time.
- Never attach or link games, keys, firmware or other copyrighted files.

## Translations

The app's own strings are in `src/android/app/src/main/assets/translations/<lang>/fractal.po` (English source,
Português (Brasil) and Español today). Strings with `{0}`-style arguments are MessageFormat strings: don't use
apostrophes in them. The emulator core strings (`cemu.po`) come from official Cemu and are generated at build time.

## Code

Pull requests are welcome. Before opening one:

- Read [BUILD.md](BUILD.md) and [CODING_STYLE.md](CODING_STYLE.md). The Android app is in `src/android`; the emulator
  core is shared with the PC build, which is the quickest way to compare a game against official Cemu.
- Keep a pull request to one subject, and explain how you tested it on a device (device, GPU driver, games).
- Renderer, recompiler and timing changes can help one game and break another. Measure before and after on the same
  scene (`tools/perf`, see [docs/BENCHMARK.md](docs/BENCHMARK.md)), check more than one game, and keep a way to turn
  risky changes off (a `perf/tuning.txt` tunable, like the existing ones) so they can be compared on devices.
- New app strings go through `tr()` and need pt/es entries in `fractal.po` (or a note in the PR so someone can
  translate them).
- Fixes in the shared emulator core may also matter for official Cemu. If you want them upstream, open them at
  [cemu-project/Cemu](https://github.com/cemu-project/Cemu) following
  [their contribution guidelines](https://github.com/cemu-project/Cemu/blob/main/CONTRIBUTING.md).

Do not submit code derived from leaked or proprietary SDKs, or any material you don't have the right to share.

## License

Fractal is licensed under the [Mozilla Public License 2.0](LICENSE.txt). By contributing you agree that your
contribution is licensed under it. Keep the original license header in files you change; new files may carry an MPL-2.0
header with your name. See [NOTICE.md](NOTICE.md).
