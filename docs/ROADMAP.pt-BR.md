<p align="center">
  <a href="ROADMAP.md">English</a> · <b>Português (Brasil)</b> · <a href="ROADMAP.es.md">Español</a>
</p>

# Roadmap do Fractal

Situação da **v0.1-beta** (novembro de 2026). Cada correção em *Feito* aponta para o commit que a fez, e os números vêm
das medições escritas nesses commits.

## De onde o Fractal vem

- **Base:** o port de Android do Cemu feito pelo SSimco (`android-port`, ponto do fork `8759323e`, 2026-04-27).
- **Cemu oficial incorporado:** os 220 commits do `main` do `cemu-project/Cemu` até `3b103161` (2026-11-04), no `7c02ce75`.
- **Trabalho do Fractal:** cerca de 160 commits desde 2026-11-08: interface, drivers, entrada, renderizador,
  recompilador, correções de jogos e ferramentas de depuração.

Aparelhos de teste: **Retroid Pocket 5** (Snapdragon 865, Adreno 650), **Anbernic RG Cube** (Unisoc T820, Mali-G57 MC4),
**Xiaomi Mi A3** (Snapdragon 665, Adreno 610), e uma build de PC do mesmo código usada para comparar com o Cemu oficial.

---

## Feito

### Correções de jogos no núcleo compartilhado do emulador

Esses bugs estão em código que o Fractal divide com a versão de PC do Cemu, então não são exclusivos do Android.

