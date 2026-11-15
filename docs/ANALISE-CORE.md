# Fase 4 — Análise do core

Análise e relatório apenas: nada do core foi reescrito. Cada conclusão vem de uma medida
(simpleperf, log de desempenho do Fractal) ou de um trecho de código citado.

## Resumo

1. **Na maioria das cenas o limite é a GPU, não o JIT.** No RP5 a GPU fica 96–97 % ocupada no
   SM3DW e no MK8; no RG Cube, 91–100 % no SM3DW, MK8 e Bayonetta 2. Acelerar o JIT não muda o
   FPS dessas cenas.
2. **Os "98 % de CPU" da Fase 3 eram em boa parte espera ativa.** O núcleo PPC principal passa
   25–48 % do tempo (RP5) e 68–89 % (RG Cube) no laço ocioso, que nunca dorme. A LatteThread
   gasta de um quarto a metade do tempo esperando comandos em laço. No RP5 cada leitura do relógio
   custa 137 ns, porque o kernel intercepta a instrução.
3. **No RP5 o emulador roda em só dois núcleos rápidos.** O `core_ctl` da Qualcomm deixa ativas
   apenas as CPUs 4 e 7. O laço ocioso, a LatteThread e o recompilador disputam essas duas.
4. **Há correções baratas:**
   - o flush-to-zero nunca é ligado no Android (erro de `#ifdef`);
   - uma única instrução (`mfspr UPIR`) deixa 1 054 funções do Bayonetta 2 no interpretador;
   - a troca de driver apaga o cache de pipelines. Sem ele, o MK8 leva 48 s até a primeira imagem
     em vez de 6 s.
5. **O JIT tem muito espaço para melhorar** (sem NEON, sem ligação entre blocos, CR em bytes), e
   as técnicas do Dolphin se aplicam. Mas, pelo item 1, isso só deve aparecer em FPS depois de
   aliviar a GPU e as esperas.

**Ordem proposta** (detalhes no fim):

1. correções rápidas;
2. trocar as esperas ativas por esperas que bloqueiam;
3. investigar o trabalho da GPU;
4. custo do recompilador;
5. pipelines no boot;
6. teste de dica de desempenho no RP5;
7. JIT ao estilo Dolphin;
8. streamout e geometry shader na Mali.

## Como foi medido

**Aparelhos**

| aparelho | SoC / GPU | driver | observações |
|---|---|---|---|
| RP5 | Snapdragon 865, Adreno 650 | fábrica (09/27/23) | perfil SD 480+, `performance_mode` 0, kernel 4.19 |
| RG Cube | Unisoc T820, Mali-G57 MC4 | sistema (r40p0) | tela 720x720 |

**Cenas e janelas.** As 5 cenas de `tools/perf/scenes` (SM3DW, MK8, Bayonetta 2, XCX, BotW).
Cada uma começa de um save apagado e recebe entrada por script. As medidas usam a janela
`# window:` de cada cena, com o cache quente (houve uma passada de aquecimento antes).

**Perfis.** simpleperf em 4 kHz por 30 s dentro de cada janela, com `--call-graph fp`:

- RP5: root, `-e cpu-cycles`;
- RG Cube: `--app`, `cpu-cycles:u`, só espaço de usuário.

**Ferramentas.** `tools/perf/jit_profile.py` divide as amostras por thread e categoria:

- JIT, atribuído por função PPC pelo `jitmap.txt`;
- interpretador e despacho HLE;
- leituras de tempo e laço ocioso (pela pilha de chamadas);
- driver da GPU, kernel, C++ do Cemu.

O log de desempenho grava por segundo: fps, shaders, pipelines e tempo de GPU ocupada (por
timestamps do Vulkan). Também grava `jitmap.txt` (funções compiladas) e `jitfail.txt` (funções
recusadas, com a instrução que causou a recusa).

**Builds.** `c3ba5e33` → `2eaa331a` (só instrumentação, sem mudança de comportamento).

### Tabela de base (build da Fase 4, cache quente)

| jogo | RP5 FPS (1 % low) | RP5 GPU ocupada | RG Cube FPS (1 % low) | RG Cube GPU ocupada |
|---|---|---|---|---|
| SM3DW | 52,6 (28,2) | 96 % | 39,6 (27,7) | 91 % |
| MK8 | 43,4 (23,6) | 97 % | 15,7 (12,8) | 100 % |
| Bayonetta 2 | 24,1 (14,7) | 74 % | 11,0 (6,5) | 100 % |
| XCX | 30,1 (29,8) | 27 % | 30,1 (29,9) | 46 % |
| BotW | 9,6 (6,0) | 52 % | 3,8 (3,2) | 80 % |

O XCX roda no limite de 30 fps do próprio jogo nos dois aparelhos e não entra na discussão de
gargalo.

## Onde vai o tempo

Para cada cena: a fatia do núcleo PPC principal (`OSSched[core=1]`; no XCX é o `core=0`) e da
LatteThread no total do processo, e como cada uma se divide. "Ocioso" é o tempo dentro de
`__OSThreadCoreIdle`.

| aparelho / jogo | núcleo principal | ocioso | JIT | interp. | C++ | LatteThread | C++ | driver GPU | leituras de tempo | kernel |
|---|---|---|---|---|---|---|---|---|---|---|
| RP5 SM3DW | 45 % | 48 % | 46 % | 1,4 % | 3 % | 43 % | 52 % | 12 % | 14 % | 16 % |
| RP5 MK8 | 34 % | 26 % | 63 % | 2,3 % | 5 % | 29 % | 49 % | 23 % | 9 % | 10 % |
| RP5 Bayonetta 2 | 30 % | 41 % | 27 % | 3,0 % | 24 % | 24 % | 51 % | 15 % | 11 % | 15 % |
| RP5 XCX | 49 % | 37 % | 52 % | 1,3 % | 5 % | 50 % | 54 % | 4 % | 18 % | 20 % |
| RP5 BotW | 32 % | 68 % | 26 % | 1,1 % | 3 % | 32 % | 37 % | 11 % | 35 % | 12 % |
| Cube SM3DW | 64 % | 68 % | 28 % | 0,6 % | 3 % | 20 % | 72 % | 16 % | 2 % | – |
| Cube MK8 | 62 % | 70 % | 26 % | 0,6 % | 3 % | 14 % | 60 % | 27 % | 1 % | – |
| Cube Bayonetta 2 | 52 % | 70 % | 8 % | 0,7 % | 21 % | 9 % | 57 % | 26 % | 1 % | – |
| Cube XCX | 40 % | 32 % | 44 % | 1,9 % | 19 % | 29 % | 83 % | 5 % | 3 % | – |
| Cube BotW | 40 % | 89 % | 7 % | 0,4 % | 3 % | 11 % | 52 % | 14 % | 22 % | – |

No RG Cube o perfil é só de usuário: o kernel não aparece, e o tempo dentro do kernel some da
conta.

**Leitura**

- **O núcleo principal está esperando, não emulando, na maior parte do tempo.** O laço ocioso
  só roda quando nenhuma thread do jogo está pronta para rodar, ou seja, quando o jogo espera a
  GPU, o vsync ou outro núcleo. JIT mais rápido só vira FPS quando esse núcleo estiver no caminho
  crítico, o que hoje não acontece em nenhuma janela medida.
