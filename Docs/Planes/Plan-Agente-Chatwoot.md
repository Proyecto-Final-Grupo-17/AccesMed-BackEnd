# Plan — Conexión Chatwoot ↔ Backend ↔ Flowise (chatbot del paciente)

> Fecha: 2026-11-07. Rama: `feature/Agente` (desde `develop`).
> Contrato funcional de los endpoints: `Docs/Features/Chatbot.md` (§3–§9). Este plan define
> **cómo** se implementa; donde el contrato diga "reutiliza `TurnoApp.x`", ahora significa
> **copiar la orquestación** de ese método (ver Regla 1).

## Reglas no negociables

1. **No se modifica la lógica existente.** Prohibido cambiar el cuerpo o la firma de métodos
   existentes en `Application/*App`, `Services/**`, `Controllers/*Controller` del panel,
   `Domain/**` y migraciones Liquibase. `TurnoApp`, `PacienteApp`, etc. quedan intactos.
   - Se permite **agregar** métodos nuevos (sin tocar los existentes) en Repositories,
     DomainServices y QueryServices cuando haga falta una búsqueda nueva
     (ej. `findPacienteActivoByNumeroTelefono`, `findTurnoByCodigo`).
   - Se permite **copiar y pegar** orquestación de los App existentes a `AgenteApp`
     (incluidos helpers privados como `generarCodigoTurno`).
2. **Ningún App llama a otro App.** `AgenteApp` y `ChatwootApp` solo usan
   DomainServices, QueryServices, Mappers, `Services/Utils` y `Services/Clients`.
3. Convenciones de `CLAUDE.md` y skills `java-springboot-code-style`, `java-springboot-javadoc`,
   `java-springboot-logging` (verbo EN + negocio ES, records por endpoint, `@Slf4j`, `log.warn`
   en el `throw`, excepciones de `Services/Errors` con `getClass()`, Javadoc en español).
4. Los tests existentes deben pasar **sin modificarlos**.
5. No commitear secretos. No hacer push. No tocar el servidor.

## Estructura final (sin carpeta `Agente/`)

```
Controllers/
  AgenteController.java       @RequestMapping("/accesmed-api/Agente")   — Custom Tools de Flowise
  ChatwootController.java     @RequestMapping("/accesmed-api/Chatwoot") — webhook del Agent Bot
Application/
  AgenteApp.java              CU del paciente (copia de orquestación, scope por teléfono)
  ChatwootApp.java            pasamanos Chatwoot → Flowise → Chatwoot (no toca dominio)
Services/
  Clients/                    NUEVO subpaquete: integraciones HTTP salientes
    ChatwootClient.java
    FlowiseClient.java
  Mappers/AgenteMapper.java
Records/
  Agente/Request/  *AgenteRequest
  Agente/Response/ *AgenteResponse
  Chatwoot/Request/ReceiveMensajeChatwootRequest.java (+ records anidados del payload)
Security/
  Config/SecurityFilterChainConfig.java   + cadena /Agente/** y /Chatwoot/**
  (filtro de API key: donde corresponda dentro de Security/, p. ej. Security/Agente/ o junto al JWT)
Config/
  AsyncConfig.java            @EnableAsync + executor para el pasamanos
```

Se **elimina** `src/main/java/com/accesmed/backend/Agente/` (solo tiene `TurnoAgenteController` vacío).

## Fase 1 — Backend

### Paso 0. Preparación
- `git checkout develop && git checkout -b feature/Agente`. No tocar
  `Docs/Otros/Analisis-estatico-issues-main.md` (untracked, ajeno).
- Correr `./mvnw test` y anotar el resultado base (requiere Docker por Testcontainers; si
  Docker no está disponible, **detenerse y reportarlo**).

