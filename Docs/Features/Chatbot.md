# Contrato del Agente (canal WhatsApp / Flowise)

> **Estado:** back implementado (rama `feature/Agente`; ver Docs/Planes/Plan-Agente-Chatwoot.md). Falta la Fase 2 (configurar Chatwoot, Flowise y el servidor). Documento de contrato de los endpoints
> `/accesmed-api/Agente/*` que consume el agente de Flowise, derivados de las
> Especificaciones de Casos de Uso del **actor Paciente**. Es la fuente de verdad para
> implementar la capa `Agente` en el back y armar el flow + Custom Tools en el repo
> `AccesMed-Flowise`.
>
> Relacionado: `Docs/Features/Turno.md` (endpoints web del Turno), las Especificaciones
> (`Docs/Especificaciones Turnos.docx`) y los `Docs/` de `AccesMed-Flowise` (`CONTEXTO.md`:
> topología; `AGENTE.md`: funciones y piezas del agente; `SERVICIOS.md`: backend, LLM y Chatwoot).
> Análisis de los CU del chatbot (pasos de la conversación, flujos alternos, matriz reglas↔excepciones):
> `Docs/Diseno-Chatbot.md`.

---

## 1. Propósito y alcance

El paciente conversa por WhatsApp. El contrato cubre las **10 funcionalidades del paciente**:

| CU | Funcionalidad | Endpoint del agente |
|----|---------------|---------------------|
| ECU-PAC-1 | Registrarse | `POST /Agente/Paciente` |
| ECU-PAC-2 | Modificar datos | `PATCH /Agente/Paciente` |
| ECU-PACIENTE-3 | Darse de baja | `DELETE /Agente/Paciente` |
| ECU-TURN-1 | Consultar información general | `GET /Agente/Clinica`, `GET /Agente/Especialidad`, `GET /Agente/Especialidad/{nombre}/Oferta` |
| ECU-TURN-2 | Consultar turnos disponibles | `GET /Agente/Turno/Disponibilidad` |
| ECU-TURN-3 | Solicitar turno | `POST /Agente/Turno` |
| ECU-TURN-4 | Cancelar turno | `DELETE /Agente/Turno/{codigo}` |
| ECU-TURN-5 | Reprogramar turno | `PATCH /Agente/Turno/{codigo}/Reprogramacion` |
| ECU-TURN-6 | Confirmar turno | `PATCH /Agente/Turno/{codigo}/Confirmacion` |
| ECU-TURN-23 | Solicitar operador humano (handoff) | **no es endpoint de dominio** — ver §9 |

Apoyo transversal (identificación del paciente, listar sus turnos por estado):
`GET /Agente/Paciente` y `GET /Agente/Turno`.

**Fuera de alcance:** los CU de médico/administrador (ECU-TURN-7..10, consultar/anunciar/
iniciar/finalizar) y las transiciones automáticas de los schedulers. Las **notificaciones
salientes** (NOTIF-1..5) son **Fase 2** (ver §11).

---

## 2. La distinción clave: pasos del sistema vs pasos del agente

Las Especificaciones relatan al **sistema**. El agente necesita los pasos del **paciente**.
Cada CU se parte en dos:

- **Comportamiento conversacional del agente (prompt de Flowise, NO endpoints):**
  - *"Validar la coherencia del mensaje"* → lo resuelve el LLM (entiende / pide reformular).
  - *"Verificar si pide operador humano"* → ramificación del agente (token `—HANDOFF—`, §9).
  - Menús, saludos, pedir datos de a uno, reintentos. Todo esto vive en el flow, no acá.
- **Operaciones de dominio (Custom Tools → endpoints `/Agente/*`):** lo que figura en este
  contrato. Cada tool es una llamada HTTP al back.

> **Regla de oro:** un endpoint del agente nunca "conversa". Recibe datos ya resueltos,
> ejecuta una operación atómica y devuelve datos. La conversación es responsabilidad del flow.

---

## 3. Principios del contrato

1. **Identidad por teléfono (verificado por el canal).** El `numeroTelefono` del paciente
   **nunca** sale de lo que el paciente escribe: sale del `sender` del payload de Chatwoot,
   que el back (pasamanos) inyecta en Flowise como variable de sesión y que Flowise reenvía
   en cada Custom Tool por el header `X-Paciente-Telefono` (ver §4). El LLM no lo compone.
2. **Scope por teléfono.** Toda operación sobre turnos/datos se valida contra el paciente
   dueño de ese teléfono. Un paciente solo ve y toca lo suyo. Reemplaza al modelo
   JWT + permisos `TURN_*` del canal web (que acá no aplica).
3. **Claves de negocio, no UUIDs.** El agente maneja lo que el paciente entiende: nombre de
   especialidad/prestación, matrícula del médico, `codigo` del turno. La capa `Agente`
   **resuelve** clave → entidad antes de delegar. Única excepción: el `slotId` (UUID opaco),
   que viaja por la sesión de Flowise y **nunca se muestra al paciente** (ver §7, ECU-TURN-3).
4. **Misma regla de negocio, orquestación propia.** Ningún `App` llama a otro `App`: `AgenteApp`
   **copia la orquestación** de `PacienteApp` y `TurnoApp` (para no tocar el panel) y reutiliza
   los DomainServices, QueryServices y Mappers. La regla de negocio vive en los DomainServices
   compartidos; `AgenteApp` agrega resolución de claves + scope por teléfono. Ver §8 y §10.
5. **Mismo formato de error.** Todos los endpoints devuelven `AccesMedError` (§5).

---

## 4. Seguridad y autenticación

Cadena de seguridad propia para `/accesmed-api/Agente/**` (aparte de la del panel web y la
de Swagger), configurada en `Security/Config/SecurityFilterChainConfig`; el filtro de la API key
es `Security/Agente/AgenteApiKeyFilter`. Un 401 sale con el formato `AccesMedError` (código
`AGENTE_NO_AUTENTICADO`).

| Header | Quién lo pone | Para qué |
|--------|---------------|----------|
| `X-Agente-Api-Key` | Flowise (fijo) | Autentica que quien llama es el agente. Debe coincidir con `ACCESMED_AGENTE_API_KEY` (env var del back, sin default; va a `.env.example` y a `application-test.yml`). |
| `X-Paciente-Telefono` | Flowise (`$vars.numeroTelefono`, inyectada por el pasamanos, §6.D) | Identidad del paciente. **No** lo compone el LLM. El back lo trata como el teléfono verificado. |

- Sin `X-Agente-Api-Key` válida → **401**. Los endpoints `/Agente/*` no usan JWT ni permisos.
  La clave se compara en tiempo constante y, si falta `accesmed.agente.api-key`, el back no arranca.
- Sin `X-Paciente-Telefono` (o con un teléfono que no es un número) → **422** `VALIDACION`. Con un
  teléfono sin paciente registrado → **404** `PACIENTE_NO_ENCONTRADO` (el agente deriva al registro).
- **Formato del teléfono:** llega en E.164 (`+5492617742682`); la búsqueda compara solo los dígitos
  (ignora `+`, espacios, guiones y paréntesis del número guardado). Al registrar un paciente desde el
  canal se guarda como `+<dígitos>`.
- La autorización real es el **scope por teléfono**: la capa `Agente` resuelve el paciente a
  partir de `X-Paciente-Telefono` y rechaza cualquier turno/dato que no le pertenezca.
- **Supuesto de confianza (Fase 1):** como solo Flowise tiene la API key y el teléfono se lo
  pasó el propio back verificado, se confía en el header. Endurecerlo (firmar el teléfono,
  mTLS) queda para más adelante.

---

## 5. Forma de los errores

Mismo contrato que el resto del back (`Controllers/Errors/AccesMedError`):

```json
{
  "timestamp": "2026-11-03T21:00:00Z",
  "status": 409,
  "codigo": "TURNO_FUERA_DE_PLAZO_CANCELACION",
  "mensaje": "Ya pasó el plazo para cancelar este turno.",
  "errores": ["..."],
  "path": "/accesmed-api/Agente/Turno/TURNO_A1B2C3D4"
}
```

**Cómo lo usa el agente:** el `mensaje` ya viene en lenguaje claro y en español (lo escribe
el back en el `throw`), así que el flow puede mostrarlo casi tal cual al paciente. El `codigo`
le sirve al agente para ramificar (p. ej. ante `*_FUERA_DE_PLAZO_*` ofrecer otra acción). El
mapeo CU-excepción ↔ `codigo` está en cada sección de §7.