- **A LatteThread também espera bastante.** "C++" inclui `LatteCP_readU32Deprc`, que gira
  esperando o próximo comando: 37 % da thread no SM3DW e 26 % no BotW, no RP5. Some-se a isso as
  leituras de tempo e o kernel (o tratamento da interceptação do relógio).
- **No RP5, o BotW não satura nem a CPU nem a GPU.** O núcleo principal fica 68 % ocioso e a GPU
  52 % ocupada. O tempo se perde em sincronização entre CPU e GPU (fences, leituras de volta de
  textura); falta medir isso de perto.
- **No RG Cube a GPU é o limite** no MK8 e no Bayonetta 2 (100 %) e quase no SM3DW (91 %). A
  thread do driver Mali (`mali-cmar-backe`) ocupa 24–39 % do processo.

## 1. Recompilador PowerPC → ARM64

Código: `src/Cafe/HW/Espresso/Recompiler/`. Siglas:

- RC = `PPCRecompiler.cpp`
- GEN = `PPCRecompilerImlGen.cpp`
- FPU = `PPCRecompilerImlGenFPU.cpp`
- BE = `BackendAArch64/BackendAArch64.cpp`
- RA = `IML/IMLRegisterAllocator.cpp`

### 1.1 Funções que caem no interpretador

**Granularidade.** O Cemu compila uma função PPC inteira de cada vez. Se uma única instrução não
for suportada, a função toda é recusada (GEN:2952–2956 → RC:213–217) e roda no interpretador para
sempre: a entrada da tabela de saltos fica marcada como "visitada" (RC:91–92, 452–453).

**O que foi recusado nas sessões medidas** (`jitfail.txt`):

| jogo | funções compiladas | recusadas | causa |
|---|---|---|---|
| SM3DW | 13 500–14 000 | 1 | frontend |
| MK8 | 15 600–20 700 | 1 | frontend |
| Bayonetta 2 | 11 000–12 400 | **1 037–1 056** | 1 054 × `mfspr r12, UPIR` (SPR 1007, número do núcleo), 1 `subfme`, 1 `psq_lx`, 1 `psq_stx` |
| XCX | 12 470 | 2 | frontend |
| BotW | 50 400–52 700 | 2 | frontend |

**Bayonetta 2.** `mfspr` só aceita LR, CTR e UGQR0–7 (GEN:407–445), e o `UPIR` é só uma leitura do
campo `spr.UPIR` do `PPCInterpreter_t`. O ganho medido é pequeno: o interpretador ocupa 2,6–3 % do
núcleo principal, porque as funções presas são frias. Ainda assim a correção é trivial e tira mil
funções do interpretador.

**Lacunas conhecidas que não apareceram nas cenas:**

- **Inteiros:** `twi`, formas OE (`addo`, `subfo`, …), `crnor`/`crnand`, `mcrxr`, `lswx`/`stswx`.
- **SPR:** XER e os demais além de LR, CTR e UGQR.
- **Cache:** `dcbt`, `dcbtst`, `dcbi`, `icbi`, `eieio`.
- **FPU:** `fnmadd(s)`, `fsqrt(s)`, `fctiw`, `mffs`/`mtfsf*`, `fcmpo`.
- **Paired singles:** `ps_cmpo0/1`, `ps_nabs`, `psq_lx`/`stx`/`lux`/`stux`, `dcbz_l`.
- **Saltos:** algumas formas de `bc` com CTR, ou com alvo fora da função.

Os jogos medidos quase não usam essas instruções. Elas podem pesar em outros títulos.

**Código acima de 0x10000000** nunca é compilado (RC:181).

### 1.2 Recompilações e custo do próprio recompilador

**Funções recompiladas em série.** A mesma função é recompilada até 184 vezes numa sessão
(`036db1d4` no Bayonetta 2, `03af1444` no XCX) por invalidação da faixa de código (RC:610). No XCX
são 417 recompilações extras em 12 000 funções. É preciso descobrir quem invalida; provavelmente é
código gerado ou copiado pelo jogo.

**A thread `PPCRecompiler` custa caro no RG Cube**: 13 % do processo no Bayonetta 2 e 27 % no XCX,
medidos depois de mais de 3 minutos de jogo.

- 40–50 % disso é o alocador de registradores: `IMLRA_extendAbstractRangeToEndOfSegment`,
  `_IMLRA_checkAndTryExtendRange`, `PPCRecRA_MaskOverlappingPhysRegForGlobalRange`.
- Outros 15–25 % são `malloc`/`free` do scudo.
- A cada decisão de spill o alocador refaz a passada inteira (RA:1956–2130).

Essa thread disputa núcleos rápidos com o jogo.

### 1.3 FPU e paired singles (candidatos a NEON)

- **Sem NEON.** Cada FPR são dois registradores IML escalares em double, ps0 e ps1 (FPU:18–28).
  Toda operação `ps_*` vira duas operações escalares, muitas vezes com cópias extras.
- **Arredondamento para single.** Custa dois `fcvt` por lane (BE:1436–1442).
- **Sem FMA.** `madd` vira `fmul` + `fadd` (FPU:270–305, 1233–1298). Isso também pode divergir do
  interpretador, que é C++ e que o clang pode contrair em FMA.
- **`psq_l`/`psq_st`.** Só o GQR0 é tratado como conhecido (`IML/IMLOptimizer.cpp:243–261`). Os
  GQR1–7 geram uma cadeia de 5 comparações e saltos, e cada caso recalcula a escala com cerca de 7
  operações (FPU:747–760, 840–858).
- **`fres` e `frsqrte`** são chamadas a funções C (FPU:646, 704).
- **FPSCR nunca é atualizado** e o modo de arredondamento é ignorado (FPU:598).
- **Flush-to-zero desligado no Android (erro confirmado).** `coreinit_Thread.cpp:19` e `:31`
  testam `__arm64__`, mas o clang do NDK define só `__aarch64__`. O FPCR.FZ nunca é ligado, e os
  denormais se comportam diferente do Cemu no PC, que liga o FTZ no MXCSR. É questão de precisão;
  o efeito em velocidade não foi medido.
- **Possível erro no `fctiwz`.** Usa `fcvtzs` de 64 bits (BE:1359–1362), que satura no limite de
  int64 e não no de int32.

### 1.4 Ligação entre blocos e despacho

- **Não há ligação direta entre funções compiladas.** Toda saída passa pela tabela de saltos
  (base em x27, entradas de 8 bytes indexadas por endereço PPC × 2; BE:914–960):
  - `bl` grava o LR na memória e carrega o destino da tabela;
  - `blr`/`bctr` saltam por registrador.
- **Sem pilha de retorno.** O código compilado nunca usa `bl`/`ret` do ARM entre funções PPC, então
  o preditor de retorno da CPU não ajuda.
- **Chamar uma função interpretada** custa uma saída completa: o thunk salva e restaura x19–x30 e
  d8–d15 (BE:1689–1724).
- **Contagem de ciclos.** Cada bloco faz carga, subtração e gravação em memória de
  `remainingCycles` (BE:966–974). O teste de saída só existe em saltos para trás.
- **Registradores de CR.** Cada bit de CR é um registrador de 1 byte. Um `cmp` gera 3 × `cmp` +
  `cset`, e o `mfcr` gera 32 pares de deslocamento e soma (GEN:491–505).