### Paso 1. Búsquedas nuevas (solo agregar)
- `PacienteRepository`/`PacienteDomainService`: `findPacienteActivoByNumeroTelefono(String)`
  → `RecursoNoEncontradoException` con código `PACIENTE_NO_ENCONTRADO` si no existe (el agente
  deriva al registro). Revisar cómo se guarda `numero_telefono` (formato) y crear un
  normalizador en `Services/Utils` (p. ej. `NormalizadorTelefono`) que compare igual el número
  que manda Chatwoot (E.164, ej. `+5492617742682`) y el que está en BD.
- `TurnoDomainService`: `findTurnoByCodigo(String)` si no existe.
- Método de scope: `validateTurnoPerteneceAPaciente(Turno, Paciente)` en `TurnoDomainService`
  (nuevo) → `ReglaNegocioException` código `RECURSO_AJENO` si el turno no es del paciente.
- Lo que haga falta para Especialidad/Oferta/Disponibilidad: primero buscar si ya existe en
  los QueryServices; agregar solo si no.

### Paso 2. Seguridad
- Cadena `/accesmed-api/Agente/**`: stateless, sin JWT, filtro que exige header
  `X-Agente-Api-Key` == property `accesmed.agente.api-key` (comparación en tiempo constante,
  `MessageDigest.isEqual`); si falla → 401 con formato `AccesMedError`. Si pasa, setear un
  `Authentication` con principal `"agente-whatsapp"`.
- Cadena `/accesmed-api/Chatwoot/**`: `permitAll` a nivel de filtro (la autenticidad la
  valida `ChatwootApp` con la firma HMAC, porque necesita el body crudo).
- Orden: estas cadenas antes de la del panel (mirar cómo está la de docs/Swagger y copiar el
  patrón con `securityMatcher` y `@Order`).
- Test estilo `DocsSecurityTest`: sin key → 401, key incorrecta → 401, key correcta → pasa;
  el panel sigue pidiendo JWT.

### Paso 3. `AgenteApp` + `AgenteController` + records + `AgenteMapper`
Endpoints (contrato detallado de request/response y códigos de error en `Chatbot.md` §7):

| Método | Ruta (`/accesmed-api/Agente` +) | CU | Copia la orquestación de |
|---|---|---|---|
| GET | `/Paciente` | apoyo | `PacienteQueryService` |
| POST | `/Paciente` | PAC-1 | `PacienteApp.createPaciente` |
| PATCH | `/Paciente` | PAC-2 | `PacienteApp.updatePaciente` |
| DELETE | `/Paciente` | PAC-3 | `PacienteApp.softDeletePaciente` (204) |
| GET | `/Clinica` | TURN-1 | `ClinicaQueryService`/DomainService |
| GET | `/Especialidad` | TURN-1 | `EspecialidadQueryService` |
| GET | `/Especialidad/{nombre}/Oferta` | TURN-1 | Prestacion/Medico QueryServices |
| GET | `/Turno/Disponibilidad` | TURN-2 | búsqueda de horarios disponibles existente |
| POST | `/Turno` | TURN-3 | `TurnoApp.createTurno` |
| GET | `/Turno` | apoyo | `TurnoQueryService` (solo turnos del paciente) |
| DELETE | `/Turno/{codigo}` | TURN-4 | `TurnoApp.cancelTurno` (motivo siempre `SOLICITUD_DEL_PACIENTE`, sin scope de médico) |
| PATCH | `/Turno/{codigo}/Reprogramacion` | TURN-5 | `TurnoApp.reprogramTurno` (sin scope de médico) |
| PATCH | `/Turno/{codigo}/Confirmacion` | TURN-6 | `TurnoApp.confirmTurno` |

- Identidad: header `X-Paciente-Telefono` (lo lee el Controller con `@RequestHeader` y lo pasa
  al App). Nunca viaja en el body.
- Patrón de cada método del App: resolver paciente por teléfono → resolver claves de negocio
  (codigo, matrícula, nombre) → `validateTurnoPerteneceAPaciente` cuando aplique → orquestación
  copiada → **publicar el mismo `TurnoNotificacionEvent`** que publica `TurnoApp` → mapear.