---

## 6. Contrato Chatwoot ↔ Backend (webhook del Agent Bot)

No es un Custom Tool: es la entrada del canal. **Chatwoot** llama al back y el back le responde
por la API de Chatwoot. Plan de implementación: `Docs/Planes/Plan-Contrato-Chatwoot.md`.

```
Paciente (WhatsApp) → Chatwoot ──webhook──▶ Back ──▶ Flowise (LLM + Custom Tools §7)
Paciente ◀─ Chatwoot ◀──API Chatwoot─── Back ◀── respuesta
```

### 6.A Chatwoot → Back

**`POST /accesmed-api/Chatwoot/Webhook`** (`ChatwootController` + `ChatwootApp`). Chatwoot lo llama
**una vez por evento** (push, uno por mensaje; no hay lotes ni polling).

| Header | Uso |
|--------|-----|
| `X-Chatwoot-Signature` | `sha256=` + hex(HMAC-SHA256(secret del Agent Bot, `"{timestamp}.{body crudo}"`)) |
| `X-Chatwoot-Timestamp` | epoch en segundos; se rechaza si difiere más de 5 min |
| `X-Chatwoot-Delivery` | id de entrega; solo para el log |

Campos del body que usa el back (el resto se ignora):

| Campo | Para qué |
|-------|----------|
| `event` | solo `message_created` |
| `id` | id del mensaje (log) |
| `content` | texto del paciente |
| `created_at` | momento de creación del mensaje (epoch o ISO); solo para el log de latencia Chatwoot → back |
| `attachments[].file_type`, `.data_url`, `.file_size` | tipo, URL de descarga y tamaño del adjunto (los audios se descargan para transcribirlos) |
| `message_type` | solo `incoming` (o `0`) |
| `private` | descarta notas privadas |
| `conversation.id` | clave de agrupación, sesión de Flowise y destino de la respuesta |
| `conversation.status` | solo `pending` (la atiende el bot) |
| `sender.id`, `sender.name`, `sender.phone_number` | contacto; respaldo en `conversation.meta.sender` |

Respuestas del back:

| Caso | Status |
|------|--------|
| Aceptado (incluso si el evento se ignora) | `200`, inmediato |
| Firma ausente, inválida o vencida | `401` `CHATWOOT_NO_AUTENTICADO` (lo responde `Security/Chatwoot/ChatwootFirmaFilter`) |
| Body que no es JSON | `422` `VALIDACION` |

Con `accesmed.chatwoot.webhook-secret` vacío la firma **no se verifica** (solo para pruebas; deja un `warn`).

### 6.B Qué atiende el bot

Un evento se atiende solo si es `message_created` + `incoming` + no privado + conversación `pending`.
Se ignora todo lo demás: los `outgoing` (respuestas del bot y de humanos → sin bucles) y las
conversaciones `open`/`resolved`. Entre los atendidos:

Los controles se aplican **en este orden** (lógica en `Services/ExternalServices/ChatwootService`,
diseño en `Docs/Diseno-Chatbot.md §2.3`):

1. **Sin teléfono** → mensaje de error técnico y handoff (no llama a Flowise).
2. **Fuera del horario de atención** (`Clinica.horarioInicioAtencion` ≤ ahora < `horarioFinAtencion`,
   en `Clinica.zonaHoraria`) → *"¡Hola, {nombre del Paciente}! Lo sentimos, en este momento no estamos
   atendiendo. Nuestro horario de atención es de {inicio} a {fin} h. Escribinos en ese horario y con
   gusto te ayudamos. 🙂"* (sin nombre si el teléfono no está registrado), **una vez por ventana**; no
   llama a Flowise.
3. **Antispam**: más de 10 mensajes en 60 s → *"Recibimos muchos mensajes seguidos…"* una vez y
   la conversación se ignora 5 min.
4. **Documento** (adjunto `image` o `file`) → *"📎 Recibimos tu archivo. Alguien del equipo lo va a
   revisar."* y la conversación se etiqueta `documentacion` (la etiqueta la crea el equipo en
   Chatwoot). El archivo queda en Chatwoot: **nunca le llega a Flowise**. Si trae pie de texto, el
   texto sigue su curso (punto 6).
5. **Sin texto ni audio** y sin documento (video, sticker; y audio si el audio está apagado) →
   con el audio habilitado, *"Por ahora puedo leer mensajes de texto y escuchar audios. ¿Me lo
   escribís o me mandás un audio?"*; con el audio apagado, *"Por ahora solo puedo leer mensajes de
   texto. ¿Me lo escribís?"* (no llama a Flowise ni entra en la agrupación). El audio se habilita
   con `ACCESMED_OPENAI_API_KEY` (opcional; vacía = apagado).
6. **Primer mensaje aceptado de la ventana** (texto o audio) → bienvenida inmediata, antes de
   agrupar: *"¡Hola, {nombre del Paciente}! 👋 Soy el asistente virtual de la clínica {clínica}.
   Atendemos de {inicio} a {fin} h.
Un consejo para que te entienda mejor: contame lo que necesitás
   en un solo mensaje (o en un audio) y esperá mi respuesta antes de mandar el siguiente 🙂 Ya reviso tu
   mensaje."* (sin nombre si el teléfono no está registrado; si el nombre de la clínica ya empieza con
   "Clínica" no se repite la palabra). Si el mensaje es un audio, sale el audio fijo de bienvenida (sin
   nombre, clínica ni horario) y el texto según `accesmed.audio.respuesta-con-texto`. **El agente de
   Flowise no saluda.**
7. **Con texto o audio** → se agrupa (6.C). El mensaje que **abre** una tanda nueva dispara *"⏳ Aguardá un
   momento, estoy procesando tu mensaje…"* en el acto (no se repite dentro de la misma tanda; si el mensaje
   es un audio, sale el audio fijo de espera); **si corresponde bienvenida, sale solo ella** (ya termina
   con "Ya reviso tu mensaje") y no el aviso aparte.

**Audio como entrada.** Al procesar la tanda, cada mensaje con adjunto `file_type = audio` se
descarga de `data_url` (con `api_access_token` y siguiendo redirecciones), se transcribe con OpenAI
(`language=es`) y la transcripción queda como **nota privada** *"🎤 Transcripción: …"* en la
conversación (la ve el equipo, no el paciente). Un audio que no se puede usar se avisa y se omite
(sin derivar): transcripción vacía o error → *"🎧 No pude escuchar bien tu audio. ¿Me lo mandás de
nuevo o me lo escribís?"*; más pesado que `accesmed.audio.max-bytes` → *"🎧 Tu audio es un poco largo
para mí. ¿Me lo mandás en uno más corto o me lo escribís?"*. Si la tanda queda sin texto, no se
llama a Flowise.

**Ventana de inactividad** por conversación: arranca con el primer mensaje, se renueva con cada
uno y vence a los 30 min sin mensajes. Reinicia la bienvenida, el aviso de fuera de horario, el
contador de fuera de tema y la sesión de Flowise (6.D).

| Regla | Valor | Property |
|-------|-------|----------|
| Ventana de inactividad | 30 min | `accesmed.chatwoot.ventana-inactividad-minutos` |
| Antispam: mensajes tolerados | 10 | `accesmed.chatwoot.antispam.max-mensajes` |
| Antispam: ventana de conteo | 60 s | `accesmed.chatwoot.antispam.ventana-segundos` |
| Antispam: silencio | 5 min | `accesmed.chatwoot.antispam.silencio-minutos` |
| Fuera de tema seguidos → handoff | 3 | `accesmed.chatwoot.fuera-de-tema.max-seguidos` |

El estado (ventanas, antispam, fuera de tema) vive en memoria y se limpia cada 10 min: supone una
sola instancia del back.

### 6.C Agrupación de mensajes en ráfaga

Por `conversation.id` (`Services/Utils/AgrupadorMensajes`, bean en `AsyncConfig`). La tanda se cierra
con la **primera** que se cumpla:

| Regla | Valor | Property |
|-------|-------|----------|
| Silencio sin mensajes nuevos | 4 s | `accesmed.chatwoot.agrupacion.espera-segundos` |
| Mensajes acumulados | 5 | `accesmed.chatwoot.agrupacion.max-mensajes` |
| Tope desde el primer mensaje | 15 s | `accesmed.chatwoot.agrupacion.espera-maxima-segundos` |