- **Estado salvo em chamadas.** Toda chamada ou retorno grava todo o estado sujo em
  `PPCInterpreter_t`, e todo segmento que pode ser ponto de entrada começa sem nada em
  registradores.

### 1.5 Memória (fastmem)

- **Fastmem por x28** (`MEM_BASE_REG`), base plana de 4 GB:
  `add w25, base, imm; ldr [x28, w25, uxtw]` mais `rev` (BE:1040–1061).
- **Não há forma `[reg, #imm]`**, então sobra um `add` por acesso.
- **Não há caminho lento nem backpatching.** O tratador de SIGSEGV só gera um dump
  (`ExceptionHandler_posix.cpp:198`). Como o OS do Wii U é HLE, os jogos quase não tocam MMIO, e
  isso não aparece como custo.
- **Possível erro no `stwcx.`.** O teste de "XER.SO" lê, na verdade, CR0.SO (GEN:1518).

### 1.6 Estudo do JIT ARM64 do Dolphin

O Dolphin é GPLv2+. Você decidiu que o Fractal pode adotar GPL-2.0-or-later para reaproveitar
código (`docs/ROADMAP-INTERNO.md`). Arquivos derivados manteriam o cabeçalho e o copyright do Dolphin.

As cinco técnicas que mais se aplicam ao Cemu num ARM fraco:

1. **Paired singles em NEON com marcação de tipo por registrador.** O Dolphin marca cada registrador
   como par completo, só ps0, duplicado, ou as versões single desses três
   (`JitArm64_RegCache.h`). As operações rodam como 2×f32 ou 2×f64 em NEON, e só convertem quando o
   tipo pedido muda. Cadeias `lfs`→`fadds`→`stfs` ficam inteiras em single.
   - Esforço: médio-alto (rastreio de tipo no IML e emissores NEON).
   - Porte: só a ideia; o código depende do cache de registradores do Dolphin.
2. **Ligação direta e corrigível entre blocos, com o teste de ciclos embutido.** O teste é um
   `SUBS` + `B.GT`, sem instrução extra no caminho ligado. Junto vem uma pilha de retorno no host
   para `blr`: o `bl` do guest vira um `BL` real e o `blr`, um `RET`, com conferência do endereço.
   - Esforço: médio.
   - Porte: `JitArm64Cache.cpp` (cerca de 130 linhas) e `JitCommon/JitCache.cpp` são adaptáveis.
3. **CR como um valor de 64 bits por campo** (`ConditionRegister.h`). Um `cmp` vira um `SUB` e um
   teste de bit vira um `CBZ`/`TBZ`. Junto vêm o carry mantido no flag do host e a liveness de
   flags.
   - Esforço: médio (muda o modelo de CR no IML).
   - Porte: `ConditionRegister.h` é independente.
4. **Especulação de GQR em `psq_l`/`psq_st`, mais propagação de constantes.** Se o bloco usa um
   GQR nulo, uma guarda na entrada transforma a carga em leitura de 2 floats com `REV32`.
   - Esforço: baixo-médio.
   - Porte: `ConstantPropagation.*` entra quase direto.
5. **Pular laços de espera ativa** (`IsBusyWaitLoop` → `CoreTiming::GlobalIdle`) e descartar
   registradores mortos.
   - Esforço: baixo-médio.
   - Porte: o detector é fácil de reescrever; o salto precisa de um gancho no escalonador do Cemu.

O backend do Cemu usa o `xbyak_aarch64`. Não vale importar o emissor do Dolphin
(`Common/Arm64Emitter`): porta-se a lógica e mantém-se o emissor.

## 2. Shaders e pipelines

**Caminho atual.** Latte → GLSL → SPIR-V (glslang, no aparelho) → pipeline do Vulkan.

**Custos medidos** no RP5 com o driver de fábrica:

| etapa | custo |
|---|---|
| GLSL → SPIR-V (glslang), cache frio | 13–16 ms por shader |
| SPIR-V do cache (`shaderCache/precompiled`) | ~1 ms por shader |
| criação de pipeline, driver de fábrica, sem cache do driver | 56–136 ms cada (MK8: 2 448 pipelines, 104 ms em média) |
| criação de pipeline com Turnip/PanVK e cache quente | < 1 ms |

**O cache do driver é o que mais pesa.** MK8 no RP5, os primeiros 60 s:

| cache do driver (`shaderCache/driver/vk/<title>.bin`) | primeira imagem | tempo total criando pipelines |
|---|---|---|
| presente | 6 s | 1,6 s |
| removido | 48 s | 257 s (somado entre as threads) |

O glslang pesa pouco depois do primeiro boot, porque o SPIR-V fica em cache. O que custa é o
driver criar o pipeline sem o próprio `VkPipelineCache`.

**Problemas encontrados:**

- **A troca de driver no Fractal apaga esse cache.** Isso acontece em
  `DriverCaches.clearForTitle`/`clearForGlobalChange`
  (`src/android/.../customdrivers/DriverCaches.kt`). Quem alterna entre o driver de fábrica e o
  Turnip perde 40 s ou mais por jogo a cada troca. Melhor manter um cache por driver: uma subpasta
  por identificador do driver, e a escolha do driver só decide qual arquivo carregar. O
  `VkPipelineCache` já traz o UUID do driver no cabeçalho, e um driver deve ignorar um cache que
  não é dele. Mesmo assim, separar por pasta evita depender disso em drivers com erro.
- **O cache estável recria todos os pipelines no boot.** `VulkanPipelineStableCache.cpp:290` chama
  `Compile(true, true, false)` em `GetPhysicalCoreCount()` threads (no máximo 8, linha 50). Isso
  é o que preenche a coluna "pipelines_sync" no começo das sessões e ocupa todos os núcleos
  durante o carregamento. Com o cache do driver presente custa pouco (1,6 s no MK8).
- **Compilação assíncrona.** Já existe e está ligada (`AsyncCompile=true`), e as extensões de que
  ela depende (`VK_EXT_pipeline_creation_cache_control`) existem nos dois aparelhos.
  `IsAsyncPipelineAllowed` (`VulkanRendererCore.cpp:196`) só deixa compilar em segundo plano
  draws com depth buffer ou com mais de 6 índices. Os demais esperam de forma síncrona.
  - **Bayonetta 2 no RP5:** ainda espera 147 ms por pipeline na janela e tem 254 quadros acima de
    50 ms. Nem todos vêm de pipeline; falta separar as causas.

**Avaliação das três propostas da spec:**

- **Emitir SPIR-V direto, sem glslang.** Ganho pequeno: só o primeiro boot de cada jogo, porque o
  SPIR-V já fica em cache. O esforço é alto (um decompilador novo). Não recomendo agora.
- **Compilação assíncrona com fallback.** Já existe. O que dá para fazer é ajustar a heurística de
  `IsAsyncPipelineAllowed`. Esforço baixo, risco de artefatos visuais em um quadro.
- **Cache persistente de pipelines.** Já existe. O ganho real está em não apagá-lo na troca de
  driver e em não recriar no boot com todos os núcleos. Esforço baixo.

## 3. GPUs alvo

### Adreno 650 (RP5)

