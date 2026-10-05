# Feature: Schedulers y Notificaciones de Turno

> **Estado actual**: implementado. Cuenta con 4 métodos `@Scheduled` en una única clase
> `TurnoScheduler` (paquete `Schedulers`), apoyada en un bean `TurnoSchedulerService` para
> la atomicidad transaccional, más el canal de notificación por mail (`CanalNotificacionTurnoMail`).

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

Las 4 transiciones automáticas son 4 métodos `@Scheduled` dentro de una única clase
`TurnoScheduler` (paquete `Schedulers`). La ejecución transaccional de cada una vive en un
bean separado `TurnoSchedulerService`, necesario porque `@Transactional` en un método
llamado por auto-invocación desde el mismo bean no funciona en Spring. `TurnoSchedulerService`
publica el evento de notificación (`TurnoNotificacionEvent`) en los casos que corresponde
(confirmación, vencimiento de validación, marcar ausente, recordatorio); los schedulers
no dependen de la capa `Application` ni importan `Notifications/` directamente.

| Método en `TurnoScheduler` | Transición | Guarda |
|---|---|---|
| `recordarConfirmacionTurno` | No cambia estado | `Pendiente` con `fechaHoraRecordatorioConfirmacion` vencida |
| `confirmarTurnosVencidos` | `Pendiente → Confirmado` | `fechaLimiteConfirmacion` vencida |
| `vencerValidacionesTurno` | `EsperaValidacion → Cancelado` | `fechaLimiteValidacion` vencida |
| `marcarAusentesTurno` | `Confirmado → Ausente` | `fechaLimiteAnuncioTardio` vencida |

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
| Confirmar Turno | NOTIF-2 Turno Confirmado | CU manual **y** `TurnoScheduler` (confirmarTurnosVencidos) vía `TurnoSchedulerService` |
| Cancelar Turno | NOTIF-3 Turno Cancelado | CU manual (Cancelar) |
| Reprogramar Turno | NOTIF-4 Turno Reprogramado | CU manual |
| Rechazar Validación / **Vencer Validación** | NOTIF-5 Turno No Validado | CU manual (Rechazar) **y** `TurnoScheduler` (vencerValidacionesTurno) vía `TurnoSchedulerService` |
| Recordatorio vencido | NOTIF-6 Recordar Confirmación | `TurnoScheduler` (recordarConfirmacionTurno) vía `TurnoSchedulerService` |
| Marcar Ausente | NOTIF-7 Turno Ausente | `TurnoScheduler` (marcarAusentesTurno) vía `TurnoSchedulerService` |

**Un detalle a respetar al implementar:**

- **`TurnoScheduler` (vencerValidacionesTurno) dispara NOTIF-5, no NOTIF-3**, aunque el estado
  destino sea el mismo `Cancelado` que usa la cancelación manual — el mensaje tiene que
  ser distinto ("no completaste la indicación a tiempo", no un genérico "se canceló tu
  turno"). El único método de cancelación en `Application` (reutilizado por el CU manual y
  por el scheduler) decide qué disparar según `motivoCancelacion`
  (`VALIDACION_VENCIDA` → NOTIF-5, cualquier otro motivo → NOTIF-3); cada canal branchea el
  mensaje según ese mismo motivo.

---

> Errores: cuando estos schedulers y canales tengan lógica, cualquier falla de negocio que
> deban propagar sigue el mismo contrato `AccesMedError` del resto del sistema — ver
> `docs/FRONTEND-GUIA.md §1`. No aplica hoy: no exponen ningún endpoint HTTP propio.
