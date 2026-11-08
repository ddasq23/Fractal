<!-- This Source Code Form is subject to the terms of the Mozilla Public
   - License, v. 2.0. If a copy of the MPL was not distributed with this
   - file, You can obtain one at https://mozilla.org/MPL/2.0/.
   -
   - Copyright (c) 2026 DFD -->

# Benchmark (Fase 3)

Como medir o desempenho do Fractal de forma repetível e comparar aparelhos, drivers e mudanças no
core.

## O que é medido

Com **Configurações › Overlay › Record performance log** ligado, cada sessão de jogo grava uma pasta
`Android/data/dev.dfd.fractal/files/perf/<data>_<titleid>/`:

| arquivo | conteúdo |
|---|---|
| `info.txt` | build, jogo, aparelho, SoC, GPU, driver (e driver customizado), opções que mudam o desempenho |
| `seconds.csv` | uma linha por segundo: fps, frametime médio / p99 / máximo, shaders compilados (do cache, falhos, ms), pipelines criados (síncronos, falhos, ms), CPU do processo, RAM, clock de cada núcleo |
| `threads.csv` | uso de CPU de cada thread por segundo (≥ 0,5 %) |
| `frames.csv` | o frametime de cada quadro |

Os arquivos são gravados a cada segundo, então um travamento ou `force-stop` não perde dados.

## Cenas

As cenas ficam em `tools/perf/scenes/*.txt`. Cada uma começa **sem save** (o script apaga o save
emulado do jogo dentro da pasta do app) e usa um roteiro de toques no GamePad para chegar sempre ao
mesmo ponto. O roteiro é aplicado pelo próprio emulador (`perf/input_<titleid>.txt`, ver
`LattePerfLog.h`) e só funciona com o log ligado e o **Controle 1 configurado como Wii U GamePad**.

| cena | caminho | janela medida |
|---|---|---|
| `sm3dw` | arquivo novo → File 1 → Mario → abertura (fogos e personagens) | 75–105 s |
| `mk8` | Grand Prix 50cc → Mario → Mushroom Cup → Mario Kart Stadium; o jogador fica parado e a corrida segue | 185–235 s |
| `bayo2` | slot 1 → Story → 2nd Climax → prólogo | 190–250 s |
| `xcx` | aviso offline → título → configurações iniciais (sai com B) → abertura no espaço | 255–325 s |
| `botw` | título → "Open your eyes" → Link acorda no Santuário da Ressurreição | 320–375 s |

A janela é contada a partir do início do jogo. Num aparelho mais rápido o jogo carrega antes, então a
mesma janela pode cair um pouco mais adiante na mesma cena; as janelas foram escolhidas dentro de
trechos longos e homogêneos para reduzir esse efeito. Toda medição é feita com o **cache de shaders já
quente** (pelo menos uma passagem anterior pela cena); a coluna de shaders da sessão mostra quanto
ainda foi compilado.

## Como rodar

Use o **build release** (o debug compila o código nativo com -O0 e asserts, e fica bem mais lento).

```sh
# uma cena, com screenshots em 60 e 90 s
tools/perf/run_scene.sh <serial adb> sm3dw saida/ "60 90"

# todas as cenas e a tabela (uma linha por cena, na janela de cada uma)
tools/perf/baseline.sh <serial adb> "RG Cube (driver do sistema)" saida/rgcube_sys

# resumir sessões avulsas
python3 tools/perf/summarize.py perf/2026* --from 60 --to 120 --label "Odin"
```

Colunas da tabela: FPS médio; 1% low (fps equivalente ao frametime do percentil 99); frametime médio
e pior; quadros acima de 50 ms (engasgos); shaders compilados e pipelines criados na sessão e dentro
da janela; tempo que a thread de renderização esperou por pipelines na janela; CPU do processo (100 %
= um núcleo); threads mais ocupadas.