- Records nombrados `<Accion><Entidad>AgenteRequest/Response` en `Records/Agente/`.
- Tests unitarios de `AgenteApp` (Mockito, estilo `TurnoAppTest`): turno de otro paciente →
  `RECURSO_AJENO`; teléfono desconocido → `PACIENTE_NO_ENCONTRADO`; cancel fuera de plazo;
  caso feliz de create/cancel/reprogram/confirm.

### Paso 4. Clientes HTTP (`Services/Clients`)
Usar `RestClient` de Spring (sin dependencias nuevas). Timeouts explícitos.

- `FlowiseClient.sendPreguntaFlowise(String pregunta, String sessionId, String numeroTelefono)`
  → `POST {flowise.base-url}/api/v1/prediction/{flowise.chatflow-id}`, header
  `Authorization: Bearer {flowise.api-key}` (omitir si está vacía), body
  `{"question": pregunta, "overrideConfig": {"sessionId": sessionId, "vars": {"numeroTelefono": numeroTelefono}}}`.
  Devuelve el campo `text` de la respuesta. Timeout de lectura ~60 s.
- `ChatwootClient`:
  - `sendMensajeChatwoot(long conversationId, String contenido)` →
    `POST {chatwoot.base-url}/api/v1/accounts/{account-id}/conversations/{id}/messages`,
    body `{"content": ..., "message_type": "outgoing", "private": false}`.
  - `openConversacionChatwoot(long conversationId)` →
    `POST .../conversations/{id}/toggle_status`, body `{"status": "open"}`.
  - Header `api_access_token: {chatwoot.api-token}`.

### Paso 5. `ChatwootApp` + `ChatwootController` (pasamanos)
- `POST /accesmed-api/Chatwoot/Webhook`: el Controller recibe `@RequestBody String` crudo +
  headers `X-Chatwoot-Signature`, `X-Chatwoot-Timestamp`; delega y responde **200 de inmediato**.
