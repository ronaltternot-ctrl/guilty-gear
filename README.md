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
| **Controles Bluetooth/USB** | Se detectan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D, L1 = Respeto. |
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
| Los botones arcade no hacen el golpe que dicen | Dentro del juego cambiaste la configuración de botones. Vuelve a la de fábrica en las opciones del juego (□ = P, ✕ = K, △ = S, ○ = HS, R = D). |
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
