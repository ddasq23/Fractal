# Upstream pendente

Levantamento de 2026-11-08 de tudo que as bases do Fractal publicaram e ainda
não estava no nosso `main`.

**Atualização (2026-11-08):** o Cemu oficial até `3b103161` entrou no merge `7c02ce75`. As 5 cenas
foram medidas de novo depois dele, sem regressão (ver [FASE3-BASE.md](FASE3-BASE.md)). A seção do
Cemu oficial abaixo fica como registro dos conflitos. Continua pendente o que vem do Cemu Plus.

## Resumo

| Fonte | Remote | Situação |
|---|---|---|
| Cemu oficial (`cemu-project/main`) | `cemu` | **Trazido no merge `7c02ce75`.** Eram 220 commits (merge-base `02383542`, 2026-04-18; tip `3b103161`, 2026-11-04). 4 já trazidos (1 em parte). |
| Port Android do SSimco (`android-port`) | `upstream` | **0 pendentes.** Parado em `8759323e` (2026-04-27), que é a nossa base. |
| Cemu Plus (`danielcamilo1/cemu-plus`) | `plus` | 68 commits depois da base do SSimco no `android-port`, quase todos de interface própria. 4 trazidos na Fase 0. **2 correções nativas novas** em `fix/texture-unmapped-memory` (2026-11-08). |

## Cemu oficial: merge de teste

Feito numa worktree descartável (`git merge --no-commit cemu/main`) e
desfeito em seguida. **22 arquivos em conflito, cerca de 47 trechos.** O
resto dos 220 commits entra sem conflito.

| Arquivo | Trechos | De onde vem o conflito |
|---|---|---|
| `src/Cafe/OS/libs/swkbd/swkbd.cpp` | 8 | `4b72d7cf` (refactor do swkbd), `bb28394c` e `9bfdf816` (estado HLE por módulo) contra o teclado Android do SSimco |
| `.github/workflows/build.yml` | 5 | CI do Fractal (Android) contra CI desktop; ficar com o nosso e reaproveitar o que servir |
| `src/input/emulated/VPADController.cpp`, `ProController.cpp`, `ClassicController.cpp` | 3 cada | `8e3e961b` (SDL opcional), `7945f102`, `ad73c1e0` (corrida no mapeamento de botões) contra a entrada Android |
| `src/input/CMakeLists.txt`, `EmulatedController.cpp`, `ControllerFactory.cpp`, `api/SDL/SDLControllerProvider.h` | 1 a 3 | SDL2 → SDL3 (`f3fecba3`) e SDL opcional |
| `vcpkg.json`, `dependencies/vcpkg` (submódulo), `CMakeLists.txt`, `src/CMakeLists.txt`, `src/util/CMakeLists.txt` | 1 a 2 | SDL3, fmt 12.1 (`b517fbc1`), wxWidgets 3.3.3, opções `ENABLE_OPENGL`/`ENABLE_VULKAN` (`0fc74035`) |
| `src/Cafe/OS/libs/padscore/padscore.cpp` | 2 | `bb28394c` |
| `src/main.cpp` | 1 | auto-updater, cleanup, SDL3 |
| `src/Cafe/GameProfile/GameProfile.h` | 1 | `0fc74035` contra o `DriverSetting` do Android |
| `src/Cafe/HW/Latte/Renderer/RendererOuputShader.cpp` | 1 | `0fc74035` contra FSR/GSR/RCAS |
| `src/Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.cpp` | 1 | mudanças Vulkan contra o port Android / FSR |
| `src/Cemu/Logging/CemuLogging.h` | 1 | `6f6c1299` (checagem de formato em tempo de compilação), `4b72d7cf` |
| `src/Cafe/HW/Espresso/Debugger/Debugger.h` | 1 | cleanup |
| `README.md` | 2 | ficar com o nosso |

