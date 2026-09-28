# Last Circle 3D — Android battle royale prototype

Prototipo **3D** de supervivencia para Android, desarrollado con Java y OpenGL ES 2.0, sin bibliotecas externas ni conexión a Internet. La isla tropical, edificios, personajes y objetos se dibujan mediante geometría original generada por código. **No es el mapa Bermuda Remasterizada ni contiene archivos, marcas, personajes o recursos de Free Fire.**

## Qué incluye

- Cámara en tercera persona, modelos 3D simples, mar, playa, carreteras, almacenes, viviendas y palmeras.
- 20 combatientes (tu personaje y 19 bots), disparos, impactos, eliminaciones y munición.
- Zona circular que se reduce, botiquines y cajas de munición que se recogen al pasar por encima.
- Controles táctiles en horizontal: joystick izquierdo para moverte, FUEGO para disparar (apuntado asistido al enemigo cercano) y CURA para curarte. Podés arrastrar el dedo derecho para orientar los disparos.
- Partidas **sin conexión y para un solo jugador**, sin multijugador, paracaídas, vehículos ni inventario avanzado.

## Compilar el APK en GitHub

1. Descomprimí el ZIP y subí **el contenido de la carpeta `last-circle/`** a la raíz de un repositorio de GitHub. Incluí la carpeta oculta `.github/`.
2. Entrá a **Actions** → **Build Android APK** → **Run workflow**, o hacé un push a `main`.
3. Cuando el proceso termine, abrí la ejecución y descargá el artefacto **LastCircle-debug-apk**. Dentro está `app-debug.apk`.
4. En Android, instalá el APK permitiendo la instalación de esta fuente si el dispositivo te lo solicita.

El workflow usa Java 17, Gradle 8.9 y el SDK de Android. No necesita que guardes claves o contraseñas en el repositorio. El APK es **debug**, pensado para probar y no para publicar en Play Store.

## Limitaciones y próximas mejoras

Esta entrega reemplaza el renderizado 2D del prototipo anterior por gráficos 3D nativos. Los modelos son low-poly y las ubicaciones son originales, no una reproducción del mapa de otro juego. No se ha validado el rendimiento en cada teléfono ni se incluye un APK precompilado: GitHub Actions lo genera.

## Licencia

El código original de este prototipo puede modificarse y subirse a tu repositorio. Evitá incorporar mapas, texturas, música o marcas de terceros sin permiso.
