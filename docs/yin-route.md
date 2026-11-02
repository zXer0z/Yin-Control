# Yin Control: sonido y ruta de audio

La actividad principal ahora contiene dos páginas nativas: Sonido (preferencias
por conexión, PCM directo, formato, búfer, remuestreador, EQ, preamplificador,
balance, protección, Quad DAC y códec Bluetooth) y Ruta de audio.

Sonido usa un carrusel de Altavoz / AUX / Bluetooth / USB con estado de la ruta
activa y tres secciones: Salida y DAC, Ecualizador, Volumen. Navegar por el
carrusel selecciona el perfil editable; no fuerza la conexión del teléfono.

La cabecera y el carrusel se crean una sola vez; cambiar de sección solo actualiza
el contenedor de ajustes. La notificación de selección del carrusel se difiere al
siguiente turno UI para evitar mutar hijos durante dispatchDraw. Las tarjetas
tienen 12 dp de separación entre sí y paginadores de 5/16 × 4 dp dentro de áreas
táctiles mayores. Los formatos, frecuencias y búfer se eligen dentro de la página,
sin diálogos. Bluetooth muestra códec/calidad y opciones agrupadas junto a la salida.
El EQ usa GainFader vertical con área táctil propia y accesibilidad de rango.

Corrección del puente: drainForHandoff termina de escribir un bloque DSP local
pendiente antes de esperar su reproducción. Antes se esperaba hasPendingData sin
escribir el resto del bloque, lo que podía impedir reconectar con Yin. Los cambios
de preferencias se agrupan durante 400 ms y solo invalidan la sesión si cambió su
perfil activo; guardar el mismo contenido o editar otra conexión no lo reinicia.

La presentación principal de Ruta es Yang (archivo original), Yin (PCM recibido),
salida de Yin (formato solicitado, aceptado, DSP, flags y búfer), Android/HAL
(comparación de frecuencia y formato). El dump y ALSA quedan en Detalle técnico.
Yang incluye formato, frecuencia y profundidad de biblioteca en los extras de
cada MediaItem. El servicio combina esos datos con el formato de entrada del
decodificador y los transmite por el puente autenticado, también al cambiar de
pista. Los datos desconocidos se muestran como no informados. El PCM del puente
sigue sin pasar por el DSP local de Yang; un archivo FLAC se decodifica antes de
transportarlo y no se identifica como FLAC en la salida PCM del dispositivo.

El monitor recibe una instantánea sincronizada de PcmSession. Muestra el PCM
recibido desde Yang, el plan DSP realmente utilizado y la configuración del
AudioTrack abierto. El formato solicitado se captura antes de negociar la salida,
para conservarlo incluso si PcmOutput necesita una alternativa compatible.

Consultar HAL y ALSA ejecuta únicamente lecturas, desde un trabajador y por acción
del usuario: dumpsys media.audio_flinger y los hw_params de reproducción legibles
en /proc/asound. AudioFlinger se vincula por PID de Yin, sesión AudioTrack, pista
activa y salida fuera de standby. Cero coincidencias o varias coincidencias no se
interpretan como una salida confirmada. El parser conserva el esquema usado por
Zero en el LG V60; otras versiones del dump pueden quedar sin interpretar.

La frecuencia del hilo se presenta separada de una frecuencia HAL explícita. El
formato HAL proviene de su campo específico. Los hw_params activos se presentan
como datos del sistema sin atribuirlos a Yin: no hay correlación HAL/ALSA de
tarjeta y dispositivo validada. Los contenedores de 32 bits no indican resolución
física del DAC y ninguna consulta certifica bit-perfect ni mide señal analógica.

Toda consulta lleva instante y clave de sesión/dispositivo/formato. Pausar,
finalizar o cambiar la ruta invalida su presentación como lectura de la sesión
actual. No hay sondeo root periódico. El usuario puede copiar el resumen.

Referencia para separar formato y frecuencia HAL de los parámetros de AudioFlinger:
https://android.googlesource.com/platform/frameworks/av/+/ca5e614%5E%21/

Validación de entrega: compilación release con R8 y lint vital; las pruebas
funcionales y de escucha las realiza el usuario según AGENTS.md.
