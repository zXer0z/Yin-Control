# DSP de Yin Control (v3.4.0-yin) para el audio de Yang

Todos los efectos se inician limpios en el perfil por defecto (`PURE HI-FI`), sin instalar módulos globales del sistema ni alterar `audioserver` o el kernel. El audio PCM decodificado entra desde Yang Player a través del puente autenticado, atraviesa el grafo FFmpeg `f64` dentro de `YinService` y sale por `PcmOutput` (`AudioTrack` / `PCM DIRECT`).

## Controles y módulos de la consola

- **SOUND COLOR FX (6 pads maestros):** `PURE HI-FI`, `BASS CLUB`, `VOCAL STUDIO`, `3D STAGE`, `TUBE WARMTH` y `NIGHT LOUNGE`. Cada pad sincroniza instantáneamente EQ, dinámica, calidez y escena espacial con estado reflejado en la pantalla OLED superior (`YinStudioControls`).
- **EQ de 10 bandas (`EqResponseView` + `GainFader`):** faders verticales lineales de `±12 dB` (`31 Hz` a `16 kHz`) y curva analítica RBJ interactiva con puntos seleccionables en escala logarítmica. Modo paramétrico con frecuencia `20–20 000 Hz`, factor `Q 0,10–12,00`, filtros campana (`PK`), estantería de graves (`LS`) y estantería de agudos (`HS`), aplicables al canal Izquierdo, Derecho o Ambos.
- **Importación AutoEQ / Equalizer APO:** parser local de texto hasta diez filtros `ON PK/LS/LSC/HS/HSC` con ganancia decimal, `Q` y preamplificador (`EffectImports`). Rechaza filtros incompatibles para evitar presets parciales.
- **Convolver IR (`afir`):** carga local de respuestas al impulso `.wav` o `.irs` (mono/estéreo, hasta `2 s`, `16 MB` y `384 kHz`), normalizadas en almacenamiento privado como WAV flotante con nombre único y controles de mezcla (`0–100%`) y ganancia (`-24..0 dB`).
- **Graves y Sub-Woofer:**
  - `Natural`: estantería de graves `f64`.
  - `Sub-Woofer`: sintetizador subarmónico `asubboost` (`cutoff >= 50 Hz`, `dry/wet`, realimentación) combinado con estantería suave.
  - `Dinámico`: `adynamicequalizer` en modo `boostbelow` adaptativo (`auto=adaptive`, ataque `15 ms`, recuperación `180 ms`).
- **Claridad y Detalle:** tres modos calibrados (`Armónico` con `crystalizer`, `Presencia` de agudos a `6,5 kHz` e `Híbrido`).
- **Calidez Analógica (`asoftclip` 2×):** saturación soft-clip seleccionable entre `Válvula (tanh)`, `Cinta (atan)` y `Clase A (cubic)` con sobremuestreo `2×`.
- **Compresor dinámico (`acompressor`):** detección RMS (`Suave 2:1`, `Estudio 3:1`, `Noche 6:1` o ajuste libre de umbral `-40..-6 dBFS`, relación `1..10:1`, ataque `1..100 ms` y recuperación `40..1000 ms`).
- **Protección Auditiva Binaural (Crossfeed Cure Tech+):** presets calibrados `Leve (700 Hz · 45%)`, `Medio (700 Hz · 60%)` y `Fuerte (650 Hz · 95%)`, o ajuste fino (`300–1200 Hz`, `20–95%`) combinando el filtro `crossfeed` y mezcla `pan` frontal para reducir la fatiga auditiva en auriculares.
- **Differential Surround (Efecto Haas):** retardo interaural `1–25 ms` mediante `adelay` + `haas`, selector de inversión (`Derecho retardado` / `Izquierdo Reverse`), mezcla `Wet/Dry 10–100%` y atenuación pasa-bajos en el canal retardado (`1,5–16 kHz`) para proyectar la escena fuera de la cabeza.
- **Panorama Magvel, Escena Estéreo y Calibración L/R:** balance estéreo, expansión Mid/Side (`extrastereo 110–200%`) y trim de ganancia (`-24..+6 dB`) y micro-retardo (`0–20 ms`) independiente por oído.
- **Control de picos (`alimiter`):** preamplificador maestro, reserva automática de margen (`Headroom`) y techo limitador configurable de `-12` a `-1 dBFS`.

> Los efectos exclusivos de auriculares (`Protección binaural`, `Differential Surround` y `Escena estéreo`) se omiten automáticamente en altavoz o señal mono.

## Orden del grafo DSP

```text
Adaptación de frecuencia (SWR/SoXR)
  → Preamplificador / Headroom
  → Ecualizador de 10 bandas (Gráfico / Paramétrico L/R)
  → Convolver IR (si irMix > 0)
  → Graves / Sub-Woofer / Dinámico
  → Claridad → Compresor → Calidez Analógica
  → Protección Binaural (Cure) → Differential Surround (Haas) → Escena Estéreo
  → Calibración L/R + Balance
  → Limitador de picos digitales
```

## Perfiles, telemetría y sistema

- **Auto-perfil por dispositivo:** opcional (`opt-in`), genera clave SHA-256 local a partir de tipo, dirección y nombre del dispositivo expuesto por Android, heredando inicialmente la configuración de su conexión (`Altavoz`, `AUX/Jack`, `BT` o `USB`).
- **Medidores PCM (`SignalMeterView` / `PcmMeter`):** puente de 28 LEDs (`Verde → Ámbar → Rojo`) en cabecera y medidores en tiempo real de entrada y salida en `dBFS`. Son mediciones digitales PCM, no un sonómetro SPL ni cálculo de dosis acústica.
- **Apariencia OLED (`YinConfigPage`):** 6 colores de acento (`Menta`, `Cian`, `Ámbar`, `Lavanda`, `Coral`, `Titanio`) que tiñen LEDs, bordes activos, faders, pantallas y acentos del chasis.
- **Supervisor root opcional (`service.d`):** script Magisk ligero que espera `sys.boot_completed=1` y verifica cada `60 s` la disponibilidad de Yin sin sostener wakelocks permanentes.
