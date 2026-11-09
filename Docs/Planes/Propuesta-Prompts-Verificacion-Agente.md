# Propuesta: prompts, verificación, mensajes y consultas del agente

> Estado: **propuesta, sin aplicar**. Sale de la revisión de los prompts que corren en staging y de las
> 299 ejecuciones guardadas en Flowise (9/11/2026), más las recomendaciones del 9/11 (secciones 5 a 7).
> Afecta: los prompts y nodos del flow (repo AccesMed-Flowise, se editan en la UI de staging y se
> exportan), la verificación del back (`POST /Agente/Verificacion`), los mensajes del canal
> (`ChatwootService`, `TandaAgenteService`, `OpenAiService`) y las herramientas de consulta
> (`AgenteController` → `AgenteApp` → `AgenteServices` → QueryServices).

## 0. Por qué

Desde que se activó la verificación (9/11, ~10:43) hubo **8 rechazos: 2 verdaderos y 6 falsos**, y
**3 derivaciones automáticas por agotar los reintentos, las 3 con al menos un rechazo falso**. Los
verdaderos (DIU y Ecocardiograma Doppler "no requieren validación") muestran que la verificación sirve; los falsos
salen de reglas del prompt que chocan con los controles:

| Caso real | Qué dijo el agente | Por qué se rechazó | Causa |
|---|---|---|---|
| Conv. 16, 14:26 | "No encontré horarios por la tarde" (cierto) | El back miró todas las franjas | `DisponibilidadAfirmada` no tiene franja |
| Conv. 16, 14:26 | "...o del jueves 26/11" (cierto) | Fecha escrita sin declarar | El prompt no dice que una mención de pasada también se declara |
| 9/11, 10:44 | "No tengo disponible jueves 12/11 10:00" (cierto) | Hora escrita sin declarar | El prompt pide decir si la hora pedida está; no hay dónde declarar que **no** está |

Y hay huecos en lo que se verifica: obras sociales y planes no se verifican, y los montos que se escriben
no se cruzan con los declarados.

Decisión tomada (9/11): **ante algo no concluyente, el agente ofrece pasar con una persona y deriva
solo si el paciente acepta.**

---

## 1. Prompt común (`prompts/comun.md`)

### 1.1 Nueva sección "Cuándo ofrecer pasar con una persona"

Va después de "Regla de oro: nunca inventes". **Reemplaza** la regla 7 de "Cómo conversar" y el "ofrecé…
pasar con una persona" sueltos de "SOLO usás los datos que te da el sistema".

```markdown
## Cuándo ofrecer pasar con una persona
Si no tenés una respuesta concluyente, no la completes ni la supongas: ofrecé pasar con una persona
del equipo preguntando "¿Querés que te pase con una persona del equipo?". No derives sin que el
paciente diga que sí; si dice que no, seguí con lo que sí podés hacer.

Ofrecelo cuando:
1. Lo que pregunta no lo responde ninguna herramienta (guardia, estacionamiento, medios de pago,
   políticas, preguntas médicas). Decí "no tengo ese dato" y ofrecé.
2. Una búsqueda vuelve vacía o ambigua y, después de repreguntarle una vez, sigue igual.
3. Lo que dice el paciente no coincide con el sistema (dice que tiene un turno y listTurnos no lo
   trae). Contale lo que muestra el sistema y ofrecé.
4. Una herramienta devuelve `ok: false` y su `instruccion` no te deja resolverlo (por ejemplo, fuera
   de plazo para cancelar o DNI registrado con otro teléfono).
5. El paciente repite el mismo pedido y, después de dos intentos, no lo pudiste resolver.

Si el paciente responde que sí a ese ofrecimiento, decí que lo pasás con alguien del equipo y
terminá con —HANDOFF—.

Derivá directo, sin preguntar, solo en dos casos:
- El paciente pide hablar con una persona.
- Una herramienta devuelve `inesperado: true` (falla del sistema): pedí disculpas y terminá con —HANDOFF—.
```

