# DSP de Yin para el audio de Yang

Los efectos nuevos están desactivados por defecto. No se instala un módulo global,
no se modifica audioserver ni el kernel. El audio sigue entrando por el puente PCM
autenticado de Yang; FFmpegKit procesa el grafo y Yin conserva su AudioTrack.

## Controles

- EQ: diez bandas gráficas o paramétricas, frecuencia 20–20000 Hz, Q 0,1–12,
  campana/estantería de graves/estantería de agudos. Barras gráficas en pasos de
  1 dB; editor paramétrico en pasos de 0,1 dB y Q de 0,01. Diez bandas visibles
  en dos filas y separación entre tipos de filtro/importación.
- Correcciones: importación local de texto AutoEQ/Equalizer APO, hasta diez filtros
  ON PK/LS/LSC/HS/HSC con Fc/Gain/Q y preamplificador. Rechaza filtros ON no
  compatibles en lugar de aplicar un preset parcial. Conserva decimales de
  ganancia, Q y preamplificador; acepta signos + y -. Perfiles antiguos de
  ganancias enteras se migran al cargar, sin perder sus ajustes.
- Convolver: WAV o IRS que contenga WAV, mono/estéreo, hasta 2 s, 16 MB y 384 kHz.
  Se copia a almacenamiento privado y convierte a WAV float con nombre único.
  No se sobrescribe un IR que una sesión anterior pueda estar leyendo.
- Graves dinámicos: filtro dinámico de estantería, boostbelow/adaptive, máximo
  configurable, con reserva de margen al activar headroom.
- Compresor: umbral, ratio, ataque y recuperación; sin makeup automático.
- Crossfeed: mezcla estéreo filtrada con intensidad ajustable.
- Surround diferencial: efecto Haas con retardo de 1–25 ms e intensidad; no es
  una reproducción del algoritmo propietario de ViPER.
- Antifatiga: atenuación ancha configurable entre 2–6 kHz y estantería suave de
  agudos desde 7,5 kHz. Es un ajuste tonal estático de comodidad, no detección de
  frecuencias dañinas ni una garantía de protección auditiva.
- Control de picos: techo de -18 a -1 dBFS y atenuación adicional. El limitador
  es de picos digitales, no un limitador true-peak con sobremuestreo ni un medidor
  de presión sonora. No calcula exposición acústica ni promete un volumen seguro.

Los efectos de auriculares se omiten en altavoz; los efectos estéreo se omiten
con un único canal. Espacio muestra claramente esta condición.

## Grafo y estado

Adaptación de frecuencia → preamplificador/margen → etapas en el orden guardado
(IR, EQ, graves, espacio, compresor y canales) → atenuación/balance → limitador.
La convolución utiliza split, afir particionado y mezcla seca/procesada; las etapas
anteriores y posteriores al IR respetan su posición real. Sin IR se usa -af.
El remuestreo respeta SWR/SoXR. Mezcla IR cero omite la convolución.

Las preferencias nuevas están dentro del JSON del perfil. «Perfil automático por
dispositivo» es opt-in: clave local derivada de tipo/dirección/nombre con SHA-256;
hereda el perfil de conexión hasta guardar sus propios ajustes. No distingue dos
auriculares pasivos conectados al mismo jack si Android no expone su identidad.
Los callbacks de dispositivos invalidan la sesión únicamente cuando cambia la
clave efectiva o el contenido del perfil; se conserva el debounce de 400 ms.

## Validación de entrega

Compilación release con R8 y lint vital. Inspección estática de la biblioteca
incluida para confirmar presencia de los filtros y opciones usados; API Java
FFprobe verificada con javap. No se ejecutaron pruebas automatizadas ni de escucha
por preferencia de AGENTS.md; importación de archivos y efectos quedan para la
comprobación funcional del usuario en el teléfono.

Fuentes de diseño:
- https://ffmpeg.org/ffmpeg-filters.html
- https://github.com/FFmpeg/FFmpeg/tree/n8.0/libavfilter
- https://github.com/likelikeslike/ViPER4Android (Cure descrito como crossfeed)
- https://www.who.int/news-room/questions-and-answers/item/deafness-and-hearing-loss-safe-listening

## Estudio y configuración

- Curva analítica RBJ del EQ: dos canales, puntos seleccionables, escala logarítmica.
  Usa la frecuencia de la sesión del mismo perfil o la seleccionada (48 kHz de
  referencia si es automática sin sesión). Omite filtros por encima de Nyquist.
  No mide auriculares ni incluye IR, preamp u otros efectos. Apagado conserva la
  curva guardada en gris y bloquea los faders gráficos.
- Medidores PCM RMS/pico antes y después del DSP. Ventanas de 100 ms y visualización
  aproximada de 400 ms, actualización UI de 200 ms. No son SPL ni exposición auditiva.
- A/B guarda solo ajustes de sonido, sin cambiar backend/formato. Estima ganancia
  salida/entrada con 5 s de señal alineados por tiempo de muestras. Es aproximado,
  no LUFS ni loudness perceptual: conviene comparar el mismo fragmento. Solo atenúa
  el ajuste de mayor nivel; cambio gradual hasta 1 dB cada 500 ms. Editar un ajuste
  fuera de las instantáneas suspende la igualación hasta guardarlo de nuevo.
- Cadena por perfil: arrastrar una fila o flechas; apagar conserva posición.
  Preamplificador primero y limitador último no se reordenan.
- Paramétrico: destino izquierdo/derecho/ambos por banda. Etapa de canales:
  ganancias -24 a +6 dB y retardos 0–20 ms por canal, solo estéreo.
- Config: seis paletas que derivan fondo, tarjetas, bordes, botones, selección,
  faders e interruptores del acento. Pantalla encendida solo con el panel visible.
  Restablecer requiere segundo toque y borra todos los perfiles y A/B; conserva
  preferencias visuales, biblioteca y archivos originales. No cambia modo físico
  del DAC ni códec Bluetooth del sistema.
- Supervisor root opcional en service.d: espera boot_completed, lock exclusivo,
  revisa proceso y servicio cada 60 s y solicita arranque si falta. El motor DSP
  sigue siendo un servicio Android autenticado, no un proceso nativo independiente.
  La actualización del supervisor se instala al abrir Yin activo o activar el motor.

Entrega: compilación release R8 y lint vital, instalación por adb. Sin pruebas
funcionales ni de escucha, según preferencia del usuario.
