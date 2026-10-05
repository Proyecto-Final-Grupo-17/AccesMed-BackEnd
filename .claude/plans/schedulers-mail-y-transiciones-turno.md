# Completar módulo de Turnos: schedulers, transiciones manuales y notificaciones por mail

## Contexto

`Docs/Features/SchedulersNotificacionesTurno.md` documenta el diseño acordado para que
el ciclo de vida de un `Turno` deje de depender de una acción manual constante: hoy
`TurnoApp` sólo implementa `createTurno`, `reprogramTurno`, `cancelTurno` y
`validateTurno`; los 4 schedulers (`RecordarConfirmacionScheduler`,
`ConfirmarTurnosAutomaticamenteScheduler`, `VencerValidacionTurnoScheduler`,
`MarcarAusentesScheduler`) son clases vacías; y el canal de notificación por mail no
existe (solo hay un stub de WhatsApp, también vacío, y el punto de extensión —
`CanalNotificacionTurno` + `TurnoNotificacionListener` — ya está armado y probado en su
diseño). Además, faltan 4 transiciones manuales del DTE que el panel web necesita para
que un turno pueda llegar a `Finalizado` sin pasar por un scheduler: confirmar, poner en
sala de espera, iniciar atención y finalizar. El usuario decidió incluir esas 4
transiciones en este mismo plan (ver D3), y agregar la notificación de `Ausente` que la
documentación dejaba pendiente (D6).

**Revisión importante sobre esta versión del plan**: la primera versión hacía que los
schedulers inyectaran `TurnoApp` (siguiendo literalmente una frase del documento
acordado). El usuario corrigió eso: un scheduler no debería depender de la capa
`Application` — solo de los `DomainService`/helpers que necesita. Esta versión rediseña
la Fase 4 alrededor de esa corrección (ver D5), de un problema técnico real que apareció
al resolverla (el bug de auto-invocación de Spring, también en D5), y de una segunda
vuelta donde se unificaron los 4 schedulers en una sola clase (D5 también) siguiendo el
mismo criterio que ya usa `TurnoApp` (una clase por entidad para sus casos de uso, no
una clase por operación).

**Ejecución de este plan**: cada fase se implementa delegando en subagentes con modelo
Haiku (para ahorrar tokens), coordinados desde esta conversación — se revisa el
resultado de cada fase antes de pasar a la siguiente.

**Antes que nada — seguridad de la credencial pegada en el chat**: la contraseña de
Gmail que compartiste queda tratada como comprometida. No se escribe en ningún archivo
del repo (ni `application*.yml`, ni `docker-compose`, ni tests). Recomiendo:
1. Generar un **App Password** de Gmail (Cuenta de Google → Seguridad → Verificación en
   dos pasos → Contraseñas de aplicaciones) en vez de usar la contraseña real de la
   cuenta.
2. Cambiar la contraseña de `proyectofinalgrupo17@gmail.com` ya que quedó expuesta en
   texto plano en esta conversación.
3. Las credenciales viajan **solo** por variables de entorno (`MAIL_USERNAME`,
   `MAIL_PASSWORD`), nunca committeadas.

---

## Decisiones de diseño

**D1 — Dónde vive el servicio de mail.** `Services/Utils/MailService.java`. Servicio
técnico (habla con SMTP vía `JavaMailSender`), no atado a ninguna entidad, del mismo
nivel que `FabricaEstrategiaCalcularMontoAPagarTurno`/`GeneradorSlotsAgenda` en
`Services/Utils`. Genérico (`enviarMail(destinatario, asunto, cuerpo)`) porque, según
confirmaste, el CU de recuperar contraseña también lo va a usar más adelante —
inyectándolo directo desde su propio `App`, sin pasar por
`TurnoNotificacionEvent`/`CanalNotificacionTurno` (específicos de `Turno`). Se agrega
también `enviarMailHtml(destinatario, asunto, cuerpoHtml)` (vía `MimeMessageHelper`) para
ese caso futuro, aunque no lo use nada todavía.

