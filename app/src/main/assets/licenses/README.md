# Dependencias nativas de audio

- `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`, AAR de Maven Central. Fuente y scripts de compilación: https://github.com/ffmpegkit-maintained/ffmpeg (árbol android-8.1-lts). FFmpeg: https://ffmpeg.org/. Licencia LGPLv3 y licencias de los componentes incluidas en `unpacked/res/raw` y en `assets/licenses` del APK. Se incorporan bibliotecas compartidas arm64-v8a sin modificar.
- `com.arthenica:smart-exception-java:0.2.1` y `smart-exception-common:0.2.1`, BSD 3-Clause. Fuentes: https://github.com/tanersener/smart-exception. Licencia local: `smart-exception-LICENSE.txt`, también incluida en el APK.

`tools/Fetch-Codecs.ps1` fija versiones y SHA256, descarga desde Maven Central y extrae el AAR. El AAR y los archivos extraídos se excluyen de Git. Los dos JAR auxiliares y la licencia se conservan junto al proyecto. Para reemplazar bibliotecas, cambiar la dependencia y recompilar el APK con `build.ps1`; no hay firma de distribución ni verificación de integridad que impida recompilar esta versión de desarrollo.

La variante audio conserva SoXR y los códecs locales. No incluye TLS. OnlineStreamBridge usa HTTPS de Android con validación de certificados y entrega el flujo a FFmpeg mediante un servidor HTTP restringido a 127.0.0.1, puerto efímero y ruta aleatoria por sesión. Admite Range para buscar sin guardar canciones. M4A es un contenedor: las pruebas incluyen AAC y ALAC por separado. DSF y DFF se decodifican a PCM 176.4 kHz; no se implementa DoP ni salida DSD nativa.
