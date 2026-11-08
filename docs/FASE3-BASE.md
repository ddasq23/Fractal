<!-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/.
   -
   - Copyright (c) 2026 DFD -->

# Fase 3 — tabela de base

Medições de 2026-11-08 com o build release (commit 47d0ba07 + cenas de 3bae0c76), log de desempenho e as
cenas de `tools/perf/scenes` (procedimento em [BENCHMARK.md](BENCHMARK.md)). Cache de shaders quente,
async compile ligado, vsync desligado, accurate barriers ligado, CPU em modo multi-core recompiler.

Colunas: FPS médio e 1% low na janela da cena; frametime médio / pior; quadros acima de 50 ms na
janela; shaders compilados / pipelines criados dentro da janela; CPU do processo (100 % = um núcleo);
a thread mais ocupada.

## RP5 — perfil SD 480+ (base de otimização)

Snapdragon 865 com cpu5 e cpu6 desligados, núcleos pequenos a até 1,80 GHz e grandes a até 1,86 GHz,
GPU a 400 MHz (regra do RetroidHub; clocks conferidos no log). Driver Vulkan **de fábrica** (Adreno 650,
build de 2023-09-27): o módulo de driver do RetroidHub deixou de carregar às 18:13 e saiu no reboot.
Rodadas anteriores (sem GamePad salvo, e com o driver do módulo) foram descartadas.

| jogo | FPS | 1% low | frametime médio / pior (ms) | > 50 ms | shaders / pipelines na janela | CPU | threads mais ocupadas |
|---|---|---|---|---|---|---|---|
| SM3DW | 53,7 | 26,9 | 18,6 / 54 | 1 | 1 / 52 | 225 % | OSSched[core=1] 97 %, LatteThread 92 % |
| MK8 | 43,5 | 24,9 | 23,0 / 85 | 10 | 0 / 15 | 285 % | OSSched[core=1] 97 %, LatteThread 77 % |
| Bayonetta 2 | 24,3 | 15,1 | 41,2 / 379 | 231 | 3 / 158 | 308 % | OSSched[core=1] 94 %, LatteThread 81 % |
| XCX | 30,1 | 29,8 | 33,3 / 34 | 0 | 0 / 0 | 203 % | LatteThread 99 %, OSSched[core=0] 99 % |
| BotW | 9,4 | 5,5 | 106,6 / 416 | 516 | 44 / 124 | 305 % | OSSched[core=1] 91 %, LatteThread 87 % |

- **Dois gargalos no RP5:** o núcleo PPC principal (91–97 %) e a LatteThread (77–99 %, a thread que
  processa os comandos da GPU emulada e grava os comandos Vulkan). Além disso a GPU a 400 MHz fica
  em 95–96 % no SM3DW e no MK8 (overlay do RetroidHub), então nesse perfil a GPU também limita.
- **XCX** fica nos 30 fps do próprio jogo com folga zero: LatteThread e núcleo PPC já em 99 %.
- **Bayonetta 2** tem engasgos de compilação na janela (158 pipelines, 231 quadros acima de 50 ms).
- **BotW:** no RP5 o jogo carrega mais rápido e aos 320–375 s o Link já está jogável, depois da cena do
  despertar; por isso há compilação na janela. No RG Cube a mesma janela ainda é a cena. Os números
  dos dois aparelhos não são da mesma imagem.

## RG Cube — Unisoc T820, Mali-G57 MC4, sem limite de clock

