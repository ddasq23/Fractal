# Ideias futuras (opcionais)

## Frame generation com Lossless Scaling (Cemu Plus)

- Origem: Cemu Plus (danielcamilo1), branch `feature/lossless-framegen` e
  commits relacionados no `android-port` deles.
- Depende de `Lossless.dll`, arquivo proprietário que o próprio usuário
  fornece. O Fractal nunca distribui nem baixa esse arquivo.
- Não trazido na Fase 0. Reavaliar depois da Fase 4 (análise do core), já que
  mexe no overlay/painel do GamePad e no fluxo de apresentação de frames.
- O header do painel do GamePad (ícone e nome do jogo, commit `5a189b9c`) e a
  toolbar do overlay vêm junto com essa feature no Cemu Plus.