| Jogo | O que estava errado | Correção | Commit |
|---|---|---|---|
| The Amazing Spider-Man | Nenhuma imagem em momento algum ("no display" na wiki de compatibilidade do Cemu). O jogo copia o quadro pronto para o scan buffer com `GX2CopySurface` em vez de `GX2CopyColorBufferToScanBuffer` | Cópias para as faixas do scan buffer da TV/GamePad são apresentadas. Jogável na build de PC do Fractal (abertura, título, capítulo 1) | [`79f2f691`](https://github.com/dfdx047/Fractal/commit/79f2f691) |
| Watch Dogs | Fechamentos aleatórios na abertura: o alocador do jogo precisa de uma área alinhada a 128 KiB e recebia alinhamento de 16 KiB. O Cemu oficial tem o mesmo problema (64 KiB no Windows) | Memória do console reservada num endereço do host alinhado a 4 GB | [`cb5321e6`](https://github.com/dfdx047/Fractal/commit/cb5321e6) |
| Watch Dogs | Fechamento na abertura: uma thread consulta um gerenciador e usa outro que a thread principal cria microssegundos depois | Patch no jogo: a thread espera o gerenciador certo | [`d316661a`](https://github.com/dfdx047/Fractal/commit/d316661a) |
| Watch Dogs | Fechamento durante o jogo: o jogo roda tarefas de visibilidade na GPU com transform feedback e lê os resultados na CPU; o Cemu só os guardava na GPU | Resultados de streamout copiados de volta para a memória do console antes que a CPU possa vê-los; origens do offset de streamout seguem o pacote | [`8b69778e`](https://github.com/dfdx047/Fractal/commit/8b69778e) |
| Watch Dogs | Fechamento durante o jogo: draws indexados com streamout gravavam as saídas na posição do valor do índice, não na ordem do index buffer | Draws indexados com streamout desenhados a partir dos vértices reunidos na ordem do índice | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Watch Dogs | Imagem congelada a partir do prólogo (tabuleiro, legendas empilhadas, tutorial "Shoot Maurice" parado): os rects do upscale final e do FXAA eram descartados | Sem descarte de faces em primitivas rect | [`6d80adae`](https://github.com/dfdx047/Fractal/commit/6d80adae) |
| Watch Dogs | Congelamento de 38-68 s ao entrar no jogo; shaders de streamout compilados de novo para cada pixel shader ligado | Vertex shaders com rasterização descartada não dependem do pixel shader; shaders grandes em cache pelo código-fonte. Agora 9 s | [`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b), [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Watch Dogs | Congelamento depois de sessões longas (por volta de 1000 s no RP5, 1250 s no PC): o jogo confere que a GPU está parada numa espera por uns 10 µs e então preenche o próximo trecho da sua lista de tarefas de GPU. O processador de comandos já tinha passado da espera e rodado o trecho vazio, então uma tarefa de visibilidade nunca terminava. O Cemu oficial tem a mesma corrida | O processador de comandos confere a espera de novo logo antes do primeiro efeito; barreiras release/acquire nas display lists. 3000 s sem congelar no PC e no RP5 | [`210b613e`](https://github.com/dfdx047/Fractal/commit/210b613e) |
| Injustice: Gods Among Us | Travamento da GPU: laços de shader sem break viravam laços infinitos | Constantes de laço emuladas (`IT_SET_LOOP_CONST`, variáveis de laço nos uniforms) | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Injustice: Gods Among Us | Lutadores esticados (skinning na GPU via streamout) | Offsets de streamout tirados do pacote, contados em DWORDs, sem o vértice base do draw | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Need for Speed: Most Wanted U | Estalos e chiados no som do motor ao acelerar | `AXDecodeAdpcmData` implementado (faltava), histórico do biquad em precisão total, contexto de laço do ADPCM, estouro no filtro passa-baixa | [`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b) |
| Todos os jogos | Pipelines pré-compilados na abertura eram montados com o estado da GPU daquele momento, então o cache do driver nunca batia e eles eram compilados de novo durante o jogo. O Cemu oficial tem o mesmo bug | Estado do pipeline montado a partir dos registradores passados ao compilador | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Todos os jogos | Cache de buffers: um uniform buffer com registrador de tamanho `0xFFFFFFFF` virava um nó de tamanho zero que corrompia a árvore de faixas | Tamanho verificado | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |

Também em código compartilhado: o recompilador agora trata `psq_lx`/`psq_stx` e as formas com update, que o Cemu
oficial manda para o interpretador ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b); no The Amazing
Spider-Man 2 são os conversores de amostras de áudio que deixavam um núcleo perto de 100 % durante o carregamento).

**Assassin's Creed IV: Black Flag:** nos nossos testes a versão oficial 2.6 do Cemu (AppImage) parou em "loading..." sem
uso de CPU, enquanto a build de PC do Fractal e o Retroid Pocket 5 rodam a cutscene de abertura e o convés do navio.
Ainda não identificamos qual mudança faz a diferença.

### Correções de jogos para Android, ARM64 e drivers de GPU de celular

| Jogo | O que estava errado | Correção | Commit |
|---|---|---|---|
| Ninja Gaiden 3: Razor's Edge | Tela preta depois de alguns minutos no Android: uma corrida no código de event flags do próprio jogo deixava duas threads esperando para sempre | O "set" de event flag do jogo não reseta mais um evento que a outra thread acabou de sinalizar | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Ninja Gaiden 3: Razor's Edge | Fechamento: funções com mais de 1 MB de código ARM64 deixavam desvios fora de alcance | Código frio emitido entre segmentos; um erro de codificação deixa a função interpretada em vez de abortar | [`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf) |
| Ninja Gaiden 3: Razor's Edge | Turnip: imagem toda borrada pela profundidade de campo lendo um depth buffer desatualizado | Barreira no fim de cada render pass no Turnip | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Ninja Gaiden 3: Razor's Edge | Fechamento no próprio alocador com o zlib-ng como zlib do host | Estado do zlib alocado no heap padrão | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Tekken Tag Tournament 2 | O driver do sistema da Qualcomm fechava ao compilar um shader de gather 4x4 na primeira luta | Escritas indexadas em GPR geradas como switch em vez de um array de GPRs com índice dinâmico | [`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d) |
| Assassin's Creed IV: Black Flag | Driver do sistema da Qualcomm: blocos verdes/magenta/azuis sobre partículas, telas de carregamento e cutscenes. O bloom amostra a textura em que desenha, o que é indefinido sem `VK_EXT_attachment_feedback_loop_layout` | Esses draws amostram uma cópia da textura | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Watch Dogs | Estádio e bloom brancos na Adreno: a Adreno devolve NaN em min/max/clamp quando um operando é NaN | min/max/clamp seguros contra NaN ligados por padrão nas GPUs Qualcomm | [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223) |
| Xenoblade Chronicles X | Turnip: travamento da GPU uns 36 s depois de começar um jogo novo, sempre | Imagens `R8G8B8A8_UNORM` continuam mutáveis (sem UBWC) no Turnip | [`2a72a16b`](https://github.com/dfdx047/Fractal/commit/2a72a16b) |
| Xenoblade Chronicles X | Adreno 650: o envio de command buffers falhava depois de ~160 s (cópias entre imagens comprimidas de formatos diferentes) | Essas cópias passam por um buffer intermediário | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Bayonetta 2 | Fechava 3 s depois de abrir (RG Cube), travava na tela de abertura (RP5): o backend ARM64 nunca carregava o índice do núcleo (`mfspr UPIR`) | SPRs acima de 998 carregados pelo nome | [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b) |
| Pokkén Tournament | Fechava sempre ao sair de um menu: armazenamento local de thread lido numa thread do host e usado em outra (TLS dinâmico no Android) | TLS do escalonador e das fibras lido na thread que roda o código. Chega às batalhas a 60 fps no RP5 | [`8ea62197`](https://github.com/dfdx047/Fractal/commit/8ea62197) |
| Need for Speed: Most Wanted U | Fechava ao entrar na cidade: descriptor sets esgotados (mais de 160 mil combinações de textura/sampler) | Pools de descritores crescem conforme a necessidade, sets sem uso há 600 quadros são liberados | [`1feba3d6`](https://github.com/dfdx047/Fractal/commit/1feba3d6) |
| Need for Speed: Most Wanted U | Folhagem e luzes brancas, estouradas | O graphic pack "Bloom Fix" da comunidade vem ligado por padrão (causa raiz ainda em aberto) | [`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86) |
| The Legend of Zelda: Breath of the Wild | Turnip: travamento da GPU na abertura com renderização em blocos | O Turnip renderiza na memória do sistema por padrão (opção por jogo) | [`525a6907`](https://github.com/dfdx047/Fractal/commit/525a6907), [`7f8d5a0f`](https://github.com/dfdx047/Fractal/commit/7f8d5a0f) |
| The Legend of Zelda: Breath of the Wild | A thread de renderização esperava 15,6 ms por quadro numa sincronização completa a cada `GX2DrawDone` | Opção por jogo, desligada nos perfis padrão do BotW: RP5 9,4 → 11,1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| Star Fox Zero | SIGBUS em atômicos de 64 bits desalinhados no ARM64 | Lock para atômicos desalinhados (do Cemu Plus) | [`c35755ec`](https://github.com/dfdx047/Fractal/commit/c35755ec) |
| Mario Kart 8 | Fechamento (estado do mixer de som) | Reset do `snd_user` trazido do Cemu oficial antes do merge completo | [`c62e6977`](https://github.com/dfdx047/Fractal/commit/c62e6977) |
| Jogos com diálogo de erro | Ficavam presos nos portáteis: os botões do diálogo de erro não recebiam foco sem mouse | A e B fecham o diálogo | [`5879e1f5`](https://github.com/dfdx047/Fractal/commit/5879e1f5) |
| Todos os jogos | Configurações e lista de jogos viravam zeros após reiniciar logo depois de salvar | Arquivos sincronizados com o armazenamento antes de substituir os antigos | [`9db92543`](https://github.com/dfdx047/Fractal/commit/9db92543) |

### Renderizador (Vulkan) para GPUs de celular por blocos

Adreno e Mali gravam e recarregam todos os attachments quando um render pass termina, então a maior parte deste
trabalho evita quebrar passes.

- Envio de buffers e occlusion queries não terminam mais o render pass; o cache de buffers fica em memória visível ao
  host nas GPUs de memória unificada ([`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac)).
- Trabalho de vértices continua sobreposto às barreiras de render target; dependências do próprio attachment
  rastreadas por attachment ([`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f),
  [`d9606be8`](https://github.com/dfdx047/Fractal/commit/d9606be8)).
- Nós ocupados do cache de buffers vão para memória nova em vez de terminar o pass
  ([`ec46fba8`](https://github.com/dfdx047/Fractal/commit/ec46fba8), [`a74fbb56`](https://github.com/dfdx047/Fractal/commit/a74fbb56)).
- Envio de texturas num command buffer de prólogo em vez de terminar o pass
  ([`2eeceaea`](https://github.com/dfdx047/Fractal/commit/2eeceaea)).
- Compressão de framebuffer (UBWC na Adreno, AFBC na Mali) mantida nas imagens de cor
  ([`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef)).
- Turnip: `vkGetFenceStatus` bloqueia no KGSL, então os command buffers terminados são achados por um marcador de
  conclusão ([`65047547`](https://github.com/dfdx047/Fractal/commit/65047547)).
- Limpeza de depth por render pass em vez de `vkCmdClearDepthStencilImage`, que custa ~3,5 ms por limpeza em 1080p no
  driver da Adreno 610 ([`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76)).
- Um cache de pipelines por driver, mantido ao trocar de driver ([`2e43b0b7`](https://github.com/dfdx047/Fractal/commit/2e43b0b7)).
- Pipelines compilados em 1-2 threads no Android em vez de até 7, o que reduziu os engasgos com cache frio
  ([`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72)).
- Swapchain escolhe um composite alpha suportado (Mesa PanVK) ([`c1f4cd8e`](https://github.com/dfdx047/Fractal/commit/c1f4cd8e)).

### Recompilador PowerPC → ARM64

- Contador de ciclos num registrador, funções juntas numa área de código, saltos e imediatos mais curtos, desvios por
  flags, acesso à memória com offset zero sem add ([`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251)).
- `mfspr UPIR` ([`d61aee38`](https://github.com/dfdx047/Fractal/commit/d61aee38), [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b)),
  `ps_nabs`, `dcbt` ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)), `psq_lx`/`psq_stx`/`psq_lux`/`psq_stux`,
  `eieio`, `dcbi` ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b)) não caem mais no interpretador.
- Funções enormes ficam dentro do alcance de desvio do ARM64 ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)).
- Flush-to-zero realmente ligado no Android ([`b385304f`](https://github.com/dfdx047/Fractal/commit/b385304f)).

### Decompilador de shaders

- Constantes de laço ([`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5)).
- Escritas indexadas em GPR como switch ([`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d)).
- Regras de NaN do R600 para min/max/clamp na Adreno ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Vertex shaders de streamout independentes do pixel shader ligado, cache de SPIR-V pelo código-fonte para shaders
  grandes ([`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b)).

### Sistema e áudio

- Correções de áudio do AX ([`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b)).
- O núcleo principal e a thread da GPU dormem em vez de girar quando ociosos; o núcleo principal dorme com o jogo
  pausado ([`ff38f5b4`](https://github.com/dfdx047/Fractal/commit/ff38f5b4), [`bf0944a4`](https://github.com/dfdx047/Fractal/commit/bf0944a4)).
- Leitura dos argumentos do `GX2Init` ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).

### Interface

- Cinco temas, design tokens, fontes incluídas, superfícies de vidro, papéis de parede estáticos e animados
  ([`73fd0c96`](https://github.com/dfdx047/Fractal/commit/73fd0c96)); animação de abertura por tema
  ([`4489bc72`](https://github.com/dfdx047/Fractal/commit/4489bc72)); ícone do Sierpinski
  ([`a59f82f8`](https://github.com/dfdx047/Fractal/commit/a59f82f8)).
- Biblioteca em grade, carrossel e lista, configuração inicial, tela de aparência
  ([`80712467`](https://github.com/dfdx047/Fractal/commit/80712467)); tempo de jogo, última vez jogado, ordenação,
  exportar/importar save e cache de shaders, tela por jogo ([`ecf40b3b`](https://github.com/dfdx047/Fractal/commit/ecf40b3b)).
- Opções de Resolução e FPS detectadas nos graphic packs de qualquer jogo
  ([`d95f0191`](https://github.com/dfdx047/Fractal/commit/d95f0191), [`9a359b76`](https://github.com/dfdx047/Fractal/commit/9a359b76)).
- Configurações agrupadas em seções, telas antigas no estilo novo
  ([`b7428b86`](https://github.com/dfdx047/Fractal/commit/b7428b86), [`383af9d2`](https://github.com/dfdx047/Fractal/commit/383af9d2)).
- Navegação por controle e legenda de botões compacta em todas as telas (`4008a538` a `572b8a9e`), foco restaurado ao
  voltar (`eb7e614e` a `7dc5303c`), layout para celular na vertical ([`94e60896`](https://github.com/dfdx047/Fractal/commit/94e60896)).
- Menu lateral no jogo seguindo o tema ([`d6bba2e5`](https://github.com/dfdx047/Fractal/commit/d6bba2e5)).
- English, Português (Brasil) e Español em todos os textos do app
  ([`c9d8a940`](https://github.com/dfdx047/Fractal/commit/c9d8a940), [`a74e2a35`](https://github.com/dfdx047/Fractal/commit/a74e2a35)).

### Drivers de GPU

- Catálogo de drivers a partir dos releases do GitHub, instalação com um toque, limpeza de cache por driver
  ([`3927b698`](https://github.com/dfdx047/Fractal/commit/3927b698)); drivers experimentais para Mali, mais fontes, volta
  automática ao driver do sistema ([`7ce49626`](https://github.com/dfdx047/Fractal/commit/7ce49626),
  [`9e8cf0c3`](https://github.com/dfdx047/Fractal/commit/9e8cf0c3)); página por desenvolvedor com o Turnip do MrPurple666
  primeiro, drivers e graphic packs na configuração inicial ([`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86)).

### Entrada

- Controles na tela: layouts independentes da resolução, perfis, macros, turbo, alternância, deslizar, modo de edição,
  cores do tema, estilos e botões de atalho ([`c813042e`](https://github.com/dfdx047/Fractal/commit/c813042e),
  [`9c15535f`](https://github.com/dfdx047/Fractal/commit/9c15535f), [`5af1e981`](https://github.com/dfdx047/Fractal/commit/5af1e981)).
- Controles físicos mapeados automaticamente ([`841f97a3`](https://github.com/dfdx047/Fractal/commit/841f97a3),
  [`e1698c63`](https://github.com/dfdx047/Fractal/commit/e1698c63)); HOME no botão Mode/Guide
  ([`b343cab3`](https://github.com/dfdx047/Fractal/commit/b343cab3)); atalhos ([`571c9d87`](https://github.com/dfdx047/Fractal/commit/571c9d87)).
- Toques rápidos nos botões da tela não se perdem mais ([`8929b786`](https://github.com/dfdx047/Fractal/commit/8929b786)).

### Upscaling

- AMD FSR 1 e Snapdragon GSR, nitidez RCAS (do Cemu Plus)
  ([`2c5dba03`](https://github.com/dfdx047/Fractal/commit/2c5dba03), [`8328081a`](https://github.com/dfdx047/Fractal/commit/8328081a)).

### Depuração

- Relatório de depuração: registro de desempenho leve em toda sessão, `events.txt` com cada problema encontrado,
  vigia de travamento, notas de crash dizendo o que a thread fazia, log anterior guardado e **Compartilhar relatório de
  depuração** no app ([`be081807`](https://github.com/dfdx047/Fractal/commit/be081807)).
- Logs de crash e de travamento com código do console, registradores do host e pilhas das threads
  ([`c7923402`](https://github.com/dfdx047/Fractal/commit/c7923402), [`fcb9863e`](https://github.com/dfdx047/Fractal/commit/fcb9863e),
  [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Registro de desempenho, cenas de benchmark com roteiro e ferramentas de profiling (`tools/perf`).

### Ganhos medidos

Das medições A/B nas mensagens de commit (mesma cena, mesmo aparelho, cache de shaders quente). As condições mudam
entre commits (driver, perfil de CPU), então compare cada linha só com ela mesma.

| Mudança | Aparelho | Antes → depois | Commit |
|---|---|---|---|
| Sem fim de render pass para envio de buffers / occlusion queries | RG Cube, Mario Kart 8 | 16,0 → 23,0 fps | [`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac) |
| Trabalho de vértices sobreposto às barreiras de render target | RG Cube, Mario Kart 8 | 24,3 → 30,2 fps | [`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f) |
| Compressão de framebuffer mantida | RP5, Super Mario 3D World | 57,0 → 59,8 fps, 1 % low 30 → 44 | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Recompilador: contador de ciclos em registrador, área de código | RP5, Mario Kart 8 | 47,8 → 50,0-50,4 fps | [`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251) |
| Limpeza de depth por render pass | Mi A3, Shovel Knight | 34 → 52 fps | [`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76) |
| Sincronização do `GX2DrawDone` por jogo | RP5, Breath of the Wild | 9,4 → 11,1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| 1-2 threads de compilação de pipelines | Mi A3, SM3DW, cache frio | 17,0 s → 7,5 s em quadros acima de 100 ms | [`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72) |
| Pipelines pré-compilados batem com o cache do driver | Watch Dogs | congelamento de 38-68 s → 9 s ao entrar no jogo | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |

---

## Em andamento

- **Watch Dogs no Turnip:** uma falha de página da GPU numa escrita de streamout pode travar a GPU depois de uns 25
  minutos. O driver do sistema não é afetado.
- **Watch Dogs superexposto no Android:** a partir da sala do Maurice a exposição automática pode travar clara demais em
  cerca de metade das sessões, no Turnip e no driver do sistema; a build de PC não é afetada. Já descartados: NaN/Inf nos
  pixel shaders, draws pulados durante a compilação assíncrona de pipelines e readback de texturas.
- **Watch Dogs na Mali** (RG Cube): a thread de renderização fica presa compilando os shaders de streamout enormes; menu
  em blocos e abertura preta.
- **Assassin's Creed IV:** comparando as cutscenes com a build de PC; as espadas compridas ficam penduradas retas do
  mesmo jeito no PC e no RP5, ainda não confirmado como bug.
- **Ninja Gaiden 3 no Turnip:** cabeças escuras flutuando e céu estourado continuam depois da correção da profundidade
  de campo.
- **Need for Speed: Most Wanted U:** achar a causa raiz da folhagem branca (o pack da comunidade esconde o problema).
- **Xenoblade Chronicles X no driver do sistema da Qualcomm:** personagens como silhuetas brancas (bug do driver); por
  enquanto use o Turnip.
- **Mali em geral:** o RG Cube, mantido no modo econômico de CPU, é a referência para aparelhos Mali mais fracos.

## Planejado

- Testes de compatibilidade nos principais jogos de Wii U e uma lista pública de compatibilidade.
- Um botão "marcar problema" dentro do jogo que salva uma captura e uma marca no log para os relatos de bug.
- Streamout em drivers Mali sem stores no pipeline de vértices (por exemplo com um passe de compute).
- Recursos de GPU que faltam, achados nos testes: compute draw calls, alpha-to-mask (`GX2SetAlphaToMaskReg`),
  `GX2ExpandDepthBuffer` e `SQ_VTX_SEMANTIC_CLEAR`, que nunca é aplicado.
- Mais desempenho no lado da CPU: Breath of the Wild e Mario Kart 8 no RP5 são limitados pelo núcleo PowerPC principal e
  pela thread de renderização, não pela GPU.
- Continuar acompanhando o `main` do Cemu oficial.
