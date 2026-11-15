<p align="center">
  <a href="ROADMAP.md">English</a> · <a href="ROADMAP.pt-BR.md">Português (Brasil)</a> · <b>Español</b>
</p>

# Roadmap de Fractal

Estado de la **v0.1-beta** (noviembre de 2026). Cada corrección en *Hecho* enlaza al commit que la hizo, y las cifras
salen de las mediciones escritas en esos commits.

## De dónde viene Fractal

- **Base:** el port de Android de Cemu hecho por SSimco (`android-port`, punto del fork `8759323e`, 2026-04-27).
- **Cemu oficial incorporado:** los 220 commits de `main` de `cemu-project/Cemu` hasta `3b103161` (2026-11-04), en `7c02ce75`.
- **Trabajo de Fractal:** unos 160 commits desde 2026-11-08: interfaz, drivers, entrada, renderizador, recompilador,
  correcciones de juegos y herramientas de depuración.

Dispositivos de prueba: **Retroid Pocket 5** (Snapdragon 865, Adreno 650), **Anbernic RG Cube** (Unisoc T820, Mali-G57
MC4), **Xiaomi Mi A3** (Snapdragon 665, Adreno 610), y una build de PC del mismo código usada para comparar con el Cemu
oficial.

---

## Hecho

### Correcciones de juegos en el núcleo compartido del emulador

Estos bugs están en código que Fractal comparte con la versión de PC de Cemu, así que no son exclusivos de Android.