| jogo | driver | FPS | 1% low | frametime médio / pior (ms) | > 50 ms | shaders / pipelines na janela | CPU | thread mais ocupada |
|---|---|---|---|---|---|---|---|---|
| SM3DW | sistema (r40p0) | **39,9** | 27,3 | 25,1 / 64 | 2 | 1 / 17 | 230 % | OSSched[core=1] 99 % |
| SM3DW | FristOneRR 3.7.0 | 28,5 | 20,2 | 35,1 / 50 | 3 | 0 / 6 | 179 % | OSSched[core=1] 99 % |
| MK8 | sistema | **16,0** | 13,3 | 62,4 / 85 | 609 | 16 / 27 | 222 % | OSSched[core=1] 98 % |
| MK8 | FristOneRR | 13,1 | 10,1 | 76,2 / 107 | 486 | 1 / 17 | 193 % | OSSched[core=1] 98 % |
| Bayonetta 2 | sistema | 11,1 | 6,5 | 90,3 / 166 | 662 | 0 / 28 | 186 % | OSSched[core=1] 98 % |
| Bayonetta 2 | FristOneRR | **12,0** | 7,5 | 83,7 / 312 | 715 | 0 / 47 | 195 % | OSSched[core=1] 98 % |
| XCX | sistema | 30,1 | 29,8 | 33,3 / 34 | 0 | 0 / 0 | 212 % | OSSched[core=0] 99 % |
| XCX | FristOneRR | 30,1 | 29,3 | 33,3 / 36 | 0 | 0 / 0 | 159 % | OSSched[core=0] 99 % |
| BotW | sistema | 3,8 | 3,3 | 265,9 / 316 | 207 | 0 / 2 | 210 % | OSSched[core=1] 98 % |
| BotW | FristOneRR | **4,7** | 4,0 | 210,6 / 250 | 261 | 2 / 7 | 202 % | OSSched[core=1] 98 % |

### Leitura

- **O RG Cube é limitado pela CPU emulada.** Em todas as cenas uma thread `OSSched` (o núcleo PPC que
  roda o jogo) fica em 98–99 % de um núcleo do T820. A GPU não é o gargalo principal: trocar de driver
  muda pouco, e o XCX fica preso nos 30 fps do próprio jogo.
- **Driver do sistema vs FristOneRR (PanVK):** o driver do sistema ganha nos jogos mais leves de GPU
  (SM3DW +40 %, MK8 +22 %); o FristOneRR ganha em Bayonetta 2 (+8 %) e BotW (+24 %). O driver do
  sistema gasta 20–38 % de um núcleo na thread `mali-cmar-backend` (o próprio driver), CPU que falta
  para a emulação; o FristOneRR não tem essa thread.
- **SM3DW e MK8 com o driver do sistema** ainda perdem 8 shaders de streamout (transform feedback sem
  vertexPipelineStoresAndAtomics, ver [MALI-ACHADOS.md](MALI-ACHADOS.md)); com o FristOneRR o MK8 perde 14.
- **Compilação em jogo:** shaders novos que aparecem durante o jogo são compilados ali mesmo e só
  vêm do cache no boot seguinte (comportamento do upstream). No MK8 há uma leva fixa aos ~230 s da
  corrida; por isso a janela termina em 228 s.

## Depois do merge do Cemu oficial (build 7c02ce75)

Mesmas cenas e janelas, medidas de novo depois do merge do upstream (220 commits, até `3b103161`).
Cada linha teve uma passada de aquecimento antes da medida; a troca de driver limpa o cache de
pipelines, então cada driver teve a sua.

### FPS médio na janela (1% low entre parênteses)

| jogo | RP5 fábrica antes | RP5 fábrica depois | RP5 Turnip R8 | RG Cube sistema antes | RG Cube sistema depois | RG Cube FristOneRR antes | RG Cube FristOneRR depois |
|---|---|---|---|---|---|---|---|
| SM3DW | 53,7 (26,9) | 53,3 (27,2) | 49,4 (29,1) | 39,9 (27,3) | 38,8 (27,8) | 28,5 (20,2) | 28,4 (20,2) |
| MK8 | 43,5 (24,9) | 44,4 (21,9) | 37,1 (25,3) | 16,0 (13,3) | 16,3 (13,7) | 13,1 (10,1) | 12,9 (9,9) |
| Bayonetta 2 | 24,3 (15,1) | 24,2 (15,1) | 23,5 (14,1) | 11,1 (6,5) | 11,1 (6,4) | 12,0 (7,5) | 11,8 (7,7) |
| XCX | 30,1 (29,8) | 30,1 (29,8) | 30,1 (29,8) | 30,1 (29,8) | 30,1 (29,9) | 30,1 (29,3) | 30,1 (28,4) |
| BotW | 9,4 (5,5) | 9,6 (6,7) | trava (ver abaixo) | 3,8 (3,3) | 3,8 (3,3) | 4,7 (4,0) | **6,4 (5,5)** |

