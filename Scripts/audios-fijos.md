# Audios fijos del canal de WhatsApp

Notas de voz pregrabadas que el back manda en lugar de sintetizar en cada envío, solo cuando el
paciente habló por audio (diseño en `Docs/Planes/Propuesta-Prompts-Verificacion-Agente.md §6.1`).
Suenan siempre igual, salen al instante y no dependen de OpenAI.

- **Dónde viven:** en el servidor, `/root/docker/accesmed/<entorno>/audios/`, montada de solo lectura
  en el contenedor (`/app/audios`). En local, `./audios` en la raíz del repo (gitignored). El back
  toma la carpeta de `accesmed.audio.fijos-dir` (`ACCESMED_AUDIO_FIJOS_DIR`).
- **Cómo se generan:** `python -I Scripts/generar-audios-fijos.py` desde la raíz del repo. Lee la
  key de `ACCESMED_OPENAI_API_KEY` (`.env`), el modelo, la voz y las instrucciones de
  `src/main/resources/application.yml` (los mismos que las respuestas) y escribe los `.ogg` en
  `./audios`. Se escuchan, se aprueban y se suben al servidor.
- **Reglas de los textos:** sin nombre de la clínica, horario, nombre del paciente ni otro dato que
  salga de la base (un audio grabado no se actualiza solo). Sin emojis ni formato. Con voseo, como
  todo el bot, y con la misma voz femenina (`coral`) que las respuestas (decidido el 9/11).

Si cambia un texto o la voz, se edita esta lista, se regeneran los audios y se vuelven a subir.
El script lee las líneas con el formato `- \`archivo\`: texto`.

## Textos

- `bienvenida`: ¡Hola! Soy el asistente virtual de la clínica. Un consejo para entenderte mejor: contame lo que necesitás en un solo mensaje o en un audio, y esperá mi respuesta antes de mandar el siguiente. Ya reviso tu mensaje.
- `espera`: Aguardá un momento, por favor, estoy procesando tu mensaje.
- `despedida`: ¡De nada! Que tengas un lindo día. Cuando necesites algo, escribinos por acá.
- `derivacion`: Listo, te paso con una persona del equipo. En breve te responden por acá.
- `audio-no-entendido`: No pude escuchar bien tu audio. ¿Me lo mandás de nuevo o me lo escribís?
- `audio-largo`: Tu audio es un poco largo para mí. ¿Me lo mandás en uno más corto o me lo escribís?

## Cuándo se usa cada uno

| Archivo | Cuándo |
|---|---|
| `bienvenida.ogg` | Primer mensaje de la ventana, si es un audio |
| `espera.ogg` | Aviso inicial de la tanda, si el mensaje que la abre es un audio. Reemplaza al texto |
| `despedida.ogg` | Respuesta con `—FIN—` |
| `derivacion.ogg` | Respuesta con `—HANDOFF—` |
| `audio-no-entendido.ogg` | El audio no se pudo transcribir |
| `audio-largo.ogg` | El audio supera el tamaño máximo |
