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

**Dónde estamos hoy (2026-11-08):** fases 0 a 4 hechas y probadas por WhatsApp en staging. La
prueba real mostró que el agente afirmaba datos y resultados sin llamar a las tools; los ajustes se
diseñaron en `Docs/Diseno-Chatbot.md §3` (Fase 5). La parte del back (5A) está implementada en
`feature/Agente`, probada en local y sin desplegar; falta la del flow (5B). Marcar con `[x]` a medida
que se completan.

- **Fase 0 — Infra**
  - [x] 0.1 Levantar Flowise en el server: imagen propia (`flowise@3.1.3` por npm) que
        `AccesMed-Flowise` construye y despliega al pushear a `main`; UI en
        `https://flowise.accesmed.site`.
  - [x] 0.2 Verificar que back ↔ Flowise se ven por la red `accesmed-staging`.
- **Fase 1 — Back (capa `Agente`)**
  - [x] 1.1 Cadena de seguridad `/Agente/**` (`X-Agente-Api-Key`) + leer `X-Paciente-Telefono`.
  - [x] 1.2 Gaps del contrato §8: `findPacienteActivoByNumeroTelefono`, scope por teléfono
        (`validateTurnoPerteneceAPaciente`), orquestación de `cancelTurno`/`reprogramTurno` copiada
        en `AgenteApp` sin `UsuarioDetails`.
  - [x] 1.3 Endpoints de consulta (Clínica, Especialidades, Disponibilidad).
  - [x] 1.4 Endpoints de Paciente (registrar / modificar / baja).
  - [x] 1.5 Endpoints de Turno (solicitar / listar / confirmar / cancelar / reprogramar).
  - [x] 1.6 Webhook pasamanos (`POST /accesmed-api/Chatwoot/Webhook`) + clientes de Chatwoot y Flowise.
  - [x] 1.7 Controles del canal (`Docs/Diseno-Chatbot.md §2.3`): `ChatwootService` en
        `Services/ExternalServices` con horario de atención, antispam, ventana de inactividad,
        bienvenida instantánea, variables nuevas para Flowise (`pacienteRegistrado`, `nombrePaciente`,
        `fechaHoraActual`), `sessionId` por ventana y token `—FUERA_DE_TEMA—`.
  - [x] 1.8 `GET /Agente/ObraSocial` (D8): catálogo de obras sociales con sus planes asociables.
- **Fase 2 — Flowise (el agente)**
  - [x] 2.1 Cargar API key de OpenAI, crear las variables de `Chatbot.md §6.D` y armar el flow (`Docs/Diseno-Chatbot.md §2.4`).
  - [x] 2.2 Custom Tools (uno por endpoint) + system prompt + handoff (`—HANDOFF—`).
- **Fase 3 — Chatwoot + WhatsApp**
  - [x] 3.1 Crear el Agent Bot en Chatwoot apuntando al webhook del back.
  - [x] 3.2 Conectar el inbox de WhatsApp y asignarle el bot.
- **Fase 4 — Prueba end-to-end**
  - [x] 4.1 Mensaje real de WhatsApp (2026-11-08, 6 personas): ver hallazgos en `Docs/Diseno-Chatbot.md §3.1`.
- **Fase 5 — Ajustes tras la prueba real (`Docs/Diseno-Chatbot.md §3`)**
  - [x] 5A.1 Capa `Services/AgenteServices` (`CatalogoAgenteService`, `TandaAgenteService`).
  - [x] 5A.2 Búsqueda por parecido (`LIKE` + `unaccent`, fallback `pg_trgm`) y endpoints
        `GET /Agente/Prestacion`, `GET /Agente/Medico`; oferta con relación prestación ↔ médico.
  - [x] 5A.3 Indicaciones en búsqueda y en la reserva; `matricula` en reservar/reprogramar/confirmar.
  - [x] 5A.4 Verificación de la respuesta del agente con un reintento (`controlInterno`) y avisos de espera.
  - [x] 5A.5 Documentos (etiqueta `documentacion`), token `—FIN—`, cancelar solo desde estados válidos.
  - [ ] 5A.6 Deploy del back a staging + crear la etiqueta `documentacion` en Chatwoot.
  - [ ] 5B Flow: `gpt-5.4` con razonamiento, prompt común con dominio y reglas, tools nuevas
        (`searchPrestacion`, `searchMedico`), variable `controlInterno`, router (despedida + `—FIN—`,
        cancelar acción, documentos), errores traducidos en la tool, indicaciones en la reserva.
  - [ ] 5C Repetir por WhatsApp los casos de `§3.1`.
