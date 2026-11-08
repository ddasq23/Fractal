# Roadmap do Fractal

## Ordem combinada

1. **Fase 0** — fork, renomeação, cherry-picks do Cemu Plus, build e smoke test. **Concluída (2026-11-08).**
2. **Fase 2** — interface nova (Compose, temas, biblioteca, wallpapers, boas-vindas).
3. Fase 1 (pendente) — perfis do Odin Hub ("SD 480+ aproximado" e "Mali-G57 aproximado"), propostos e aprovados antes de aplicar.
4. Fase 3 — ferramenta de medição (FPS/frametime, CSV por sessão, cenas fixas).
5. **Merge completo do Cemu oficial (`cemu-project/main`)** — antes da Fase 4.
6. Fase 4 — análise do core (`docs/ANALISE-CORE.md`), já sobre a base atualizada.

Ideias opcionais fora da ordem: `docs/IDEIAS-FUTURAS.md`.

## Merge do Cemu oficial

- Base do SSimco (`android-port`): `8759323e` (2026-04-27). O SSimco não atualizou desde então.
- `merge-base` com `cemu-project/main`: `02383542` (2026-04-18).
- `cemu-project/main` estava **220 commits à frente** em 2026-11-08 (tip `3b103161`, 2026-11-04).
- Remote local: `cemu` → `https://github.com/cemu-project/Cemu.git`.
- Lista completa, merge de teste (22 arquivos em conflito) e pendências do Cemu Plus: `docs/UPSTREAM-PENDENTE.md`.

Já trazidos antes do merge (por causa do crash do Mario Kart 8):
`c2d428e5`, `263b3510`, `d28c7654` e a parte `snd_user` do `9bfdf816`.
O restante do `9bfdf816` (nsysnet, vpad, nn_*, rpl, swkbd, ax) depende de
outros commits e entra com o merge.

### Commits por área (220 no total, contagem por caminho, um commit pode contar em mais de uma)

| Área | Commits |
|---|---|
| `src/Cafe/HW/Espresso` (CPU) | 25 (21 em `Recompiler/`) |
| `src/Cafe/HW/Latte` (GPU/shaders) | 65 (28 em `Renderer/Vulkan`, 16 em `LegacyShaderDecompiler`) |
| `src/Cafe/OS` (HLE) | 74 |
| `src/gui` (wxWidgets, desktop) | 34 — irrelevante para o Android |
| `src/Common` / `src/config` | 10 / 6 |
| `src/android` | 0 |

Risco de conflito: o port do SSimco e os cherry-picks de FSR/GSR/RCAS mexem em
`VulkanRenderer.*`, `RendererOuputShader.*`, `LatteRenderTarget.cpp` e
`CemuConfig.*`. As 28 mudanças em `Renderer/Vulkan` são o ponto mais provável de conflito.

### Relevantes para o ARM64 / recompilador
- `aa5032cc` Optimize single-precision stores on AArch64
- `278500f5` Fix stfs conversion on ARM64
- `3a1d2573` AArch64: Restore code size after processing jumps
- `cc6fea12` Lower optimized single-precision stores to SINGLE_FTZ
- `7326b8f2` Speed up stfs for values already rounded to single precision
- `c174b2dd` Allow cold code after the main code
- Correções de instruções: `9cbb00a2` (divisão por zero/overflow), `8cf39971` (subfic carry),
  `9a5b3301` (mcrfs/fcmpo), `10ba5585` (sthbrx), `d1ca2e98` (scan lendo memória não mapeada)
- `3f8dd913` PPCRec: Code cleanup and refactoring — grande; fazer o merge **antes** de mexer no recompilador
- `091a711f` coreinit: Disable default NaN mode on ARM
- Só x64 (sem efeito no Android): `540487bf`, `8303fede`, `c9c43737`, `fe6720c5`, `22a2a3ee`, `908f10cc`

### Relevantes para desempenho de GPU
- `3923bfcc` Optimize shader lookup by caching whole sets
- `6d050e68` Optimize by using incremental state update checking
- `038781a0` Widen conditions for fast draw mode
- `45eedb4c` Separate function for continued drawcalls
- `37730751`, `309bca61`, `321fa4d7`, `a5d8ceb0`, `fc0a841c` otimizações menores
- `c7ee701e` Omit FragCoordScale uniform if unused
- `3310f3b8` Experimental barrier skip when the game does not request it
- `62ac48f0` Support for VK_EXT_attachment_feedback_loop
- `c080b738` Rework interval tree for vertex/uniform cache

