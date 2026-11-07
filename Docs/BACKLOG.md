# Backlog — AccesMed Backend

> Pendientes futuros, no prioritarios. A diferencia de `CLAUDE.md` (convenciones ya
> vigentes) o `Docs/Planes/` (planes de implementación en curso), esto es una lista de
> ideas/mejoras para retomar más adelante, sin fecha asignada.

## Logging

- **Correlation/request id (MDC)**: hoy cada línea de log (`Controller` → `App` →
  `DomainService`) es independiente. Sin un id común, con tráfico concurrente en
  `staging`/`prod` va a ser difícil reconstruir la secuencia completa de una request
  específica entre capas.

  Implementación prevista: un `Filter`/`HandlerInterceptor` que haga
  `MDC.put("requestId", UUID...)` al entrar la request y `MDC.clear()` al salir, más
  agregar `%X{requestId}` al pattern de Logback. No requiere tocar las líneas de
  `log.info/warn/debug` existentes.

  Retomar cuando se acerque `staging` o haya tráfico concurrente real.

## Chatbot de WhatsApp (en progreso)

Roadmap de implementación del chatbot (agente Flowise). Doc del chatbot:
`Docs/Features/Chatbot.md`; repo del agente: `AccesMed-Flowise` (hermano).
Topología pasamanos:
`WhatsApp → Chatwoot → back → Flowise → back → Chatwoot`.

**Dónde estamos hoy (2026-11-03):** diseño cerrado y contrato escrito. Falta implementar
(todos los pasos de abajo pendientes). Marcar con `[x]` a medida que se completan.

- **Fase 0 — Infra**
  - [ ] 0.1 Levantar Flowise en el server: imagen propia (`flowise@3.1.3` por npm) que
        `AccesMed-Flowise` construye y despliega al pushear a `main`; UI en
        `https://flowise.accesmed.site`.
  - [ ] 0.2 Verificar que back ↔ Flowise se ven por la red `accesmed-staging`.
- **Fase 1 — Back (capa `Agente`)**
  - [ ] 1.1 Cadena de seguridad `/Agente/**` (`X-Agente-Api-Key`) + leer `X-Paciente-Telefono`.
  - [ ] 1.2 Gaps del contrato §8: `findPacienteActivoByNumeroTelefono`, `AlcancePacienteService`
        (scope por teléfono), overloads de `cancelTurno`/`reprogramTurno` sin `UsuarioDetails`.
  - [ ] 1.3 Endpoints de consulta (Clínica, Especialidades, Disponibilidad).
  - [ ] 1.4 Endpoints de Paciente (registrar / modificar / baja).
  - [ ] 1.5 Endpoints de Turno (solicitar / listar / confirmar / cancelar / reprogramar).
  - [ ] 1.6 Webhook pasamanos + clientes de Chatwoot y Flowise.
- **Fase 2 — Flowise (el agente)**
  - [ ] 2.1 Cargar API key de OpenAI y armar el chatflow.
  - [ ] 2.2 Custom Tools (uno por endpoint) + system prompt + handoff (`—HANDOFF—`).
- **Fase 3 — Chatwoot + WhatsApp**
  - [ ] 3.1 Crear el Agent Bot en Chatwoot apuntando al webhook del back.
  - [ ] 3.2 Conectar el inbox de WhatsApp y asignarle el bot.
- **Fase 4 — Prueba end-to-end**
  - [ ] 4.1 Mensaje real de WhatsApp: registrarse → sacar turno → confirmar → handoff.

> Las Fases 1-2 se pueden validar sin WhatsApp usando el chat de prueba de Flowise y la UI
> de Chatwoot. Notificaciones salientes (NOTIF-1..5) = Fase 2 posterior (contrato §11).