| Juego | Qué fallaba | Corrección | Commit |
|---|---|---|---|
| The Amazing Spider-Man | Nunca muestra imagen ("no display" en la wiki de compatibilidad de Cemu). El juego copia el cuadro terminado al scan buffer con `GX2CopySurface` en lugar de `GX2CopyColorBufferToScanBuffer` | Las copias a los rangos del scan buffer de la TV/GamePad se presentan. Jugable en la build de PC de Fractal (intro, título, capítulo 1) | [`79f2f691`](https://github.com/dfdx047/Fractal/commit/79f2f691) |
| Watch Dogs | Cierres aleatorios al iniciar: el asignador del juego necesita un área alineada a 128 KiB y recibía alineación de 16 KiB. El Cemu oficial tiene el mismo problema (64 KiB en Windows) | Memoria de la consola reservada en una dirección del host alineada a 4 GB | [`cb5321e6`](https://github.com/dfdx047/Fractal/commit/cb5321e6) |
| Watch Dogs | Cierre al iniciar: un hilo consulta un gestor y usa otro que el hilo principal crea microsegundos después | Parche del juego: el hilo espera al gestor correcto | [`d316661a`](https://github.com/dfdx047/Fractal/commit/d316661a) |
| Watch Dogs | Cierre durante el juego: el juego ejecuta tareas de visibilidad en la GPU con transform feedback y lee los resultados en la CPU; Cemu solo los guardaba en la GPU | Resultados de streamout copiados de vuelta a la memoria de la consola antes de que la CPU pueda verlos; orígenes del offset de streamout según el paquete | [`8b69778e`](https://github.com/dfdx047/Fractal/commit/8b69778e) |
| Watch Dogs | Cierre durante el juego: los draws indexados con streamout escribían las salidas en la posición del valor del índice, no en el orden del index buffer | Draws indexados con streamout dibujados a partir de los vértices reunidos en orden del índice | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Watch Dogs | Imagen congelada desde el prólogo (tablero, subtítulos apilados, tutorial "Shoot Maurice" detenido): se descartaban los rects del escalado final y del FXAA | Sin descarte de caras en primitivas rect | [`6d80adae`](https://github.com/dfdx047/Fractal/commit/6d80adae) |
| Watch Dogs | Congelamiento de 38-68 s al entrar al juego; shaders de streamout compilados de nuevo por cada pixel shader enlazado | Los vertex shaders con rasterización descartada no dependen del pixel shader; shaders grandes en caché por su código fuente. Ahora 9 s | [`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b), [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Watch Dogs | Congelamiento tras sesiones largas (alrededor de 1000 s en la RP5, 1250 s en PC): el juego comprueba que la GPU está detenida en una espera durante unos 10 µs y entonces llena el siguiente tramo de su lista de tareas de GPU. El procesador de comandos ya había pasado la espera y ejecutado el tramo vacío, así que una tarea de visibilidad nunca terminaba. El Cemu oficial tiene la misma carrera | El procesador de comandos vuelve a comprobar la espera justo antes de su primer efecto; barreras release/acquire en las display lists. 3000 s sin congelarse en PC y en la RP5 | [`210b613e`](https://github.com/dfdx047/Fractal/commit/210b613e) |
| Injustice: Gods Among Us | Cuelgue de la GPU: bucles de shader sin break se volvían infinitos | Constantes de bucle emuladas (`IT_SET_LOOP_CONST`, variables de bucle en los uniforms) | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Injustice: Gods Among Us | Luchadores estirados (skinning en la GPU vía streamout) | Offsets de streamout tomados del paquete, contados en DWORDs, sin el vértice base del draw | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |
| Need for Speed: Most Wanted U | Chasquidos y crujidos en el sonido del motor al acelerar | `AXDecodeAdpcmData` implementado (faltaba), historial del biquad con precisión completa, contexto de bucle de ADPCM, desbordamiento del filtro paso bajo | [`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b) |
| Todos los juegos | Los pipelines precompilados al iniciar se armaban con el estado de la GPU de ese momento, así que la caché del driver nunca coincidía y se compilaban de nuevo durante el juego. El Cemu oficial tiene el mismo bug | Estado del pipeline armado a partir de los registros pasados al compilador | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |
| Todos los juegos | Caché de buffers: un uniform buffer con registro de tamaño `0xFFFFFFFF` se volvía un nodo de tamaño cero que corrompía el árbol de rangos | Tamaño comprobado | [`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5) |

También en código compartido: el recompilador ahora maneja `psq_lx`/`psq_stx` y sus formas con update, que el Cemu
oficial envía al intérprete ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b); en The Amazing Spider-Man 2
son los conversores de muestras de audio que tenían un núcleo cerca del 100 % durante la carga).

**Assassin's Creed IV: Black Flag:** en nuestras pruebas la versión oficial 2.6 de Cemu (AppImage) se quedó en
"loading..." sin uso de CPU, mientras que la build de PC de Fractal y la Retroid Pocket 5 ejecutan la cinemática inicial
y la cubierta del barco. Todavía no identificamos qué cambio marca la diferencia.

### Correcciones de juegos para Android, ARM64 y drivers de GPU móviles

| Juego | Qué fallaba | Corrección | Commit |
|---|---|---|---|
| Ninja Gaiden 3: Razor's Edge | Pantalla negra tras unos minutos en Android: una carrera en el código de event flags del propio juego dejaba dos hilos esperando para siempre | El "set" de event flag del juego ya no reinicia un evento que el otro hilo acaba de señalar | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Ninja Gaiden 3: Razor's Edge | Cierre: funciones de más de 1 MB de código ARM64 dejaban saltos fuera de alcance | Código frío emitido entre segmentos; un error de codificación deja la función interpretada en lugar de abortar | [`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf) |
| Ninja Gaiden 3: Razor's Edge | Turnip: toda la imagen borrosa por la profundidad de campo leyendo un depth buffer desactualizado | Barrera al final de cada render pass en Turnip | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Ninja Gaiden 3: Razor's Edge | Cierre en su propio asignador con zlib-ng como zlib del host | Estado de zlib asignado en el heap por defecto | [`8eaae97a`](https://github.com/dfdx047/Fractal/commit/8eaae97a) |
| Tekken Tag Tournament 2 | El driver del sistema de Qualcomm se cerraba al compilar un shader de gather 4x4 en la primera pelea | Escrituras indexadas en GPR generadas como switch en lugar de un array de GPRs con índice dinámico | [`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d) |
| Assassin's Creed IV: Black Flag | Driver del sistema de Qualcomm: bloques verdes/magenta/azules sobre partículas, pantallas de carga y cinemáticas. El bloom muestrea la textura en la que dibuja, algo indefinido sin `VK_EXT_attachment_feedback_loop_layout` | Esos draws muestrean una copia de la textura | [`64c4b75c`](https://github.com/dfdx047/Fractal/commit/64c4b75c) |
| Watch Dogs | Estadio y bloom blancos en Adreno: Adreno devuelve NaN en min/max/clamp cuando un operando es NaN | min/max/clamp seguros frente a NaN activados por defecto en GPUs Qualcomm | [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223) |
| Xenoblade Chronicles X | Turnip: cuelgue de la GPU unos 36 s después de empezar una partida nueva, siempre | Las imágenes `R8G8B8A8_UNORM` siguen siendo mutables (sin UBWC) en Turnip | [`2a72a16b`](https://github.com/dfdx047/Fractal/commit/2a72a16b) |
| Xenoblade Chronicles X | Adreno 650: el envío de command buffers fallaba tras ~160 s (copias entre imágenes comprimidas de formatos distintos) | Esas copias pasan por un buffer intermedio | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Bayonetta 2 | Cierre 3 s después de iniciar (RG Cube), cuelgue en la pantalla de inicio (RP5): el backend ARM64 nunca cargaba el índice del núcleo (`mfspr UPIR`) | SPRs por encima de 998 cargados por nombre | [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b) |
| Pokkén Tournament | Se cerraba cada vez que salía de un menú: almacenamiento local de hilo leído en un hilo del host y usado en otro (TLS dinámico en Android) | TLS del planificador y de las fibras leído en el hilo que ejecuta el código. Llega a los combates a 60 fps en la RP5 | [`8ea62197`](https://github.com/dfdx047/Fractal/commit/8ea62197) |
| Need for Speed: Most Wanted U | Cierre al entrar en la ciudad: descriptor sets agotados (más de 160 mil combinaciones de textura/sampler) | Los pools de descriptores crecen según se necesite, los sets sin uso durante 600 cuadros se liberan | [`1feba3d6`](https://github.com/dfdx047/Fractal/commit/1feba3d6) |
| Need for Speed: Most Wanted U | Follaje y luces blancos, quemados | El paquete gráfico "Bloom Fix" de la comunidad viene activado por defecto (causa raíz aún abierta) | [`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86) |
| The Legend of Zelda: Breath of the Wild | Turnip: cuelgue de la GPU al iniciar con renderizado por bloques | Turnip renderiza en la memoria del sistema por defecto (opción por juego) | [`525a6907`](https://github.com/dfdx047/Fractal/commit/525a6907), [`7f8d5a0f`](https://github.com/dfdx047/Fractal/commit/7f8d5a0f) |
| The Legend of Zelda: Breath of the Wild | El hilo de renderizado esperaba 15,6 ms por cuadro en una sincronización completa en cada `GX2DrawDone` | Opción por juego, desactivada en los perfiles por defecto de BotW: RP5 9,4 → 11,1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| Star Fox Zero | SIGBUS en atómicos de 64 bits desalineados en ARM64 | Lock para atómicos desalineados (de Cemu Plus) | [`c35755ec`](https://github.com/dfdx047/Fractal/commit/c35755ec) |
| Mario Kart 8 | Cierre (estado del mezclador de sonido) | Reinicio de `snd_user` traído del Cemu oficial antes del merge completo | [`c62e6977`](https://github.com/dfdx047/Fractal/commit/c62e6977) |
| Juegos con diálogo de error | Se quedaban atascados en las portátiles: los botones del diálogo de error no recibían foco sin ratón | A y B cierran el diálogo | [`5879e1f5`](https://github.com/dfdx047/Fractal/commit/5879e1f5) |
| Todos los juegos | La configuración y la lista de juegos quedaban en ceros tras reiniciar poco después de guardar | Los archivos se sincronizan con el almacenamiento antes de reemplazar los antiguos | [`9db92543`](https://github.com/dfdx047/Fractal/commit/9db92543) |

### Renderizador (Vulkan) para GPUs móviles por bloques

Adreno y Mali guardan y recargan todos los attachments cuando termina un render pass, así que la mayor parte de este
trabajo evita cortar passes.

- La subida de buffers y las occlusion queries ya no terminan el render pass; la caché de buffers vive en memoria
  visible para el host en GPUs de memoria unificada ([`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac)).
- El trabajo de vértices sigue solapado con las barreras de render target; dependencias del propio attachment
  rastreadas por attachment ([`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f),
  [`d9606be8`](https://github.com/dfdx047/Fractal/commit/d9606be8)).
- Los nodos ocupados de la caché de buffers se mueven a memoria nueva en lugar de terminar el pass
  ([`ec46fba8`](https://github.com/dfdx047/Fractal/commit/ec46fba8), [`a74fbb56`](https://github.com/dfdx047/Fractal/commit/a74fbb56)).
- Subida de texturas en un command buffer de prólogo en lugar de terminar el pass
  ([`2eeceaea`](https://github.com/dfdx047/Fractal/commit/2eeceaea)).
- Compresión de framebuffer (UBWC en Adreno, AFBC en Mali) activa en las imágenes de color
  ([`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef)).
- Turnip: `vkGetFenceStatus` bloquea en KGSL, así que los command buffers terminados se detectan con un marcador de
  finalización ([`65047547`](https://github.com/dfdx047/Fractal/commit/65047547)).
- Limpieza de depth con un render pass en lugar de `vkCmdClearDepthStencilImage`, que cuesta ~3,5 ms por limpieza en
  1080p en el driver de la Adreno 610 ([`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76)).
- Una caché de pipelines por driver, conservada al cambiar de driver ([`2e43b0b7`](https://github.com/dfdx047/Fractal/commit/2e43b0b7)).
- Pipelines compilados en 1-2 hilos en Android en lugar de hasta 7, lo que redujo los tirones con caché fría
  ([`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72)).
- La swapchain elige un composite alpha compatible (Mesa PanVK) ([`c1f4cd8e`](https://github.com/dfdx047/Fractal/commit/c1f4cd8e)).

### Recompilador PowerPC → ARM64

- Contador de ciclos en un registro, funciones agrupadas en un área de código, saltos e inmediatos más cortos, saltos
  por flags, acceso a memoria con offset cero sin add ([`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251)).
- `mfspr UPIR` ([`d61aee38`](https://github.com/dfdx047/Fractal/commit/d61aee38), [`613dc67b`](https://github.com/dfdx047/Fractal/commit/613dc67b)),
  `ps_nabs`, `dcbt` ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)), `psq_lx`/`psq_stx`/`psq_lux`/`psq_stux`,
  `eieio`, `dcbi` ([`9107127b`](https://github.com/dfdx047/Fractal/commit/9107127b)) ya no recurren al intérprete.
- Las funciones enormes se mantienen dentro del alcance de salto de ARM64 ([`a26051bf`](https://github.com/dfdx047/Fractal/commit/a26051bf)).
- Flush-to-zero realmente activado en Android ([`b385304f`](https://github.com/dfdx047/Fractal/commit/b385304f)).

### Decompilador de shaders

- Constantes de bucle ([`2eef8ff5`](https://github.com/dfdx047/Fractal/commit/2eef8ff5)).
- Escrituras indexadas en GPR como switch ([`1293cd7d`](https://github.com/dfdx047/Fractal/commit/1293cd7d)).
- Reglas de NaN del R600 para min/max/clamp en Adreno ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Vertex shaders de streamout independientes del pixel shader enlazado, caché de SPIR-V por código fuente para shaders
  grandes ([`9b6ee09b`](https://github.com/dfdx047/Fractal/commit/9b6ee09b)).

### Sistema y audio

- Correcciones de audio de AX ([`44cdd26b`](https://github.com/dfdx047/Fractal/commit/44cdd26b)).
- El núcleo principal y el hilo de la GPU duermen en lugar de girar cuando están ociosos; el núcleo principal duerme con
  el juego en pausa ([`ff38f5b4`](https://github.com/dfdx047/Fractal/commit/ff38f5b4), [`bf0944a4`](https://github.com/dfdx047/Fractal/commit/bf0944a4)).
- Lectura de los argumentos de `GX2Init` ([`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).

### Interfaz

- Cinco temas, design tokens, fuentes incluidas, superficies de cristal, fondos estáticos y animados
  ([`73fd0c96`](https://github.com/dfdx047/Fractal/commit/73fd0c96)); animación de inicio por tema
  ([`4489bc72`](https://github.com/dfdx047/Fractal/commit/4489bc72)); icono de Sierpinski
  ([`a59f82f8`](https://github.com/dfdx047/Fractal/commit/a59f82f8)).
- Biblioteca en cuadrícula, carrusel y lista, configuración inicial, pantalla de apariencia
  ([`80712467`](https://github.com/dfdx047/Fractal/commit/80712467)); tiempo de juego, última partida, orden,
  exportar/importar partidas y caché de shaders, pantalla por juego ([`ecf40b3b`](https://github.com/dfdx047/Fractal/commit/ecf40b3b)).
- Opciones de Resolución y FPS detectadas en los paquetes gráficos de cualquier juego
  ([`d95f0191`](https://github.com/dfdx047/Fractal/commit/d95f0191), [`9a359b76`](https://github.com/dfdx047/Fractal/commit/9a359b76)).
- Configuración agrupada en secciones, pantallas antiguas con el estilo nuevo
  ([`b7428b86`](https://github.com/dfdx047/Fractal/commit/b7428b86), [`383af9d2`](https://github.com/dfdx047/Fractal/commit/383af9d2)).
- Navegación con mando y leyenda de botones compacta en todas las pantallas (`4008a538` a `572b8a9e`), foco restaurado
  al volver (`eb7e614e` a `7dc5303c`), diseño para teléfono en vertical ([`94e60896`](https://github.com/dfdx047/Fractal/commit/94e60896)).
- Menú lateral en el juego con el tema de la app ([`d6bba2e5`](https://github.com/dfdx047/Fractal/commit/d6bba2e5)).
- English, Português (Brasil) y Español en todos los textos de la app
  ([`c9d8a940`](https://github.com/dfdx047/Fractal/commit/c9d8a940), [`a74e2a35`](https://github.com/dfdx047/Fractal/commit/a74e2a35)).

### Drivers de GPU

- Catálogo de drivers desde los releases de GitHub, instalación con un toque, limpieza de caché por driver
  ([`3927b698`](https://github.com/dfdx047/Fractal/commit/3927b698)); drivers experimentales para Mali, más fuentes,
  vuelta automática al driver del sistema ([`7ce49626`](https://github.com/dfdx047/Fractal/commit/7ce49626),
  [`9e8cf0c3`](https://github.com/dfdx047/Fractal/commit/9e8cf0c3)); página por desarrollador con el Turnip de MrPurple666
  primero, drivers y paquetes gráficos en la configuración inicial ([`0386ec86`](https://github.com/dfdx047/Fractal/commit/0386ec86)).

### Entrada

- Controles en pantalla: diseños independientes de la resolución, perfiles, macros, turbo, alternar, deslizar, modo de
  edición, colores del tema, estilos y botones de atajo ([`c813042e`](https://github.com/dfdx047/Fractal/commit/c813042e),
  [`9c15535f`](https://github.com/dfdx047/Fractal/commit/9c15535f), [`5af1e981`](https://github.com/dfdx047/Fractal/commit/5af1e981)).
- Mandos físicos asignados automáticamente ([`841f97a3`](https://github.com/dfdx047/Fractal/commit/841f97a3),
  [`e1698c63`](https://github.com/dfdx047/Fractal/commit/e1698c63)); HOME en el botón Mode/Guide
  ([`b343cab3`](https://github.com/dfdx047/Fractal/commit/b343cab3)); atajos ([`571c9d87`](https://github.com/dfdx047/Fractal/commit/571c9d87)).
- Los toques rápidos en los botones en pantalla ya no se pierden ([`8929b786`](https://github.com/dfdx047/Fractal/commit/8929b786)).

### Escalado

- AMD FSR 1 y Snapdragon GSR, nitidez RCAS (de Cemu Plus)
  ([`2c5dba03`](https://github.com/dfdx047/Fractal/commit/2c5dba03), [`8328081a`](https://github.com/dfdx047/Fractal/commit/8328081a)).

### Depuración

- Informe de depuración: registro de rendimiento ligero en cada sesión, `events.txt` con cada problema encontrado,
  vigilante de cuelgues, notas de cierre que dicen qué hacía el hilo, log anterior conservado y **Compartir informe de
  depuración** en la app ([`be081807`](https://github.com/dfdx047/Fractal/commit/be081807)).
- Logs de cierres y cuelgues con código de la consola, registros del host y pilas de los hilos
  ([`c7923402`](https://github.com/dfdx047/Fractal/commit/c7923402), [`fcb9863e`](https://github.com/dfdx047/Fractal/commit/fcb9863e),
  [`f8dbb223`](https://github.com/dfdx047/Fractal/commit/f8dbb223)).
- Registro de rendimiento, escenas de benchmark con guion y herramientas de profiling (`tools/perf`).

### Mejoras medidas

De las mediciones A/B en los mensajes de commit (misma escena, mismo dispositivo, caché de shaders caliente). Las
condiciones cambian entre commits (driver, perfil de CPU), así que compara cada fila solo consigo misma.

| Cambio | Dispositivo | Antes → después | Commit |
|---|---|---|---|
| Sin fin de render pass para subidas de buffers / occlusion queries | RG Cube, Mario Kart 8 | 16,0 → 23,0 fps | [`2c1895ac`](https://github.com/dfdx047/Fractal/commit/2c1895ac) |
| Trabajo de vértices solapado con las barreras de render target | RG Cube, Mario Kart 8 | 24,3 → 30,2 fps | [`7b36890f`](https://github.com/dfdx047/Fractal/commit/7b36890f) |
| Compresión de framebuffer activa | RP5, Super Mario 3D World | 57,0 → 59,8 fps, 1 % low 30 → 44 | [`017be0ef`](https://github.com/dfdx047/Fractal/commit/017be0ef) |
| Recompilador: contador de ciclos en registro, área de código | RP5, Mario Kart 8 | 47,8 → 50,0-50,4 fps | [`23b10251`](https://github.com/dfdx047/Fractal/commit/23b10251) |
| Limpieza de depth con render pass | Mi A3, Shovel Knight | 34 → 52 fps | [`aabeea76`](https://github.com/dfdx047/Fractal/commit/aabeea76) |
| Sincronización de `GX2DrawDone` por juego | RP5, Breath of the Wild | 9,4 → 11,1 fps | [`951eb1e7`](https://github.com/dfdx047/Fractal/commit/951eb1e7) |
| 1-2 hilos de compilación de pipelines | Mi A3, SM3DW, caché fría | 17,0 s → 7,5 s en cuadros de más de 100 ms | [`9893cf72`](https://github.com/dfdx047/Fractal/commit/9893cf72) |
| Los pipelines precompilados coinciden con la caché del driver | Watch Dogs | congelamiento de 38-68 s → 9 s al entrar al juego | [`69ac8320`](https://github.com/dfdx047/Fractal/commit/69ac8320) |

---

## En curso

- **Watch Dogs en Turnip:** un fallo de página de la GPU en una escritura de streamout puede colgar la GPU tras unos
  25 minutos. El driver del sistema no está afectado.
- **Watch Dogs sobreexpuesto en Android:** desde la sala de Maurice la exposición automática puede quedarse demasiado
  clara en cerca de la mitad de las sesiones, en Turnip y en el driver del sistema; la build de PC no está afectada.
  Descartado por ahora: NaN/Inf en los pixel shaders, draws omitidos durante la compilación asíncrona de pipelines y
  readback de texturas.
- **Watch Dogs en Mali** (RG Cube): el hilo de renderizado se queda compilando los enormes shaders de streamout; menú en
  bloques e intro negra.
- **Assassin's Creed IV:** comparando las cinemáticas con la build de PC; las espadas largas cuelgan rectas igual en PC y
  en la RP5, aún no confirmado como bug.
- **Ninja Gaiden 3 en Turnip:** cabezas oscuras flotando y cielo quemado siguen tras la corrección de la profundidad de
  campo.
- **Need for Speed: Most Wanted U:** encontrar la causa raíz del follaje blanco (el paquete de la comunidad lo oculta).
- **Xenoblade Chronicles X con el driver del sistema de Qualcomm:** personajes como siluetas blancas (bug del driver);
  por ahora usa Turnip.
- **Mali en general:** la RG Cube, en su modo económico de CPU, es la referencia para dispositivos Mali más débiles.

## Planeado

- Pruebas de compatibilidad en los principales juegos de Wii U y una lista pública de compatibilidad.
- Un botón "marcar problema" dentro del juego que guarde una captura y una marca en el log para los reportes.
- Streamout en drivers Mali sin stores en el pipeline de vértices (por ejemplo con un pase de compute).
- Funciones de GPU que faltan, encontradas en las pruebas: compute draw calls, alpha-to-mask (`GX2SetAlphaToMaskReg`),
  `GX2ExpandDepthBuffer` y `SQ_VTX_SEMANTIC_CLEAR`, que nunca se aplica.
- Más rendimiento del lado de la CPU: Breath of the Wild y Mario Kart 8 en la RP5 están limitados por el núcleo PowerPC
  principal y el hilo de renderizado, no por la GPU.
- Seguir el `main` del Cemu oficial.