**D2 — `spring-boot-starter-mail` se autoconfigura solo.** No hace falta un
`@Configuration` a mano para `JavaMailSenderImpl`: Spring Boot lo arma automáticamente a
partir de `spring.mail.*` en el `.yml`.

**D3 (confirmada por vos) — Alcance ampliado a las 4 transiciones manuales que faltan.**
`PENDIENTE→CONFIRMADO`, `CONFIRMADO→EN_SALA_DE_ESPERA`, `EN_SALA_DE_ESPERA→EN_CURSO`,
`EN_CURSO→FINALIZADO`. Las tres últimas **no llevan notificación** — el paciente ya está
físicamente en la clínica. Solo `confirmTurno` notifica (NOTIF-2).

**D4 — Nomenclatura de las transiciones nuevas**, siguiendo `ARQUITECTURA.md §5` (fila
"Actualizar un campo puntual, sin body" → `<verbo><Concepto><Entidad>`,
`@PatchMapping("/<Recurso>/{id}")` solo con `@PathVariable UUID id`, sin Request record):

| Transición | Método (Controller/App) | Ruta | Response record |
|---|---|---|---|
| `PENDIENTE→CONFIRMADO` | `confirmTurno` | `PATCH /Turno/{id}/Confirmacion` | `ConfirmTurnoResponse` |
| `CONFIRMADO→EN_SALA_DE_ESPERA` | `startSalaDeEsperaTurno` | `PATCH /Turno/{id}/SalaDeEspera` | `StartSalaDeEsperaTurnoResponse` |
| `EN_SALA_DE_ESPERA→EN_CURSO` | `startAtencionTurno` | `PATCH /Turno/{id}/Atencion` | `StartAtencionTurnoResponse` |
| `EN_CURSO→FINALIZADO` | `finishTurno` | `PATCH /Turno/{id}/Finalizacion` | `FinishTurnoResponse` |

**D5 (corregida por vos, en varias vueltas) — Los schedulers NO inyectan `TurnoApp`, y
quedan en una sola clase.** Se dio en tres pasos:

1. *Por qué no `TurnoApp`*: pensalo como el equivalente de `App` pero para el
   disparador "paso del tiempo" en vez de "HTTP request" — necesita su propia
   orquestación fina, igual que un `Controller` la necesita en `App`, así que no debe
   depender de la capa `Application` existente (pensada para casos de uso manuales con
   Request/Response).
2. *Por qué hace falta un bean nuevo y no alcanza con que el scheduler use los
   `DomainService` directo*: cuando una transición automática combina más de un
   `DomainService` (ej. vencer validación: liberar slot + guardar motivo + transicionar
   + notificar), esos pasos tienen que ser todo-o-nada — necesitan una transacción que
   los envuelva a los cuatro. Poner `@Transactional` en un método del mismo bean
   scheduler y llamarlo como `this.metodo()` desde el propio `@Scheduled` **no
   funciona**: esa llamada no pasa por el proxy de Spring, así que la transacción no se
   abre de verdad y un turno que falla a mitad de camino queda con una escritura
   parcial sin rollback (corrupción silenciosa). La solución: un bean nuevo y separado,
   **`Schedulers/TurnoSchedulerService.java`**, con un método `@Transactional` por
   transición automática — como el scheduler lo llama desde **otro bean**, el proxy sí
   intercepta.
3. *Por qué una sola clase de scheduler y no 4*: con toda la lógica pesada movida a
   `TurnoSchedulerService`, cada scheduler quedó reducido a un cron + una consulta +
   un loop — ya no hay nada que las diferencie salvo el cron. `TurnoApp` ya agrupa sus 4
   casos de uso (`createTurno`/`reprogramTurno`/`cancelTurno`/`validateTurno`) en una
   sola clase por entidad, no una por operación; seguir esa misma idea acá da
   `Schedulers/TurnoScheduler.java`, una única clase con los 4 métodos `@Scheduled`.
   Reemplaza a los 4 stubs que ya existían (`RecordarConfirmacionScheduler`,
   `ConfirmarTurnosAutomaticamenteScheduler`, `VencerValidacionTurnoScheduler`,
   `MarcarAusentesScheduler` se eliminan).

