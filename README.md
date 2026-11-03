# Aura

App de música nativa para Android (Kotlin + Jetpack Compose + Media3) con descargador integrado.

- **Reproductor**: segundo plano, notificación y pantalla de bloqueo; velocidades de 0,5× a 2×; saltos de 15 s; aleatorio y repetición; cola «A continuación».
- **Vídeos**: se abren a pantalla completa en horizontal, con doble toque a los lados para ±15 s.
- **Descargador** (yt-dlp + ffmpeg + QuickJS): analiza el enlace, permite editar título y artista, elegir formato (MP3 o vídeo) y **recortar el clip**; destino: biblioteca/playlist de Aura y/o Música·Películas/Aura del teléfono.
- **Colección**: Me gusta, playlists, vídeos y música que ya está en el teléfono.

GitHub Actions compila el APK en cada cambio en `main` y lo publica en Releases (`Aura.apk`).