- **Sem regressão.** Com o driver de fábrica do RP5 e o do sistema do RG Cube, as cinco cenas ficam
  dentro da variação entre rodadas (±1 fps).
- **BotW com FristOneRR subiu de 4,7 para 6,4 fps** no RG Cube. Na mesma cena do despertar, o jogo
  fica 1–2 fps acima em todo o trecho final.
- **Mali sem falhas de compilação.** Antes do merge, SM3DW e MK8 perdiam 8 shaders de streamout com o
  driver do sistema (14 com o FristOneRR). Depois do merge nenhum shader ou pipeline falha nos 5
  jogos. Sem `vertexPipelineStoresAndAtomics` o efeito de streamout continua faltando, e o próprio
  Cemu avisa isso no log.

### Turnip no RP5 (Turnip v26.0.0 R8, K11MCH1, Mesa 26.0.0-devel)

| jogo | driver | FPS | 1% low | > 50 ms | CPU | LatteThread |
|---|---|---|---|---|---|---|
| SM3DW | fábrica | 53,3 | 27,2 | 0 | 217 % | 87 % |
| SM3DW | Turnip | 49,4 | 29,1 | 0 | 176 % | 59 % |
| MK8 | fábrica | 44,4 | 21,9 | 15 | 292 % | 80 % |
| MK8 | Turnip | 37,1 | 25,3 | 0 | 225 % | 56 % |
| MK8 | Turnip, GMEM forçado | 22,5 | 15,9 | 211 | 181 % | 36 % |
| MK8 | Turnip, sysmem forçado | 38,4 | 25,9 | 2 | 230 % | 56 % |
| Bayonetta 2 | fábrica | 24,2 | 15,1 | 258 | 308 % | 81 % |
| Bayonetta 2 | Turnip | 23,5 | 14,1 | 255 | 248 % | < 47 % |
| XCX | fábrica | 30,1 | 29,8 | 0 | 203 % | 99 % |
| XCX | Turnip | 30,1 | 29,8 | 0 | 181 % | 78 % |

- **O Turnip gasta bem menos CPU** (a LatteThread cai de 80–99 % para 56–78 %) e **dá quadros mais
  regulares** (1% low maior, nenhum quadro acima de 50 ms no MK8), mas **perde FPS médio** onde a GPU
  pesa: SM3DW −7 %, MK8 −16 %.
- **Não é GMEM contra sysmem.** O Turnip já escolhe sysmem sozinho: forçar sysmem dá o mesmo
  resultado (38,4), e forçar GMEM derruba o MK8 para 22,5 fps (os muitos render passes do Cemu
  custam caro em GMEM).
- **A GPU fica ociosa com o Turnip.** No overlay do RetroidHub, no MK8 a GPU fica em 74 % com o Turnip
  contra 99 % com o driver de fábrica, e nem a LatteThread nem a GPU estão no limite. O tempo se perde
  em espera entre CPU e GPU (submissões, fences, leituras de volta). Fica para a análise da Fase 4.
- **BotW trava com o Turnip quando o cache está quente.** A passada de aquecimento (cache frio) roda e
  chega a 11–12 fps na janela, acima do driver de fábrica. As duas passadas com cache quente morreram
  aos ~19 s, ainda no carregamento, com `VK_ERROR_DEVICE_LOST` no `vkQueueSubmit` ("failed to submit
  command buffer. Error -4"). Com o cache frio os desenhos que esperam pipeline são pulados; com o
  cache quente algum shader roda logo no carregamento e trava a GPU. O driver de fábrica não tem o
  problema.
