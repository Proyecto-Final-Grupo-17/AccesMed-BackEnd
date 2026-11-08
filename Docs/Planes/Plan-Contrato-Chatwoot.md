# Plan — Contrato Chatwoot ↔ Backend (webhook del Agent Bot)

> Fecha: 2026-11-07. Rama: `feature/Agente`. Contrato resultante: `Docs/Features/Chatbot.md §6`.
> Continúa `Plan-Agente-Chatwoot.md` (que dejó armado el pasamanos sin contrato acordado).

## Decisiones tomadas

| Tema | Decisión |
|------|----------|
| Cómo entra un mensaje | Push: Chatwoot llama al webhook **una vez por mensaje** (`message_created`). Sin polling ni lotes. |
| Datos que usa el back | `conversation.id`, `conversation.status`, `sender.id`, `sender.name`, `sender.phone_number` (respaldo `conversation.meta.sender`), `id`, `content`, `message_type`, `private`. |
| Firma | HMAC `X-Chatwoot-Signature` / `X-Chatwoot-Timestamp` verificada en un **filtro de seguridad** (`Security/Chatwoot/ChatwootFirmaFilter`) → **401** `CHATWOOT_NO_AUTENTICADO`. Sale de `ChatwootApp`. |
| Agrupación | Por conversación: 4 s sin mensajes nuevos **o** 5 mensajes **o** 15 s desde el primero. Nunca dos llamadas simultáneas a Flowise por conversación. El texto se junta de los propios webhooks (en memoria). Configurable en `accesmed.chatwoot.agrupacion.*`. |
| Back → Flowise | `question` = mensajes unidos por salto de línea; `sessionId = chatwoot-<conversationId>`; `vars`: `numeroTelefono`, `nombreContacto`. |
| Medios sin texto | Respuesta inmediata: "Por ahora solo puedo leer mensajes de texto. ¿Me lo escribís?" (no pasa por Flowise). |
| Token del back en Chatwoot | `access_token` del **Agent Bot** (`ACCESMED_CHATWOOT_API_TOKEN`): los mensajes figuran como del bot. |
| Entorno | El Agent Bot apunta a **staging** y se conecta directo al inbox **AccesMed WhatsApp** (el de prueba no funciona). |
| `resolved` → paciente vuelve a escribir | Se verifica en la prueba y se documenta el comportamiento real de Chatwoot. |

## Pasos

1. **Doc** — `Chatbot.md §6` reescrito como contrato (A–G), `§4`/`§11` ajustados, `ARQUITECTURA.md` (filtro de firma, agrupador).
2. **Código**
   - `Security/Chatwoot/ChatwootFirmaFilter` (lee el body una vez, verifica, re-expone el body) + cadena `chatwootSecurityFilterChain`.
   - `Services/Utils/AgrupadorMensajes<T>` (genérico, scheduler propio, `AutoCloseable`) + bean en `AsyncConfig`.
   - `ChatwootApp`: filtrar → medio sin texto → agrupar → `processTandaChatwoot` (Flowise, handoff, errores).
   - Records: `id` del mensaje; `FlowiseClient` con `nombreContacto`; log de `X-Chatwoot-Delivery`.
3. **Build local** — `mvnw compile`.
4. **Chatwoot** — ✅ Agent Bot `AccesMed Bot` creado y conectado a AccesMed WhatsApp. Pendiente: copiar token/secret al `.env` del servidor, desplegar a staging y probar.