- **Driver de fábrica.** Tem `geometryShader` e `vertexPipelineStoresAndAtomics`, então o streamout
  roda por SSBO.
  - GPU a 96–97 % no SM3DW e no MK8: o limite é a GPU, com o clock em 400 MHz no perfil SD 480+.
- **Turnip** (Mesa 26.0.0-devel, v26.0.0 R8; ver `docs/FASE3-BASE.md`):
  - gasta menos CPU (a LatteThread cai de 80–99 % para 56–78 %) e dá quadros mais regulares;
  - perde FPS médio (MK8 −16 %), com a GPU ociosa entre submissões;
  - forçar GMEM derruba o MK8 para 22,5 fps, porque o Cemu usa muitos render passes;
  - o BotW com cache quente perde o dispositivo (`VK_ERROR_DEVICE_LOST`) aos ~19 s.
- **`core_ctl` (perfil SD 480+).** Durante os jogos, o cluster das CPUs 4–6 fica com 1 núcleo
  ativo (`need=3 active=1`), mais a CPU 7. As CPUs 5 e 6 tiveram zero amostras em 30 s. É provável
  que seja o perfil SD 480+ limitando o aparelho de propósito. O app não muda isso sem root, mas
  uma sessão de dica de desempenho (ADPF, `PerformanceHintManager`, Android 12+) pode convencer o
  `core_ctl` a liberar núcleos. Vale testar, porque é barato.

### Mali-G57 (RG Cube)

**Recursos do driver do sistema (r40p0)**, pelo log:

- tem `geometryShader`;
- **não tem `vertexPipelineStoresAndAtomics`**;
- tem `VK_EXT_transform_feedback`.

**Onde o Cemu depende desses recursos:**

- **Streamout.** Desde o merge do upstream, o streamout é emulado gravando num storage buffer a
  partir do vertex shader, o que exige `vertexPipelineStoresAndAtomics`
  (`VulkanRenderer.cpp:713–715`; o próprio Cemu avisa no log). Sem esse recurso o efeito some.
  Nos 5 jogos nenhum shader ou pipeline falha mais. O build `2eaa331a` registra no log o primeiro
  draw com streamout de cada jogo, para saber quais o usam.
  - **Custo de emular sem o recurso:** rodar o vertex shader de streamout como compute shader,
    lendo o vertex buffer e gravando o buffer de streamout. É outro gerador (GLSL de compute a
    partir do mesmo decompilador), mais um dispatch e uma barreira por draw com streamout.
    Esforço alto. Afeta poucos draws (efeitos de partículas e similares), então o custo de GPU
    deve ser pequeno.
- **Geometry shader.** É usado:
  - para os geometry shaders dos jogos;
  - para emular primitivas RECTS (`rectsEmulationGS_generate`, `VulkanPipelineCompiler.cpp:15`).

  O driver do sistema tem `geometryShader`; FristOneRR e malisx2 não.
  - **Sem o recurso:** emular RECTS expandindo os vértices na CPU ou num vertex shader indexado
    (4 vértices por retângulo, com o índice dizendo o canto). Isso é simples e barato. Geometry
    shaders gerais em compute são esforço alto, e nenhum pipeline falhou por isso nas 5 cenas.
- **Desempenho.** A GPU está em 100 % no MK8 e no Bayonetta 2. GPUs Mali e Adreno trabalham por
  tiles: cada render pass que carrega e grava os anexos da memória custa banda. O build
  `2eaa331a` passou a contar render passes e draws por segundo, para medir isso na próxima etapa.

## 4. Outros gargalos encontrados

### 4.1 Laço ocioso e leitura do relógio

- **O laço não dorme.** `__OSThreadCoreIdle` (`coreinit_Thread.cpp:1286`) nunca dorme no núcleo
  principal. A cada volta ele chama `__OSCheckSystemEvents` → `AXOut_update` (com `shared_mutex` e
  `steady_clock`), `alarm_update` e `nnNfp_update`. Cada uma dessas chamadas lê o relógio.
- **O relógio é caro no RP5.** `__rdtsc` no ARM64 é `mrs cntvct_el0` (`Common/precompiled.h:418`),
  e no kernel 4.19 do RP5 essa leitura é interceptada pelo kernel. Microbenchmark:

  | aparelho | `mrs cntvct_el0` | `clock_gettime` |
  |---|---|---|
  | RP5 | 137 ns | 156 ns |
  | RG Cube | 24 ns | 34,5 ns |

- **A conversão também é cara.** `PPCTimer_getFromRDTSC` (`PPCTimer.cpp:127`) usa um spinlock, um
  `mfence` e uma divisão de 128 bits (`__udivmodti4`).
- **No Bayonetta 2 do RG Cube, quase todo o laço ocioso é overhead.** O laço ocupa 69 % do núcleo
  principal. Desse núcleo, saem `pthread_mutex` lock/unlock (28 %), `clock_gettime` (14 %),
  `__udivmodti4` (7 %), `alarm_update` (4 %) e `PPCTimer_getFromRDTSC` (4 %).

### 4.2 Espera de comandos na LatteThread

- **Espera ativa.** `LatteCP_readU32Deprc` (`LatteCommandProcessor.cpp:168`) espera comandos girando
  80 × `isb` e depois chama `LatteTiming_HandleTimedVsync`, `LatteAsyncCommands_checkAndExecute` e
  `yield`, num laço sem dormir.
- **No BotW do RP5:**
  - 81 % das leituras de tempo da LatteThread vêm de `LatteTiming_HandleTimedVsync`
    (`LatteTiming.cpp:156`), que lê o relógio a cada volta;
  - as leituras de tempo somam 35 % da thread, mais 26 % de `LatteCP_readU32Deprc`.

### 4.3 Efeito disso

As duas esperas ativas ocupam núcleos rápidos sem trabalho útil.

- **No RP5, isso pesa duas vezes.** Só há dois núcleos rápidos ativos, e eles são divididos entre o
  laço ocioso, a LatteThread e o recompilador. Além disso, o calor e o consumo de CPU tiram margem
  da GPU, que divide o mesmo orçamento térmico. Essa segunda parte é hipótese, ainda não medida.
- **Bloquear de verdade** (variável de condição ou futex com timeout até o próximo alarme ou vsync)
  libera esses núcleos.

## 5. Impacto, risco e esforço

Os ganhos de FPS são estimativas a partir dos perfis, não medidas. Esforço em dias de trabalho.