Mientras una tanda de la conversación se procesa, lo nuevo espera a la siguiente: **nunca hay dos
llamadas simultáneas a Flowise para la misma conversación**. El texto se junta de los propios
webhooks (no se consulta `GET /messages`). El estado vive en memoria: si el back se reinicia en esos
segundos, esa tanda no se responde.

### 6.D Back → Flowise

```json
POST {accesmed.flowise.base-url}/api/v1/prediction/{chatflowId}
{
  "question": "hola
quiero un turno
con cardiología",
  "overrideConfig": {
    "sessionId": "chatwoot-<conversation.id>-<inicio de la ventana en epoch>",
    "vars": {
      "numeroTelefono": "+5492617742682",
      "nombreContacto": "Ana",
      "pacienteRegistrado": "true",
      "nombrePaciente": "Ana",
      "fechaHoraActual": "sábado 08/11/2026 10:35",
      "entradaAudio": "false"
    }
  }
}
```

`question` = los textos de la tanda unidos por salto de línea (los audios, ya transcriptos). El `sessionId` cambia con cada
ventana de inactividad (6.B): la memoria del agente arranca limpia después de 30 min sin mensajes.

| Variable | Valor |
|----------|-------|
| `numeroTelefono` | teléfono verificado del canal; los Custom Tools lo mandan en `X-Paciente-Telefono` |
| `nombreContacto` | nombre del perfil de WhatsApp (vacío si no viene) |
| `pacienteRegistrado` | `"true"` / `"false"`: hay un paciente activo con ese teléfono (se consulta en cada tanda) |
| `nombrePaciente` | nombre del `Paciente` registrado, o vacío |
| `fechaHoraActual` | día, fecha y hora en la zona de la clínica, para interpretar "mañana", "el jueves" |
| `entradaAudio` | `"true"` / `"false"`: en la tanda hubo al menos un audio transcripto (la respuesta se le va a leer en voz alta) |

En el flow se leen como `$vars.<nombre>`. El chatflow tiene que tener el override activado
(`apiConfig.overrideConfig.status = true`) y **cada variable habilitada** en
`apiConfig.overrideConfig.variables`; si no, Flowise las ignora sin error (verificado en el código de
3.1.3, `Diseno-Chatbot.md §2.4.4`). Crearlas además en *Variables* da valores por defecto para probar
desde la UI.

**Tokens de la respuesta** (los quita el back antes de enviar el texto):

- `—HANDOFF—` → envía el texto y abre la conversación (pasa a una persona).
- `—FUERA_DE_TEMA—` → envía el texto y suma 1 al contador de la conversación; cualquier respuesta sin
  el token lo reinicia. Al llegar a 3 seguidos, avisa *"Te paso con una persona del equipo."* y abre
  la conversación.
- `—FIN—` (el paciente se despidió) → envía el texto, **resuelve** la conversación y cierra la
  ventana: el próximo mensaje arranca de cero (conversación nueva en Chatwoot, bienvenida y sesión
  nueva de Flowise). Si la misma respuesta trae `—HANDOFF—`, gana el handoff.
- Respuesta vacía o error de Flowise → mensaje de error técnico y handoff.

**Respuesta con voz.** Si `entradaAudio` es `true` y hay texto (sin tokens), el back genera una nota de
voz con OpenAI (`POST /v1/audio/speech`, `opus`) a partir de un *texto para voz* determinista: sin
`* _ ~ #`, viñetas ni emojis; `jueves 12/11/2026 09:30` se lee "jueves 12 de noviembre a las 9:30",
`12/11/2026` "12 de noviembre de 2026" y `$85.800` "85.800 pesos"; los códigos `TURNO_…` se reemplazan
por "(te lo dejo por escrito)" si el texto también se manda. Sube primero la nota de voz y, con
`accesmed.audio.respuesta-con-texto: false` (default), solo la voz (que lee también los códigos); con `true`, después el mismo texto.
Si la voz falla o el texto para voz supera 4096 caracteres, sale solo el texto. Handoff, fuera de tema
y fin se interpretan igual.

Antes de entregar la respuesta, el back la **verifica** (6.H).

### 6.E Back → Chatwoot (API)

Autentica con `api_access_token` = **access token del Agent Bot** (`ACCESMED_CHATWOOT_API_TOKEN`):
los mensajes figuran como enviados por el bot.

| Acción | Endpoint |
|--------|----------|
| Responder | `POST /api/v1/accounts/{account}/conversations/{id}/messages` `{content, message_type: "outgoing", private: false}` |
| Derivar a persona | `POST /api/v1/accounts/{account}/conversations/{id}/toggle_status` `{status: "open"}` |
| Fin de la charla (`—FIN—`) | `POST /api/v1/accounts/{account}/conversations/{id}/toggle_status` `{status: "resolved"}` |
| Nota de voz | `POST /api/v1/accounts/{account}/conversations/{id}/messages` multipart: `attachments[]` (`audio/ogg`, `respuesta.ogg`), `message_type=outgoing`, `private=false`, sin `content` |
| Nota privada (transcripción) | `POST /api/v1/accounts/{account}/conversations/{id}/messages` `{content: "🎤 Transcripción: …", message_type: "outgoing", private: true}` |
| Descargar adjunto | `GET {data_url}` (con `api_access_token`, sigue redirecciones) |
| Etiquetar documento | `POST /api/v1/accounts/{account}/conversations/{id}/labels` `{labels: ["documentacion"]}` |

