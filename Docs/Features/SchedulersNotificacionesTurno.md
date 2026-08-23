# Feature: Schedulers y Notificaciones de Turno

> **Estado actual**: solo está armada la estructura de paquetes y clases (skeleton), sin
> lógica de negocio ni integración real. Este documento describe el diseño acordado; se
> completa a medida que se implementen los CU de `Turno` y los canales de notificación.

## Contexto

- **Para qué es**: el ciclo de vida de un `Turno` tiene transiciones que no dependen de
  una acción manual del paciente o del personal de la clínica, sino del paso del tiempo
  (una confirmación que vence, un recordatorio que corresponde mandar, una validación no
  completada a tiempo). Los *schedulers* son los que barren la base periódicamente y
  aplican esas transiciones. Cada transición (y algunas acciones manuales) además necesita
  avisarle al paciente lo que pasó con su turno — eso es responsabilidad de las
  *notificaciones*.

- **Para qué sirve**: automatiza las transiciones de `Turno` que de otra forma quedarían
  colgadas esperando que alguien las dispare a mano (confirmar un turno pendiente,
  cancelar una validación vencida, marcar un ausente), y le mantiene al paciente
  información al día sin que el personal de la clínica tenga que avisarle manualmente por
  cada evento.

- **Quiénes la usan**: nadie las invoca directamente — los schedulers corren con identidad
  de sistema (sin usuario autenticado) y las notificaciones se disparan solas al final de
  cada caso de uso. El beneficiario final es el paciente, que recibe el aviso, y el
  personal de la clínica, que ya no necesita gestionar esas transiciones a mano.

---

## Diseño

### Schedulers

Un scheduler nunca reprograma turnos por su cuenta: llama al **mismo método de
`Application`** que usaría el caso de uso manual equivalente (ej. el mismo método de
cancelación que usa "Cancelar Turno" manual), así toda la validación y la notificación
quedan en un solo lugar. Un scheduler **nunca** importa nada de `Notifications/`
directamente — es el `App`, al final de la orquestación, el que publica el evento de
notificación.

| Scheduler | Transición | Guarda |
|---|---|---|
| `RecordarConfirmacionScheduler` | No cambia estado | `Pendiente` con `fechaHoraRecordatorioConfirmacion` vencida |
| `ConfirmarTurnosAutomaticamenteScheduler` | `Pendiente → Confirmado` | `fechaLimiteConfirmacion` vencida |
| `VencerValidacionTurnoScheduler` | `EsperaValidacion → Cancelado` | `fechaLimiteValidacion` vencida |
| `MarcarAusentesScheduler` | `Confirmado → Ausente` | `fechaLimiteAnuncioTardio` vencida |

Reglas comunes a los cuatro:

- Cada turno es su propia transacción: un fallo no bloquea el resto del lote.
- La guarda se reevalúa **dentro** de la transacción, por la concurrencia con el paciente
  confirmando/cancelando por WhatsApp en simultáneo.
- Idempotencia por filtrado del `HistoricoEstadoTurno` vigente (`fechaHoraFin IS NULL`).
- El intervalo del barrido tiene que ser más fino que la menor de las tolerancias
  configuradas en las prestaciones.
- **Orden obligatorio**: `ConfirmarTurnosAutomaticamenteScheduler` corre antes que
  `MarcarAusentesScheduler` en cada barrido, para que un turno no salte de `Pendiente`
  directo a `Ausente` sin pasar por `Confirmado` (y sin la notificación de confirmación en
  el medio).

### Notificaciones — Listener + Adaptador

El `App` no sabe por qué canal se notifica ni cuántos canales hay: al final de cada caso
de uso publica un `TurnoNotificacionEvent` (tipo de evento + turno) vía
`ApplicationEventPublisher`, y se desentiende. `TurnoNotificacionListener` escucha ese
evento y le delega la notificación a **cada canal activo** (`CanalNotificacionTurno`, uno
por integración externa — WhatsApp, mail, etc. — registrado como `@Component`). Si un
canal falla, se loguea y se sigue con el resto: no se pierde la notificación por los
demás canales por una caída puntual de uno.

Sumar un canal nuevo (ej. mail) es agregar un adaptador que implemente
`CanalNotificacionTurno` — no hay que tocar el listener ni el `App`. Hoy solo está
implementado el canal de WhatsApp (`CanalNotificacionTurnoWhatsApp`); el de mail queda
para cuando se decida encararlo.

| Evento | Notificación | Quién lo dispara |
|---|---|---|
| Registrar Turno | NOTIF-1 Turno Registrado | CU manual (Solicitar) |
| Confirmar Turno | NOTIF-2 Turno Confirmado | CU manual **y** `ConfirmarTurnosAutomaticamenteScheduler` |
| Cancelar Turno | NOTIF-3 Turno Cancelado | CU manual (Cancelar) |
| Reprogramar Turno | NOTIF-4 Turno Reprogramado | CU manual |
| Rechazar Validación / **Vencer Validación** | NOTIF-5 Turno No Validado | CU manual (Rechazar) **y** `VencerValidacionTurnoScheduler` |
| Recordatorio vencido | NOTIF-6 Recordar Confirmación | `RecordarConfirmacionScheduler` (es el propio disparador, no reacciona a nada) |

**Dos detalles a respetar al implementar:**

- **`VencerValidacionTurnoScheduler` dispara NOTIF-5, no NOTIF-3**, aunque el estado
  destino sea el mismo `Cancelado` que usa la cancelación manual — el mensaje tiene que
  ser distinto ("no completaste la indicación a tiempo", no un genérico "se canceló tu
  turno"). El único método de cancelación en `Application` (reutilizado por el CU manual y
  por el scheduler) decide qué disparar según `motivoCancelacion`
  (`VALIDACION_VENCIDA` → NOTIF-5, cualquier otro motivo → NOTIF-3); cada canal branchea el
  mensaje según ese mismo motivo.
- **`MarcarAusentesScheduler` no tiene notificación mapeada.** Ni el DTE ni la tabla de
  eventos definen un NOTIF-7 para "Ausente" — puede ser una omisión de la documentación o
  una decisión real de no avisarle al paciente que faltó. Queda pendiente de decisión
  antes de implementar; si se agrega, sería un séptimo valor en `TipoNotificacionTurno`
  (`AUSENTE`) y una fila más en esta tabla.

---

> Errores: cuando estos schedulers y canales tengan lógica, cualquier falla de negocio que
> deban propagar sigue el mismo contrato `AccesMedError` del resto del sistema — ver
> `docs/FRONTEND-GUIA.md §1`. No aplica hoy: no exponen ningún endpoint HTTP propio.