**Ponto que mais pesa:** o vcpkg novo (submódulo atualizado, fmt 12.1). O
build Android usa o vcpkg do repositório (`-DENABLE_VCPKG=ON`), então o merge
recompila todas as dependências. O SDL está desligado no Android
(`-DENABLE_SDL=OFF`), então a troca SDL2 → SDL3 só gera conflitos de código,
sem efeito em tempo de execução. Os conflitos de CPU e GPU são poucos: o
recompilador (`Espresso/Recompiler`) não tem nenhum; em `Espresso` só o
`Debugger.h`.

### Ordem sugerida para o merge
1. Merge numa branch `merge/cemu-<data>`, nunca direto no `main`.
2. Resolver build (CMake, vcpkg, SDL3, fmt) e compilar só o núcleo.
3. Resolver entrada e HLE (`swkbd`, `padscore`, `src/input`), testar teclado e controle no Odin.
4. Resolver render (`VulkanRenderer.cpp`, `RendererOuputShader.cpp`) e conferir FSR/GSR/RCAS e drivers custom.
5. Smoke test dos 5 jogos e comparar o fallback para o interpretador com a tabela do `ROADMAP.md`.

### Já trazidos
- `c2d428e5`, `263b3510`, `d28c7654`: correções do `snd_user` (crash do Mario Kart 8).
- `9bfdf816`: só a parte do `snd_user`. O restante (nsysnet, vpad, nn_*, rpl, swkbd, ax) vem no merge.

Os commits mais relevantes para ARM64, desempenho de GPU e compatibilidade
estão comentados no `ROADMAP.md` e marcados como "relevante" na lista abaixo.

## Cemu Plus

Trazidos na Fase 0: `c44f90c1` (atomics de 64 bits desalinhados), `40b46380`
(crash handler, parte nativa), `d0301dbe` (FSR 1 + GSR) e `5a189b9c` (RCAS e
variantes do GSR, parte de upscale).

**Candidatos novos, nativos, pequenos** (branch `fix/texture-unmapped-memory`):
- `86476991` (2026-11-08): texturas com dados fora da memória mapeada são limpas em vez de travar o emulador.
- `1490fda6` (2026-11-08): o log do Cemu também vai para o logcat, e o log de textura sem memória diz o ponteiro e o mip.

**Fora de escopo** (interface e recursos próprios do Plus, já cobertos ou
substituídos pela interface do Fractal, ou ideias para depois): tela dupla e
troca de telas (`3e03126e`, `87b94ab3`, `6fe1167f`, `dbdaf5ca`), recorte e
painel do GamePad (`b3e81cb9`, `3f66e099`, `133a9d47`, `4f592bea`, `67f5565b`,
`f8fd1566`, `b9ed9b1c`), tema e assistente estilo Wii U (`198eda91`,
`33b80b0c`, `d1c5bbca`, `d54bb5b7`, `0a0c6321`, `5d945118`, `1d2ec66b`),
entrada por controle e perfis por jogo (`f8db882d`, `5591e2c1`, `1b77cf83`,
`6b55711a`), Lossless Scaling frame gen (`916ed53e`, anotado em
`docs/IDEIAS-FUTURAS.md`), versão (`6d17330e`).