Se deriva cuando: la respuesta de Flowise trae `—HANDOFF—` (se quita el token y se envía el resto, §9);
se acumulan 3 respuestas `—FUERA_DE_TEMA—` seguidas (6.D); Flowise falla o devuelve vacío (antes se avisa *"Estamos con problemas técnicos, en breve te atiende una
persona."*); el mensaje no trae teléfono. Si Chatwoot falla al responder, solo `log.error`.

### 6.F Estados de la conversación (quién atiende)

| Estado | Atiende | Cómo se llega |
|--------|---------|---------------|
| `pending` | Bot | toda conversación nueva de un inbox con Agent Bot (lo hace Chatwoot) |
| `open` | Persona | handoff del back, o un operador la toma |
| `resolved` | — | la cierra el operador |
| `open` → `pending` | vuelve al bot | el operador la devuelve a mano |

Paciente que escribe después de una conversación `resolved`: Chatwoot abre una conversación
nueva en `pending` y vuelve a atender el bot (verificado, ver 6.G).

### 6.G Configuración en Chatwoot (`chat.accesmed.site`)

1. **Agent Bot** `AccesMed Bot` (Ajustes → Bots, o `POST /api/v1/accounts/{account}/agent_bots`):
   `outgoing_url` = `https://<host del back>/accesmed-api/Chatwoot/Webhook`; su `secret` →
   `ACCESMED_CHATWOOT_WEBHOOK_SECRET`; su access token → `ACCESMED_CHATWOOT_API_TOKEN`.
2. **Conectar el bot al inbox** (Inbox → Configuración del bot, o `POST /inboxes/{id}/set_agent_bot`).
3. Token y secret van **solo** al `.env` local / del servidor, nunca al repo (se copian desde
   Ajustes → Bots → Editar: "Webhook Secret" y "Token de acceso").

**Estado actual (2026-11-07):** `AccesMed Bot` creado (URL `https://accesmed.site/accesmed-api/Chatwoot/Webhook`,
back de **staging**) y conectado al inbox **AccesMed WhatsApp**. El inbox "WhatsApp (Prueba)" no se usa
(no funciona).

**Verificado en la primera prueba real (2026-11-07, staging):**
- Paciente que escribe en una conversación **`open`** (asignada a una persona): Chatwoot manda el
  webhook igual, pero el back lo ignora (no es `pending`) y responde la persona.
- Paciente que escribe después de que la conversación quedó **`resolved`**: Chatwoot **no la reabre**,
  crea una **conversación nueva en `pending`** y el bot la atiende. Ciclo completo WhatsApp → Chatwoot →
  back → Flowise → Chatwoot ≈ 4,6 s (espera de agrupación de 4 s incluida).
- Chatwoot también manda al webhook otros eventos (`conversation_created`, `message_updated`, etc.):
  el back responde 200 y los descarta.

### 6.H Tanda y avisos de espera

Diseño en `Docs/Diseno-Chatbot.md §3.4.6` y `§4.7`; código en `Services/AgenteServices/TandaAgenteService`.
El back manda *"⏳ Aguardá un momento, estoy procesando tu mensaje…"* al paciente apenas **recibe el
mensaje que abre la tanda** (no al despacharla; `Docs/Diseno-Chatbot.md §5`, D41). **En el primer mensaje
de la ventana no sale aparte**: la bienvenida termina con *"Ya reviso tu mensaje"*. Mientras Flowise la
procesa, el back la tiene **abierta** por teléfono: los endpoints que llama el agente (con
`X-Paciente-Telefono`) mandan un aviso propio, en texto, con estas reglas: **uno por tipo por tanda**,
**máximo dos de herramienta por tanda** (las escrituras cuentan) y ninguno después del reintento:

| Tool | Aviso |
|------|-------|
| catálogo (`Especialidad`, `Especialidad/{nombre}/Oferta`, `Prestacion`, `Medico`) | *"🔎 Revisando la cartilla…"* |
| `Turno/Disponibilidad`, `Horario` | *"🔎 Buscando horarios disponibles…"* |
| `GET Turno` (mis turnos) | *"🔎 Revisando tus turnos…"* |
| `Turno/Cotizacion`, `Prestacion/Cobertura` | *"🔎 Revisando tu cobertura…"* |
| `createTurno` | *"⏳ Reservando tu turno…"* |
| `cancelTurno` / `reprogramTurno` / `confirmTurno` | *"⏳ Cancelando / Reprogramando / Confirmando tu turno…"* |
| `createPaciente` / `updatePaciente` / `softDeletePaciente` | *"⏳ Guardando tus datos…"* |
| **2.ª** llamada a `POST /Agente/Verificacion` de la misma tanda | *"🔁 Estoy chequeando la información, ya te respondo…"* (una vez; no cuenta como herramienta) |

`getPaciente`, `Clinica` y `ObraSocial` no avisan. Estado en memoria por teléfono (una sola instancia del back).

**La verificación de la respuesta ya no la hace el back al entregarla** (se retiró el 2026-11-09, el
reintento con `controlInterno` incluido): la hace el flow llamando a `POST /Agente/Verificacion`
(ver §13 y `Docs/Diseno-Chatbot.md §4.3`).

---

## 7. Endpoints por caso de uso

> Un único controller, `AgenteController` (`@RequestMapping("/accesmed-api/Agente")`), delega en
> `AgenteApp`. Records en `Records/Agente/Request` y `Records/Agente/Response`, con sufijo
> `...AgenteRequest/Response` (acción en inglés): p. ej. `Records/Agente/Request/CreateTurnoAgenteRequest`.
>
> **Dónde dice "delegar en `PacienteApp.x` / `TurnoApp.x` / reusa `QueryService`":** significa que
> `AgenteApp` **copia la orquestación** de ese método (ningún `App` llama a otro `App`; el panel
> queda intacto). Las búsquedas nuevas (por teléfono, por nombre, por código) se agregaron a
> Repositories/DomainServices sin tocar los métodos existentes.
>
> **Diferencias entre este contrato y el código real** (manda el código): `Turno` no tiene FK a `MedicoPrestacion` (se valida con `findVigenteEnFecha`);
> `ListHorarioDisponibleAgenteResponse` devuelve `{ slotId, fechaHora, franja }` y la franja se filtra
> por `horaDesde` (MAÑANA < 12:00, TARDE 12:00-19:00, NOCHE desde 19:00); el `GET /Agente/Turno` exige
> el query param `accion`; PAC-2 con `obraSocial` **reemplaza** las coberturas activas (la entidad
> `ObraSocialPaciente` es inmutable: baja + alta); ante varias coberturas, `createTurno` usa la primera
> del paciente cuyo plan está Publicado y cubre la prestación, o particular.

En todas las tablas, `X-Paciente-Telefono` + `X-Agente-Api-Key` van en el header (§4) y no
se repiten en el body.

**Fechas y horas:** todas las que devuelve `/Agente/*` (`fechaHora` de la disponibilidad y
`fechaHoraInicio` de los turnos) van **sin zona y en la hora de la clínica** (`Clinica.zonaHoraria`),
p. ej. `2026-11-11T14:00:00`: es la hora que el agente le muestra al paciente, sin conversiones. Hasta
el 2026-11-08 `fechaHoraInicio` salía en UTC (`…T17:00:00Z`) y el agente a veces mostraba 17:00 para
un turno de las 14:00. Los `mensaje` de error con fecha y hora (`FormatoMensaje.fechaHora`) también
usan la zona de la clínica.

### 7.1 ECU-PAC-1 · Registrarse — `POST /Agente/Paciente`

Crea el paciente con el teléfono verificado del canal (no se pide ni se recibe en el body).

**Request (`CreatePacienteAgenteRequest`):**

| Campo | Tipo | Obl. | Notas |
|-------|------|------|-------|
| `dni` | String | Sí | Único entre pacientes activos. |
| `nombre` | String | Sí | |
| `apellido` | String | Sí | |
| `fechaNacimiento` | LocalDate | Sí | `@NotNull @Past`. |
| `email` | String | Sí | `@NotBlank @Email`. Único entre pacientes activos. |
| `obraSocial` | objeto | No | `{ nombreObraSocial, nombrePlan, nroSocio }`. Si viene, se resuelve por **nombre** (ver resolución). Informativo, no se valida contra API externa. |

**Resolución (capa Agente):** `numeroTelefono` ← header. Si vino `obraSocial`: resolver
`nombreObraSocial` → `ObraSocial` activa y `nombrePlan` → `Plan` activo de esa obra social
(vía `ObraSocialQueryService` / `PlanQueryService`), obteniendo sus UUIDs. Luego arma el
`CreatePacienteRequest` web (`dni, nombre, apellido, fechaNacimiento, email, numeroTelefono,
obrasSociales[{obraSocialId, planId, nroSocio}]`) y delega en **`PacienteApp.createPaciente`**.

**Response (`CreatePacienteAgenteResponse`):** confirmación con `nombre`, `apellido` (para el
mensaje de bienvenida del CU). No expone UUIDs al paciente.

**Errores:** `PACIENTE_DNI_DUPLICADO`/`..._TELEFONO_DUPLICADO` (→ Excepción 5 "DNI ya
registrado" / "ya registrado con este número"), obra social/plan no encontrados (→ Excepción
6/7), Bean Validation 422 (faltan dni/nombre/apellido → Excepción 4).

> **Gap:** antes de crear hay que chequear "¿ya existe un paciente activo con este teléfono?"
> (Excepción 3). Hoy existe `existsByNumeroTelefonoAndDeletedAtIsNull` → se reutiliza. Ver §8.

### 7.2 ECU-PAC-2 · Modificar datos — `PATCH /Agente/Paciente`

Identifica al paciente por el teléfono del header (no por id en la ruta: el agente nunca usa
el UUID). Campos opcionales independientes.

**Request (`UpdatePacienteAgenteRequest`):** `nombre?`, `apellido?`, `fechaNacimiento?`,
`email?`, `obraSocial?` (`{ nombreObraSocial, nombrePlan, nroSocio }`). `dni` y
`numeroTelefono` **no** son modificables.

**Resolución:** teléfono → `Paciente` activo (**gap §8**: `findPacienteActivoByNumeroTelefono`).
Resolver obra social/plan por nombre si vino. Delegar en **`PacienteApp.updatePaciente`** con
el `id` resuelto internamente. (La asignación/reemplazo de la cobertura sigue la lógica del CU;
ver §8 si `updatePaciente` no cubre crear/reemplazar `ObraSocialPaciente` por nombre.)

**Response:** datos actualizados (sin UUIDs). **Errores:** paciente no encontrado →
derivar a registro (Excepción 3); nombre/apellido nulos → Excepción 4; obra social/plan →
Excepción 5/6.

### 7.3 ECU-PACIENTE-3 · Darse de baja — `DELETE /Agente/Paciente`

Sin body. Identifica por teléfono. Delega en **`PacienteApp.softDeletePaciente(id)`**.

**Response:** 204. **Errores:** paciente no encontrado (Excepción 3: "No encontramos un
paciente registrado con este número"); `ReglaNegocio` si tiene turnos vivos
(`validateSinTurnosVivosDePaciente`).

### 7.3.1 Apoyo · Catálogo de obras sociales — `GET /Agente/ObraSocial`

Para ECU-PAC-1 y ECU-PAC-2: le da al agente los nombres exactos que acepta `ObraSocialAgenteRequest`,
así el paciente no tiene que acertarlos (decisión D8 de `Docs/Diseno-Chatbot.md`). No requiere
registro (sí `X-Agente-Api-Key`).

**Response 200:** obras sociales activas, ordenadas por nombre, con sus planes **no deshabilitados**
(mismo criterio con el que el registro resuelve el plan). Se omiten las que no tienen planes.

```json
[
  { "nombre": "OSDE", "planes": ["OSDE 210", "OSDE 310", "OSDE 410"] }
]
```

### 7.4 ECU-TURN-1 · Información general

Consulta abierta (no requiere registro). Igual se exige `X-Agente-Api-Key`.

- **`GET /Agente/Clinica`** → nombre, ubicación, horarioInicioAtencion, horarioFinAtencion
  (vía `ClinicaDomainService` / `ClinicaQueryService`). Es la info del paso 3 del CU y también
  el "mensaje de presentación" que varios CU muestran cuando el paciente no está registrado.
- **`GET /Agente/Especialidad`** → lista de especialidades activas (`nombre`) vía
  `EspecialidadQueryService`.
- **`GET /Agente/Especialidad/{nombre}/Oferta`** → el nombre se resuelve **por parecido**
  ("derma", "traumtitini" → la especialidad; ver búsqueda abajo). Devuelve las prestaciones
  publicadas, **cada una con los médicos que la atienden hoy** (`MedicoPrestacion` vigente: no todos
  los médicos de una especialidad hacen todas sus prestaciones). Las prestaciones sin médicos se
  omiten. Vía `CatalogoAgenteService`.

  ```json
  { "especialidad": "Dermatología",
    "prestaciones": [
      { "nombre": "Crioterapia", "requiereValidacion": false,
        "medicos": [{ "nombre": "Camila", "apellido": "Vega", "matricula": "MP-12134" }] } ] }
  ```

  `requiereValidacion` = alguna indicación vigente lo requiere (el turno arranca en
  `ESPERA_VALIDACION`). **Errores:** `404 ESPECIALIDAD_NO_ENCONTRADA` (ninguna parecida → Excepción 3
  de ECU-TURN-1); `409 ESPECIALIDAD_AMBIGUA` (varias parecidas: el mensaje las nombra).
- **`GET /Agente/Prestacion?nombre=…`** → prestaciones publicadas parecidas al nombre ("¿hacen
  electrocardiogramas?"), hasta 10: `{ nombre, especialidad, requiereValidacion, indicaciones[], medicos[] }`,
  con `indicaciones[] = { nombre, descripcion, requiereValidacion }`. Lista vacía si no hay.
- **`GET /Agente/Medico?nombre=…&especialidad=…&prestacion=…`** → médicos activos parecidos por nombre o
  apellido ("Raúl", "dra gómez"), por especialidad o por prestación; los tres son opcionales pero hace
  falta al menos uno (`422` si no viene ninguno). Hasta 10: `{ nombre, apellido, matricula, especialidad,
  prestaciones[], coincidencia, haceLaPrestacion, atiendeParticular, proximosDiasConAgenda[] }`. Detalle
  y semántica en §7.11. Lista vacía si no hay.

**Búsqueda por parecido** (`search<Entidad>ByNombre` en los DomainServices): se normaliza la consulta
(minúsculas, sin tildes ni signos, sin "dr/dra/la/de…"); primero **cada palabra** tiene que estar
contenida (`LIKE`) en el nombre (o en nombre/apellido del médico) sobre `lower(unaccent(...))`; si no
hay coincidencias, se busca por **similitud de trigramas** (`word_similarity` de `pg_trgm` ≥ 0,35) para
tolerar errores de tipeo. Si hay varias coincidencias y una es exacta, gana la exacta.

### 7.5 ECU-TURN-2 · Turnos disponibles — `GET /Agente/Turno/Disponibilidad`

Reusa **`AgendaHorariosDiaQueryService.findHorariosDisponibles(criteria, pageable)`** (ya
filtra `estaOcupada=false`, `fechaLimiteReserva > now` y horizonte de reserva de la clínica).

**Query params:** `nombrePrestacion` (req), `matricula` (req), `franjaHoraria?`
(`MANANA|TARDE|NOCHE`), paginación.

**Resolución:** `matricula` → `Medico` activo; `nombrePrestacion` se resuelve **por parecido entre
las prestaciones publicadas que ese médico atiende hoy** (así se valida también que la atienda:
"consulta" con una dermatóloga → Consulta Dermatológica); construir el `AgendaHorariosCriteria` con
esos UUIDs + franja y delegar. Errores nuevos: `409 PRESTACION_AMBIGUA` (el médico atiende varias
parecidas al nombre).

**Response (`ListHorarioDisponibleAgenteResponse`):** lista de `{ slotId, fechaHora, franja }`.
El `slotId` es **UUID opaco**: viaja a Flowise para usarlo en Solicitar/Reprogramar, pero el
agente **solo muestra `fechaHora`** al paciente, nunca el id (ECU-TURN-3).

**Errores:** prestación/médico inexistentes (Excepción 5/6), médico no ofrece la prestación
(Excepción 7), sin turnos (lista vacía → el agente dice Excepción 8).

### 7.6 ECU-TURN-3 · Solicitar turno — `POST /Agente/Turno`

**Request (`CreateTurnoAgenteRequest`):** `slotId` (UUID opaco de la Disponibilidad). El
paciente ya está identificado por el header; el médico, la prestación y la cobertura se
**derivan del slot** y del paciente — no viajan en el body.

**Resolución:** teléfono → `Paciente`; del `slotId` se obtienen `MedicoPrestacion` →
`medicoId`, `prestacionId`; cobertura: si el paciente tiene `ObraSocialPaciente` para esa
prestación, se usa (regla del CU: el monto se calcula según cobertura). Se arma el
`CreateTurnoRequest` web (`pacienteId, medicoId, prestacionId, slotId, obraSocialPacienteId?`)
y se delega en **`TurnoApp.createTurno`**.

**Response:** bloque base del Turno (código, fechaHoraInicio, prestación, médico con `matricula`,
montoAPagar, tipoCobertura, estadoActual = `PENDIENTE` o `ESPERA_VALIDACION`) e **`indicaciones[]`**
(`{ nombre, descripcion, requiereValidacion }`) para que el agente las repita, sin exponer UUIDs
salvo el `codigo`. Reprogramar y confirmar también devuelven `matricula`.

**Errores:** `PRESTACION_NO_PUBLICADA`, `AGENDA_HORARIO_FUERA_DE_PLAZO` (→ Excepción 9
"ese horario ya no está disponible"), recursos no encontrados.

> **Decisión tomada:** el `slotId` viaja por la sesión de Flowise (opción A). Alternativa
> (no elegida): que Solicitar reciba `fecha+hora+matricula+prestacion` y el back re-resuelva
> el slot. Se eligió A por simplicidad; el UUID nunca llega al paciente, que es lo que pide el CU.

### 7.7 Apoyo · Listar mis turnos — `GET /Agente/Turno`

Para ECU-TURN-4/5/6 (paso "mostrar la lista de turnos"). Lista los turnos **del paciente del
header** filtrando por estado vigente **y** por plazo vigente de la acción: deja afuera los turnos
cuya fecha límite ya venció (`ahora > fechaLimite`, la misma comparación de las operaciones).
La fecha límite que usa cada acción: `CANCELABLE` → `fechaLimiteCancelacion`;
`REPROGRAMABLE` → `fechaLimiteReprogramacion`; `CONFIRMABLE` → `fechaLimiteConfirmacion`. El 409
`*_FUERA_DE_PLAZO_*` de cancelar/reprogramar/confirmar queda como defensa ante un vencimiento
entre el listado y la acción.

**Query params:** `accion` = `CANCELABLE | REPROGRAMABLE | CONFIRMABLE` (traduce a los estados
del CU: cancelable = `PENDIENTE|CONFIRMADO|ESPERA_VALIDACION`; reprogramable =
`PENDIENTE` (un turno `CONFIRMADO` no se reprograma: hay que cancelarlo y sacar otro);
confirmable = `PENDIENTE`).

**Resolución:** reusa `TurnoQueryService` con criteria `pacienteId` (resuelto del teléfono) +
estado. **Scope por teléfono forzado** (nunca lista turnos de otro paciente).

**Response:** lista de `{ codigo, fechaHoraInicio, nombrePrestacion, nombreMedico, apellidoMedico, matricula, estadoActual }`.
**Errores:** lista vacía → el agente dice "No tiene turnos que se puedan …" (Excepción 4 de
cada CU).

### 7.8 ECU-TURN-4 · Cancelar — `DELETE /Agente/Turno/{codigo}`

Sin body. **Resolución:** `codigo` → `Turno`; **validar que el turno pertenece al paciente del
header** (scope). Delegar en **`TurnoApp.cancelTurno`** con motivo por defecto
`SOLICITUD_DEL_PACIENTE` (ver §8: cómo delegar sin `UsuarioDetails`).

**Response:** 204. **Errores:** `TURNO_FUERA_DE_PLAZO_CANCELACION` (→ Excepción 5), turno no
encontrado o ajeno al paciente (→ 404/scope), `409 TURNO_NO_CANCELABLE`: desde el canal solo se
cancela un turno `PENDIENTE`, `CONFIRMADO` o `ESPERA_VALIDACION` (los mismos estados del listado
`CANCELABLE`); un turno en sala de espera, en curso o terminado no (el panel no cambia).

### 7.9 ECU-TURN-5 · Reprogramar — `PATCH /Agente/Turno/{codigo}/Reprogramacion`

**Request (`ReprogramTurnoAgenteRequest`):** `slotId` (UUID opaco de la Disponibilidad del
mismo médico y prestación; se obtiene con `GET /Agente/Turno/Disponibilidad` filtrando por el
médico/prestación del turno; el agente toma `nombrePrestacion` y `matricula` del turno elegido en
`GET /Agente/Turno?accion=REPROGRAMABLE`). **Resolución:** `codigo` → `Turno`; scope por teléfono; armar el
`ReprogramTurnoRequest` web (`id, slotId`) y delegar en **`TurnoApp.reprogramTurno`**
(ver §8: `UsuarioDetails`).

**Response:** bloque base del turno nuevo + `turnoOrigenId` interno (no se muestra). **Errores:**
`TURNO_FUERA_DE_PLAZO_REPROGRAMACION` (Excepción 5), `AGENDA_HORARIO_FUERA_DE_PLAZO`
(Excepción 7), `PRESTACION_NO_PUBLICADA`, `PLAN_NO_PUBLICADO` (el plan de la cobertura del turno dejó de
estar Publicado), `TURNO_TRANSICION_INVALIDA` (el turno no está `PENDIENTE`).

### 7.10 ECU-TURN-6 · Confirmar — `PATCH /Agente/Turno/{codigo}/Confirmacion`

Sin body. **Resolución:** `codigo` → `Turno`; scope por teléfono; delegar en
**`TurnoApp.confirmTurno(id)`** (este método **no** pide `UsuarioDetails`, delega limpio).

**Response:** bloque base, `estadoActual = CONFIRMADO`. **Errores:**
`TURNO_FUERA_DE_PLAZO_CONFIRMACION` (Excepción 5), turno no `PENDIENTE`, no encontrado/ajeno.

### 7.11 Consulta flexible · Médicos — `GET /Agente/Medico`

Todos los parámetros son opcionales, pero hace falta al menos uno de `nombre`, `especialidad` o `prestacion`
(`422 VALIDACION` si no viene ninguno). Sin registro. Se resuelven por parecido (§7.4). Compatible con el uso
anterior (`?nombre=`): los campos nuevos son aditivos.

| Parámetro | Efecto |
|-----------|--------|
| `nombre` | nombre o apellido aproximado del médico |
| `especialidad` | **filtra** a los médicos de esa especialidad |
| `prestacion` | si no se nombró al médico ni la especialidad, **filtra** a quienes la hacen hoy; si se nombró, no filtra: se informa por médico |

**Response** (lista, hasta 10):

```json
[{ "nombre": "Ricardo", "apellido": "Molina", "matricula": "MP-12011", "especialidad": "Cardiología",
   "prestaciones": ["Consulta Cardiológica", "Electrocardiograma"], "coincidencia": "EXACTA",
   "haceLaPrestacion": true, "atiendeParticular": true,
   "proximosDiasConAgenda": [{ "fecha": "2026-11-12", "fechaTexto": "jueves 12/11/2026", "hayHorariosLibres": false }] }]
```

- `haceLaPrestacion` / `atiendeParticular`: `null` si no se pidió `prestacion`; con ella, `true`/`false` según
  la asignación `MedicoPrestacion` vigente hoy. "¿El traumatólogo Pérez hace consultas generales?" →
  `?nombre=Pérez&especialidad=traumatología&prestacion=consulta` → `haceLaPrestacion` sí o no.
- `proximosDiasConAgenda`: días con agenda en las próximas 4 semanas (con la prestación pedida, si vino);
  `hayHorariosLibres` = queda algún horario libre y dentro del plazo de reserva. El agente copia `fechaTexto`.
- Lista vacía si lo nombrado no existe.

### 7.12 Consulta flexible · Horarios — `GET /Agente/Horario`

Busca horarios disponibles con filtros, en **una sola llamada**, agrupados por día, médico y prestación. Sin
registro. Es la herramienta pensada para "¿turno con el Dr. Molina el jueves?" (reemplaza el recorrido
especialidad → prestación → médico → horario cuando el paciente ya sabe qué quiere).
`GET /Agente/Turno/Disponibilidad` sigue funcionando.

**Query params** (todos opcionales, pero **al menos uno de `medico`, `prestacion` o `especialidad`**; si no,
`422 VALIDACION`):

| Parámetro | Tipo | Significado |
|-----------|------|-------------|
| `medico` | texto | matrícula o nombre/apellido aproximado |
| `prestacion` | texto | nombre aproximado |
| `especialidad` | texto | nombre aproximado |
| `fecha` | `AAAA-MM-DD` | día puntual (pisa el rango) |
| `fechaDesde` / `fechaHasta` | `AAAA-MM-DD` | rango, inclusive |
| `diaSemana` | `LUNES`…`DOMINGO` | "los jueves": se expande a las fechas del período dentro del horizonte de reserva |
| `franjaHoraria` | `MANANA`/`TARDE`/`NOCHE` | franja del día |
| `horaDesde` / `horaHasta` | `HH:mm` | hora de **inicio** del horario: desde (inclusive) hasta (exclusive); se intersecta con la franja |
| `pagina` | entero ≥ 0 | página de días (default 0), para pedir más cuando `hayMas` es `true` |

`422` también si `horaDesde >= horaHasta` o `fechaDesde > fechaHasta`.

**Reglas fijas** (como `findHorariosDisponibles`): horario libre, no borrado, dentro del plazo de reserva y de
`diasMaximosAnticipacionReserva`; médico activo; el médico **sigue haciendo la prestación en la fecha del
horario**; prestación **publicada**.

**Response** (`SearchHorarioAgenteResponse`):

```json
{ "dias": [{
    "fecha": "2026-11-19", "fechaTexto": "jueves 19/11/2026",
    "medicos": [{
      "nombre": "Ricardo", "apellido": "Molina", "matricula": "MP-12011", "especialidad": "Cardiología",
      "atiendeDesde": "09:00:00", "atiendeHasta": "18:00:00",
      "prestaciones": [{
        "prestacion": "Consulta Cardiológica", "hayMas": false,
        "horarios": [{ "slotId": "0b612a5d-…", "fechaHora": "2026-11-19T14:00:00",
                       "fechaHoraTexto": "jueves 19/11/2026 14:00", "franja": "TARDE" }] }] }] }],
  "hayMas": false, "codigoMotivo": null, "motivo": null }
```

- Tope: 3 días por página y 10 horarios por prestación (de un médico en un día). `hayMas` (global) es `true`
  si hay más días (pedir `pagina+1`) o más horarios (afinar con `franjaHoraria`, `horaDesde`, `fecha`).
  Cada `prestaciones[].hayMas` indica cuál se recortó.
- `atiendeDesde`/`atiendeHasta`: rango en que el médico atiende ese día (primer inicio a último fin de sus
  horarios cargados, libres u ocupados).
- `slotId` es el UUID opaco para `Turno/Cotizacion` y `POST /Turno`; el agente muestra `fechaHoraTexto`.
- **Sin resultados:** `dias: []`, `hayMas: false` y el porqué en `codigoMotivo` / `motivo` (no es un error HTTP):
  `NO_ENCONTRADO` (el médico, la prestación o la especialidad no existen), `NO_HACE_PRESTACION`,
  `TODO_OCUPADO` (hay horarios cargados pero ocupados o vencidos), `NO_ATIENDE_ESE_DIA` (nada cargado para esos
  filtros) y `FUERA_DE_HORIZONTE`.

### 7.13 Consulta flexible · Cobertura — `GET /Agente/Prestacion/Cobertura`

Qué cubre una obra social y plan para una prestación y cuánto se paga, **sin elegir un horario**. El monto final
del turno lo sigue dando `Turno/Cotizacion` antes de reservar.

**Query params:** `prestacion` (**obligatorio**, aproximado), `obraSocial`, `plan`, `medico` (matrícula o nombre).

- Con `obraSocial` y `plan`: ese plan. Solo `obraSocial`: todos sus planes vigentes. Solo `plan`: los planes
  vigentes de ese nombre en cualquier obra social. El nombre se compara sin mayúsculas ni tildes, igual o contenido.
- **Sin `obraSocial` ni `plan`:** usa la cobertura del paciente registrado (`X-Paciente-Telefono`). Sin paciente
  registrado → `409 PACIENTE_NO_REGISTRADO`; sin obra social cargada → `409 PACIENTE_SIN_OBRA_SOCIAL`.
- Con `medico`: el monto de ese médico (`409 MEDICO_NO_ATIENDE_PRESTACION` si no la hace); sin `medico`: el de
  cada médico que la hace hoy (`409 PRESTACION_SIN_MEDICOS` si ninguno).

**Response** (`GetCoberturaPrestacionAgenteResponse`):

```json
{ "prestacion": "Electrocardiograma",
  "coberturas": [{
    "obraSocial": "OSDE", "plan": "OSDE 450", "cubre": true, "modalidad": "PORCENTUAL",
    "porcentajeCobertura": 80.00, "coseguro": null, "codigoMotivo": null, "motivo": null,
    "montos": [{ "nombre": "Ricardo", "apellido": "Molina", "matricula": "MP-12011",
                 "precioParticular": 24200.00, "montoAPagar": 19360.00 }] }] }
```

- `cubre: false` con `codigoMotivo` / `motivo`: `OBRA_SOCIAL_NO_ENCONTRADA` ("No trabajamos con la obra social X"),
  `PLAN_NO_ENCONTRADO`, `PLAN_NO_PUBLICADO` o `PLAN_NO_CUBRE_PRESTACION`. En esos casos `montos[].montoAPagar` es el
  precio particular. No es un error HTTP.
- Errores: `404 PRESTACION_NO_ENCONTRADA`, `409 PRESTACION_AMBIGUA`, `404 MEDICO_NO_ENCONTRADO`, `409 MEDICO_AMBIGUO`.

### 7.14 Lista de claves de verificación — `POST /Agente/Verificacion`

Claves (todas opcionales) del request y qué se contrasta con la base. Detalle en `Docs/Diseno-Chatbot.md §4.3`.

| Clave | Campos | Se verifica |
|-------|--------|-------------|
| `especialidades` | `nombre` | existe y está activa |
| `prestaciones` | `nombre`, `especialidad?`, `indicaciones?[]`, `requiereValidacion?` | publicada y datos declarados |
| `medicos` | `matricula`, `nombre?`, `especialidad?`, `prestacion?` | activo y datos declarados |
| `horarios` | `fecha`, `diaSemana?`, `hora`, `matricula`, `prestacion` | existe un slot libre y el día corresponde |
| `disponibilidad` | `prestacion`, `matricula?`, `fecha?`, `franjaHoraria?`, `hora?`, `hay` | hay (o no) horarios libres en ese día, franja u hora (`hora` exige `fecha`) |
| `turnos` | `codigo`, `fecha?`, `hora?`, `estado?` | es del paciente del teléfono y datos reales |
| `cotizaciones` | `fecha`, `hora`, `matricula`, `prestacion`, `cobertura`, `plan?`, `monto?` | monto, cobertura y plan |
| `obrasSociales` | `nombre`, `planes?[]` | obra social activa y planes vigentes suyos |
| `coberturas` | `prestacion`, `plan`, `cubre` | el plan cubre (o no) la prestación hoy |

El response repite las listas con `{dato, verdadero, motivo, correcto}` por ítem.

---

## 8. Trabajo nuevo en el back (gaps del diseño inicial, ya resueltos)

1. **`findPacienteActivoByNumeroTelefono(String)`** en `PacienteDomainService` +
   `PacienteRepository.findActivoByNumeroTelefonoNormalizado` (compara solo dígitos, con
   `Services/Utils/NormalizadorTelefono`). Lanza `RecursoNoEncontrado` `PACIENTE_NO_ENCONTRADO`.
2. **Delegación sin `UsuarioDetails`.** Sin overloads ni cambios en `TurnoApp`: `AgenteApp` copia la
   orquestación de `cancelTurno`/`reprogramTurno` **sin el scope de médico** (el actor es el paciente).
3. **Scope por teléfono:** `TurnoDomainService.validateTurnoPerteneceAPaciente(turno, paciente)` →
   `ReglaNegocio` `RECURSO_AJENO` (409). No hace falta un `AlcancePacienteService` aparte.
4. **Cobertura por nombre (PAC-1/PAC-2):** `ObraSocialDomainService.findObraSocialActivaByNombre` y
   `PlanDomainService.findPlanActivoByObraSocialIdAndNombre`; `AgenteApp` crea/reemplaza la
   `ObraSocialPaciente`.
5. **Cadenas de seguridad** `/Agente/**` (API key) y `/Chatwoot/**` (firma HMAC en `ChatwootFirmaFilter`)
   en `SecurityFilterChainConfig`.
6. **Webhook + clientes de Chatwoot y Flowise** (el pasamanos, §6), con agrupación de mensajes.
7. Búsquedas nuevas por clave de negocio: `findTurnoByCodigo`, `findEspecialidadActivaByNombre`,
   `findEspecialidadesActivas`, `findPrestacionActivaByNombre`, `findPrestacionesPublicadasByEspecialidadId`,
   `findMedicoActivoByMatricula`, `findMedicosActivosByEspecialidadId`, `findAgendaHorarioActivoById`,
   `existsCoberturaActivaByPlanAndPrestacion`, `findTurnosByPacienteIdAndEstadosVigentes`.

---

## 9. Handoff a humano (ECU-TURN-23)

No modifica el dominio (el CU lo dice: "el sistema no crea ni modifica instancias"). Por eso
**no es un endpoint `/Agente/*`**: lo maneja el pasamanos (§6).

- El agente de Flowise, cuando detecta que el paciente quiere una persona o está trabado,
  agrega el token **`—HANDOFF—`** a su respuesta.
- El back, al recibir la respuesta de Flowise, detecta el token y llama a Chatwoot
  `toggle_status: open` → la conversación cae en la bandeja de los operadores humanos. El bot
  deja de responder mientras esté en `open` (§6.B y §6.F).
- También deriva el back solo, sin token de handoff, tras 3 respuestas `—FUERA_DE_TEMA—` seguidas (§6.D).
- Si Chatwoot falla al enviar la respuesta o al derivar (`Services/ExternalServices/ChatwootService`): el back
  solo hace `log.error` y el paciente no recibe un mensaje extra. Es la decisión vigente
  (coherente con §6.E); la Excepción 2 de la especificación del CU queda desactualizada.

---

## 10. Lo que NO es endpoint (vive en el flow de Flowise)

- Interpretar lenguaje natural, pedir datos de a uno, reintentar (Excepción 1 de todos los CU).
- Los menús y saludos ("ofrecer cancelar/reprogramar/confirmar", "consultar especialidades").
- Decidir el handoff (agregar `—HANDOFF—`).
- Mantener el hilo de la conversación y la memoria (vive en Flowise + Chatwoot; el back **no**
  guarda estado de chat, sin tablas de chat).

---

## 11. Fase 2 (fuera de este contrato)

- **Notificaciones salientes (NOTIF-1..5):** van por **Chatwoot API → WhatsApp**, no por
  Flowise. Requieren plantillas aprobadas por Meta (envío fuera de la ventana de 24 hs).
  Implementar `CanalNotificacionTurnoWhatsApp` (hoy stub) contra la API de Chatwoot.
- Endurecer la confianza del `X-Paciente-Telefono` (firma / mTLS).
- TLS en `accesmed.site` (hoy el webhook entraría por HTTP; el cert es solo de `chat.`).

---

## 12. Resumen de endpoints

| Método | Ruta | CU | Copia la orquestación de |
|--------|------|----|-----------|
| POST | `/accesmed-api/Chatwoot/Webhook` | canal | — (pasamanos: `ChatwootApp`) |
| POST | `/accesmed-api/Agente/Paciente` | PAC-1 | `PacienteApp.createPaciente` |
| PATCH | `/accesmed-api/Agente/Paciente` | PAC-2 | `PacienteApp.updatePaciente` |
| DELETE | `/accesmed-api/Agente/Paciente` | PAC-3 | `PacienteApp.softDeletePaciente` |
| GET | `/accesmed-api/Agente/Paciente` | apoyo | `PacienteQueryService` |
| GET | `/accesmed-api/Agente/ObraSocial` | apoyo PAC-1/2 | `ObraSocialDomainService` + `PlanDomainService` |
| GET | `/accesmed-api/Agente/Clinica` | TURN-1 | `ClinicaQueryService` |
| GET | `/accesmed-api/Agente/Especialidad` | TURN-1/2/3 | `EspecialidadQueryService` |
| GET | `/accesmed-api/Agente/Especialidad/{nombre}/Oferta` | TURN-1/2/3 | `CatalogoAgenteService` (por parecido, prestación ↔ médico) |
| GET | `/accesmed-api/Agente/Prestacion?nombre=` | TURN-1/2/3 | `CatalogoAgenteService.searchPrestaciones` |
| GET | `/accesmed-api/Agente/Medico?nombre=&especialidad=&prestacion=` | TURN-1/2/3 | `CatalogoAgenteService.searchMedicos` |
| GET | `/accesmed-api/Agente/Horario` | TURN-2/3/5 | `CatalogoAgenteService.searchHorarios` → `AgendaHorariosDiaQueryService` |
| GET | `/accesmed-api/Agente/Prestacion/Cobertura` | TURN-3 | `CotizacionAgenteService.getCoberturaPrestacion` |
| GET | `/accesmed-api/Agente/Turno/Disponibilidad` | TURN-2/3/5 | `findHorariosDisponibles` |
| GET | `/accesmed-api/Agente/Turno/Cotizacion` | TURN-3/5 | `CotizacionAgenteService` (sin guardar) |
| POST | `/accesmed-api/Agente/Verificacion` | canal | `VerificacionAgenteService` (solo lectura) |
| POST | `/accesmed-api/Agente/Turno` | TURN-3 | `TurnoApp.createTurno` |
| GET | `/accesmed-api/Agente/Turno` | TURN-4/5/6 | `TurnoQueryService` |
| DELETE | `/accesmed-api/Agente/Turno/{codigo}` | TURN-4 | `TurnoApp.cancelTurno` |
| PATCH | `/accesmed-api/Agente/Turno/{codigo}/Reprogramacion` | TURN-5 | `TurnoApp.reprogramTurno` |
| PATCH | `/accesmed-api/Agente/Turno/{codigo}/Confirmacion` | TURN-6 | `TurnoApp.confirmTurno` |

---

## 13. Cambios de contrato de la etapa 4 (2026-11-09)

Detalle y decisiones en `Docs/Diseno-Chatbot.md §4`. El flow tiene que adaptarse antes de desplegar
este back: con `cobertura` obligatoria, el flow anterior no puede reservar.

- **Fechas legibles.** Los responses con fecha (horarios, turnos, crear, reprogramar, confirmar y
  cotización) traen `fechaHoraTexto` (`jueves 12/11/2026 09:30`): el agente la copia tal cual, no calcula el día.
  `GET /Agente/Turno/Disponibilidad` acepta `fecha=AAAA-MM-DD` para pedir un día puntual.
- **Búsqueda por parecido.** `searchMedico` y `searchPrestacion` traen `coincidencia`: `EXACTA` o
  `APROXIMADA` (fallback por trigramas). Umbral: `accesmed.agente.busqueda.umbral-similitud` (0.5).
- **Cotización.** `GET /Agente/Turno/Cotizacion?slotId=&cobertura=PARTICULAR|OBRA_SOCIAL[&codigoTurno=]`
  devuelve cobertura, monto, indicaciones y si requiere validación, sin guardar. Con `codigoTurno`
  cotiza la reprogramación (conserva la cobertura del turno). `cubre=false` con `motivo` si no aplica.
- **Reserva.** `POST /Agente/Turno` exige `cobertura`. Con `OBRA_SOCIAL` y un plan que no cubre falla
  con `PLAN_NO_CUBRE_PRESTACION` (ya no cae a particular en silencio).
- **Pasarse a particular.** `PATCH /Agente/Paciente` acepta `particular: true`, que da de baja las
  coberturas activas; no se combina con `obraSocial` (422).
- **Verificación.** `POST /Agente/Verificacion` (`X-Agente-Api-Key`; `X-Paciente-Telefono` para turnos y
  cotizaciones): recibe afirmaciones por entidad (`especialidades`, `prestaciones`, `medicos`,
  `horarios`, `disponibilidad`, `turnos`, `cotizaciones`; todas opcionales) y responde `verdadero` más
  la misma estructura, con `{dato, verdadero, motivo, correcto}` por ítem. Property
  `accesmed.agente.verificacion.habilitada` (default `true`): en `false` todo da verdadero.
- **Avisos de espera.** El back manda "⏳ Un momento…" al recibir el mensaje que abre la tanda (etapa 5); las
  consultas ya no avisan, las escrituras siguen con su aviso propio.
- **Timeouts.** `accesmed.flowise.timeout-conexion-segundos` (5) y `timeout-lectura-segundos` (60); al
  vencer, el canal deriva la conversación a una persona.
- **Cierre por inactividad.** Un `@Scheduled` (cada minuto) cierra las conversaciones atendidas por el
  bot sin mensajes más allá de `ventana-inactividad-minutos`: manda un cierre fijo y las resuelve en
  Chatwoot. No toca las derivadas a una persona.

---

## 14. Cambios de contrato de la etapa 5 (2026-11-09)

Detalle y decisiones en `Docs/Diseno-Chatbot.md §5`.

- **Audio como entrada y salida** (opcional, `ACCESMED_OPENAI_API_KEY`; vacía = apagado): ver 6.B y 6.D.
- **Variable nueva `entradaAudio`** en `$vars` (`"true"`/`"false"`); el flow tiene que habilitarla en
  *Override Config* (`Diseno-Chatbot.md §5.6`).
- **Textos nuevos** de bienvenida, fuera de horario y "texto o audio" (6.B).
- **"⏳ Un momento…" al recibir** el mensaje que abre la tanda; ya no al despacharla.
- **Properties:** `accesmed.openai.base-url`, `accesmed.openai.api-key`, `accesmed.audio.modelo-transcripcion`,
  `modelo-voz`, `voz`, `instrucciones-voz`, `max-bytes`, `timeout-segundos` y `respuesta-con-texto`.

---

## 15. Cambios de contrato de la etapa 6 (2026-11-09)

Detalle en `Docs/Diseno-Chatbot.md §6` y `Docs/Planes/Propuesta-Prompts-Verificacion-Agente.md`. Todo es
aditivo: el flow actual sigue funcionando.

- **Verificación:** `disponibilidad` suma `franjaHoraria` y `hora`; listas nuevas `obrasSociales` y `coberturas` (§7.14).
- **Consultas nuevas:** `GET /Agente/Horario` (§7.12), `GET /Agente/Prestacion/Cobertura` (§7.13) y filtros
  `especialidad`/`prestacion` en `GET /Agente/Medico` (§7.11).
- **Avisos de espera:** aviso inicial "⏳ Aguardá un momento, estoy procesando tu mensaje…"; las consultas vuelven a
  avisar en texto y hay un aviso de reintento (6.H). En el primer mensaje de la ventana, la bienvenida hace de aviso.
- **Bienvenida** nueva (6.B): "…Atendemos de {inicio} a {fin} h.\nUn consejo para que te entienda mejor: contame lo
  que necesitás en un solo mensaje (o en un audio) y esperá mi respuesta antes de mandar el siguiente 🙂 Ya reviso tu mensaje."
- **Audios fijos** pregrabados (bienvenida, espera, despedida, derivación, audio no entendido, audio largo) cuando el
  paciente habló por audio: property `accesmed.audio.fijos-dir` (`ACCESMED_AUDIO_FIJOS_DIR`, default `./audios`;
  opcional, sin archivo sale el texto). `Docs/Diseno-Chatbot.md §5.9`.
