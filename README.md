# Link a MP3

App Android propia para descargar el audio (MP3) o el vídeo de enlaces de YouTube, SoundCloud, Instagram, TikTok, X, Facebook, Vimeo, Bandcamp y cientos de sitios más. Lleva dentro **yt-dlp** y **ffmpeg**, así que no necesita servidores ni Termux.

## Qué hace
- **Compartir → Link a MP3** desde cualquier app, o pegar el enlace a mano.
- Formatos: MP3 de alta calidad, MP3 ligero (128 kbps), lista completa en MP3 o vídeo MP4.
- MP3 con portada y metadatos (título y artista).
- Se guarda en **Música/Link a MP3** (los vídeos van a Películas/Link a MP3), así que aparece en cualquier reproductor.
- La descarga sigue aunque cierres la app, con progreso en la notificación y botón para cancelar.
- Opción para descargar directamente al compartir, sin abrir la app.
- Se actualiza sola el motor yt-dlp cada 3 días. También hay un botón «Actualizar motor».

## Cómo se compila
GitHub Actions compila el APK en cada cambio en `main` (`.github/workflows/build.yml`) y lo publica en **Releases** como `LinkAMp3.apk`.

En local: Android Studio, o `gradle :app:assembleRelease` (Gradle 8.9, JDK 17).

El APK se firma con `app/linkamp3.jks`. Así cada versión nueva se instala encima de la anterior sin desinstalar. Mantén el repositorio **privado**.