La regla 6 (errores de herramientas) queda igual, pero el caso `ok: false` sin solución pasa a remitir a esta
sección.

### 1.2 "Formato de salida": declarar todo lo que se escribe

Cambia el esquema de `disponibilidad` (franja y hora opcionales) y se agrega `obrasSociales`:

```json
"disponibilidad": [{"fecha": "2026-11-12", "franjaHoraria": "TARDE", "hora": null,
                    "prestacion": "Consulta Cardiológica", "matricula": "MP-10234", "hay": false}],
"obrasSociales": [{"nombre": "OSDE", "planes": ["OSDE 210", "OSDE 310"]}]
```

Y el párrafo final queda:

```markdown
**Toda fecha, hora, matrícula, código de turno o monto que escribas va declarado**, aunque sea una
mención de pasada ("también hay el jueves 26/11"):
- Un horario que ofrecés → `horarios`.
- Que hay o que no hay turnos un día, en una franja o en una hora puntual → `disponibilidad`, con
  `fecha`, `franjaHoraria` (MANANA, TARDE, NOCHE) y `hora` según lo que consultaste.
  Ejemplos: "no hay a la tarde" → `franjaHoraria: "TARDE", hay: false`; "el jueves 12 a las 10 no está
  libre" → `fecha: "2026-11-12", hora: "10:00", hay: false`; "también hay el 26/11" →
  `fecha: "2026-11-26", hay: true`.
- Obras sociales y planes que nombrás → `obrasSociales`.
- Un monto → `cotizaciones`.
Si no afirmaste ningún dato concreto, escribí `<datos>{}</datos>`.
```

### 1.3 Ajustes menores

- "Regla de oro" punto 3: agregar "…ni que un horario puntual está o no está libre".
- "SOLO usás los datos…": el "Si un dato no está, decí 'no tengo ese dato'…" pasa a apuntar a la
  sección 1.1.

---

## 2. Prompts de cada agente

| Archivo | Cambio |
|---|---|
| `turno-nuevo.md`, paso 3 | "Si el paciente pidió un día y hora concretos, decile si está entre los disponibles **y declaralo en `disponibilidad` con `fecha`, `hora` y `hay`**; si no está, ofrecé los más cercanos de la lista." |
| `turno-nuevo.md`, paso 2 | "Si pidió una franja y no hay horarios, decilo (declarado con `franjaHoraria`) y ofrecé otra franja o ver sin preferencia." |
| `oferta.md`, paso 6 | "…respondé con lo que devuelve **y declaralo en `obrasSociales`**." |
| `oferta.md`, "Cosas que no sabés" | Remitir a la sección 1.1 (ofrecer, no derivar). |
| `mis-turnos.md`, errores | "Fuera de plazo para cancelar" y "la prestación o el plan ya no están" → remitir a 1.1 (ya ofrecían; queda unificado). |
| `mis-datos.md`, errores | "DNI ya registrado con otro teléfono" → remitir a 1.1. |
| `router.md`, ejemplos de continuación | **Agregar:** "'sí' después de '¿Querés que te pase con una persona del equipo?' es 'Pide hablar con una persona o dice que no entiende al asistente'." Sin esto, el "sí" se enruta al agente de la gestión y no al nodo Operador. |

---

## 3. Flow: nodos `Verificar *` y reintentos

Los cuatro nodos tienen la misma función; el cambio va en los cuatro.

1. **Mensaje de rechazo accionable.** El de "no declarado" pasa a: "Escribiste X pero no está en
   `<datos>`. Si salió de una herramienta de este mensaje, declaralo (horarios, disponibilidad o
   cotizaciones); si no, sacalo del texto." Así el reintento sabe qué hacer y no repite el error.
2. **Montos.** Sumar al control del texto: cada `$ 85.800` / `$85800` escrito tiene que estar como
   `monto` en `cotizaciones`.
3. **Horas de `disponibilidad`.** El control ya toma `fecha` y `hora` de `disponibilidad` como
   declaradas; con el esquema nuevo, "el 12/11 a las 10:00 no está" deja de rechazarse.