| # | item | impacto estimado | risco | esforço |
|---|---|---|---|---|
| A1 | ligar o FTZ no Android (`__aarch64__`) | precisão igual à do Cemu no PC | baixo | < 1 |
| A2 | `mfspr UPIR` (e SPRs simples) no frontend do JIT | ~1 000 funções a menos no interpretador no Bayonetta 2; ≤ 3 % do núcleo principal | baixo | < 1 |
| A3 | cache de pipelines por driver, sem apagar na troca | evita 40 s ou mais de boot e engasgos depois de cada troca de driver | baixo | 1 |
| B | trocar as esperas ativas (laço ocioso, LatteCP, vsync) por esperas que bloqueiam; leitura de tempo mais barata (sem spinlock e sem divisão de 128 bits) | libera 25–89 % de um núcleo rápido e parte da LatteThread. FPS: pouco nas cenas limitadas pela GPU; o BotW do RP5 é o candidato a ganhar. Menos calor e bateria | médio (tempo de áudio, alarmes e vsync) | 3–5 |
| C | investigar o trabalho da GPU: render passes, draws, cargas e gravações de anexos, leituras de volta; corrigir o que for redundante | o maior potencial nas cenas limitadas pela GPU (MK8 no RG Cube a 15,7 fps com 100 % de GPU); desconhecido até medir | médio | 5 de investigação, depois cada correção |
| D | thread do recompilador: descobrir quem invalida as funções recompiladas 184 vezes; alocador mais barato ou thread com prioridade menor | 13–27 % do processo no RG Cube (Bayonetta 2, XCX) | baixo-médio | 2–4 |
| E | pipelines no boot: recriar com menos threads ou sob demanda; revisar `IsAsyncPipelineAllowed` | carregamento e engasgos (Bayonetta 2) | médio (artefatos de um quadro) | 2–3 |
| F | RP5: sessão ADPF (`PerformanceHintManager`) para pedir núcleos ao `core_ctl` | pode liberar as CPUs 5 e 6; a medir | baixo | 1 |
| G1 | JIT: ligação entre blocos e pilha de retorno | menos saltos indiretos; vale onde o JIT domina (MK8 no RP5: 63 % do núcleo principal) | médio | 5–10 |
| G2 | JIT: NEON para paired singles e FPRs em single | menos instruções por operação de FPU | alto | 10–15 |
| G3 | JIT: CR em 64 bits, carry em flags, propagação de constantes, GQR | menos instruções em comparações e ramos | médio | 5–10 |
| H1 | Mali: streamout por compute quando falta `vertexPipelineStoresAndAtomics` | correção visual, não velocidade | alto | 10 ou mais |
| H2 | drivers sem geometry shader: RECTS por vertex shader indexado | permite usar FristOneRR e malisx2 sem perder primitivas | médio | 3–5 |

Também entram em A, de carona: o possível erro do `fctiwz` (saturação em int64) e o do `stwcx.`
(CR0.SO no lugar de XER.SO), cada um com um teste antes.

## 6. Ordem de ataque proposta

1. **A1, A2, A3.** Baratas, de baixo risco, e cada uma pode ser medida isoladamente.
2. **B.** Libera núcleos rápidos e reduz calor. Medir FPS, CPU por thread e GPU ocupada antes e
   depois nas 5 cenas, nos dois aparelhos.
3. **C.** Depois de B o gargalo fica mais limpo. Os contadores de render passes e draws já estão no
   log. Esta é a etapa com mais potencial de FPS.
4. **D e E.** Custo do recompilador e boot de pipelines; ajudam carregamento e engasgos.
5. **F.** Experimento rápido no RP5; pode entrar antes, em paralelo com B.
6. **G1 → G3 → G2.** O JIT ao estilo Dolphin. Só depois de B e C, quando o núcleo PPC voltar a
   ser o caminho crítico. Antes disso, medir de novo para confirmar que o JIT pesa.
7. **H1 e H2.** Compatibilidade na Mali, quando a lista de jogos afetados (pelo log de streamout)
   justificar.

## 7. Resultados de 09/11

Build final do dia (`613dc67b` + `d9606be8`), mesmas cenas e janelas do baseline das 05:56. FPS médio
na janela; entre parênteses, o 1 % low.

| cena | RP5 manhã | RP5 agora | RG Cube manhã | RG Cube agora |
|---|---|---|---|---|
| SM3DW | 52,9 (27,4) | 54,5 (27,6) | 35,4 (22,5) | 40,9 (27,3) |
| MK8 | 43,8 (25,4) | 45,6 (26,4) | 16,1 (13,8) ¹ | 27,4–30,5 (20–22) ² |
| Bayonetta 2 | 23,2 (13,8) | 23,7 (15,0) | 11,1 (6,3) | 11,1 (6,5) |
| XCX | 30,1 (29,7) | 30,1 (29,5) | 30,1 (29,6) | 30,1 (29,1) |
| BotW | 9,5 (6,0) | 9,3 (6,7) | 3,7 (3,3) | 4,5 (4,0) |

¹ Controle medido de manhã com o build antigo; a tabela de base registrou 12,0.
² Varia com o trecho da corrida que cai na janela (episódios de uploads de buffer, ver abaixo).

**O que entrou, em ordem de efeito (RG Cube, MK8):**

1. **Uploads diretos no cache de buffers** (`2c1895ac`): o cache fica em memória visível ao host e,
   quando a faixa não está em uso pela GPU, o upload é um `memcpy` sem encerrar o render pass.
   17,7 → 23,0 fps.
2. **Barreiras sem os estágios de vértice** (`7b36890f`): numa GPU de tiles, uma barreira com destino
   no vertex shader faz o trabalho de vértice do próximo pass esperar todo o fragmento do anterior.
   Agora o estágio de vértice só entra quando um shader de vértice ou geometria lê um alvo escrito.
   24,3 → 30,5 fps; GPU por frame 41,0 → 32,5 ms. Sem efeito no Adreno.
3. **Occlusion queries dentro do render pass** (`2c1895ac`): 16,0 → 17,7 fps.
4. **Laço ocioso e LatteThread sem espera ativa** (`ff38f5b4`): mesmo FPS, menos CPU. XCX: processo
   204 → 119 % no RP5 e 222 → 163 % no RG Cube; LatteThread 99 → 15 % e 96 → 25 %.

**Erro encontrado e corrigido:** o `mfspr UPIR` no JIT (A2) não emitia a leitura, porque os backends
só tratavam SPRs abaixo de 999 (o UPIR é o 1007). O Bayonetta 2 caía em 3 s no RG Cube e travava
na tela de boot no RP5 (`613dc67b`).

**O que se aprendeu:**

- **RP5 com GPU limitada a 400 de 905 MHz** o dia todo: o limite vem do sensor de carcaça
  (`skin-therm` ~52 °C, aparelho no carregador rodando sem parar), não da bateria (35 °C). Os
  números do RP5 acima estão nessa condição. `run_scene.sh` agora registra a temperatura da
  carcaça (`skin_c`).
- **BotW no RP5 não é limitado por render passes:** desligar as barreiras precisas tira 350
  quebras por frame (513 → 164 passes) e o FPS fica em 9,2.
- **Perfil de GPU por render pass** (`pass_profile=1` em `perf/tuning.txt`): timestamps em volta de
  cada pass, somados por assinatura (tamanho, formatos, pixel shader) e escritos no log a cada 10 s.
  - MK8 no RP5: GPU ocupada 88 % a 400 MHz; o pass principal (1280×720, 11-11-10 float + D32F)
    leva 23 %. Há ~40 passes de 32×32 e 16×16 por frame com 6 alvos e 1 draw cada, 8 % da GPU.
  - SM3DW no RP5: o G-buffer (4 alvos + D32F a 720p) aparece ~4 vezes por frame. ~9 dos ~37
    passes por frame terminam por upload de buffer que caiu no caminho lento (faixa ainda em uso).
  - Bayonetta 2 no RP5: nenhum pass domina.

### Tarde de 09/11

**Buffer cache: mover em vez de quebrar o pass** (`ec46fba8`, `a74fbb56`). Quando o upload cai numa
faixa ainda em uso pela GPU, nós de até 1 MB vão para memória nova (e o vertex/uniform binding é
refeito) em vez de terminar o render pass. SM3DW: RP5 54,0 → 55,7, RG Cube 41,1 → 42,9; passes por
frame no Cube 36 → 30. MK8 no Cube: mesma média, 1 % low 21,7 → 23,0.

