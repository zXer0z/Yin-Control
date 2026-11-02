# V60: capacidades con el kernel instalado, sin modificarlo

Lectura de solo consulta del 2026-11-01: uname devuelve
4.19.325-v60-dop-lab3+. La política de audio actual declara:

- Puerto Speaker: PCM16, 48000 Hz. El perfil genérico direct_pcm también menciona
  speaker entre muchos destinos; esto no demuestra que el amplificador del altavoz
  reciba realmente todas las frecuencias que declara ese perfil genérico.
- direct_pcm: PCM16 hasta 192000; PCM8_24, PCM24_PACKED y PCM32 hasta 384000,
  incluyendo 352800. La presencia de 352800 en la política no revoca el rechazo
  observado previamente en la implementación PCM, documentado en AudioSettings.
- dop: flags DIRECT|DOP, portadores 176400 y 352800, solo jack.
- zero_bt96_direct: PCM24_PACKED/PCM32, 96000 Hz, solo A2DP. Son contenedores
  admitidos por la política root; no prueban la calidad ni el bitrate del enlace.
- hifi_playback USB: formato/canales/frecuencias dinámicos. No hay un DAC USB
  conectado en la consulta, por lo que no se puede fijar su máximo.

Zero DopBackend.playbackValidated y DopLabGate.environment exigen explícitamente
el kernel lab3, además de hashes de HAL/políticas, estado de AUX y confirmación
de ALSA/MI2S/ESS. La reproducción de archivo implementada está restringida a
DSD128. No afirmar que esto funciona en un kernel stock ni habilitar DoP en el
puente PCM de Yin: primero debe recuperarse el transporte separado de bitstream.

ES9219 soporta PCM32/384 y DSD256 en el chip. DSD256 no implica que el teléfono
exponga una ruta DSD nativa utilizable. DoP256 requiere portador 705600, que no
figura en esta ruta; el DoP128 anterior usa 352800. Root y LSPosed no añaden
capacidades físicas al DAC ni al amplificador.

Propuesta de controles: altavoz 16/48 como perfil confirmado; jack PCM hasta
384 con puente validado y variantes experimentales separadas; Bluetooth según
códec/dispositivo negociado (LDAC 24/96 como objetivo, PCM32 marcado contenedor);
USB según DAC, driver y límites reales del motor. El motor USB actual de Yin
limita PCM a 192000: es límite de software, no un máximo universal del V60.
No se cambiaron controles basándose solo en perfiles genéricos o cifras del chip.

Configuración solicitada posteriormente por el usuario y aplicada en Yin:
altavoz fijo PCM16/48000; Bluetooth hasta PCM24/96000, sin alternativa PCM32 en
PcmOutput; jack conserva 16/24/32/Float32 y las frecuencias musicales anunciadas,
incluidas 32/64/128 kHz. Float selecciona AudioTrack estándar. 352800 se ofrece
explícitamente como experimental junto con 384000 cuando existe puente de alta
frecuencia; ambas solicitan entero32 y conservan la alternativa compatible y su
diagnóstico si se rechaza DIRECT. Esto no equivale a confirmar que 352800 funcione.
El usuario confirmó que «12 bits» era una errata por 16 bits. USB no se ha probado.

Efectos PCM posibles sin modificar kernel: EQ paramétrico por canal, convolución
FIR/IR, crossfeed, anchura estéreo, retardos, compresor, limitador con sobremuestreo,
loudness/graves dinámicos y perfiles por dispositivo. Requieren implementar DSP;
no están todos presentes. El puente actual procesa Yang. Procesamiento global
para otras apps requiere integración adicional con efectos/rutas del sistema.
En DoP, bloquear DSP PCM para preservar el bitstream.

Fuentes:
- https://www.esstech.com/essnews/lg-v60-adopts-ess-technologys-third-generation-headphone-soc-with-quad-dac-technology/
- https://www.esstech.com/wp-content/uploads/2026/03/ES9219-Datasheet-v1.3.pdf
- https://dsd-guide.com/dop-open-standard
- https://www.sony.co.jp/en/Products/LDAC/info/
- https://source.android.com/docs/core/audio/usb