4. **Enviar `obrasSociales` al back.** Agregar la clave a la lista que arma el `cuerpo`.
5. **Agotar los reintentos ofrece en vez de derivar.** El `fallbackMessage` de los 4 Loop pasa de
   "Perdón, no pude completar eso. Te paso con una persona del equipo. —HANDOFF—" a
   "Perdón, no pude completar eso. ¿Querés que te pase con una persona del equipo?" (sin token). Con
   el ejemplo nuevo del router, el "sí" llega al nodo Operador. Coherente con la decisión del 9/11.
6. `maxLoopCount` queda en 2: cada reintento cuesta 10–15 s. Se revisa cuando bajen los rechazos falsos.

---

## 4. Back: `POST /Agente/Verificacion`

Cambios compatibles hacia atrás (los campos nuevos son opcionales): el back se puede desplegar antes
que el flow.

### 4.1 `DisponibilidadAfirmada`

`Records/Agente/Request/VerifyRespuestaAgenteRequest.DisponibilidadAfirmada` suma
`FranjaHorariaAgente franjaHoraria` y `LocalTime hora` (los dos opcionales).

`VerificacionAgenteService.verifyDisponibilidad`:
- Con `hora`: exige `fecha` (si falta, rechaza pidiendo la fecha) y verifica si existe un horario libre
  en esa fecha y hora.
- Sin `hora`: filtra por `fecha` (si viene) y por `franjaHoraria` (si viene), con el mismo
  `LocalTimeFilter` sobre `horaDesde` que usa `AgenteApp.listHorariosDisponibles`.
- `findSlotsLibres` suma el parámetro de franja.
- El motivo se arma según lo que vino, sin "una fecha desconocida": "Sí hay horarios libres el
  12/11/2026 a la tarde. Consultalos antes de decir que no hay." / "No hay horarios libres a la
  tarde." / "El 12/11/2026 a las 10:00 no hay un horario libre."

### 4.2 `ObraSocialAfirmada` (nuevo)

`record ObraSocialAfirmada(String nombre, List<String> planes)` y la lista `obrasSociales` en
`VerifyRespuestaAgenteRequest`. Verifica que la obra social exista y esté activa y que cada plan
nombrado sea suyo y esté vigente (la misma consulta que alimenta `listObraSocial` del canal). El
response suma la lista correspondiente.

### 4.3 Documentación (mismo cambio)

- `Docs/Diseno-Chatbot.md` §4.3: esquema de `<datos>` (≈ línea 1353) y tabla de qué se verifica
  (≈ línea 1373): `disponibilidad` con franja y hora, y `obrasSociales`.
- `Docs/Features/Chatbot.md` (≈ línea 733): lista de claves del endpoint.

---

## 5. Mensajes de seguimiento

### 5.1 Qué hay hoy

| Momento | Mensaje | Dónde |
|---|---|---|
| Llega el mensaje que abre una tanda | "⏳ Un momento…" | `ChatwootService.MENSAJE_ESPERA` (D41) |
| El agente llama a una escritura | "⏳ Reservando / Cancelando / Reprogramando / Confirmando tu turno…", "⏳ Guardando tus datos…" | `TandaAgenteService` + `AgenteApp` (uno por tanda) |
| El agente llama a una consulta | — | Se quitaron el 9/11 (commit `3703416`): llegaban 8–12 s tarde (B4) y "Un momento" ya cubría la espera |

### 5.2 Por qué conviene volver a las consultas

Los 8–12 s de "tarde" son justamente el hueco que hoy queda en silencio: "Un momento" sale al
recibir el mensaje y la respuesta llega 15–35 s después. Un segundo aviso a los ~6 s, que además
dice **qué** se está haciendo, le da seguimiento al paciente. Es la misma razón por la que se
mantuvieron los de escritura.

### 5.3 Propuesta