**Compressão de framebuffer** (`017be0ef`). Toda imagem de cor era criada com
`VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT`, que desliga a AFBC (Mali) e a UBWC (Adreno). Agora só as
imagens com compressão em bloco ficam mutáveis. Uma view com outro VkFormat numa imagem não mutável é
recusada (`texture_isViewFormatCompatible`), e o core cria uma textura separada sincronizada por
cópia, como já fazia para formatos incompatíveis. Nas 5 cenas nenhum jogo precisou disso para
render targets.

| cena | RP5 antes → depois | RG Cube antes → depois |
|---|---|---|
| SM3DW | 57,0 (30,1) → 59,8 (44,5); GPU 87 → 64 % | 42,4–43,3 → 43,7–43,8 |
| MK8 | 46,1 → 48,2–48,4 | 29,6–29,7 → 30,0–31,6 |
| Bayonetta 2 | 23,6 → 24,9 | 11,1 → 11,3 |
| XCX | 30,1 → 30,1 | 30,1 → 30,1 |
| BotW | 9,3 → 9,5 | 4,5 → 4,6 |

A/B alternado no mesmo build (`mutable_off=0` restaura a flag), MK8:

- RP5: sem compressão 46,0 / 45,9 (GPU 96–97 %), com compressão 47,8 / 48,2 (GPU 84–87 %).
- RG Cube, 3 pares: sem 31,6 / 29,0 / 29,7, com 30,3 / 31,0 / 30,3. Empate dentro do ruído, que
  no Cube chega a 2–3 fps entre rodadas; só A/B alternado serve lá.

Bateria final do RP5 com o build do fim da tarde (5 cenas seguidas, 13:20): SM3DW 59,9 (48,0),
MK8 47,8 (23,5), Bayonetta 2 25,1 (15,6), XCX 30,1 (29,5), BotW 9,5 (6,7).

**Perfis na Mali não são confiáveis por pass.** A Mali grava timestamps por job, então o
`pass_profile` atribui o trabalho anterior aos passes pequenos que dependem dele. No Bayonetta 2,
um pass de 320×180 com uma única leitura de textura aparece com 4 ms. Antes de confiar em um custo
por pass no Cube, pular os draws (`skip_ps1..4=<hash>`) e medir o FPS.

**Bug do driver do Adreno 650.** Sem a flag, o XCX caía aos ~160 s no RP5 com
`vkQueueSubmit` = `VK_ERROR_OUT_OF_HOST_MEMORY`. Restaurando a flag em qualquer metade da lista de
formatos o jogo roda; limitar a compressão a imagens grandes não resolve. A causa é o
`vkCmdCopyImage` entre duas imagens UBWC de formatos diferentes (RGBA8 ↔ R32F e parecidos, ~25 por
segundo no XCX). Essas cópias agora passam por um buffer de staging
(`vkCmdCopyImageToBuffer` + `vkCmdCopyBufferToImage`), contador `copies_via_buffer` no log.

**Bug achado de passagem:** `FormatInfoVK::isCompressed` nunca é verdadeiro, então
`VK_IMAGE_CREATE_BLOCK_TEXEL_VIEW_COMPATIBLE_BIT` nunca é usado. Não mexido.

**Uploads de textura num command buffer de prólogo** (`2eeceaea`). Todo upload de textura dentro
de um render pass encerrava o pass, que depois reabria no mesmo FBO: 14 vezes por frame no
Bayonetta 2, nos dois aparelhos. Agora cada textura guarda o último command buffer que a usou
(`GetImageObj`, amostragem pelos descriptor sets ativos em cada draw, attachment no início do pass,
apresentação). Se o buffer atual ainda não usou a imagem, o upload vai para um command buffer de
prólogo, submetido logo antes do principal no mesmo `vkQueueSubmit`, e o pass continua aberto.
Contador `uploads_prologue`, tunable `upload_prologue` (padrão 1).

| Bayonetta 2, A/B alternado | com prólogo | sem prólogo |
|---|---|---|
| RG Cube (GPU 100 %) | 11,8 / 11,6 fps; 1 % low 8,1 / 8,0 | 11,2 / 11,1 fps; 1 % low 6,0 / 6,3 |
| RP5 (núcleo PPC 92 %) | 24,6 / 24,9 | 24,7 / 24,6 |

No RP5 os render passes caem de 52 para 36 por frame, mas o FPS não muda porque a CPU é o limite.
SM3DW, MK8, XCX e BotW não mudam e rodam sem crash nos dois aparelhos.

**Bug introduzido e corrigido no mesmo dia.** O `skip_ps` do `c56440e0` aninhou um `if` sem chaves
dentro do `if` indexado/não indexado, e o `else` passou a pertencer ao `if` interno: draws não
indexadas nunca eram gravadas (`f2cf8808` corrige). As medições feitas com os builds desse
intervalo (12:50–14:34) não valem como números absolutos: o teste de pular os passes minúsculos do
MK8 no Cube e os A/Bs do laço ocioso. As tabelas acima usam builds anteriores ou posteriores.

**Laço ocioso que dorme, de novo** (`idle_wait_max_us=250`; build com o bug acima, vale só como
comparação relativa). BotW no RP5: mesmo FPS (9,6 contra 9,5) com 180 % de CPU no lugar de 239 %.
MK8 no Cube: 30,3 contra 30,8, com 210 % no lugar de 259 %. Mas no RP5 o SM3DW cai de 59,9 para
53,2 e o MK8 de 48,0 para 46,5, com o 1 % low do SM3DW indo de 48,7 para 26,9. Continua desligado.

**ADPF descartado.** `dumpsys performance_hint` mostra `HAL Support: false` no RP5 e no RG Cube
(Android 13): `APerformanceHint_createSession` não cria sessão. O item F da seção 5 não se aplica a
esses aparelhos.

**Sem store de profundidade nos passes que não a escrevem** (`depth_store_none=1`, padrão 0).
Quando nem a draw que abre o pass nem o pass anterior no mesmo framebuffer escreveram
profundidade/stencil, o pass começa com `STORE_OP_NONE` (`VK_EXT_load_store_op_none` ou
`VK_QCOM_render_pass_store_ops`; os dois aparelhos têm). Se uma draw depois escreve, o pass
recomeça com o store. Contadores `depth_passes`, `depth_unused`, `depth_readonly`,
`depth_store_skipped`, `depth_store_restarts`.

| A/B alternado | com | sem | stores pulados/s | recomeços/s |
|---|---|---|---|---|
| Cube MK8 | 29,6 / 30,6 | 28,6 / 30,1 | ~354 | 0 |
| Cube SM3DW | 42,4 | 43,6 | ~272 | 9 |
| Cube Bayonetta 2 | 11,5 / 11,6 | 11,2 / 11,7 | ~9 | 1 |
| RP5 MK8 | 47,5 / 48,3 | 47,9 / 47,3 | ~529 | 0 |
| RP5 SM3DW | 59,8 (1 % low 45,5) | 59,8 (43,5) | ~478 | 17 |
| RP5 BotW | 9,4 | 9,4 | ~251 | 0 |