### Relevantes para compatibilidade
- `feb7c7b0` Vulkan: Fix async pipeline compile race condition (usamos compilação assíncrona)
- `42876aa8` Texture readback for Color Splash and **Xenoblade Chronicles X** (jogo de benchmark)
- `4e3c824f` Many rendering/shader corrections; `0a2b6ff7` shader loop regression
- `fced55a9`, `9743a320`, `c25dbdf8` texture readback
- `e8bee64b` Fix rare corruption in buffer cache
- `880d2b3b` vertexPipelineStoresAndAtomics optional e `dfb9a99c` remove transform feedback
  extension path — **importantes para o Mali-G57**
- `077592d4` instance attribute divisor; `4716b53c` little-endian indices; `fad88fd3` UINT endianness
- HLE: `14e74ea3` nsysnet CPU starvation, `f0009d8e` TCL yield, `934329f9` GX2 flush,
  `0e4fc41f` sndcore DRC mix mask, alarmes (`b93335be`, `c8a5e25d`, `f38c013b`, `2c212b52`, `ae937e78`),
  crashes no encerramento (`0df18ba8`, `c0f02bcb`, `fb3d265d`)

## Fase 4: experimento com o JitArm64 do Dolphin (pedido do usuário, 2026-11-08)

Primeiro experimento da Fase 4, depois do perfil com simpleperf (que mostra quanto do tempo do núcleo PPC
está no código recompilado e quanto está em HLE, agendador e esperas):

- Comparar o código gerado pelo backend AArch64 do Cemu (`Espresso/Recompiler/BackendAArch64`, IR + alocação
  de registradores; a memória já é base + endereço, sem checagem) com o do JitArm64 do Dolphin para os
  blocos mais quentes.
- Se a diferença valer, protótipo de um backend alternativo atrás de uma opção, com volta para o
  recompilador atual.
- **Licença (decidido pelo usuário em 2026-11-08):** o Fractal pode adotar a licença do Dolphin,
  GPL-2.0-or-later, e reaproveitar código do JitArm64. O código do Cemu é MPL-2.0 sem o aviso
  "Incompatible With Secondary Licenses", e a seção 3.3 da MPL permite distribuir a obra combinada sob a
  GPL-2.0-or-later; os arquivos do Cemu continuam também sob a MPL. Quando o primeiro código derivado do
  Dolphin entrar: o binário e o repositório passam a GPL-2.0-or-later (LICENSE e README), os arquivos
  derivados levam o cabeçalho GPL com o copyright do Dolphin Emulator Project, e os arquivos novos nossos
  passam a usar cabeçalho GPL-2.0-or-later.

## Para a Fase 4: fallback para o interpretador

Smoke test de 2026-11-08 no Odin 2 Mini (sem limite de clock), linhas
`PPCRecompiler: Unsupported instruction at ...` no `log.txt` nos primeiros 90–120 s:

| Jogo | Linhas | Endereços únicos |
|---|---|---|
| Bayonetta 2 | **994** | **477** |
| Xenoblade Chronicles X | 4 | 2 |
| Zelda: Breath of the Wild | 3 | 2 |
| Super Mario 3D World | 2 | 1 (`0x026add90`) |
| Mario Kart 8 | 1 | 1 |

Endereços mais frequentes no Bayonetta 2: `0x02ce28d4` (24), `0x0345f538` (19),
`0x0341da9c` (15), `0x03474698` (11), `0x03473fd4` (11).

São medidas do boot apenas, base antes do merge. Repetir depois do merge e
na cena fixa da Fase 3, e identificar quais opcodes caem no fallback.

## Smoke test de boot (2026-11-08, Odin 2 Mini)

| Jogo | Resultado |
|---|---|
| Super Mario 3D World | Tela de título |
| Mario Kart 8 | Crashava (snd_user, endianness do MixControl); após os commits do snd_user, roda a demo |
| Bayonetta 2 | Cinemática de abertura |
| Xenoblade Chronicles X | Aviso de modo offline do jogo |
| Zelda: Breath of the Wild | Tela preta aos 90 s, processo vivo; confirmar com mais tempo |