**Texto del aviso inicial:** "⏳ Aguardá un momento, estoy procesando tu mensaje…". Va con voseo,
como el resto del bot ("Aguarde" mezclaría usted y vos).

**El aviso inicial no sale antes de la bienvenida.** Hoy salen en orden, pero si la bienvenida va
en audio, la síntesis tarda ~3–4 s y WhatsApp entrega el audio después que el texto: el paciente ve
"Un momento" primero. Propuesta: en el **primer mensaje de la ventana no se manda aviso aparte**;
la bienvenida termina con "Ya reviso tu mensaje 🙂" y cumple las dos funciones.

**Todos los puntos donde se puede avisar:**

| # | Punto | Aviso | Tiempo desde el mensaje | Propuesta |
|---|---|---|---|---|
| 1 | Llega el mensaje (no es el primero de la ventana) | "⏳ Aguardá un momento, estoy procesando tu mensaje…" | ~0,2 s | Mantener (texto nuevo) |
| 2 | Primer mensaje de la ventana | Bienvenida con "Ya reviso tu mensaje 🙂" | ~0,2 s (texto) | Reemplaza al 1 |
| 3 | Transcripción del audio | "🎧 Escuchando tu audio…" | ~4 s | **No**: dura 1–2 s, no aporta |
| 4 | Consulta de catálogo (especialidades, prestaciones, médicos) | "🔎 Revisando la cartilla…" | ~6–8 s | **Volver** |
| 5 | Consulta de horarios | "🔎 Buscando horarios disponibles…" | ~6–8 s | **Volver** |
| 6 | Consulta de turnos del paciente | "🔎 Revisando tus turnos…" | ~6–8 s | **Volver** |
| 7 | Cotización / cobertura | "🔎 Revisando tu cobertura…" | ~8–12 s | **Nuevo** |
| 8 | Datos del paciente, clínica, obras sociales | — | — | No (rápidas, se encadenan con otras) |
| 9 | Escrituras | Los de hoy | — | Mantener |
| 10 | Reintento por verificación rechazada | "🔁 Estoy chequeando la información, ya te respondo…" | ~15–20 s | **Nuevo**: es el tramo más largo sin aviso (10–15 s extra) |
| 11 | Flowise pasa los 20 s sin responder | "Sigo con tu consulta, ya casi está 🙏" | 20 s | Opcional (hoy en backlog); con 10 cubre casi todo |

**Reglas para no saturar:** cada aviso sale **una vez por tanda y por tipo**; como mucho **dos avisos
de herramienta por tanda** (a veces el agente encadena catálogo → horarios → cotización); en el reintento
de la verificación no se repiten los avisos de herramienta (sale solo el 10).

**Cambios en el back:**
- `AccionAgente` vuelve a tener las consultas (`CONSULTA_CATALOGO`, `CONSULTA_HORARIOS`,
  `CONSULTA_TURNOS`) y suma `CONSULTA_COBERTURA`.
- `TandaAgenteService` pasa de un booleano por tanda a un conjunto de tipos ya avisados más un
  contador (tope de 2).
- El aviso 10 lo dispara `AgenteApp.verifyRespuestaAgente`: la segunda verificación de la misma tanda
  es un reintento.
- `Docs/Features/Chatbot.md §6.H` y `Docs/Diseno-Chatbot.md §3.4.6`: tabla nueva.

### 5.4 Aviso en audio cuando el paciente habla por audio

Es viable y barato: el aviso es un texto fijo, así que es uno de los **audios fijos versionados**
(6.1): no se sintetiza en cada envío y sale al instante, como el texto.

