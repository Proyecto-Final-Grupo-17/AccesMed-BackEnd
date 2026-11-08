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

- **Sin teléfono** → mensaje de error técnico y handoff (no llama a Flowise).
- **Sin texto** (audio, imagen, sticker) → responde *"Por ahora solo puedo leer mensajes de texto. ¿Me lo escribís?"*
  (no llama a Flowise ni entra en la agrupación).
- **Con texto** → se agrupa (6.C).

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
    "sessionId": "chatwoot-<conversation.id>",
    "vars": { "numeroTelefono": "+5492617742682", "nombreContacto": "Ana" }
  }
}
```

`question` = los mensajes de la tanda unidos por salto de línea. En el flow se leen como
`$vars.numeroTelefono` y `$vars.nombreContacto` (las variables deben estar habilitadas para override
en la configuración de seguridad del chatflow).

### 6.E Back → Chatwoot (API)

Autentica con `api_access_token` = **access token del Agent Bot** (`ACCESMED_CHATWOOT_API_TOKEN`):
los mensajes figuran como enviados por el bot.

| Acción | Endpoint |
|--------|----------|
| Responder | `POST /api/v1/accounts/{account}/conversations/{id}/messages` `{content, message_type: "outgoing", private: false}` |
| Derivar a persona | `POST /api/v1/accounts/{account}/conversations/{id}/toggle_status` `{status: "open"}` |

Se deriva cuando: la respuesta de Flowise trae `—HANDOFF—` (se quita el token y se envía el resto, §9);
Flowise falla o devuelve vacío (antes se avisa *"Estamos con problemas técnicos, en breve te atiende una
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
> del paciente cuyo plan cubre la prestación, o particular.

En todas las tablas, `X-Paciente-Telefono` + `X-Agente-Api-Key` van en el header (§4) y no
se repiten en el body.

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

### 7.4 ECU-TURN-1 · Información general

Consulta abierta (no requiere registro). Igual se exige `X-Agente-Api-Key`.

- **`GET /Agente/Clinica`** → nombre, ubicación, horarioInicioAtencion, horarioFinAtencion
  (vía `ClinicaDomainService` / `ClinicaQueryService`). Es la info del paso 3 del CU y también
  el "mensaje de presentación" que varios CU muestran cuando el paciente no está registrado.
- **`GET /Agente/Especialidad`** → lista de especialidades activas (`nombre`) vía
  `EspecialidadQueryService`.
- **`GET /Agente/Especialidad/{nombre}/Oferta`** → prestaciones activas (`nombre`) + médicos
  activos (`nombre`, `apellido`, `matricula`) de esa especialidad (vía `PrestacionQueryService`
  + `MedicoQueryService`). **Errores:** especialidad inexistente → Excepción 3 de ECU-TURN-1.

### 7.5 ECU-TURN-2 · Turnos disponibles — `GET /Agente/Turno/Disponibilidad`

Reusa **`AgendaHorariosDiaQueryService.findHorariosDisponibles(criteria, pageable)`** (ya
filtra `estaOcupada=false`, `fechaLimiteReserva > now` y horizonte de reserva de la clínica).

**Query params:** `nombrePrestacion` (req), `matricula` (req), `franjaHoraria?`
(`MANANA|TARDE|NOCHE`), paginación.

**Resolución:** `nombrePrestacion` → `Prestacion` activa; `matricula` → `Medico` activo;
validar `MedicoPrestacion` vigente (médico ofrece la prestación); construir el
`AgendaHorariosCriteria` con esos UUIDs + franja y delegar.

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

**Response:** bloque base del Turno (código, fechaHoraInicio, montoAPagar, tipoCobertura,
estadoActual = `PENDIENTE` o `ESPERA_VALIDACION`), sin exponer UUIDs salvo el `codigo`.

**Errores:** `PRESTACION_NO_PUBLICADA`, `AGENDA_HORARIO_FUERA_DE_PLAZO` (→ Excepción 9
"ese horario ya no está disponible"), recursos no encontrados.

> **Decisión tomada:** el `slotId` viaja por la sesión de Flowise (opción A). Alternativa
> (no elegida): que Solicitar reciba `fecha+hora+matricula+prestacion` y el back re-resuelva
> el slot. Se eligió A por simplicidad; el UUID nunca llega al paciente, que es lo que pide el CU.

### 7.7 Apoyo · Listar mis turnos — `GET /Agente/Turno`

Para ECU-TURN-4/5/6 (paso "mostrar la lista de turnos"). Lista los turnos **del paciente del
header** filtrando por estado vigente.

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
encontrado o ajeno al paciente (→ 404/scope), turno en estado final.

### 7.9 ECU-TURN-5 · Reprogramar — `PATCH /Agente/Turno/{codigo}/Reprogramacion`

**Request (`ReprogramTurnoAgenteRequest`):** `slotId` (UUID opaco de la Disponibilidad del
mismo médico y prestación; se obtiene con `GET /Agente/Turno/Disponibilidad` filtrando por el
médico/prestación del turno; el agente toma `nombrePrestacion` y `matricula` del turno elegido en
`GET /Agente/Turno?accion=REPROGRAMABLE`). **Resolución:** `codigo` → `Turno`; scope por teléfono; armar el
`ReprogramTurnoRequest` web (`id, slotId`) y delegar en **`TurnoApp.reprogramTurno`**
(ver §8: `UsuarioDetails`).

**Response:** bloque base del turno nuevo + `turnoOrigenId` interno (no se muestra). **Errores:**
`TURNO_FUERA_DE_PLAZO_REPROGRAMACION` (Excepción 5), `AGENDA_HORARIO_FUERA_DE_PLAZO`
(Excepción 7), `PRESTACION_NO_PUBLICADA`, `TURNO_TRANSICION_INVALIDA` (el turno no está `PENDIENTE`).

### 7.10 ECU-TURN-6 · Confirmar — `PATCH /Agente/Turno/{codigo}/Confirmacion`

Sin body. **Resolución:** `codigo` → `Turno`; scope por teléfono; delegar en
**`TurnoApp.confirmTurno(id)`** (este método **no** pide `UsuarioDetails`, delega limpio).

**Response:** bloque base, `estadoActual = CONFIRMADO`. **Errores:**
`TURNO_FUERA_DE_PLAZO_CONFIRMACION` (Excepción 5), turno no `PENDIENTE`, no encontrado/ajeno.

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
- Si Chatwoot falla al enviar la respuesta o al derivar (`Application/ChatwootApp`): el back
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
| GET | `/accesmed-api/Agente/Clinica` | TURN-1 | `ClinicaQueryService` |
| GET | `/accesmed-api/Agente/Especialidad` | TURN-1/2/3 | `EspecialidadQueryService` |
| GET | `/accesmed-api/Agente/Especialidad/{nombre}/Oferta` | TURN-1/2/3 | `Prestacion`/`MedicoQueryService` |
| GET | `/accesmed-api/Agente/Turno/Disponibilidad` | TURN-2/3/5 | `findHorariosDisponibles` |
| POST | `/accesmed-api/Agente/Turno` | TURN-3 | `TurnoApp.createTurno` |
| GET | `/accesmed-api/Agente/Turno` | TURN-4/5/6 | `TurnoQueryService` |
| DELETE | `/accesmed-api/Agente/Turno/{codigo}` | TURN-4 | `TurnoApp.cancelTurno` |
| PATCH | `/accesmed-api/Agente/Turno/{codigo}/Reprogramacion` | TURN-5 | `TurnoApp.reprogramTurno` |
| PATCH | `/accesmed-api/Agente/Turno/{codigo}/Confirmacion` | TURN-6 | `TurnoApp.confirmTurno` |