Vale olhar à parte o `95a1791c` ("graphic packs carregam uma vez, sem travar
ao sair do jogo"): mistura interface e núcleo; avaliar só a parte nativa.

## Lista completa do Cemu oficial (220)

Classificação pelo caminho dos arquivos (um commit entra na primeira área que
ele toca, na ordem das seções). "relevante" = comentado no `ROADMAP.md`.

### CPU (Espresso): 25

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `e2a69bec` | 2026-04-29 | debugger: Rework PPC debugger |  |
| `40516839` | 2026-04-30 | PPCRec: Cleanup and smaller fixes |  |
| `5c56bfe4` | 2026-06-04 | RPL: Use normalized module names + small code cleanup |  |
| `3a1d2573` | 2026-08-16 | AArch64: Restore code size after processing jumps (#2027) | relevante |
| `10ba5585` | 2026-09-09 | PPCRec: Fix sthbrx changing the address register | relevante |
| `9a5b3301` | 2026-09-09 | PPCRec: Fix mcrfs and fcmpo using the wrong instruction | relevante |
| `8cf39971` | 2026-09-09 | PPCRec: Fix subfic carry when subtracting from -1 | relevante |
| `a81a28cc` | 2026-09-10 | RPL: Refactor RPL loader + robustness improvements |  |
| `c231df47` | 2026-10-04 | PPCRec: Separate stfs from paired-single stores |  |
| `c174b2dd` | 2026-10-04 | PPCRec: Allow cold code after the main code | relevante |
| `908f10cc` | 2026-10-04 | PPCRec: Add x64 helpers for stfs conversion |  |
| `22a2a3ee` | 2026-10-04 | PPCRec: Fix stfs conversion on x64 |  |
| `278500f5` | 2026-10-04 | PPCRec: Fix stfs conversion on ARM64 | relevante |
| `fe6720c5` | 2026-10-04 | PPCRec: Fix x64 memory accesses using R12 |  |
| `9cbb00a2` | 2026-10-04 | PPCRec: Fix division by zero and signed overflow | relevante |
| `7326b8f2` | 2026-10-04 | PPCRec: Speed up stfs for values already rounded to single precision | relevante |
| `c9c43737` | 2026-10-04 | PPCRec: Use a single 64-bit store for x64 stfd |  |
| `aa5032cc` | 2026-10-04 | PPCRec: Optimize single-precision stores on AArch64 | relevante |
| `cc6fea12` | 2026-10-04 | PPCRec: Lower optimized single-precision stores to SINGLE_FTZ | relevante |
| `8303fede` | 2026-10-13 | PPCRec: Save XMM6-XMM15 on x64 recompiler entry |  |
| `540487bf` | 2026-10-13 | PPCRec: Speed up x64 recompiler entry |  |
| `d1ca2e98` | 2026-10-22 | PPCRec: Fix function scanning reading unmapped memory | relevante |
| `bb28394c` | 2026-10-25 | HLE: Bind guest PPC/IOSU variables to lifetime of their module |  |
| `8699409c` | 2026-11-01 | linux: Fix various compiler warnings (#2105) |  |
| `3f8dd913` | 2026-11-01 | PPCRec: Code cleanup and refactoring | relevante |

### GPU (Latte): 65

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `414aa9d6` | 2026-04-29 | GX2+Latte: Rework GX2CopySurface |  |
| `1baf219c` | 2026-04-29 | Vulkan: Add tilemode to texture debug name |  |
| `c080b738` | 2026-04-29 | Latte: Rework interval tree for vertex/uniform cache | relevante |
| `d92edefa` | 2026-04-29 | PPCAsm: Reloc modifiers should behave more like LLVM/gnu assembler |  |
| `58c25d53` | 2026-04-30 | Latte: Clean up LatteBufferCache.cpp |  |
| `e8bee64b` | 2026-05-06 | Latte: Fix rare corruption in buffer cache | relevante |
| `0fc74035` | 2026-05-09 | build+Latte: Add working ENABLE_OPENGL and ENABLE_VULKAN build options (#1887) |  |
| `dfb9a99c` | 2026-05-19 | Vulkan: Remove unused transform feedback extension path (#1919) | relevante |
| `a29a160b` | 2026-05-23 | Vulkan: Remove set decoration from vertex attributes (#1923) |  |
| `a02ba9d8` | 2026-05-28 | Vulkan: Skip zero-size readback buffer barriers (#1929) |  |
| `0a4f634e` | 2026-06-10 | Latte: Handle texture copy edge-case |  |
| `038781a0` | 2026-06-27 | Latte: Widen conditions for fast draw mode | relevante |
| `321fa4d7` | 2026-06-27 | Latte+Vulkan: Small code cleanup and optimization | relevante |
| `37730751` | 2026-06-27 | Latte+Vulkan: Several smaller optimizations | relevante |
| `309bca61` | 2026-06-27 | Latte+Vulkan: More code tweaks for performance and cleanup | relevante |
| `45eedb4c` | 2026-06-27 | Vulkan: Separate function for continued drawcalls | relevante |
| `c7ee701e` | 2026-06-27 | Latte: Omit FragCoordScale uniform in shader if unused | relevante |
| `6d050e68` | 2026-06-27 | Latte+Vulkan: Optimize by using incremental state update checking | relevante |
| `a5d8ceb0` | 2026-06-27 | Vulkan: Additional smaller optimizations | relevante |
| `3923bfcc` | 2026-06-27 | Latte: Optimize shader lookup by caching whole sets | relevante |
| `57bc919e` | 2026-06-27 | Latte: Simplify code |  |
| `e142fadf` | 2026-06-27 | Latte: Remove calculation of unused minIndex |  |
| `62ac48f0` | 2026-06-27 | Latte: Add support for VK_EXT_attachment_feedback_loop | relevante |
| `4c4ef50f` | 2026-06-27 | Vulkan: Smaller fixes + code cleanup |  |
| `c2e807f8` | 2026-06-30 | Metal: specify blit options when copying to textures (#1912) |  |
| `880d2b3b` | 2026-07-01 | Vulkan: Make vertexPipelineStoresAndAtomics optional | relevante |
| `2b9938d4` | 2026-07-10 | Latte: Fix caching of shader error state |  |
| `d8a5f4ba` | 2026-07-13 | Latte: Smaller fixes |  |
| `50b9e4ba` | 2026-07-22 | vulkan: crash on fence errors instead of possibly spamming the log |  |
| `cee557d4` | 2026-09-07 | GeneralSettings: Do not enumerate devices while running and disable drop-down |  |
| `b2356961` | 2026-09-10 | Latte: Fix destination slice index for texture copies |  |
| `73001002` | 2026-09-10 | Latte: Add support for texture readback with non-zero slice index |  |
| `bef2d224` | 2026-09-10 | Latte: Refresh source texture before copy |  |
| `9d3a4dc6` | 2026-09-10 | Latte: Fix incorrect signedness of LSHR_INT instruction |  |
| `c25dbdf8` | 2026-09-10 | Latte: Partial support for mips in texture readback | relevante |
| `bda380dd` | 2026-09-10 | Latte+GX2: Improve GX2Invalidate and implement basic tex invalidation |  |
| `303915a6` | 2026-09-10 | Latte: Rehash texture immediately if it switches to GPU-updated mode |  |
| `afb97632` | 2026-09-10 | Latte: Use correct surface width/height when creating tex copy target |  |
| `9743a320` | 2026-09-10 | Latte: Add non-zero mip support to texture readback + refactor | relevante |
| `fad88fd3` | 2026-09-10 | Latte: Fix endianness of UINT texture formats | relevante |
| `57c89d62` | 2026-09-10 | Latte: Fix barriers and feedback loop disabling too early |  |
| `3310f3b8` | 2026-09-10 | Vulkan: Experimental barrier skip when the game does not request it | relevante |
| `54ffbed2` | 2026-09-19 | Latte: Handle edge cases in texture hashing for 16-byte compressed texture formats (#2051) |  |
| `e33db57f` | 2026-09-19 | Latte: Use the Wii U overflow value for MOVA |  |
| `c12284f1` | 2026-09-20 | Latte: Refactor texture and vertex fetch constants into unified enum |  |
| `fc0a841c` | 2026-09-20 | Latte: Very minor optimization for vertex buffer binding | relevante |
| `00bd6c96` | 2026-09-20 | Latte+GX2: Improve GX2SetShaderModeEx and handle uniform mode |  |
| `0a2df059` | 2026-09-20 | Latte: Copying a cubemap slice should create the target as 2D/2D_ARRAY |  |
| `71d92f66` | 2026-09-20 | Vulkan: Fix imgui leaking viewport/scissor state into game rendering |  |
| `4e3c824f` | 2026-09-30 | Latte: Many rendering/shader corrections for Latte, Vulkan, Metal and OpenGL (#2080) | relevante |
| `0a2b6ff7` | 2026-10-01 | Latte: Fix shader loop regression and improve break accuracy | relevante |
| `a079f55c` | 2026-10-02 | Latte: Fix SAMPLE_LB texture offsets |  |
| `9a3f9b79` | 2026-10-02 | Latte: Fix SAMPLE_G texture offsets |  |
| `32e6628a` | 2026-10-02 | Latte: Support burst for vertex exports |  |
| `fced55a9` | 2026-10-06 | Latte: Fix texture readback heuristics for more games | relevante |
| `21878f14` | 2026-10-12 | OpenGL: Fix wrong variable used for polygon offset invalidation tracking (#2093) |  |
| `4a26a3e7` | 2026-10-15 | Metal: Fix ring allocator corruptions due to skipping sync points |  |
| `5b8d5133` | 2026-10-25 | general: Small refactor and robustness improvements |  |
| `42876aa8` | 2026-10-26 | Latte: Fix texture readback for Color Splash and Xenoblade Chronicles X (#2104) | relevante |
| `2e0d11ca` | 2026-10-31 | Latte: Zero out remapped uniforms of the opposite mode |  |
| `077592d4` | 2026-10-31 | Vulkan: Add support for instance attribute divisor | relevante |
| `182b0bc7` | 2026-10-31 | Latte: Special case memcpy-style texture copies |  |
| `4716b53c` | 2026-10-31 | Latte+Vulkan+Metal: Improve support for little-endian indices | relevante |
| `34b59085` | 2026-10-31 | Latte: Refine heuristic in GetActiveColorBufferMask |  |
| `feb7c7b0` | 2026-11-01 | Vulkan: Fix async pipeline compile race condition | relevante |

### HLE (Cafe OS / IOSU): 62

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `6f6c1299` | 2026-04-22 | Logging: Add compile-time format checks (#1885) |  |
| `1c2b7d78` | 2026-04-26 | build: Add ENABLE_LIBUSB option (#1886) |  |
| `0b661488` | 2026-04-30 | coreinit: Always try to print symbols for PPC stack traces |  |
| `a8bd6f84` | 2026-05-04 | coreinit: Implement OSDynLoad_IsModuleLoaded |  |
| `6ab2f501` | 2026-05-09 | coreinit: Stub MCP_DemoGetRemainder to 99 (#1902) |  |
| `7ff99a5e` | 2026-05-19 | debugger: Add support for loading symbols from .map file (#1916) |  |
| `591e721d` | 2026-06-02 | RPL: Remove incorrect ref count check (#1942) |  |
| `c0f02bcb` | 2026-06-04 | procui: Avoid crash during title shutdown | relevante |
| `326933b2` | 2026-06-05 | nn_ac: Add local IP getter implementation for Linux (#1839) |  |
| `c2d428e5` | 2026-06-10 | snduser: Improve MIX functions | trazido |
| `fc001cdf` | 2026-06-10 | sndcore: Improve PCM16 linear sampling + correctly clamp sndcore2 ADSR |  |
| `263b3510` | 2026-06-10 | snduser: Avoid hardcoded samples-per-frame | trazido |
| `e70b270e` | 2026-06-10 | sndcore: Small fix and cleanup |  |
| `d28c7654` | 2026-06-10 | snduser: Implement MIXSetDeviceSPan + MIXSetDeviceLFE | trazido |
| `a3d6395e` | 2026-06-23 | Add command line parameters to make Cemu more useful for running tests (#1959) |  |
| `53a74e44` | 2026-06-25 | coreinit+UI: Implement exit() |  |
| `1c7fbafb` | 2026-06-25 | coreinit: Flush logs when forwarding (#1965) |  |
| `a39e99ec` | 2026-06-27 | h264: Improve H264DECMemoryRequirement |  |
| `359f1d9b` | 2026-06-29 | h264: Improve H264DECGetImageSize and fix a regression |  |
| `0e1c927d` | 2026-07-13 | h264: Handle stream interruption more gracefully |  |
| `372cadd6` | 2026-07-13 | RPL: Improve load order |  |
| `4b72d7cf` | 2026-07-13 | swkbd: Refactor + implement and stub more API |  |
| `578312b6` | 2026-07-13 | nn_nfp: Fix wrong constant |  |
| `3005cb7b` | 2026-07-18 | coreinit: Implement a few more DynLoad functions (#1990) |  |
| `13d1826b` | 2026-07-19 | nn_fp: Improve Comment struct and fix invalid Mii name on Linux (#1986) |  |
| `b8f2cf4b` | 2026-07-23 | coreinit: Implement Dynload notify callbacks (#2003) |  |
| `daacdda0` | 2026-08-06 | coreinit: Return correct size from `MEMResizeForMBlockExpHeap` when requested size equals block size (#2022) |  |
| `51250c02` | 2026-09-03 | Fix `makeCallableExport` (#2049) |  |
| `fb3d265d` | 2026-09-10 | RPL: Fix race condition when closing Cemu while game is booting | relevante |
| `38c6ea18` | 2026-09-10 | Remove mac disclaimer + minor code cleanup |  |
| `f0009d8e` | 2026-09-10 | TCL: Yield thread when waiting for ringbuffer space | relevante |
| `3277f9f9` | 2026-09-20 | coreinit: Fix export module for OSAllocFromSystem/OSFreeToSystem |  |
| `14e74ea3` | 2026-09-20 | nsysnet: Avoid CPU starvation when games call select in a loop | relevante |
| `0d8ff87b` | 2026-09-20 | GX2: Check actual connection status in GX2SetDRCConnectCallback |  |
| `0314ec91` | 2026-09-20 | GX2: Set correct shader mode default |  |
| `e24374a1` | 2026-09-21 | coreinit: fix OSTicksToCalendarTime accuracy w/ leap years |  |
| `091a711f` | 2026-10-04 | coreinit: Disable default NaN mode on ARM | relevante |
| `c8a5e25d` | 2026-10-13 | coreinit: Ignore OSSetAlarm calls with a non-positive delay | relevante |
| `b93335be` | 2026-10-13 | coreinit: Fix periodic alarms with a start time in the future | relevante |
| `f38c013b` | 2026-10-13 | coreinit: Fix reading past the end of the host alarm list | relevante |
| `ae937e78` | 2026-10-13 | coreinit: Add OSSetAlarmTag and OSCancelAlarms | relevante |
| `2c212b52` | 2026-10-13 | coreinit: Add the alarm queue functions | relevante |
| `a53cf0ec` | 2026-10-15 | h264: Clean-up debug H264 log statement |  |
| `0e4fc41f` | 2026-10-22 | sndcore: Fix DRC device mix mask indexing | relevante |
| `934329f9` | 2026-10-22 | GX2: Fix flush not starting a bigger cmd buffer when empty | relevante |
| `f3f85d9e` | 2026-10-25 | coreinit: Close open IPC handles on title shutdown |  |
| `c15df109` | 2026-10-25 | HLE: Fix missing registration of nn_sl |  |
| `a3c4d359` | 2026-10-25 | nn_fp: Remove double constinit |  |
| `5b430d65` | 2026-10-25 | coreinit: Clean up more state on title shutdown |  |
| `0fa6cd02` | 2026-10-25 | dmae+tcl+ntag+nfc: Reset state on title start |  |
| `bfb15452` | 2026-10-25 | GX2: Reset more state on title start |  |
| `fe5ef4bf` | 2026-10-25 | mic+nn_aoc+nn_fp: Reset more state on title start |  |
| `02b5f211` | 2026-10-25 | H264: Properly reset state and fix session cleanup |  |
| `9bfdf816` | 2026-10-25 | HLE: Reset more state correctly + related tweaks | parcial (só snd_user) |
| `83904818` | 2026-10-25 | nlibcurl: Rework for better accuracy |  |
| `1f29e853` | 2026-10-25 | nsysnet: Implement blocking accept + smaller fixes |  |
| `2d600762` | 2026-10-25 | util: Clean up PPCConcurrentQueue and guard against push during shutdown |  |
| `5f10237e` | 2026-10-26 | coreinit: Add FSTimeToCalendarTime (#1592) |  |
| `04453d71` | 2026-10-31 | GX2: Implement GX2RSetPixelUniformBlock |  |
| `b6121dc3` | 2026-10-31 | debugging: Also use local and weak symbols |  |
| `0df18ba8` | 2026-10-31 | coreinit: Avoid crash on title exit in OSFreeToSystem | relevante |
| `3b103161` | 2026-11-04 | nlibcurl: Respect proxy option in settings.xml |  |

### Entrada: 7

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `f3fecba3` | 2026-05-08 | Migrate from SDL2 to SDL3 3.4.2 (#1847) |  |
| `8e3e961b` | 2026-05-09 | build+input: Make SDL optional (#1895) |  |
| `ad73c1e0` | 2026-05-15 | Input: Fix race condition in button mapping access (#1900) |  |
| `dbec2c71` | 2026-06-13 | Clean up includes in MainWindow and remove duplicate helper (#1955) |  |
| `54d03923` | 2026-09-10 | General code cleanup + various smaller fixes |  |
| `7945f102` | 2026-09-10 | input+kpad: Avoid overwriting values before use |  |
| `52881127` | 2026-10-25 | input: Only start wiimote workers if Wii Remotes are actually used |  |

### Outros (Common, config, util, audio…): 34

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `f8fb588b` | 2026-04-22 | build: macOS CMake fixes (#1883) |  |
| `15ceda12` | 2026-04-29 | GfxPack: Log line number for errors caused by malformed expressions |  |
| `f9ba33a4` | 2026-04-29 | PPCAsm: Support cr register in bcclr instructions |  |
| `b2a735e0` | 2026-04-29 | PPCAsm: Support C-style escapes in strings |  |
| `12970c2d` | 2026-05-06 | PPCAsm: Fix string parsing when string contains escaped double quote |  |
| `e4fa4701` | 2026-06-08 | logging: Improve Windows OS version detection (#1949) |  |
| `bae38e56` | 2026-06-08 | GraphicPack: Allow overriding cos.xml permissions (#1948) |  |
| `079a4af6` | 2026-06-17 | debugging: Add GDB pretty printers for common types (#1957) |  |
| `f3d5788f` | 2026-06-23 | FST: Look for .key file next to wud/wux file (#1961) |  |
| `c201c651` | 2026-07-05 | ppc: Add mfcr and mtcrf support to assembler |  |
| `aa7c82dd` | 2026-07-06 | ppc: add mcrf support to assembler |  |
| `f8282763` | 2026-08-16 | Enforce that first arg contains '.rpx' in cos-argstr parameter (#2028) |  |
| `fce56be3` | 2026-08-21 | ppc: add LWBRX and LHBRX to PPC assembler |  |
| `f36bc09c` | 2026-08-21 | ppc: add bdnzlr and bdzlr instructions to PPC assembler |  |
| `22b0b8b0` | 2026-08-21 | ppc: add blrl, bdzlrl and bdnzlrl instructions to PPC assembler |  |
| `5ead5800` | 2026-08-21 | cli: add launch setting to open debugger |  |
| `95efc3fd` | 2026-09-10 | prudp: Fix syn packet length check + read data payload size correctly |  |
| `f07c987d` | 2026-09-10 | nex+fp: Fix retry timeout |  |
| `a1cd67ca` | 2026-09-10 | UI: Refactor auto-updater and make it more robust |  |
| `89af5432` | 2026-09-10 | debugging: Added RPX dump option |  |
| `8a471c89` | 2026-09-10 | docs: Move contribution guidelines to separate file + text tweaks |  |
| `3d5881ac` | 2026-09-20 | util: Added small frame based CPU benchmark helper |  |
| `b1fe77e9` | 2026-09-22 | Refactor ELF reader class |  |
| `5e09ec72` | 2026-09-23 | UI: Various (macOS) UI fixes (#2064) |  |
| `c717fcab` | 2026-09-24 | general: More `MEMPTR` operators should be const (#2072) |  |
| `c78e7766` | 2026-10-04 | Common: Check whether PEXT is fast on the current CPU |  |
| `aa20e2f8` | 2026-10-10 | debugging: Fix the `MEMPTR` pretty printer and add a `CafeString` pretty printer (#2089) |  |
| `b0446411` | 2026-10-15 | GraphicPack: Allocate the full code cave of a patch group |  |
| `babd6906` | 2026-10-15 | GraphicPack: Resolve patch groups in linear time |  |
| `29f2f1fa` | 2026-10-22 | deps: Remove the unused Boost.Asio Bluetooth header |  |
| `e7107612` | 2026-10-29 | discord-rpc: Show game name as activity status (#2107) |  |
| `43d8640a` | 2026-11-01 | CI: Ship windows builds with debug symbols |  |
| `2065d6ae` | 2026-11-01 | windows: Exclude crash handler from stack trace |  |
| `511a4dfd` | 2026-11-04 | docs: Remove Matrix |  |

### Build / dependências: 13

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `aa6e2c05` | 2026-04-22 | build.MD: Add pkgconf to mac brew dependencies & mention bundle flag (#1882) |  |
| `0d832c48` | 2026-05-07 | CI: Disable fail-fast for macOS builds (#1901) |  |
| `251662aa` | 2026-06-03 | vcpkg: Update vcpkg + update to SDL 3.4.10 (#1943) |  |
| `a52c9bd7` | 2026-06-10 | README.md: Add LLM policy |  |
| `b517fbc1` | 2026-06-11 | build: Make CMake ask for fmt 12.1 instead of 9 (#1953) |  |
| `65a37336` | 2026-06-11 | ih264d: Fix color inaccuracy on aarch64 (#1954) |  |
| `0e7e9ee6` | 2026-06-30 | CI: homebrew - Untap aws and azure to silence warnings (#1972) |  |
| `145ad1e9` | 2026-07-11 | deps: update to wxwidgets 3.3.3 |  |
| `acb105bd` | 2026-07-13 | build: Link SDL3 earlier (#1982) |  |
| `f82598e4` | 2026-07-17 | build: Fix CMAKE_CXX_FLAGS |  |
| `f6a8883d` | 2026-08-18 | build: add missing package for arch linux (#2031) |  |
| `c43303e9` | 2026-09-20 | CI: Bump macOS runner version from 14 to 15 (#2065) |  |
| `8e4eb1e5` | 2026-11-04 | ih264d: Cap display reordering to 5 frames |  |

### Desktop (wxWidgets) / recursos: 14

| Commit | Data | Assunto | Nota |
|---|---|---|---|
| `cd23241f` | 2026-04-22 | UI: Added a button to download and install gfx packs from an url (#1866) |  |
| `2fd322ec` | 2026-05-15 | gui: improve graphic pack download buttons |  |
| `ca15637b` | 2026-05-23 | UI: Fix formatting error in TextureRelationWindow (#1926) |  |
| `745d30bc` | 2026-06-09 | debugger: Tweaks + add context menu to write different value types (#1950) |  |
| `d20d701e` | 2026-06-10 | OpenGL: Clamp vsync to valid value (#1937) |  |
| `fe70fdcf` | 2026-06-13 | debugger: Don't clear register views when continuing to run code |  |
| `4ee027d3` | 2026-06-21 | UI: Show "Could not decrypt title" for drag and drop too (#1962) |  |
| `1706e5f3` | 2026-07-31 | UI: Correct tooltips on checksum tool buttons (#2014) |  |
| `5f7f0a80` | 2026-09-02 | macOS: Never hide menu bar (#1989) |  |
| `686164a4` | 2026-09-10 | GameList: Fix ordering |  |
| `d3d16f7a` | 2026-09-23 | UI: Clean up (#2069) |  |
| `d46466cd` | 2026-10-25 | UI: Fix incorrect path encoding in some error messages |  |
| `c78fafa4` | 2026-10-25 | ui: Fix issue where ChecksumTool would ignore missing files |  |
| `67955f47` | 2026-11-01 | ui: Move image handler initialization |  |
