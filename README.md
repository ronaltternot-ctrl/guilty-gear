# GGXX ΛC+ para Android

App de Android dedicada a **Guilty Gear XX Accent Core Plus (versión PSP)**: abres la app, eliges tu copia del juego una vez y a partir de ahí entras directo a jugar, con los botones originales de la PSP en pantalla (o tu control Bluetooth) y la resolución que quieras, **desde la original de PSP hasta 4K**.

Por dentro usa el motor de emulación de [PPSSPP](https://www.ppsspp.org/) con una pantalla de inicio propia y ajustes pensados para este juego.

> El juego **no** viene incluido. Necesitas tu propia copia en formato ISO o CSO (sacada de tu UMD).

---

## Qué hace la app

| | |
|---|---|
| **Arranque directo** | Sin menús de emulador: pulsas *Jugar* y entra el juego. |
| **Resolución 1× a 8×** | 1× = 480×272 (PSP original) · 4× = 1920×1088 (Full HD) · **8× = 3840×2176 (4K)**. Te recomienda la mejor para tu pantalla. |
| **También BlazBlue** | Abre **BlazBlue: Calamity Trigger** (US ULUS-10519, EU ULES-01497, JP ULJM-05613) y también Continuum Shift II. La app lo reconoce y adapta los botones arcade (**A, B y C** arriba, **D** abajo), los textos y los controles por gestos (cadena A → B → C, Drive, agarre B+C, Rapid Cancel y Barrier Burst). Con el **Catálogo** cambias entre Guilty Gear, BlazBlue y KOF sin volver a buscar el archivo. Resolución, efectos, FSR, multijugador, prueba del equipo, **HUD propio** y **perfiles por personaje** también funcionan (CT americano y europeo, CS II americano); el visor de hitboxes es solo de Guilty Gear. |
| **También KOF** | Abre **The King of Fighters Collection: The Orochi Saga** (US ULUS-10360, EU ULES-01191; también reconoce cualquier título «King of Fighters» / «KOF»). Igual que con BlazBlue: botones arcade de **Neo Geo** (A, B, C y D en arco, □ = A, ✕ = B, △ = C, ○ = D), cabecera y textos propios, y gestos adaptados (cadena A → B → C → D, ↓ = 2D, dos dedos = CD, agarre C, **Roll** A+B y **MAX** A+B+C, con un súper en el cuarto botón). También con **HUD propio** (las cinco entregas) y **perfiles por personaje** ('96, '97 y '98) en la versión americana; el visor de hitboxes es solo de Guilty Gear. |
| **Catálogo** | Botón **CATÁLOGO**: tus juegos en una cuadrícula con el **icono de cada uno** (el que trae el propio ISO o CSO; si no tiene, uno dibujado), una etiqueta por juego (Guilty Gear, BlazBlue, KOF u otro), su ID y formato, y filtros. Toca un juego para elegirlo, mantén pulsado para quitarlo (el archivo no se borra). Con *Añadir juegos* eliges **varios ISO / CSO a la vez**. Abajo, la lista de juegos compatibles y lo que tiene cada uno. |
| **Prueba de tu equipo** | Al abrir la app por primera vez mide tu GPU (una escena tipo pelea de 1× a 8× y los efectos reales de la app a la resolución de tu pantalla), mira CPU, RAM y Vulkan, y te sugiere resolución, motor gráfico y filtro (o AMD FSR) con un botón *Aplicar recomendados*. Se puede repetir desde *Tu equipo*. |
| **HUD propio** | Vida (con el daño reciente marcado), medidor de poder, burst, tiempo y contador de combos con su daño, leídos de la memoria del juego y dibujados encima con 4 diseños: **Neón**, **Arcade**, **Lateral** (en las franjas negras de los teléfonos alargados) y **Datos** (números exactos para entrenar). Con vista previa en la app. **Guilty Gear** (ULUS-10409): vida, tensión, burst, tiempo, nombres y el contador de golpes del juego. **BlazBlue** (ULUS-10519, ULES-01497, ULUS-10579): vida, Heat y nombres (cuando reconoce al personaje). **KOF** (ULUS-10360): vida, POW / POWER con stocks y tiempo en las cinco entregas, y nombres en '96–'98. En BlazBlue y KOF los golpes del combo se cuentan por las bajadas de vida (≈). |
| **Botones PSP o Arcade** | Elige en la pantalla de inicio: **PSP** (△ ○ ✕ □, L, R, como en la consola) o **Arcade** (P, K, S, HS y D, colocados como en la máquina de Guilty Gear: K S HS arriba, P y D abajo). Con vista previa. |
| **Controles por gestos** | Estilo Skullgirls: tocar = cadena de golpes P → K → S → HS, deslizar = dash, salto o barrida, dos dedos = Dust, agarre o bloqueo, mantener y arrastrar = stick. Botones con los **especiales y el Overdrive de tu personaje** que hacen el movimiento por ti (236, 623, 632146, cargas…), más RC y Burst. Se activan en la pantalla de inicio y cada especial se puede cambiar por personaje. |
| **Controles Bluetooth/USB** | Se detectan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D, L1 = Respeto. |
| **Perfiles por personaje** | Los botones cambian solos según el personaje que uses. Cada personaje recuerda su propia distribución (si mueves botones mientras juegas con él, se guarda para él) y puedes elegir PSP o Arcade por personaje. **Guilty Gear** (ULUS-10409, combates individuales), **BlazBlue** (experimental: reconoce al personaje por los nombres de sus sprites) y **KOF '96, '97 y '98** (cambia cuando entra el siguiente miembro del equipo). Cada juego tiene su lista de personajes. |
| **Efectos visuales** | Arcade (monitor de recreativa), CRT (televisor de tubo), Scanlines, **IA** (red neuronal NNEDI3) y xBR. Solo procesan la imagen final: los sprites no se tocan. Con vista previa en la app. |
| **BETA · AMD GRAPHICS** | **AMD FidelityFX Super Resolution 1.0** en dos pasadas: EASU escala la imagen (×1.5, ×2 o ×3 sobre tu resolución interna, hasta el tamaño de tu pantalla) y RCAS la afina con la nitidez que elijas. Funciona con Vulkan y con OpenGL ES 3.0 (y 2.0). Avisa cuando la resolución interna ya es tan alta que FSR aporta poco. Se guarda por juego y no afecta a los estados guardados. Licencia MIT de AMD en *Créditos y licencias*. |
| **Multijugador con salas** | El modo «Network» del juego (ad hoc, 2 jugadores) por Wi-Fi: uno *crea la sala* y le dice el código (por ejemplo `R2M0-299`) al otro, que pulsa *Unirme*. Misma red Wi-Fi o zona Wi-Fi compartida. Opción *Internet* (beta) con servidores públicos de PPSSPP. |
| **Visor de hitboxes** | Actívalo en la pantalla de inicio y verás encima del juego las **hurtboxes** (verde, donde te pueden pegar), las **hitboxes** (rojo, lo que golpea), la posición de cada personaje (cruz blanca) y los proyectiles. Puedes elegir qué mostrar. Experimental, versión americana (ULUS-10409). |
| **Vulkan u OpenGL** | Vulkan por defecto (rinde mejor con los efectos de los súper ataques). |
| **Detecta tu copia** | Lee el archivo y te confirma que es el juego correcto (por ejemplo `ULUS-10409`). |
| **Partidas y guardados** | Guardado normal del juego y estados rápidos desde el menú de pausa. |

En el juego, el botón **Atrás** (o el icono de pausa) abre el menú: continuar, guardar o cargar estado, ajustes y **Salir**, que te devuelve a la pantalla de inicio.

---

## Cómo obtener el APK (sin instalar nada en tu PC)

GitHub compila la app gratis en sus servidores. Solo necesitas una cuenta.

1. **Crea una cuenta** en [github.com](https://github.com) (gratis).
2. **Crea un repositorio nuevo**: botón **+** arriba a la derecha → *New repository*. Ponle un nombre (por ejemplo `ggxx-acplus-android`) y pulsa *Create repository*.
3. **Sube los archivos de este proyecto**: en la página del repositorio pulsa *uploading an existing file* y arrastra **todo el contenido** de esta carpeta (incluida la carpeta `.github`). Pulsa *Commit changes*.
   - Si después no ves nada en la pestaña **Actions**, es que la carpeta oculta `.github` no se subió. Crea el archivo a mano: *Add file → Create new file*, nombre `.github/workflows/build.yml`, y pega el contenido de ese archivo.
4. **Espera a que compile**: pestaña **Actions** → verás *Compilar APK* en marcha. Tarda unos 30–60 minutos la primera vez (las siguientes, menos).
   - Si no arrancó solo, entra en *Compilar APK* → *Run workflow*.
5. **Descarga el APK en tu teléfono**: en la página del repositorio, sección **Releases** (a la derecha) → abre la última → toca el archivo `GGXX-ACPlus-Android-….apk`.
6. **Instálalo**: Android te pedirá permiso para instalar apps de origen desconocido para tu navegador. Acéptalo e instala.

Para actualizar en el futuro, vuelve a compilar y descarga el APK nuevo: se instala encima conservando tus partidas.

## Primer uso

1. Copia tu ISO o CSO del juego a la memoria del teléfono (por ejemplo a *Descargas*) o a la microSD. Evita Google Drive u otras nubes: tiene que estar en el teléfono.
2. Abre **GGXX AC+** → *Elegir archivo ISO / CSO* → selecciónalo. Verás ✓, el icono y el nombre del juego. Si tienes varios juegos, entra en **CATÁLOGO** → *Añadir juegos* y elígelos todos a la vez.
3. Elige la resolución (la marcada como recomendada es un buen punto de partida) y pulsa **JUGAR**.

## Si algo no va bien

| Problema | Qué hacer |
|---|---|
| Va lento o se traba | Baja la resolución un nivel. En teléfonos de gama media, 3× o 4× suele ser lo ideal. |
| Fallos gráficos o pantalla negra | Cambia el motor gráfico a OpenGL. |
| "No encuentro el juego" | Moviste o borraste el archivo: vuelve a elegirlo. |
| No aparecen los botones en pantalla | Activa *Mostrar botones en pantalla* en la pantalla de inicio. |
| No nos encontramos en «Network» | El anfitrión debe pulsar JUGAR primero. Los dos en la misma red (algunas redes de invitados o de cafés aíslan los dispositivos: usa la zona Wi-Fi de un teléfono). Revisa que el código diga «✓ Sala en …». |
| Va lento con «IA» o «IA máx.» | Son los efectos más pesados. Prueba «IA» en vez de «IA máx.», o «xBR» / «FSR», que son ligeros. |
| Los botones no cambian con el personaje | Revisa que *Cambiar los botones según el personaje* esté activado, que no uses gestos y que tu copia sea una de las compatibles (Guilty Gear ULUS-10409, BlazBlue ULUS-10519 / ULES-01497 / ULUS-10579, KOF ULUS-10360). En Guilty Gear no cambia en combates por equipos; en KOF solo en '96, '97 y '98. En BlazBlue y KOF el juego escribe `PSP/SYSTEM/acplus_game_report.txt` con lo que leyó: si no reconoce a tu personaje, ese informe sirve para corregirlo. |
| El aviso «Controles: …» muestra otro nombre | Avísanos qué personaje usabas y qué nombre salió: los perfiles funcionan igual, solo hay que corregir la tabla de nombres. |
| Los botones arcade no hacen el golpe que dicen | Dentro del juego cambiaste la configuración de botones. Vuelve a la de fábrica en las opciones del juego (□ = P, ✕ = K, △ = S, ○ = HS, R = D). |
| Las hitboxes no salen | Revisa que el visor esté activado y que tu copia sea ULUS-10409. Al empezar el combate tarda unos segundos: abajo a la izquierda ves en qué paso va. Si se queda en «buscando la cámara», camina hacia los lados. |
| Las hitboxes no coinciden con los personajes | Guarda un estado justo en ese momento (Atrás → «Guardar estado»), sal y pulsa *Enviar informe del visor*. Con el informe se puede corregir; a veces basta con pegar un ajuste en *Ajuste manual*, sin versión nueva. |
| Con gestos, un especial no sale | Ese personaje puede tener un movimiento genérico en ese botón: en *Controles por gestos → Especiales de cada personaje* elige el correcto. Mira también que el aviso «Especiales: …» muestre tu personaje y que *Tú juegas como* sea tu lado. |
| Con gestos, los especiales salen al revés | La app aún no sabe hacia dónde miras: toca la flecha de arriba a la izquierda. Se corrige sola cuando el detector encuentra a los personajes (versión ULUS-10409). |
| FSR no parece hacer nada | Mira el texto del panel de FSR en *Gráficos*: si tu resolución interna ya llena la pantalla, FSR solo puede afinar. Baja la resolución interna a 1× o 2× y deja que FSR escale. Al entrar al juego sale el aviso «AMD FSR 1.0 (beta): …» cuando está activo. |
| El HUD propio no aparece | Activa *Mostrar mi HUD* y usa una versión compatible (Guilty Gear ULUS-10409, BlazBlue ULUS-10519 / ULES-01497 / ULUS-10579, KOF ULUS-10360). Solo sale durante los combates (se esconde en menús y en la pausa del juego). En KOF lo activa al reconocer qué entrega está corriendo; si no sale en una, avísanos cuál. |
| La prueba del equipo recomienda poco | Es una estimación con una escena de prueba. Puedes subir la resolución a mano; si va lento, vuelve a la recomendada. |
| En BlazBlue un especial del botón no sale | En BlazBlue los cuatro botones son los mismos para todos los personajes (la app no lee cuál usas). Cámbialos en *Controles por gestos → Especiales* con el movimiento de tu personaje. |
| En KOF un especial del botón no sale | Como en BlazBlue, los cuatro botones son los mismos para todos los personajes. Cámbialos en *Controles por gestos → Especiales*. Cada entrega tiene sus reglas (en '94 no se puede rodar, el modo MAX cambia según el año). |
| Un juego sale en el catálogo con un icono dibujado | Ese archivo no trae icono o no se pudo leer. Si dice «Sin acceso», lo moviste o borraste: vuelve a añadirlo con *Añadir juegos*. |
| Quiero reasignar el control | En el juego: Atrás → Ajustes → pestaña Controles → asignación de botones. |
| La app dice "Partida en curso" | El juego quedó abierto en segundo plano: *Continuar* o *Cerrar partida*. |

---

## Detalles técnicos

- **Motor**: PPSSPP, versión fijada en [`PPSSPP_VERSION`](PPSSPP_VERSION).
- **Cambios sobre PPSSPP** ([`patches/`](patches/)):
  - Pantalla de inicio propia (`LauncherActivity`) y nombre/ícono/paquete propios (`com.acplus.psplauncher`), así que convive con un PPSSPP normal.
  - El emulador corre en un proceso aparte (`:emu`). Al salir del juego, ese proceso termina y vuelves a la pantalla de inicio.
  - Los ajustes elegidos se pasan al emulador con `--appendconfig` (resolución interna, motor gráfico, FPS, botones táctiles) y `--pause-menu-exit` para que *Salir* cierre el juego.
  - La "memory stick" se fija en el almacenamiento de la app, así que nunca pide elegir carpeta.
  - Etiquetas P, K, S, HS y D para los botones arcade (`patches/0002-arcade-button-labels.patch` + `assets/ui_images/gg_buttons.svg`, generado con [`scripts/make_gg_glyphs.py`](scripts/make_gg_glyphs.py) a partir de la fuente Roboto Condensed que ya trae PPSSPP). El modo arcade usa los botones personalizados de PPSSPP con el mapeo del juego en PSP: □ = P, ✕ = K, △ = S, ○ = HS, R = D.
  - Perfiles por personaje (`patches/0003` + [`overlay/UI/AcPlusProfiles.h`](overlay/UI/AcPlusProfiles.h)): cada ~0,2 s se lee el personaje del jugador en la memoria del juego (`0x08C6BB58` para 1P y `0x08C6BB5A` para 2P, direcciones de la base de datos CWCheat para ULUS-10409). Cuando cambia, se guarda la distribución del personaje anterior si la editaste (`PSP/SYSTEM/acplus_profiles/char_NN.ini`) y se carga la del nuevo, o su tipo de botones elegido en la app. `Config::SaveTouchLayout/LoadTouchLayout` guardan solo los botones en pantalla.
  - Efectos visuales: la configuración añadida (`--appendconfig`) puede elegir la cadena de shaders (`[PostShaderList]`). Los shaders propios están en [`overlay/assets/shaders/`](overlay/assets/shaders/) (`acplus.ini`). Todos usan `Upscaling=True`, así que el juego se dibuja a 480×272 y el shader construye la imagen a la resolución de la pantalla.
  - **IA (NNEDI3)**: la misma red neuronal que PPSSPP trae para reescalar texturas, convertida en post-procesado de 3 pasos (filas ×2, columnas ×2 y Catmull-Rom a pantalla corrigiendo el desfase de medio píxel). La generan [`scripts/make_nnedi3_shaders.py`](scripts/make_nnedi3_shaders.py) (pesos tomados de `tex_nnedi3_2x_single.csh`) y [`scripts/make_crt_shaders.py`](scripts/make_crt_shaders.py) los CRT. [`scripts/check_shaders.py`](scripts/check_shaders.py) los compila como GLSL ES 1.00 y como Vulkan, igual que PPSSPP.
  - Multijugador: la app escribe la sección `[Network]` de la configuración añadida. El anfitrión arranca el servidor PRO ad hoc integrado de PPSSPP y se conecta a su **propia IP de Wi-Fi** (con `localhost` PPSSPP entra en un modo de varias instancias en el mismo equipo que no acepta jugadores de la red). El invitado se conecta a la IP que sale del código de sala ([`RoomCode.java`](overlay/android/src/org/ppsspp/ppsspp/RoomCode.java): IPv4 + 3 bits de control en 7 caracteres Crockford base32). Modo P2P directo en LAN; en *Internet* se usa un servidor con relay (AemuPostoffice).
  - **Visor de hitboxes** (`patches/0003` + [`overlay/UI/AcPlusHitboxes.h`](overlay/UI/AcPlusHitboxes.h) + [`overlay/UI/AcPlusHitboxCore.h`](overlay/UI/AcPlusHitboxCore.h)): `EmuScreen` lo actualiza cada fotograma y lo dibuja en `renderUI()` encima de la imagen del juego (antes de los botones táctiles), con el mismo rectángulo de pantalla que usa PPSSPP para la imagen. La app lo enciende con la sección `[Hitboxes]` de `acplus.ini`.
    - Las cajas usan el formato de las versiones de PC (Accent Core +R y #Reload, según [kof-combo-hitboxes](https://github.com/odabugs/kof-combo-hitboxes)): lista de entradas de 12 bytes (`x, y, ancho, alto` en `s16` y tipo en `u8`: 1 = golpe, 2 = daño) relativas al punto de apoyo del personaje, en 1/100 de píxel.
    - Como en PSP las direcciones no están documentadas, se **detectan solas** al empezar el combate: busca a los dos luchadores (los números de personaje de la pantalla de selección, primero en `0x0913D000`, donde los códigos CWCheat tienen los objetos de jugador), prueba dónde está el puntero a la lista de cajas y su número, luego la posición, y por último la cámara (la busca en la memoria y, mientras la encuentra, centra entre los dos personajes).
    - Escribe `PSP/SYSTEM/acplus_hitbox_report.txt` con lo que encontró y un volcado de los objetos; la app lo comparte con *Enviar informe del visor*. El campo *Ajuste manual* acepta valores como `P1=0x0913D000 Stride=0x124 BoxPtr=0x54 BoxCount=0x84 Pivot=0xB0 CameraCenter=0x08D20010 Scale=0.0075 GroundY=248` para corregir la detección sin compilar otra versión.
    - [`scripts/test_hitbox_core.cpp`](scripts/test_hitbox_core.cpp) prueba la detección con una memoria simulada (ruido, señuelos, varias distribuciones y sin estructura de cámara).
  - **Varios juegos**: [`GameInspector`](overlay/android/src/org/ppsspp/ppsspp/GameInspector.java) reconoce BlazBlue y KOF por el ID del disco (y KOF también por el título). El diseño arcade usa las letras A–D que ya trae PPSSPP (`I_A`…`I_D`) con el mapeo de PSP de cada juego (BlazBlue □ = A, △ = B, ○ = C, ✕ = D; KOF □ = A, ✕ = B, △ = C, ○ = D), y los gestos usan un `ButtonMap` por juego en [`AcPlusGestureCore.h`](overlay/UI/AcPlusGestureCore.h) (cadena, barrida, golpe de dos dedos, agarre, RC/Roll y Burst/MAX). El emulador elige el mapa según el ID del disco o el título; los especiales de BlazBlue y KOF se guardan como `MovesBB` y `MovesKOF` en `acplus.ini`.
  - **Catálogo** ([`GameCatalog.java`](overlay/android/src/org/ppsspp/ppsspp/GameCatalog.java)): la lista de juegos (hasta 40) guarda cada archivo con su permiso de lectura. Al identificar un juego, `GameInspector` copia también `PSP_GAME/ICON0.PNG` (144×80) del ISO o CSO a `files/catalog/`; los juegos añadidos con versiones anteriores sacan su icono la primera vez que se abre el catálogo. Sin icono se dibuja una portada con los colores del juego.
  - **HUD propio** ([`AcPlusHudCore.h`](overlay/UI/AcPlusHudCore.h) + [`AcPlusHud.h`](overlay/UI/AcPlusHud.h)): lee cada fotograma la vida (`0x0913D01E`, +0x124 para 2P), la tensión (`0x08C6BB7C`, +0x148 para 2P), el burst (+0xF8), los golpes del combo (+0x100 en el bloque del rival) y el tiempo (`0x08C6CC14`). Son las direcciones de los códigos CWCheat de ULUS-10409 y siguen la misma estructura que la versión de PS2. Solo se muestra cuando los valores son válidos y los objetos de los luchadores cambian (combate en marcha). Calcula el daño de cada combo y el rastro del daño reciente. Los diseños se dibujan como primitivas (rectángulos, degradados, cuadriláteros y texto) con el renderizador de interfaz de PPSSPP; [`scripts/test_hud_core.cpp`](scripts/test_hud_core.cpp) prueba la lectura y genera las primitivas con las que [`scripts/render_hud_previews.py`](scripts/render_hud_previews.py) hace las vistas previas de la app.
  - **HUD y perfiles en BlazBlue y KOF** ([`AcPlusGameData.h`](overlay/UI/AcPlusGameData.h), un lector por juego que usan el HUD y los perfiles):
    - **BlazBlue**: los códigos CWCheat de cada versión guardan en una dirección fija el puntero al objeto de cada luchador (CT US `0x08C0938C` / `0x08C09390`, CT EU `0x08C0B80C` / `0x08C0B810`, CS II US `0x08C32B70` / `0x08C32B74`). En el objeto: vida `s32` (+`0x798`, máximo +`0x79C`; CS II +`0x810` / +`0x814`) y Heat (+`0x2214`; CS II +`0x422C`, máximo 10000). Qué personaje es no sale en ningún código: el motor nombra los sprites con dos letras del personaje (`rg000_00` = Ragna, códigos de [dantarion/bbtools](https://github.com/dantarion/bbtools)), así que se buscan esos nombres dentro del objeto. Experimental.
    - **KOF**: las cinco entregas son juegos de Neo Geo dentro de un emulador que guarda la RAM de trabajo del Neo Geo (`0x100000`–`0x10FFFF`) en una dirección fija de cada juego, en palabras de 16 bits (el byte `b` del Neo Geo está en `base + ((b - 0x100000) ^ 1)`). La base sale del código CWCheat de vida de cada entrega y se confirma porque su código de tiempo cae justo en el tiempo del Neo Geo. Con esa base se usan las direcciones del Neo Geo de los trucos de [FinalBurn Neo](https://github.com/finalburnneo/FBNeo-cheats): vida, POW / POWER y stocks, tiempo, y en '96–'98 los tres miembros de cada equipo, su orden y cuántos ha perdido cada jugador (de ahí sale quién pelea). Qué entrega corre se sabe por la instrucción que comprueba el código CWCheat «Hit Anywhere» de cada una y porque su RAM cambia. En '97 el código de tiempo de la PSP está 0x1000 más allá que en la recreativa: se prueban los dos sitios y se usa el que tiene sentido.
    - BlazBlue y KOF no tienen contador de golpes conocido: el HUD cuenta como combo las bajadas de vida seguidas (menos de 0,8 s entre una y otra). [`scripts/test_game_data.cpp`](scripts/test_game_data.cpp) prueba los lectores con una memoria simulada y comprueba las direcciones contra los códigos CWCheat.
  - **Prueba de tu equipo** ([`DeviceBenchmark.java`](overlay/android/src/org/ppsspp/ppsspp/DeviceBenchmark.java)): contexto OpenGL ES fuera de pantalla (EGL pbuffer), escena con 3 capas de fondo, 2 luchadores y 24 sprites con transparencia a cada resolución interna, y los shaders reales de los efectos a la medida del juego en tu pantalla. Tiempos con `glFinish()`. Recomienda la mayor resolución que dibuja en ≤ 6 ms (sin pasar de la que tu pantalla aprovecha), FSR si no llega a la de tu pantalla y FSR cabe en ≤ 8 ms, y Vulkan si el teléfono tiene Vulkan 1.1 o más (Android 9+) y la GPU no es antigua.
  - **AMD FSR 1.0 (beta)** ([`acplus_fsr1_easu.fsh`](overlay/assets/shaders/acplus_fsr1_easu.fsh), [`acplus_fsr1_rcas.fsh`](overlay/assets/shaders/acplus_fsr1_rcas.fsh)): adaptación de `FsrEasuF` y `FsrRcasF` de `ffx_fsr1.h` (licencia MIT de AMD, incluida en los shaders y en la pantalla de créditos), con precisión alta para las coordenadas. Escritos en GLSL ES 1.00 como el resto de shaders de PPSSPP: PPSSPP los traduce a GLSL ES 3.00 en OpenGL ES 3 y a GLSL de Vulkan. `patches/0005` añade la clave `OutputScaleSetting` a los shaders de post-procesado: la pasada EASU sale al tamaño de su entrada por el factor elegido, sin pasar del tamaño del juego en pantalla, así que el juego mantiene la resolución interna que elegiste. RCAS trabaja a ese mismo tamaño y después la imagen se lleva a la pantalla. La app guarda FSR por juego (ID del disco) y lo escribe en la configuración; [`AcPlusFsr.h`](overlay/UI/AcPlusFsr.h) lo vuelve a aplicar al arrancar si una configuración por juego de PPSSPP lo hubiera cambiado. Los estados guardados no incluyen el post-procesado.
  - **Controles por gestos** (`patches/0004` + [`overlay/UI/AcPlusGestures.h`](overlay/UI/AcPlusGestures.h) + [`overlay/UI/AcPlusGestureCore.h`](overlay/UI/AcPlusGestureCore.h)): una vista a pantalla completa en `EmuScreen` recibe los toques y los convierte en «pasos» (botones de PSP mantenidos N fotogramas). `patches/0004` añade un gancho en `__CtrlVblank()` que reproduce esos pasos en cada vblank emulado, justo antes de que el juego lea el control, así que los movimientos (por ejemplo 236 = ↓ 2 fotogramas, ↘ 2, → + botón 3) salen exactos. Adelante/atrás se resuelven en cada fotograma según hacia dónde mira el personaje, que se saca de las posiciones que encuentra el detector del visor de hitboxes (si no, 1P mira a la derecha y la flecha lo invierte). Una carga ya mantenida con el stick no se vuelve a esperar. Los especiales de cada personaje los escribe la app en `acplus.ini` (`[Gestures]`, `MovesN = 236P,623S,…`); los predeterminados están en [`GestureMoves.java`](overlay/android/src/org/ppsspp/ppsspp/GestureMoves.java). [`scripts/test_gesture_core.cpp`](scripts/test_gesture_core.cpp) prueba los gestos con toques simulados.
- **Archivos propios** ([`overlay/`](overlay/)): `LauncherActivity.java`, `ControlsPreviewView.java` (vista previa de los botones), `GameInspector.java` (lee `PARAM.SFO` e `ICON0.PNG` de ISO y CSO para identificar el juego), `GameCatalog.java` (iconos del catálogo), `GameRosters.java` (personajes de cada juego para los perfiles), `LaunchContract.java`, tema e íconos.
- **Compilación**: [`.github/workflows/build.yml`](.github/workflows/build.yml) descarga PPSSPP, aplica los cambios con [`scripts/prepare.sh`](scripts/prepare.sh) y compila `assembleNormalRelease` para `arm64-v8a`. Firma con [`keystore/release.p12`](keystore/) para que las actualizaciones se instalen encima.

### Compilar en tu PC (opcional)

Necesitas Android Studio (o el SDK/NDK de Android) y Java 21:

```bash
bash scripts/prepare.sh build/ppsspp
cd build/ppsspp
./gradlew assembleNormalRelease \
  -PRELEASE_STORE_FILE="$PWD/../../keystore/release.p12" \
  -PRELEASE_STORE_PASSWORD=acplus-launcher -PRELEASE_KEY_ALIAS=acplus -PRELEASE_KEY_PASSWORD=acplus-launcher
# APK: build/ppsspp/android/build/outputs/apk/normal/release/
```

Para teléfonos de 32 bits añade `-PLAUNCHER_ABIS=arm64-v8a,armeabi-v7a`.

> La clave de firma de `keystore/` es pública dentro de este repositorio: sirve para uso personal. Si vas a compartir el APK con otras personas, crea tu propia clave y no la publiques.

## Licencia

PPSSPP y este proyecto se distribuyen bajo la licencia GPL 2.0 o posterior (ver [`LICENSE.txt`](LICENSE.txt)). Guilty Gear es una marca de Arc System Works; este proyecto no está afiliado ni respaldado por ellos y no incluye ningún archivo del juego.
