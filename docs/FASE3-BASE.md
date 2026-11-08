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

Snapdragon 865 com cpu5 e cpu6 desligados, núcleos grandes a 1,80 / 1,86 GHz, GPU a 400 MHz
(regra do RetroidHub). Driver Vulkan: o do módulo do RetroidHub (build 1745012551), não o de fábrica.

*Pendente:* a primeira rodada no RP5 foi descartada (o GamePad emulado não estava salvo, então as cenas
não começavam) e a segunda parou porque o Vulkan do aparelho deixou de carregar (permissão do driver
do módulo). Será refeita.

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
