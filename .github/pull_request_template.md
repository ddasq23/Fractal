## What this changes

<!-- What the change does and why. Link the issue it fixes, e.g. "Fixes #123". -->

## How it was tested

<!-- Devices, SoC/GPU, GPU driver and games. For renderer or recompiler changes, say which scenes you ran and
     compare before/after (FPS, screenshots). For UI changes, add screenshots, ideally in more than one theme. -->

| Device | GPU driver | Game / screen | Result |
|---|---|---|---|
| | | | |

## Checklist

- [ ] I read [CONTRIBUTING.md](https://github.com/dfdx047/Fractal/blob/main/CONTRIBUTING.md) and followed [CODING_STYLE.md](https://github.com/dfdx047/Fractal/blob/main/CODING_STYLE.md).
- [ ] New or changed behavior that could cost performance or break a game has a way to turn it off, or was checked on more than one game.
- [ ] New app strings go through `tr()` and are added to `fractal.po` in `src/android/app/src/main/assets/translations/pt` and `.../es` (or I said so below and someone else can translate them).
- [ ] No games, keys, firmware, proprietary drivers or SDK material are included.
- [ ] Changed files keep their original license header; new files carry an MPL-2.0 header.