- Si el mensaje que abre la tanda es un audio, el aviso 1 sale como nota de voz ("Aguardá un momento,
  por favor, estoy procesando tu mensaje") en lugar del texto.
- Si la síntesis falló o el audio está apagado, sale el texto.
- Los avisos de herramientas (4–7, 10) siguen en texto: son cortos, llegan rápido y una nota de voz
  por cada uno sería invasivo. Decidido el 9/11.
- WhatsApp entrega una nota de voz ~1–2 s más tarde que un texto; con Flowise tardando 10 s o más no
  importa.

---

## 6. Audio, bienvenida y menú

### 6.1 Siempre la misma voz

La voz **ya es fija**: el back manda `voice: coral` y las mismas `instructions` en cada síntesis
(`accesmed.audio.voz`, `instrucciones-voz`). Lo que cambia entre audios es la **interpretación**:
`gpt-4o-mini-tts` es un modelo "dirigible" y no determinista, así que con la misma voz varían el
tono, el ritmo y el acento de una síntesis a otra. No tiene parámetro de temperatura ni de semilla.

Propuesta, en este orden:
1. **Audios fijos pregrabados** (decidido el 9/11): se generan una vez con la API de OpenAI, se
   aprueban escuchándolos y el back los lee de una carpeta del servidor. Suenan siempre idénticos,
   salen al instante, no gastan API y no fallan si OpenAI no responde.
   - **Cuáles:**

     | Archivo | Cuándo se manda (si el paciente habló por audio) |
     |---|---|
     | `espera.ogg` | Aviso inicial de la tanda (5.4) |
     | `bienvenida.ogg` | Primer mensaje de la ventana (6.2) |
     | `despedida.ogg` | Respuesta con `—FIN—`: el nodo Despedida del flow o el cierre que escribe el agente. Reemplaza la síntesis de esa respuesta; el texto sale igual |
     | `derivacion.ogg` | Respuesta con `—HANDOFF—` (Operador, fallback del loop, derivación del agente). *A confirmar* |
     | `audio-no-entendido.ogg`, `audio-largo.ogg` | Avisos de audio que no se pudo usar: solo los reciben quienes mandan audios. *A confirmar* |

     El cierre por inactividad y el fuera de horario siguen solo en texto (el paciente no está
     esperando o el mensaje lleva el horario, que cambia).
   - **Dónde:** una carpeta en el servidor, `/root/docker/accesmed/<entorno>/audios/`, al lado de
     `postgres-data/`, montada de solo lectura en el contenedor (`../audios:/app/audios:ro` en
     `docker/deploy/docker-compose.yml`). El back la toma de la property
     `accesmed.audio.fijos-dir: ${ACCESMED_AUDIO_FIJOS_DIR:./audios}`: el compose la fija en
     `/app/audios` (es fija, no va al `.env`) y en local apunta a `./audios` en la raíz del repo
     (gitignored). Es opcional: sin carpeta o sin un archivo, el back loguea `warn` al arrancar y manda
     el texto.
   - **Cómo se generan:** un script en `Scripts/` (`generar-audios-fijos`) llama una vez a
     `/v1/audio/speech` con el mismo modelo, voz e instrucciones de `application.yml` y escribe los
     `.ogg` (Opus, como las respuestas). Se corre a mano cuando cambia un texto o la voz. Los textos de
     cada audio se versionan en `Scripts/audios-fijos.md`, para saber qué dice cada uno y poder
     regenerarlos si se pierde la carpeta del servidor.
   - **Sin datos que cambian:** el audio no nombra la clínica ni el horario de atención (salen de la
     tabla `Clinica` y un audio commiteado quedaría desactualizado). Esos datos van en el texto que
     acompaña a la bienvenida. Ejemplo de audio: "¡Hola! Soy el asistente virtual de la clínica. Un
     consejo para entenderte mejor: contame lo que necesitás en un solo mensaje o en un audio, y
     esperá mi respuesta antes de mandar el siguiente. Ya reviso tu mensaje."
   - **Si falta un archivo** (o no se puede leer), el back loguea `warn` al arrancar y manda el texto.
   - El fuera de horario sigue solo en texto: lleva el horario, que cambia.
2. **Instrucciones de voz más cerradas** (acento rioplatense, mismo ritmo, mismo tono, sin
   dramatizar), para acotar la variación de las respuestas, que no se pueden cachear.
3. Si sigue variando: probar `tts-1-hd`, que es más estable pero no acepta instrucciones (pierde el
   acento rioplatense). Se decide escuchando.

### 6.2 Bienvenida con una ayuda breve

Hoy: "¡Hola, Franco! 👋 Soy el asistente virtual de la clínica X. Nuestro horario de atención es de
8:00 a 20:00 h."

Propuesta (texto):

> ¡Hola, Franco! 👋 Soy el asistente virtual de la clínica X. Atendemos de 8:00 a 20:00 h.
> Un consejo para que te entienda mejor: contame lo que necesitás en un solo mensaje (o en un audio)
> y esperá mi respuesta antes de mandar el siguiente 🙂 Ya reviso tu mensaje.

En audio va el audio fijo de 6.1 (sin nombre, clínica ni horario) y el texto de arriba lo
acompaña con esos datos. Decidido el 9/11.

### 6.3 Menú

```text
¿En qué te puedo ayudar? 😊
1. Información de la clínica (dirección, horarios, contacto).
2. Especialidades, médicos y cómo prepararte para un estudio.
3. Sacar un turno.
4. Ver, cancelar, reprogramar o confirmar tus turnos.
5. Registrarte o actualizar tus datos.
6. Otra consulta: ¡preguntame lo que necesites!
7. Cómo usar el asistente.
8. Hablar con una persona del equipo.
También podés escribirme tu consulta directamente, sin elegir un número.
```

- **6** responde con un mensaje fijo que invita a preguntar con ejemplos: "¡Dale! Contame qué
  necesitás. Por ejemplo: '¿El Dr. Molina atiende el jueves?', '¿Mi obra social cubre una
  ecografía?', '¿Qué tengo que llevar para un electrocardiograma?'". La pregunta siguiente se
  enruta como cualquier otra.
- **7** responde con una guía breve que **no repite la bienvenida**: qué puede hacer el asistente,
  que se puede preguntar en lenguaje natural (con ejemplos distintos a los del 6), que acepta
  audios, que en cualquier momento se puede escribir "menú" o pedir una persona, y que para sacar o
  gestionar turnos hay que estar registrado.
- **8** es el nodo Operador de hoy.

**Router:** hoy "elige otra consulta" deriva a una persona: hay que cambiarlo. Dos escenarios
nuevos ("Elige 'otra consulta' del menú" y "Pregunta cómo usar el asistente") con sus nodos de
respuesta fija, y el ejemplo de los números pasa a 1–8.

---

## 7. Consultas flexibles: herramientas como QueryServices

### 7.1 Qué falta hoy

Las herramientas de consulta piden **claves exactas**: `listHorariosDisponibles` exige prestación **y**
matrícula, y `getCotizacionTurno` exige un `slotId`. Por eso el agente siempre recorre el embudo
especialidad → prestación → médico → horario, aunque el paciente ya sepa lo que quiere. Cada paso es
una llamada al modelo (3–7 s).

| Pregunta del paciente | Hoy | Debería |
|---|---|---|
| "¿Quiero un turno con el Dr. Molina el jueves?" | `searchMedico` → preguntar prestación → `listHorariosDisponibles` por cada prestación | **Una** consulta: horarios del jueves del Dr. Molina agrupados por prestación (o "ese día no atiende") |
| "¿El traumatólogo Pérez hace consultas generales?" | `searchMedico` y el agente interpreta la lista | Una consulta con médico + especialidad + prestación que responda sí o no |
| "¿Mi obra social cubre la prestación X?" | No hay forma sin elegir un horario (`getCotizacionTurno` pide `slotId`) | Una consulta de cobertura por obra social, plan y prestación |

### 7.2 Enfoque: filtros dinámicos, pero acotados

**No** conviene darle al agente un "SQL" ni un criteria genérico: un modelo arma filtros mal, puede
traer datos que no le corresponden y la verificación se vuelve imposible. La propuesta es la misma
idea de los QueryServices del panel (`Criteria` + `Specification` con filtros opcionales), expuesta
como **pocas herramientas con todos los filtros opcionales**, resueltas por nombre aproximado (como
hoy) y con las uniones hechas en el back. El agente elige los filtros; el back garantiza las reglas
(publicado, activo, vigente, libre, en plazo).

**Sobre los joins.** `AgendaHorariosDia` tiene `prestacion` y `agendaMedico` obligatorios, y
`AgendaMedico` tiene `medico` obligatorio: ahí el join correcto es **interno** (un horario siempre
tiene médico; un LEFT JOIN no cambia el resultado y le quita información al optimizador). El
**LEFT JOIN** sirve donde la relación puede faltar y esa ausencia es la respuesta: por ejemplo
prestación ⟕ `ObraSocialPlanPrestacion` para un plan → sin fila = "tu plan no la cubre". Además, hay
que exigir con `EXISTS` que el `MedicoPrestacion` esté **vigente en la fecha del horario**: la agenda
dice qué prestación se ofrece en ese horario, pero la asignación médico↔prestación es la que dice si
el médico la sigue haciendo.

### 7.3 Herramientas propuestas

**1. `searchHorarios`** — reemplaza a `listHorariosDisponibles` (o la amplía con compatibilidad).

- Filtros, todos opcionales pero **al menos uno de médico, prestación o especialidad**:
  `medico` (nombre aproximado o matrícula), `prestacion`, `especialidad`, `fecha` **o**
  `fechaDesde`/`fechaHasta`, `diaSemana` (para "los jueves"), `franjaHoraria`, `horaDesde`/`horaHasta`.
- Consulta: `agenda_horarios_dia` ⋈ `agenda_medico` ⋈ `medico` ⋈ `prestacion` ⋈ `especialidad`, con
  `EXISTS` sobre `medico_prestacion` vigente en la fecha y sobre el histórico de la prestación
  publicada; médico y horario no borrados, horario libre, dentro del plazo de reserva y del horizonte
  (`diasMaximosAnticipacionReserva`). Los índices que hacen falta ya existen
  (`(agenda_medico_id, fecha)`, `prestacion_id`, `fecha_limite_reserva`).
- Respuesta **agrupada**: por día → médico (nombre, matrícula, especialidad, rango en que atiende ese
  día) → prestación → horarios (`slotId`, `fechaHoraTexto`, franja). Con tope (por ejemplo 3 días,
  10 horarios por prestación) y un `hayMas` para paginar. Si no hay nada, dice **por qué**: el médico
  no atiende ese día, atiende pero está todo ocupado, o no hace esa prestación.
- Con esto, "turno con Molina el jueves" es **una** llamada.

**2. `searchMedico` ampliada** — suma filtros `especialidad` y `prestacion`, y por cada médico
`haceLaPrestacion` (sí/no), `atiendeParticular` y los días en que tiene agenda en las próximas
semanas. "¿El traumatólogo Pérez hace consultas generales?" se responde con una llamada y sin
interpretar listas.

**3. `getCoberturaPrestacion`** (nueva) — `prestacion` (obligatoria), `obraSocial` y `plan` (opcionales;
si el paciente está registrado y no los nombra, usa los suyos), `medico` (opcional).
- Consulta: prestación ⟕ `ObraSocialPlanPrestacion` del plan (vigente, plan publicado), con la
  obra social del plan.
- Respuesta: `cubre`, modalidad, porcentaje o coseguro y, si el motivo es que no cubre, el motivo
  (plan no publicado, prestación no cubierta, obra social inexistente).
- El **monto** depende del médico (el precio particular está en `MedicoPrestacion`): con `medico`
  devuelve el monto exacto con la misma estrategia que `CotizacionAgenteService`; sin `medico`,
  devuelve el monto por cada médico que la hace. El monto final del turno lo sigue dando
  `getCotizacionTurno` antes de reservar.

**Capa:** las tres van por `AgenteController` → `AgenteApp` → `CatalogoAgenteService` /
`CotizacionAgenteService` (la capa `AgenteServices` ya existe para esto) → QueryServices con
`Specification` y fetch joins (evitar N+1 al armar los grupos). Los response son records nuevos en
`Records/Agente/Response`.

**Verificación:** los horarios que salgan de `searchHorarios` se declaran y verifican como hoy
(`horarios`). La cobertura se agrega a `<datos>` (`coberturas`: prestación, plan, cubre) y al
endpoint de verificación.

### 7.4 Prompt

- Sección nueva **"Elegí la consulta más directa"**: si el paciente ya dio médico, día o prestación,
  usá `searchHorarios` con esos filtros en una sola llamada; el recorrido especialidad → prestación →
  médico → horario es solo para quien no sabe qué quiere o eligió una opción del menú.
- Ejemplos pregunta → herramienta con filtros, en el prompt y en la descripción de cada herramienta.
- "Si la consulta no devuelve nada, decí por qué (lo que trae `motivo`) y ofrecé la alternativa más
  cercana": se conecta con la sección 1.1 (si no es concluyente, ofrecer una persona).

### 7.5 Registro

Sin cambios respecto de hoy (decidido el 9/11): las consultas no exigen registro.

| Consulta | Registro |
|---|---|
| Clínica, especialidades, prestaciones, médicos, indicaciones, obras sociales aceptadas | No |
| Horarios (`searchHorarios`) | No |
| Cobertura con un plan que nombra el paciente | No |
| Cobertura "mi obra social" (usa la del paciente), cotización, turnos, datos | Sí |

---

## 8. Fuera de esta propuesta (pendientes aparte)

- **Respuesta solo en audio** (`accesmed.audio.respuesta-con-texto`): decisión pendiente. Si se
  adopta, hay que reescribir la sección de audio del prompt (hoy asume que el texto también llega).
- **Transcripción**: pasar a OpenAI una pista de vocabulario (nombres de prestaciones y médicos) para
  evitar casos como "É unha ecografía Doppler".
- **Tiempos**: router con un modelo chico, probar `reasoning: none` o un modelo más chico en los
  agentes, recortar el bloque común del prompt (~5–6 mil tokens por llamada), espera de agrupación
  de 4 a 2–3 s. La sección 7 ya baja tiempos (menos llamadas al modelo por respuesta).
- **Nombres de médicos y prestaciones en el texto** sin declarar: no se agrega control (se cruzan mal
  por regex); lo cubren la regla del prompt y la verificación de lo declarado.

---

## 9. Orden y pruebas

Orden decidido el 9/11:

1. **Audios fijos:** se generan con la API de OpenAI (textos de 6.1) y se escuchan. Con el OK, sigue.
2. **Servidor:** se suben a `/root/docker/accesmed/staging/audios/`.
3. **Back, todo junto:** verificación (4), mensajes y audios fijos (5, 6.1, 6.2) y consultas
   flexibles (7), con la documentación de cada cambio. Commits en la rama de trabajo; los PR los
   hace Franco.
4. **Flowise, todo junto** en la UI de staging: prompts y verificación (1–3), menú y router (6.3),
   herramientas nuevas y su prompt (7.3–7.4). Después se exporta al repo AccesMed-Flowise.
5. Pruebas por WhatsApp:
   - Los tres casos de la sección 0.
   - Pedir algo que no existe dos veces → ofrece una persona; con "sí" → Operador.
   - "¿Aceptan IOMA?" con una obra social inexistente → no la afirma.
   - "¿Hay estacionamiento?" → "no tengo ese dato" y ofrece una persona.
   - Monto escrito distinto del cotizado → rechazo.
   - Primer mensaje en audio → la bienvenida llega antes que cualquier aviso.
   - "¿Quiero un turno con el Dr. Molina el jueves?" → una sola consulta, horarios por prestación.
   - "¿El traumatólogo X hace consultas generales?" → sí o no directo.
   - "¿OSDE 210 cubre un ecocardiograma?" → cubre o no, sin pedir un horario.
   - Menú 6, 7 y 8.