Sem crash em nenhuma cena. Tudo dentro do ruído: nem a Mali nem a Adreno pagam caro pelo store
de uma profundidade que não mudou. Fica desligado.

### Noite de 09/11

**Onde está o limite de cada cena.** Teste com `diag_tiny_scissor=1`, que recorta toda draw para 1×1
pixel: o trabalho de fragmento some e o de vértice continua.

| cena | normal | scissor 1×1 | GPU ocupada | leitura |
|---|---|---|---|---|
| RP5 MK8 | 47,9 | 47,2 | 84 → 24 % | limite é a CPU (núcleo PPC 1 a 97 %), não a GPU |
| RP5 BotW | 9,5 | 11,5 | 40 → 22 % | a GPU está no caminho crítico mesmo ociosa: a CPU espera por ela |
| Cube MK8 | 30,3–31,3 | 52,6 | 100 → 78 % | limite é o fragmento |
| Cube Bayonetta 2 | 11,6 | 40,6 | 100 → 80 % | limite é o fragmento (~60 ms por frame) |

**Não é banda de textura nem anisotrópico.** A Mali não tem BC1–BC5 e recebe essas texturas
descomprimidas em RGBA8/R8/RG8, então parecia o suspeito. `diag_lod_bias=64` (um mip abaixo, ~¼ da
banda de textura) e `aniso_max_log2=0` não mudam o FPS: Cube MK8 31,1 e 30,3, Bayonetta 2 11,6 e 11,8;
RP5 MK8 47,6 e 47,7. Transcodificar BC para ETC2/ASTC não compensaria.

**Fixar as threads pesadas nos núcleos grandes piora** (`fast_cores=3`, padrão 0). No RP5 o núcleo PPC
principal e a LatteThread passam boa parte do tempo nos A55, mas fixá-las em 4–7 derruba o MK8 de 47,9
para 40,1 (GPU 84 → 70 %: o orçamento de energia vai para a CPU) e não muda o BotW (9,4), só a CPU
(240 → 187 %). No Cube: BotW 4,5 contra 4,6, MK8 29,8 contra 30,6. Fica como diagnóstico.

**BotW: a espera era o sync completo do `GX2DrawDone`.** O tempo que a LatteThread passa esperando a
GPU agora é separado por motivo (`gw_*_ms`). No BotW do RP5 são 15,6 ms por frame, todos em leituras
de volta forçadas. Com Vulkan, todo `GX2DrawDone` (2 por frame no BotW) insere
`IT_HLE_SYNC_ASYNC_OPERATIONS`, que espera a GPU terminar tudo o que foi enviado. Sem esse sync
(`drawdone_sync=0`), as leituras de volta terminam de forma assíncrona e chegam à CPU alguns
milissegundos depois:

| RP5 BotW, A/B alternado | sem sync | com sync |
|---|---|---|
| FPS (1 % low) | 11,1 (8,5) / 11,2 (7,6) | 9,4 (6,7) / 9,5 (6,7) |
| espera pela GPU | 0,02 ms/frame | 15,6 ms/frame |

Virou opção por jogo: `fullSyncAtGX2DrawDone` no perfil do jogo (desligada nos perfis padrão do BotW
JPN/USA/EUR) e um botão "Sincronização total com o GX2DrawDone()" na tela de perfil do jogo. Sem a
opção no perfil, nada muda. Com o perfil padrão e sem tunable, o BotW do RP5 roda a 11,2 e o do Cube
a 6,0, contra 4,5 com o sync forçado (+33 %). No MK8 a
espera equivalente é 1 ms por frame (consultas de oclusão), então não vale mexer nos outros jogos.

**Multiplicação exata nos shaders custa ~6 % na Mali.** Todo produto passa por `mul_nonIEEE`
(0 × inf = 0, como no Wii U). Cube MK8, rodadas alternadas: `strict_mul=0` (multiplicação comum)
32,7 / 34,1 / 31,7 (média 32,8) contra 30,3 / 31,3 / 30,7 do normal (média 30,8). Variantes exatas mais
baratas não recuperam nada: NaN → 0 deu 29,7 / 29,2; zerar as entradas (permite FMA) deu 31,2 / 30,4.
Ambas foram removidas. No RP5 o MK8 é limitado pela CPU, então não há ganho. As capturas do MK8 com
multiplicação comum são iguais às normais. O padrão não muda: o Cemu também não desliga a opção no MK8
e só uma pista foi verificada. Quem quiser pode desligar por jogo em "Precisão da multiplicação em
shaders". Os perfis padrão do Cemu já a desligam em 9 jogos (SM3DW, XCX, Captain Toad...).

**Depois do sync, o BotW do RP5 fica preso na CPU.** Sem o sync completo, a espera pela GPU cai de
15,4 para 0,02 ms por frame e o núcleo PPC 1 fica a 93 %, como no MK8. O próximo ganho tem de vir do
recompilador.

**O que ainda segura o BotW: a espera pela LatteThread.** Contadores novos medem o tempo que as
threads do jogo ficam bloqueadas em esperas do GX2 (`wait_timestamp_ms`, `wait_flip_ms`,
`wait_vsync_ms`). No BotW do RP5, de ~88 ms por frame, 27,4 ms são `GX2WaitTimeStamp` (o
`GX2DrawDone` esperando a LatteThread processar tudo o que foi enviado; o marcador é escrito quando a
LatteThread chega ao EOP, não quando a GPU termina) e 15,7 ms são vsync. No MK8, 2,4 e 1,6 ms de
20,6. Por isso o núcleo 1 do BotW passa 37 % no laço ocioso. Pular essa espera derruba o jogo em
`LatteBufferCache_reserveRange` (o jogo reaproveita buffers que a LatteThread ainda não leu): a espera é
necessária e o ganho tem de vir de encurtar a LatteThread. Dela, 19 % é `clock_gettime` girando em
`LatteCP_itWaitRegMem`, ~15 % é o driver Adreno e o resto é tradução espalhada (hash de textura 2,8 %,
conversão de índices 2,1 %).

A espera também acontece no sentido contrário. `wait_cp_fence_ms` mede a LatteThread parada em
`WAIT_REG_MEM`: o `GX2SetGPUFence` do jogo manda a GPU esperar um valor que outra thread da CPU
escreve quando termina o trabalho dela. No BotW do RP5 são 22,3 ms por frame. O frame vira um
pingue-pongue: a LatteThread espera a CPU (22 ms) e a CPU espera a LatteThread (27 ms). Dos ~56 ms
em que a LatteThread ocupa a CPU, ~22 ms são esse giro; o trabalho real de tradução fica em ~34 ms.
O `disableGPUFence` dos perfis do BotW não existe neste código (só aparece no `.ini`, e com `false`).

Dormir nessa espera piora (`cp_fence_sleep_us`, padrão 0 = girar). A ideia era liberar o núcleo grande
para a thread que escreve a fence. RP5 BotW, alternado: girando 11,2 / 11,2; dormindo 100 µs 10,8 /
10,7; 20 µs 10,6. A LatteThread cai de 63 % para 47 % de CPU, mas a resposta à fence fica mais lenta.

**Turnip no RP5: pior em tudo.** Mesa Turnip v26.0.0 R8, com aquecimento antes de medir:

| RP5 | driver de fábrica | Turnip R8 |
|---|---|---|
| BotW | 11,2 | `VK_ERROR_DEVICE_LOST` no envio do command buffer (uma vez aos 16 s) |
| MK8 | 47,9 | 35,8 |
| SM3DW | 59,8 | 48,3 |

O driver de fábrica continua o recomendado no RP5. No Cube, nenhum driver alternativo é viável (ver
`MALI-ACHADOS.md`).

**Bayonetta 2 não serve para A/B entre builds com caches de shader diferentes.** O script escorrega:
no segundo 250, uma rodada está no combate e outra numa parte mais leve da fase (11,6 contra 16,9 e
27,7 fps). Comparar só rodadas com a mesma captura.

### Manhã de 10/11: JIT AArch64 por dentro

**Ferramenta nova.** `jit_code=1` em `perf/tuning.txt` grava `jitcode.bin` (o código host de cada
função recompilada), e `tools/perf/jit_annotate.py` cruza com um `perf.data` do simpleperf para dar
o perfil por instrução do JIT, agrupado por tipo (memória guest, estado do PPC, contagem de ciclos,
saltos, FPU...) e com as funções mais quentes desmontadas.

**Achado: a contagem de ciclos era ~31 % do tempo no JIT do MK8.** Cada segmento fazia
`ldr/sub/str` do `remainingCycles` na estrutura do PPC, e o teste de cada salto para trás recarregava
o valor: uma cadeia de dependência por memória. Uma única função do MK8 (`0274032c`, um laço de 3
iterações) levava 32 % do JIT.

O que entrou, todos com chave em `perf/tuning.txt` para A/B (padrão ligado):

| mudança | chave | A/B alternado |
|---|---|---|
| `remainingCycles` fixo em `w24` (salvo/recarregado só ao entrar/sair do JIT e em volta de HLE) | `jit_cycles_reg` | BotW Cube 6,1/6,1 → 6,5/6,4 (+5 %); MK8 RP5 49,2/49,6 → 50,0/50,4 |
| código numa arena única (funções a cada 64 B, não uma página de 4 KB cada) | `jit_arena` | MK8 RP5 47,8/47,7 → 48,7/49,1 |
| saltos com 1 instrução (sem `nop` reservado; refaz com 2 se algum não alcança) | `jit_short_jumps` | ver linha abaixo |
| imediatos negativos e `<<12` numa instrução (`sub`/`cmn` em vez de `mov`+`add`) | `jit_short_imm` | ver linha abaixo |
| desvio direto pelas flags (`b.cond` em vez de `cset`+`tbnz`) quando nada mexeu no NZCV | `jit_flag_branch` | os três juntos: BotW Cube 6,36/6,23 → 6,42/6,36; BotW RP5 11,7 → 11,8 |

Sem chave: acesso à memória guest sem `add` quando o deslocamento é zero (inteiros e FPR) e
remoção do `cmp` repetido (um `cmpw` do PPC vira três comparações IML dos mesmos operandos).

**Relógio por draw** (`LatteGPU_GetTick`). `GetTickCount()` com `CLOCK_MONOTONIC_RAW` rodava em todo
draw e era ~3 % da LatteThread no MK8 do RP5. Ele só serve para envelhecer texturas (limiar de
100 ms), então agora usa `CLOCK_MONOTONIC_COARSE`, como o `GetTickCount` do Windows, que tem
resolução de ~15 ms.

**Testado e descartado:**

- **spirv-opt nos shaders** (glslang com `ENABLE_OPT`, nunca tinha rodado no Android). Nenhum
  ganho no Cube (MK8 30,1 contra 30,4/31,5) e +30 % no tempo de compilação por shader. Um "+32 %"
  inicial era uma rodada parada no menu de karts: 6 mil draws/s contra 36 mil. Agora a regra é
  conferir draws/s antes de aceitar um ganho.
- **Readback do Adreno via imagem linear** (o contorno do Dolphin PR 11084): todas as 52
  readbacks/s passaram pelo caminho novo e o BotW ficou em 11,72 contra 11,72.
- **TLS `initial-exec`** para as variáveis `thread_local` quentes (~2 % no `tlsdesc_resolver`): o
  bionic recusa o `dlopen` ("using IE access model") e o app não abre.
- **Um command pool por slot** (reset do pool inteiro em vez de `vkResetCommandBuffer`). O perfil
  DWARF da LatteThread no BotW (RP5) mostrou o reset a 7,3 % da thread, quase tudo `memcpy`/`free`
  dentro do driver Adreno. Com `vkResetCommandPool` o custo continua exatamente 7,3 %, e o FPS
  empatou nos dois aparelhos (RP5 11,8/11,7 contra 11,7/11,7; Cube 6,4/6,3 contra 6,4/6,5).
  Revertido.

**Onde está o limite agora:**

- MK8 no RP5: GPU 89–91 % ocupada a 400 MHz (no carregador). Ganho de CPU não aparece mais.
- MK8 e BotW no Cube: GPU (Mali) 99–100 %.
- BotW no RP5: o frame (86 ms) é serial. A LatteThread espera 23,8 ms por frame na fence
  (`WAIT_REG_MEM` por um valor que o jogo escreve no fim do trabalho de CPU), e o jogo espera
  27,8 ms nos dois `GX2DrawDone` até a LatteThread esvaziar a fila. O núcleo 1 passa ~37 % no
  laço ocioso (`__OSThreadCoreIdle`, `alarm_update`, `AXOut_update`), e só ~45 % é JIT. O
  trabalho real do jogo (~48 ms) e o da LatteThread (~31 ms) se somam.
  - O submit, que é 19,8 % da LatteThread (inclusivo), cai justamente quando ela entra na espera
    da fence. Por isso cortar custo ali não dá FPS.
  - O que conta é o processamento das draws (`draw_execute`, 38 %): `vkCmdDrawIndexed` (4,8 % da
    thread), `vkCmdBindDescriptorSets` (3,3 %), `LatteBufferCache_Sync` (4 %) e
    `draw_setRenderPass` (4,6 %).
  - A escrita das readbacks na memória do guest é 4,9 % (`memcpy` de ~52 readbacks/s; o buffer
    já é `HOST_CACHED`).
  - Perfil feito com `simpleperf record -t <tid> --call-graph dwarf` com root no RP5. O
    unwinding por frame pointer perdia 45 % do tempo do driver.

## Pendências e limitações

- O RG Cube foi perfilado sem root (só espaço de usuário): o tempo de kernel e o custo das
  interceptações não aparecem.
- Nos perfis anteriores ao `c1211d77` os símbolos internos do `libCemuAndroid.so` não foram
  resolvidos (o cache de binários pegou a biblioteca sem `.symtab`). Isso deixou 1–18 % de "C++
  sem símbolo" em algumas threads. O perfil do Bayonetta 2 no RG Cube foi refeito com símbolos e
  confirma a leitura: decodificação de textura (`optimizedDecodeLoop_*`, 12 %) e
  `GX2TilingAperature_RetileTexture` (5,5 %) no núcleo principal.
- O "GPU ocupada" é o tempo entre timestamps do início e do fim de cada command buffer, mesclando
  intervalos sobrepostos. Mostra quanto a fila tem trabalho, não a ocupação das unidades da GPU.
- As estimativas de impacto da seção 5 precisam ser confirmadas item a item, medindo antes e
  depois.