- `ChatwootApp.receiveMensajeChatwoot(...)`:
  1. Verificar firma: `sha256=` + hex(HMAC-SHA256(webhook-secret, "{timestamp}.{rawBody}")),
     comparación en tiempo constante, rechazar timestamp > 5 min. Si
     `accesmed.chatwoot.webhook-secret` está vacía, loguear `warn` y aceptar (solo para poder
     probar en Fase 2 si el Agent Bot no manda firma; documentarlo).
  2. Parsear a `ReceiveMensajeChatwootRequest` (ignorar campos desconocidos).
  3. Procesar solo si `event == "message_created"`, `message_type == "incoming"`,
     conversación en `pending` y `private == false`; si no, ignorar (`debug`).
  4. Teléfono: `sender.phone_number` (fallback `conversation.meta.sender.phone_number`).
  5. Procesamiento **asíncrono** (`@Async` en un bean aparte o método público de otro bean,
     para que el proxy funcione): Flowise con `sessionId = "chatwoot-" + conversationId`.
  6. Respuesta con token `—HANDOFF—` → quitar el token, enviar el texto restante (si hay) y
     `openConversacionChatwoot`. Si no → `sendMensajeChatwoot`.
  7. Errores: si Flowise falla → mensaje genérico al paciente ("Estamos con problemas
     técnicos, en breve te atiende una persona") + `openConversacionChatwoot`. Si Chatwoot
     falla → `log.error` (no hay a quién responder).
- Tests: firma válida/inválida/vieja, filtrado (outgoing, open, private, otro evento),
  handoff, fallo de Flowise (clientes mockeados).

### Paso 6. Configuración
Properties nuevas (en `application.yml` con `${ENV:default}`):

| Property | Env var | Default |
|---|---|---|
| `accesmed.agente.api-key` | `ACCESMED_AGENTE_API_KEY` | sin default (obligatoria) |
| `accesmed.chatwoot.base-url` | `ACCESMED_CHATWOOT_BASE_URL` | `https://chat.accesmed.site` |
| `accesmed.chatwoot.account-id` | `ACCESMED_CHATWOOT_ACCOUNT_ID` | `1` |
| `accesmed.chatwoot.api-token` | `ACCESMED_CHATWOOT_API_TOKEN` | sin default (obligatoria) |
| `accesmed.chatwoot.webhook-secret` | `ACCESMED_CHATWOOT_WEBHOOK_SECRET` | vacío |
| `accesmed.flowise.base-url` | `ACCESMED_FLOWISE_BASE_URL` | `http://accesmed-flowise:3000` |
| `accesmed.flowise.chatflow-id` | `ACCESMED_FLOWISE_CHATFLOW_ID` | sin default (obligatoria) |
| `accesmed.flowise.api-key` | `ACCESMED_FLOWISE_API_KEY` | vacío |

- Agregar a `.env.example`, `src/test/resources/application-test.yml` (valores dummy),
  `docker/deploy/docker-compose.yml` y `docker/deploy/.env.example` (crearlo si no existe,
  solo placeholders). Usar `@ConfigurationProperties` (record) si el proyecto ya lo hace;
  si no, `@Value`.

### Paso 7. Documentación (mismo cambio)
- `Docs/ARQUITECTURA.md`: quitar el slice vertical `Agente` (líneas ~53, ~57, árbol ~449,
  ~579); el agente entra por `Controllers/AgenteController` + `Application/AgenteApp`;
  agregar `Services/Clients`, `Records/Agente`, `Records/Chatwoot`; documentar la regla
  "ningún App llama a otro App" y que `AgenteApp` copia orquestación para no tocar el panel.
- `CLAUDE.md`: estructura (`Services/Clients`), env vars obligatorias nuevas en "Secretos".
- `Docs/Features/Chatbot.md`: webhook pasa a `POST /accesmed-api/Chatwoot/Webhook`; §8
  (gaps) actualizado: sin overloads, orquestación copiada; §12 tabla de endpoints.
- `Docs/Security.md`: cadenas `/Agente/**` (API key) y `/Chatwoot/**` (HMAC en el App).
- `Docs/DEPLOY.md`: env vars nuevas del contenedor del back.
- `Docs/BACKLOG.md`: marcar avance.

### Paso 8. Verificación
- `./mvnw test` completo: los tests previos pasan sin cambios + los nuevos pasan.
- `git diff develop --stat` y revisar que ningún archivo de lógica existente aparezca
  modificado salvo: `SecurityFilterChainConfig`, Repositories/DomainServices/QueryServices
  con métodos **agregados**, configs y docs.
- No commitear: dejar los cambios en la rama para revisión.

## Fase 2 — Chatwoot (la ejecuta Franco / con confirmación explícita)

1. Cargar en el `.env` del servidor (`docker/deploy`) las env vars nuevas y redeployar staging.
2. Flowise: obtener `chatflow-id` y API key del chatflow; configurar en los Custom Tools los
   headers `X-Agente-Api-Key` y `X-Paciente-Telefono` (= `$vars.numeroTelefono`).
3. Chatwoot → Perfil → token de acceso (o token del bot) → `ACCESMED_CHATWOOT_API_TOKEN`.
4. Chatwoot → Ajustes → Bots → Agregar Bot "AccesMed Bot", URL
   `https://accesmed.site/accesmed-api/Chatwoot/Webhook` (requiere TLS en `accesmed.site`),
   guardar el secret en `ACCESMED_CHATWOOT_WEBHOOK_SECRET`.
5. Chatwoot → Bandejas → **"AccesMed WhatsApp"** (la bandeja de prueba no se usa) →
   Configuración del Bot → elegir "AccesMed Bot". Desde ese momento las conversaciones nuevas
   entran en `pending` y las atiende el bot.
6. Probar: mensaje desde WhatsApp → respuesta del bot; pedir operador → conversación pasa a
   `open`. Verificar en el log que llega la firma HMAC y el `phone_number` del sender.