- **Fase 6 — Verificación, cobertura y ajustes (`Docs/Diseno-Chatbot.md §4`)**
  - [x] 6A.1 Fechas legibles (`fechaHoraTexto`), filtro `fecha` en horarios, `coincidencia` en búsquedas y umbral por property.
  - [x] 6A.2 Cotización (`GET /Agente/Turno/Cotizacion`) y `cobertura` obligatoria en `createTurno`.
  - [x] 6A.3 `POST /Agente/Verificacion` (`VerificacionAgenteService`) con interruptor `accesmed.agente.verificacion.habilitada`.
  - [x] 6A.4 `particular` en `PATCH /Agente/Paciente`, aviso "Un momento…" inmediato, timeouts de Flowise y cierre por inactividad.
  - [x] 6A.5 Panel: P1 (la cobertura del turno es del paciente) y P2 (unicidad del teléfono por dígitos).
  - [x] 6A.6 Endpoints nuevos probados contra el back local (2026-11-09): cotización, verificación (18 afirmaciones, veredictos correctos), `cobertura` obligatoria, `particular` + `obraSocial` rechazado, "Bilardo" sin resultados.
  - [x] 6B Flow en la UI de staging (2026-11-09), exportado a `AccesMed-Flowise`: tools nuevas y modificadas (`getCotizacionTurno`, `cobertura`, `fecha`, `particular`), nodos Verificar + Condition + Loop en las 4 ramas con `<datos>`, prompts (§4.11), router gpt-5.4 sin razonamiento y `listObraSocial` en Oferta. Probado por la API de predicción de Flowise (turno nuevo hasta la cotización, mis turnos, mis datos, obras sociales, estacionamiento).
  - [x] 6C.1 Retiro de la verificación vieja en el back (§4.13): `TandaAgenteService` solo avisa las escrituras, sin `controlInterno`.
  - [x] 6C.2 `controlInterno` fuera del flow y deploy del back a staging.
  - [ ] 6C.3 Batería de pruebas de §4.1 por WhatsApp (reservar y reprogramar de punta a punta, F1–F8 y B1–B4), medir el delay y decidir Strict Tool Calling / quitar `temperature` (§4.9).
  - [ ] 6D Decidir el código de país del teléfono del panel (P2, parte abierta): exigir el prefijo `+54 9…` en front y back (recomendado), completarlo solo, o guardar el número nacional (ver `Docs/Diseno-Chatbot.md §4`, decisión (a)). Hoy el panel acepta un teléfono sin código de país y el bot no lo reconoce; los duplicados por formato que ya existan en la base siguen sin resolverse.

- **Fase 7 — Audio y mensajes del canal (`Docs/Diseno-Chatbot.md §5`)**
  - [x] 7A Back: transcripción y voz con OpenAI (`OpenAiClient`, `OpenAiService`), audio como entrada en `ChatwootApp`, nota privada con la transcripción, `entradaAudio`, "Un momento" al recibir, textos nuevos de bienvenida y fuera de horario, log de latencia.
  - [ ] 7B Deploy a staging + `ACCESMED_OPENAI_API_KEY` en el servidor.
  - [ ] 7C Flow: variable `entradaAudio` y bloque de voz en `comun.md`; export a `AccesMed-Flowise`.
  - [ ] 7D Prueba por WhatsApp (§5.8.5).

> Las Fases 1-2 se pueden validar sin WhatsApp usando el chat de prueba de Flowise y la UI
> de Chatwoot. Notificaciones salientes (NOTIF-1..5) = Fase 2 posterior (contrato §11).