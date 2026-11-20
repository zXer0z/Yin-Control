# Yin Control v3.4.0-yin: consola hardware, salida y ruta de audio

La consola principal (`MainActivity`) organiza toda la configuración e instrumentación en **6 pestañas hardware directas** en la barra inferior iluminada por LEDs:

1. **SALIDA (`YinSoundPage` · sección 0):** carrusel de perfiles por conexión (`Altavoz`, `Jack`, `BT`, `USB`) con indicador LED `EN USO`, interruptor de perfil automático por dispositivo, códec inalámbrico Bluetooth A2DP (control root LDAC / aptX Adaptive / aptX / SBC), Ganancia física del **ESS Sabre Quad DAC** (`Normal`, `Alta Impedancia`, `AUX`), selección de motor (`AudioTrack` o `PCM Directo`), frecuencia, profundidad de bits y tamaño de búfer.
2. **EQ (`YinSoundPage` · sección 1):** curva analítica RBJ en pantalla OLED de 10 bandas, selector de presets (`Plano`, `Bajos`, `Voces`, `Agudos`, `Acústico`), 10 faders verticales `GainFader`, editor paramétrico (`YinDspControls`), importador AutoEQ/APO y módulo Convolver WAV/IRS.
3. **DINÁMICA (`YinSoundPage` · sección 2):** matriz **SOUND COLOR FX** de 6 pads de un toque (`PURE HI-FI`, `BASS CLUB`, `VOCAL STUDIO`, `3D STAGE`, `TUBE WARMTH`, `NIGHT LOUNGE`), fader lineal de preamplificador (`MixerFaderView`), Headroom automático, limitador de picos, Graves / Sub-Woofer, Claridad, Compresor RMS y Calidez Analógica `2×`.
4. **ESPACIO (`YinSoundPage` · sección 3):** Crossfader horizontal `Panorama estéreo Magvel` (`Balance L/R`), **Protección Auditiva Binaural (Cure Tech+)**, **Differential Surround (Haas)**, Escena Estéreo Mid/Side y Calibración fina L/R por oído.
5. **RUTA (`YinRoutePage`):** flujo verificado extremo a extremo:
   - `01 // ORIGEN YANG`: códec, frecuencia y bits del archivo original (`DSP YANG: BYPASS`).
   - `02 // ENTRADA YIN`: PCM float recibido por el socket del puente.
   - `03 // MOTOR YIN`: formato solicitado vs aceptado por `AudioTrack`, flags `0x1 · DIRECT`, búfer, preamp, ganancia final y etapas DSP activas.
   - `04 // ANDROID / HAL & ALSA`: lectura explícita bajo demanda de `dumpsys media.audio_flinger` y nodos `/proc/asound/.../hw_params`.
6. **SISTEMA (`YinConfigPage`):** selector de color OLED (`Menta`, `Cian`, `Ámbar`, `Lavanda`, `Coral`, `Titanio`), opción mantener pantalla encendida con la consola visible, diagnóstico de Root/LSPosed/Quad DAC/Versión, supervisor `service.d` Magisk y reinicio seguro de perfiles DSP.

## Inspección HAL y ALSA sin sondeo continuo

Consultar HAL y ALSA ejecuta únicamente lecturas desde un hilo trabajador por acción directa del usuario (`dumpsys media.audio_flinger` y los `hw_params` legibles en `/proc/asound`). `AudioFlinger` se correlaciona por PID de Yin, sesión de AudioTrack, pista activa y salida fuera de standby. Los contenedores de 32 bits no certifican resolución física del DAC ni implican bit-perfect cuando existe procesamiento DSP activo.

Validación de entrega: compilación release con R8 y verificación funcional directa en LG V60 vía ADB (`versionCode = 148`, `3.4.0-yin`) sin alterar ni borrar pistas reales según `AGENTS.md`.
