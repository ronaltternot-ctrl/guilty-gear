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
| **Botones PSP o Arcade** | Elige en la pantalla de inicio: **PSP** (△ ○ ✕ □, L, R, como en la consola) o **Arcade** (P, K, S, HS y D, colocados como en la máquina de Guilty Gear: K S HS arriba, P y D abajo). Con vista previa. |
| **Controles por gestos** | Estilo Skullgirls: tocar = cadena de golpes P → K → S → HS, deslizar = dash, salto o barrida, dos dedos = Dust, agarre o bloqueo, mantener y arrastrar = stick. Botones con los **especiales y el Overdrive de tu personaje** que hacen el movimiento por ti (236, 623, 632146, cargas…), más RC y Burst. Se activan en la pantalla de inicio y cada especial se puede cambiar por personaje. |
| **Controles Bluetooth/USB** | Se detectan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D, L1 = Respeto. |
| **Perfiles por personaje** | Los botones cambian solos según el personaje que uses. Cada personaje recuerda su propia distribución (si mueves botones mientras juegas con él, se guarda para él) y puedes elegir PSP o Arcade por personaje. Versión americana (ULUS-10409). |
| **Efectos visuales** | Arcade (monitor de recreativa), CRT (televisor de tubo), Scanlines, **IA** (red neuronal NNEDI3), xBR y FSR. Solo procesan la imagen final: los sprites no se tocan. Con vista previa en la app. |
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
2. Abre **GGXX AC+** → *Elegir archivo ISO / CSO* → selecciónalo. Verás ✓ y el nombre del juego.
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
| Los botones no cambian con el personaje | Revisa que *Cambiar los botones según el personaje* esté activado y que tu copia sea ULUS-10409. En combates por equipos no cambia. |
| El aviso «Controles: …» muestra otro nombre | Avísanos qué personaje usabas y qué nombre salió: los perfiles funcionan igual, solo hay que corregir la tabla de nombres. |
| Los botones arcade no hacen el golpe que dicen | Dentro del juego cambiaste la configuración de botones. Vuelve a la de fábrica en las opciones del juego (□ = P, ✕ = K, △ = S, ○ = HS, R = D). |
| Las hitboxes no salen | Revisa que el visor esté activado y que tu copia sea ULUS-10409. Al empezar el combate tarda unos segundos: abajo a la izquierda ves en qué paso va. Si se queda en «buscando la cámara», camina hacia los lados. |
| Las hitboxes no coinciden con los personajes | Guarda un estado justo en ese momento (Atrás → «Guardar estado»), sal y pulsa *Enviar informe del visor*. Con el informe se puede corregir; a veces basta con pegar un ajuste en *Ajuste manual*, sin versión nueva. |
| Con gestos, un especial no sale | Ese personaje puede tener un movimiento genérico en ese botón: en *Controles por gestos → Especiales de cada personaje* elige el correcto. Mira también que el aviso «Especiales: …» muestre tu personaje y que *Tú juegas como* sea tu lado. |
| Con gestos, los especiales salen al revés | La app aún no sabe hacia dónde miras: toca la flecha de arriba a la izquierda. Se corrige sola cuando el detector encuentra a los personajes (versión ULUS-10409). |
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
  - **Controles por gestos** (`patches/0004` + [`overlay/UI/AcPlusGestures.h`](overlay/UI/AcPlusGestures.h) + [`overlay/UI/AcPlusGestureCore.h`](overlay/UI/AcPlusGestureCore.h)): una vista a pantalla completa en `EmuScreen` recibe los toques y los convierte en «pasos» (botones de PSP mantenidos N fotogramas). `patches/0004` añade un gancho en `__CtrlVblank()` que reproduce esos pasos en cada vblank emulado, justo antes de que el juego lea el control, así que los movimientos (por ejemplo 236 = ↓ 2 fotogramas, ↘ 2, → + botón 3) salen exactos. Adelante/atrás se resuelven en cada fotograma según hacia dónde mira el personaje, que se saca de las posiciones que encuentra el detector del visor de hitboxes (si no, 1P mira a la derecha y la flecha lo invierte). Una carga ya mantenida con el stick no se vuelve a esperar. Los especiales de cada personaje los escribe la app en `acplus.ini` (`[Gestures]`, `MovesN = 236P,623S,…`); los predeterminados están en [`GestureMoves.java`](overlay/android/src/org/ppsspp/ppsspp/GestureMoves.java). [`scripts/test_gesture_core.cpp`](scripts/test_gesture_core.cpp) prueba los gestos con toques simulados.
- **Archivos propios** ([`overlay/`](overlay/)): `LauncherActivity.java`, `ControlsPreviewView.java` (vista previa de los botones), `GameInspector.java` (lee `PARAM.SFO` de ISO y CSO para identificar el juego), `LaunchContract.java`, tema e íconos.
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
