# Achados na Mali (para a análise da Fase 4)

Aparelho de teste: Anbernic RG Cube — Unisoc T820, Mali-G57 MC4, Android 13,
tela 720x720. Driver do sistema (Mali proprietário), salvo indicação.

## 1. Transform feedback (streamout)

**O que acontece.** O Wii U usa streamout em vários jogos. No Vulkan o Cemu
tem dois caminhos:

- `VK_EXT_transform_feedback` (blocos `XfbBlock` no vertex shader);
- emulação por SSBO (o vertex shader grava num storage buffer), que exige
  `vertexPipelineStoresAndAtomics`.

O driver Mali do sistema (r40p0) tem a extensão, mas não tem
`vertexPipelineStoresAndAtomics`, então o Cemu usa o caminho `XfbBlock`
(`VulkanRenderer.cpp`: `useTFEmulationViaSSBO` vira false quando falta
`vertexPipelineStoresAndAtomics`). FristOneRR e malisx2 não têm nenhum dos
dois e caem no mesmo caminho sem a extensão, o que nem seria válido.

**Efeito.** O GLSL gerado declara os blocos XFB com `location` 0, 4, … e
colide com os `dummyPassParameterSem0..31` que o gerador já declarou
(`LatteDecompilerEmitGLSLHeader.hpp`, laço "TODO: fix this"). O glslang
recusa: `'location' : overlapping use of location N`. Na Adreno esse caminho
nunca roda (ela tem `vertexPipelineStoresAndAtomics` e usa SSBO), por isso o
bug estava escondido. Na build debug isso cai
num `cemu_assert_debug` e fecha o app (SIGTRAP na thread `compilePl`); na
release o shader é descartado e o draw some.

**Visto em.** Super Mario 3D World e Mario Kart 8 (8 shaders cada, 16
pipelines descartados; 14 no MK8 com FristOneRR, que chegou à demo; hashes `ba1f37687e3ab46f`, `ef63f79bcc7e1312`,
`71d79f4932a08f1d` e variações, aux `0x204`). Bayonetta 2, XCX e BotW não
usaram streamout nos primeiros minutos.

**Para a Fase 4.**
- Corrigir as `location` dos blocos XFB: começar depois dos pass params
  ativos e pular os `dummyPassParameterSem` nesse intervalo. Isso deve
  bastar no driver do sistema (que tem a extensão).
- Não emitir `XfbBlock` quando o aparelho não tem a extensão.
- Avaliar alternativas sem stores no vertex shader: gravar o streamout num
  passe de compute a partir do vertex buffer, ou rodar o vertex shader de
  streamout como compute.
- PanVK varia por build: o zenithblue expõe `vertexPipelineStoresAndAtomics`
  (e usaria o caminho por SSBO, como a Adreno), o FristOneRR não.

## 2. Geometry shader

O driver do sistema e o PanVK zenithblue têm `geometryShader`; FristOneRR e
malisx2 não. Nos primeiros minutos dos 5 jogos nenhum pipeline falhou por
geometry shader (a ver em jogo, não só na tela de título).

## Drivers testados (RG Cube, build release)

| Driver | Carrega | geometryShader | transform feedback | vertexPipelineStores | Resultado |
|---|---|---|---|---|---|
| Sistema, Mali r40p0 | — | sim | sim | não | os 5 jogos chegam ao título |
| zenithblue PanVK 26.3.1 | sim | sim | sim | sim | `vkCreateSwapchainKHR` falha com `VK_ERROR_INVALID_EXTERNAL_HANDLE` (não importa os buffers do gralloc da Unisoc); volta automática ok |
| FristOneRR 3.7.0 | sim | não | não | não | os 5 jogos chegam ao título; MK8 chega à demo (14 shaders de streamout descartados) |
| malisx2 0.4.0 (r44p1) | sim | não | não | não | SM3DW e Bayonetta chegam ao título |

O PanVK zenithblue só reporta `compositeAlpha` INHERIT; o Cemu pedia OPAQUE
fixo (corrigido em `SwapchainInfoVk.cpp`), mas o erro de buffer externo
continua.

## 3. Outros

- Sem formatos BC1–BC5 (`VK_FORMAT_BC*_BLOCK not supported`): o Cemu
  descomprime na CPU, custo extra de CPU e memória.
- Sem `VK_EXT_pipeline_robustness`: cai em `robustBufferAccess`.
- Sem modo de apresentação imediato (só FIFO/mailbox).

## Observação sobre a build de teste

A build debug do APK compila o código nativo sem otimização (`-O0`) e com
`CEMU_DEBUG_ASSERT`. Testes de compatibilidade e desempenho na Mali usam a
build release.