**Capas finales (ninguna se salta la regla "solo `DomainService`/`QueryService` toca
`Repository`")**:
- `TurnoDomainService` gana un método nuevo, `findTurnosByEstadoVigente(EstadoTurno
  estado)`, que sí toca `TurnoRepository` (como corresponde a un `DomainService`) — es
  genérico, lo usan los 4 barridos, cada uno pasando su propio estado.
- `TurnoScheduler` (la única clase con `@Scheduled`) inyecta `TurnoDomainService` (para
  encontrar candidatos) y `TurnoSchedulerService` (para ejecutar, transaccionalmente,
  cada transición) — nunca `TurnoRepository` ni `TurnoApp`.
- `TurnoSchedulerService` inyecta los `DomainService` que cada transición necesita
  (`TurnoDomainService`, `HistoricoEstadoTurnoDomainService`,
  `AgendaHorariosDiaDomainService`) más `ApplicationEventPublisher` — tampoco toca
  `Repository` directo ni depende de `TurnoApp`.

`TurnoSchedulerService` no es un `App`: no tiene Request/Response, no responde a ningún
endpoint HTTP, no se prueba con `MockMvc`. Esto contradice una frase literal del
documento acordado ("un scheduler nunca importa nada de `Notifications/` directamente")
— se corrige esa frase en la Fase 5 para que diga que la publica `TurnoSchedulerService`,
no el scheduler en sí ni `Application`.

**Costo aceptado**: `TurnoApp.confirmTurno` y `TurnoSchedulerService.confirmarTurno` van
a tener las mismas 2-3 líneas (transicionar + publicar evento) escritas dos veces. No es
duplicación de *regla de negocio* — la guarda de cada transición sigue viviendo, única,
en `HistoricoEstadoTurnoDomainService` — es solo la secuencia de orquestación, que ahora
tiene dos lugares (el disparo manual y el automático) en vez de uno. Es el trade-off
directo de que ninguno de los dos dependa del otro.

**D6 (confirmada por vos) — Se agrega `AUSENTE` a `TipoNotificacionTurno`.**
`TurnoSchedulerService.marcarAusenteTurno` dispara esa notificación.

**D7 (revisada) — El vencimiento de validación no comparte código con el rechazo
manual.** `TurnoSchedulerService.vencerValidacionTurno` arma su propia secuencia
(liberar slot → `motivoCancelacion = VALIDACION_VENCIDA` → guardar → transicionar →
publicar `NO_VALIDADO`) usando solo `DomainService`s — no reutiliza nada de
`TurnoApp.validateTurno`. Es parecida a la rama de rechazo manual pero no idéntica (ver
D9: no marca las indicaciones como validadas), así que tampoco habría un método 1:1
limpio para compartir entre `App` y este bean sin parametrizarlo de más — se acepta la
pequeña duplicación.

**D8 — Aprobar una validación (`validateTurno(aprobado=true)`) no publica ningún
evento.** La tabla del documento no mapea ningún NOTIF para "validación aprobada"; el
paciente ya recibió NOTIF-1 al crear el turno y todavía falta que lo confirmen. Queda un
comentario en el código explicando por qué esa rama no publica nada.

**D9 — Al vencer la validación no se marcan las indicaciones como "validadas".** El
rechazo manual sí las marca (alguien tomó la decisión, con o sin indicación cumplida).
Un timeout no es una decisión: nadie evaluó las indicaciones. Laguna del dominio que no
cubre ningún documento — la marco explícitamente por si preferís lo contrario.

**D10 — Cron configurable, no hardcodeado.** Cada scheduler lee su expresión cron de
`application.yml` (`accesmed.scheduler.<nombre>.cron`), mismo patrón que
`accesmed.cors.allowed-origins`.

**D11 — `@EnableScheduling` en una clase de config nueva**, `Config/SchedulingConfig.java`
(no en `AccesMedApplication`), porque `Config/` es "lo transversal de infraestructura"
según `CLAUDE.md`.

---

## Fase 1 — Canal de mail

### 1.1 Dependencia — `pom.xml`
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

### 1.2 Config — `application.yml`
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```
Sin default para `MAIL_USERNAME`/`MAIL_PASSWORD` — que falle explícito si no están
seteadas. Se setean como variables de entorno locales, nunca en un archivo committeado.

### 1.3 `Services/Utils/MailService.java` (nuevo)
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender javaMailSender;

    public void enviarMail(String destinatario, String asunto, String cuerpo) {
        log.debug("Enviando mail: destinatario={}, asunto={}", destinatario, asunto);
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        javaMailSender.send(mensaje);
    }

    public void enviarMailHtml(String destinatario, String asunto, String cuerpoHtml) {
        log.debug("Enviando mail HTML: destinatario={}, asunto={}", destinatario, asunto);
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException excepcion) {
            throw new MailPreparationException("No se pudo preparar el mail HTML", excepcion);
        }
    }
}
```
No atrapa `MailException` (unchecked; `TurnoNotificacionListener` ya aísla la falla de
cada canal con su propio `try/catch` + `log.error` — replicarlo acá duplicaría el log).
`enviarMailHtml` queda listo para recuperar contraseña (D1), sin uso todavía.

### 1.4 `Notifications/Canales/CanalNotificacionTurnoMail.java` (nuevo)
Implementa `CanalNotificacionTurno`, arma asunto/cuerpo según `TipoNotificacionTurno` y
(para `CANCELADO`/`NO_VALIDADO`) según `turno.getMotivoCancelacion()`, y llama a
`mailService.enviarMail(turno.getPaciente().getEmail(), asunto, cuerpo)`.

### 1.5 `Notifications/TipoNotificacionTurno.java`
Agregar el valor `AUSENTE` (D6).

---

## Fase 2 — Cerrar el circuito de notificaciones en `TurnoApp` (rutas manuales)

Inyectar `ApplicationEventPublisher` en `TurnoApp` y reemplazar los 4 `// TODO`
existentes:

- `createTurno` → `REGISTRADO` sobre `turnoGuardado`.
- `reprogramTurno` → `REPROGRAMADO` sobre `turnoNuevoGuardado`.
- `cancelTurno` → `motivoCancelacion == VALIDACION_VENCIDA ? NO_VALIDADO : CANCELADO`.
- `validateTurno` → rama `aprobado=false`: publica `NO_VALIDADO` al final (la rama ya
  hace todo lo necesario: liberar slot, motivo, transicionar, marcar indicaciones
  validadas); rama `aprobado=true`: sin publish (D8), con un comentario corto
  explicando por qué.

`TurnoApp` **no** gana métodos nuevos para el vencimiento de validación, marcar ausente
ni recordar confirmación — esos tres viven exclusivamente en `TurnoSchedulerService`
(Fase 4), porque no tienen CU manual equivalente y no deben depender de `Application`.

---

## Fase 3 — Las 4 transiciones manuales nuevas (D3/D4)

**`Services/DomainServices/HistoricoEstadoTurnoDomainService.java`** — agregar, con el
mismo patrón que las 5 transiciones existentes (`getEstadoVigente` + guarda +
`cerrarYAbrirTramo`):
- `transitionPendienteToConfirmadoTurno(turno)` — valida `PENDIENTE`.
- `transitionConfirmadoToEnSalaDeEsperaTurno(turno)` — valida `CONFIRMADO`.
- `transitionEnSalaDeEsperaToEnCursoTurno(turno)` — valida `EN_SALA_DE_ESPERA`.
- `transitionEnCursoToFinalizadoTurno(turno)` — valida `EN_CURSO`.
- `transitionToAusenteTurno(turno)` — valida `CONFIRMADO` (no `EN_SALA_DE_ESPERA`: si el
  paciente hizo check-in, ya no tiene sentido marcarlo ausente).

**`Records/Turno/Response/`** — 4 records nuevos (`ConfirmTurnoResponse`,
`StartSalaDeEsperaTurnoResponse`, `StartAtencionTurnoResponse`, `FinishTurnoResponse`),
mismos campos que `ValidateTurnoResponse`.

**`Services/Mappers/TurnoMapper.java`** — 4 métodos `toXResponse(Turno, EstadoTurno)`
nuevos, calcados de `toValidateResponse`.

**`Application/TurnoApp.java`** — 4 métodos públicos `@Transactional`, sin Request
record (D4), ej.:
```java
@Transactional
public ConfirmTurnoResponse confirmTurno(UUID id) {
    log.info("Confirmación de turno iniciada: turnoId={}", id);
    var turno = turnoDomainService.findTurnoById(id);
    historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turno);
    applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.CONFIRMADO, turno));
    return turnoMapper.toConfirmResponse(turno, EstadoTurno.CONFIRMADO);
}
```
`startSalaDeEsperaTurno`/`startAtencionTurno`/`finishTurno` son iguales pero sin el
`publishEvent` (D3).

**`Controllers/TurnoController.java`** — 4 endpoints `@PatchMapping`, solo
`@PathVariable UUID id`, sin body. Ej.:
```java
@PatchMapping("/Turno/{id}/Confirmacion")
public ResponseEntity<ConfirmTurnoResponse> confirmTurno(@PathVariable UUID id) {
    log.info("Solicitud recibida: confirmar turno id={}", id);
    return ResponseEntity.ok(turnoApp.confirmTurno(id));
}
```

---

## Fase 4 — `TurnoScheduler` + `TurnoSchedulerService` (D5)

### 4.1 `Config/SchedulingConfig.java` (nuevo, D11)
```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

### 4.2 `application.yml` — cron configurables (D10)
```yaml
accesmed:
  scheduler:
    recordar-confirmacion:
      cron: ${ACCESMED_SCHEDULER_RECORDAR_CONFIRMACION_CRON:*/5 * * * * *}
    confirmar-turnos-automaticamente:
      cron: ${ACCESMED_SCHEDULER_CONFIRMAR_TURNOS_CRON:*/5 * * * * *}
    vencer-validacion-turno:
      cron: ${ACCESMED_SCHEDULER_VENCER_VALIDACION_CRON:*/5 * * * * *}
    marcar-ausentes:
      cron: ${ACCESMED_SCHEDULER_MARCAR_AUSENTES_CRON:*/5 * * * * *}
```

### 4.3 `TurnoDomainService` — un único método nuevo, genérico
```java
public List<Turno> findTurnosByEstadoVigente(EstadoTurno estado) {
    log.debug("Buscando turnos con estado vigente: estado={}", estado);
    return turnoRepository.findByEstadoVigente(estado);
}
```
Y en `TurnoRepository`, la query que lo respalda (mismo estilo que las de "turnos
vivos" ya existentes, vía `HistoricoEstadoTurno`):
```java
@Query("SELECT h.turno FROM HistoricoEstadoTurno h WHERE h.estado = :estado AND h.fechaHoraFin IS NULL")
List<Turno> findByEstadoVigente(EstadoTurno estado);
```
Un solo método genérico, no 4 queries con fecha embebida — cada scheduler pasa su
propio `EstadoTurno` y filtra por su propia fecha límite en memoria (ver 4.5), tal como
lo describiste: "traer los turnos con el estado vigente buscado, y por cada uno leer la
fecha límite".

### 4.4 `Schedulers/TurnoSchedulerService.java` (nuevo — resuelve la atomicidad, D5)
Solo la parte transaccional de cada transición automática. Inyecta los `DomainService`
que cada una necesita, nunca `TurnoRepository` ni `TurnoApp`:
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoSchedulerService {

    private final TurnoDomainService turnoDomainService;
    private final HistoricoEstadoTurnoDomainService historicoEstadoTurnoDomainService;
    private final AgendaHorariosDiaDomainService agendaHorariosDiaDomainService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void confirmarTurno(Turno turno) {
        historicoEstadoTurnoDomainService.transitionPendienteToConfirmadoTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.CONFIRMADO, turno));
    }

    @Transactional
    public void vencerValidacionTurno(Turno turno) {
        agendaHorariosDiaDomainService.releaseAgendaHorario(turno.getAgendaHorarios());
        turno.setMotivoCancelacion(MotivoCancelacion.VALIDACION_VENCIDA);
        turnoDomainService.saveTurno(turno);
        historicoEstadoTurnoDomainService.transitionEsperaValidacionToCanceladoTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.NO_VALIDADO, turno));
    }

    @Transactional
    public void marcarAusenteTurno(Turno turno) {
        historicoEstadoTurnoDomainService.transitionToAusenteTurno(turno);
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.AUSENTE, turno));
    }

    @Transactional
    public void recordarConfirmacionTurno(Turno turno) {
        applicationEventPublisher.publishEvent(new TurnoNotificacionEvent(TipoNotificacionTurno.RECORDATORIO_CONFIRMACION, turno));
    }
}
```
Recibe el `Turno` ya cargado (lo trajo `TurnoScheduler` con `findTurnosByEstadoVigente`),
no un `id` — no hace falta re-buscarlo: la guarda de concurrencia (¿sigue en el estado
esperado?) la vuelve a chequear, fresca contra la base, el propio método de
`HistoricoEstadoTurnoDomainService` (`getEstadoVigente` re-consulta por id), así que
igual queda cubierto el caso "el paciente confirmó por WhatsApp mientras corría el
barrido". Cada método es `@Transactional` y se llama desde **otro bean**
(`TurnoScheduler`) — por eso el proxy de Spring intercepta la llamada y la transacción
por turno es real.

### 4.5 `Schedulers/TurnoScheduler.java` (nuevo — reemplaza a los 4 stubs)
Una sola clase, un método `@Scheduled` por transición, seguendo tu algoritmo: traer por
estado vigente, filtrar en memoria por fecha límite, y si corresponde, ejecutar.
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TurnoScheduler {

    private final TurnoDomainService turnoDomainService;
    private final TurnoSchedulerService turnoSchedulerService;

    @Scheduled(cron = "${accesmed.scheduler.confirmar-turnos-automaticamente.cron}")
    public void confirmarTurnosVencidos() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteConfirmacion())) {
                try {
                    turnoSchedulerService.confirmarTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo confirmando automáticamente el turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    @Scheduled(cron = "${accesmed.scheduler.vencer-validacion-turno.cron}")
    public void vencerValidacionesTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.ESPERA_VALIDACION);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteValidacion())) {
                try {
                    turnoSchedulerService.vencerValidacionTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo venciendo la validación del turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    @Scheduled(cron = "${accesmed.scheduler.marcar-ausentes.cron}")
    public void marcarAusentesTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.CONFIRMADO);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaLimiteAnuncioTardio())) {
                try {
                    turnoSchedulerService.marcarAusenteTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo marcando ausente el turno {}", turno.getId(), excepcion);
                }
            }
        }
    }

    @Scheduled(cron = "${accesmed.scheduler.recordar-confirmacion.cron}")
    public void recordarConfirmacionTurno() {
        ZonedDateTime ahora = ZonedDateTime.now();
        List<Turno> turnos = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE);
        for (Turno turno : turnos) {
            if (ahora.isAfter(turno.getFechaHoraRecordatorioConfirmacion())) {
                try {
                    turnoSchedulerService.recordarConfirmacionTurno(turno);
                } catch (RuntimeException excepcion) {
                    log.error("Fallo recordando confirmación del turno {}", turno.getId(), excepcion);
                }
            }
        }
    }
}
```
`TurnoScheduler` inyecta solo `TurnoDomainService` (encontrar candidatos) y
`TurnoSchedulerService` (ejecutar, transaccionalmente, cada transición) — nunca
`TurnoRepository` ni `TurnoApp`. Reemplaza a los 4 archivos stub
(`RecordarConfirmacionScheduler`, `ConfirmarTurnosAutomaticamenteScheduler`,
`VencerValidacionTurnoScheduler`, `MarcarAusentesScheduler` se eliminan).

El orden que pide el documento (confirmar antes que marcar ausentes) queda garantizado
por diseño, no por secuencia de ejecución: cada método consulta por su propio estado
vigente (`PENDIENTE` uno, `CONFIRMADO` el otro), así que un turno recién confirmado en
este barrido no puede aparecer en la consulta de ausentes hasta el barrido siguiente.

---

## Fase 5 — Documentación

- **`CLAUDE.md`** línea ~38: corregir el DTE documentado al enum real de
  `Domain/EstadoTurno.java`.
- **`Docs/Features/SchedulersNotificacionesTurno.md`**:
  - Cambiar el encabezado de "solo skeleton" a implementado.
  - Corregir la mención a 4 clases scheduler separadas: ahora son 4 métodos
    `@Scheduled` en una única clase, `TurnoScheduler`.
  - Corregir la frase "un scheduler nunca importa nada de `Notifications/`
    directamente" — ahora dice que `TurnoScheduler` delega en `TurnoSchedulerService`,
    que es quien publica el evento (D5).
  - Corregir "llama al mismo método de `Application`" donde ya no aplica (la
    confirmación automática reutiliza lógica equivalente a la manual, pero vía
    `TurnoSchedulerService`, no vía `TurnoApp`).
  - Agregar la fila de `AUSENTE`/NOTIF-7 (D6).
- Mencionar (una vez) que `ProcesoAgente`, `MensajeClave`, `TipoMensajeClave` figuran en
  el dominio núcleo pero no están implementados — a tu criterio si se documentan como
  pendientes o se quitan de esa lista.

---

## Fase 6 — Tests

- `HistoricoEstadoTurnoDomainServiceTest` (Mockito): las 5 transiciones nuevas + guardas
  que fallan con `ReglaNegocioException`.
- `TurnoDomainServiceTest` (Mockito): `findTurnosByEstadoVigente` delega correctamente
  en `TurnoRepository.findByEstadoVigente`.
- `TurnoAppTest` (Mockito): los 4 CU existentes + `confirmTurno`/
  `startSalaDeEsperaTurno`/`startAtencionTurno`/`finishTurno`, capturando el evento
  publicado (`ArgumentCaptor<TurnoNotificacionEvent>`).
- `TurnoSchedulerServiceTest` (Mockito): los 4 métodos, mockeando los `DomainService` y
  verificando la transición + el evento publicado (o su ausencia, para
  `recordarConfirmacionTurno` que no transiciona nada).
- `TurnoSchedulerTest` (Mockito, sin contexto de Spring): mockear `TurnoDomainService`
  (candidatos devueltos, mezclando turnos con fecha límite vencida y no vencida) y
  `TurnoSchedulerService`; verificar que solo se ejecuta sobre los vencidos, una vez por
  turno, y que un `RuntimeException` en uno no corta el loop de los demás.
- `CanalNotificacionTurnoMailTest` (Mockito sobre `MailService`): un caso por
  `TipoNotificacionTurno`.
- `MockMvc` para los 4 endpoints nuevos del Controller.

---

## Verificación end-to-end

1. `./mvnw -q -DskipTests=false test`.
2. Setear `MAIL_USERNAME`/`MAIL_PASSWORD`, levantar la app en `dev`, crear un turno vía
   Swagger con un paciente de `email` real de prueba, confirmarlo por
   `PATCH /Turno/{id}/Confirmacion` y verificar que llega el mail.
3. Bajar el cron de un scheduler a un valor corto en `dev` y verificar en los logs que
   el barrido corre y transiciona un turno de prueba con fecha límite ya vencida.
4. Revisar en la base (`historico_estado_turno`) que cada transición cerró el tramo
   anterior y abrió uno nuevo, sin dos tramos vigentes para el mismo turno — y, en
   particular, forzar el fallo de un turno del lote (ej. un id inexistente mezclado con
   ids reales) para confirmar que el resto del barrido se procesa igual (la prueba real
   de que el `@Transactional` por turno en `TurnoSchedulerService` efectivamente se
   aplica, y no el bug de auto-invocación que motivó el rediseño de esta fase).
