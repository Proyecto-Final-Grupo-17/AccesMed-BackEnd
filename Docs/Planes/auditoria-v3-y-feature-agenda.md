# Plan — Auditoría v3 y feature de Agenda

> **Estado al 2026-08-23.** **Fase A ejecutada completa** (A2, A3, A1, A4, A5, A6) y **Fase
> C ejecutada** (C2, C1). **Fase B ejecutada**: feature de Agenda completa (7 endpoints,
> `AgendaMedicoApp`/`AgendaMedicoController`, `GeneradorSlotsAgenda`, extensión de
> `AgendaHorariosRepository`/`AgendaHorariosDomainService` con el stack de escritura,
> filtros derivados nuevos en `MedicoCriteria`). Con el stack de escritura de Agenda ya
> disponible, se completaron **A4 paso 2** y **A6**, que quedaban pendientes en el código
> con `// TODO`. El módulo `ObraSocialPlanPrestacion` (repo, `DomainService`, `QueryService`,
> `App`/`ObraSocialPrestacionController`, records) se construyó fuera de este plan, cerrando
> los `// TODO` de A4 paso 4 (`PrestacionApp.disablePrestacion`) y A5 paso 1
> (`PlanApp.disablePlan`, `ObraSocialApp.softDeleteObraSocial`); ver
> `Docs/Features/ObraSocialPrestacion.md`. Pendiente: el front de la feature de Agenda (Fase
> C de Agenda, no incluida en este plan — va en uno propio) y los tests de
> integración/App de la Fase B y del módulo `ObraSocialPlanPrestacion` (se dejaron sin
> escribir por alcance; solo se cubrió con tests unitarios `GeneradorSlotsAgendaTest`).

Documento de ejecución. La **Fase A** corrige los desajustes entre
`Docs/Dominio/dominio-reglas-validaciones.md` (v3) y el código. La **Fase B** construye la
feature de Agenda. La Fase B depende de la A: sin la migración de `MedicoPrestacion` a
vigencia y sin el parámetro de horizonte en `Clinica`, dos reglas de AGEN no se pueden
cumplir.

Convenciones de código: `.claude/skills/java-springboot-code-style`,
`java-springboot-javadoc`, `java-springboot-logging`. Estructura: `Docs/ARQUITECTURA.md`.

---

## Resultado de la auditoría

Se recorrió el dominio entero contra la lista "Qué cambió respecto de la v2" (§ inicial de
las reglas) y contra §6 (bajas y cascadas). Distinción importante: un **desajuste** es
código que contradice la doc; **no construido** es backlog que no contradice nada.

### Lo que ya está correcto

No hace falta tocar nada de esto — se verificó, está bien:

- Ejes de retiro correctos en `Prestacion`, `Plan`, `Turno` (estados, sin `deletedAt`),
  `IndicacionPrestacion` y `UsuarioRol` (vigencia), y el resto del catálogo (baja lógica).
- Sin columna `estado_actual` residual: el estado vigente se deriva del histórico, y los
  changelogs documentan por qué no hay índice único parcial.
- `EstadoTurno` con `EN_CURSO` (no `EN_TRANSCURSO`) y `FINALES` como constante de código.
- `Usuario → Medico`/`Admin` unidireccional: la FK vive en `usuario` y `Medico` no navega
  de vuelta.
- Baja restrictiva de `Especialidad` implementada (`validateSinMedicosActivos` +
  `validateSinPrestacionesActivas`).
- Baja de `ObraSocial` restrictiva por transitividad, con cascada sobre sus planes.

### Desajustes a corregir (Fase A)

| # | Desajuste | Regla | ¿Impacta al front? |
|---|---|---|---|
| A1 | `MedicoPrestacion` usa baja lógica; la doc la pone en vigencia | v3 §3, §5 MED | **Sí** — cambia request y response de asignar/desasignar |
| A2 | `Clinica` conserva `diasMinimos`/`diasMaximosVigenciaAgenda` (derogados) y le falta `diasMaximosAnticipacionReserva` | §5 CONFIG | **Sí** — cambia el form de configuración |
| A3 | `MotivoCancelacion` conserva `BAJA_DE_PRESTACION` | v3 §4 | **Sí** — si el front lista los motivos |
| A4 | Cascada de `disablePrestacion` incompleta | §6 Prestación | No |
| A5 | Cascada de `disablePlan` incompleta | §6 Plan | No |
| A6 | `updatePrestacion` no revalida ni recalcula los `AgendaHorarios` futuros libres | v3 §6, §5 PREST | **Sí** — el response debe informar los slots afectados |

### No construido todavía (backlog, fuera de este plan)

No son contradicciones: son módulos que aún no existen.

| Módulo | Estado | Qué bloquea |
|---|---|---|
| `Archivo` | Entidad inexistente (v3 punto 9) | Adjuntos de paciente y turno |
| `Turno` | Solo lectura (`TurnoRepository`/`TurnoDomainService` para precondiciones ajenas) | Todo el canal del chatbot; la cascada de cancelación en Agenda |
| Seguridad (`Usuario`, `Rol`, `UsuarioRol`, `Permiso`) | Entidades y tablas; sin stack | Login y autorización |

**`ObraSocialPlanPrestacion` ya no está en esta lista**: el módulo completo (repo,
`ObraSocialPlanPrestacionDomainService`, `ObraSocialPlanPrestacionQueryService`,
`ObraSocialPrestacionApp`/`ObraSocialPrestacionController`) se construyó fuera de este
plan, incluida la asignación anidada al crear obra social/plan y el listado dinámico con
`ObraSocialPrestacionCriteria`. Cierra el paso "baja de coberturas" de A4 y A5. La
desasignación es restrictiva por turnos vivos del par plan-prestación, mismo criterio que
el resto de las bajas restrictivas del dominio (piso duro nuevo en `TurnoRepository`/
`TurnoDomainService`). Ver `Docs/Features/ObraSocialPrestacion.md`.

---

## Fase A — Correcciones

### A1. `MedicoPrestacion`: baja lógica → vigencia

Es el desajuste de fondo: AGEN exige una `MedicoPrestacion` **vigente en la fecha del
slot**, y con `deletedAt` solo se puede responder "activa ahora".

**Migración** — `20260815HHMMSS-MedicoPrestacion.xml`, changeset
`updated-table-MedicoPrestacion-vigencia`:

1. `addColumn` `fecha_inicio_vigencia timestamptz` y `fecha_fin_vigencia timestamptz`.
2. Backfill: `fecha_inicio_vigencia = created_at` para todas las filas;
   `fecha_fin_vigencia = deleted_at` para las que estaban dadas de baja, `NULL` para el resto.
3. `addNotNullConstraint` sobre `fecha_inicio_vigencia`.
4. `dropColumn` `deleted_at`, `deleted_by`, `deleted_reason`.
5. `ck_medico_prestacion_vigencia`: `fecha_fin_vigencia IS NULL OR fecha_inicio_vigencia < fecha_fin_vigencia`.
6. `ex_medico_prestacion_no_solapamiento`:
   `EXCLUDE USING gist (medico_id WITH =, prestacion_id WITH =, tstzrange(fecha_inicio_vigencia, fecha_fin_vigencia) WITH &&)`.
   La extensión `btree_gist` ya la habilita el changelog de `AgendaMedico`.

Con `rollback` en cada paso.

**Entidad** `Domain/MedicoPrestacion.java`: la región `Baja` pasa a `Vigencia`, con
`fechaInicioVigencia` (`@NotNull`, `ZonedDateTime`) y `fechaFinVigencia` (`ZonedDateTime`).

**`MedicoPrestacionRepository`** — las tres queries actuales se reemplazan:

| Antes | Después |
|---|---|
| `findByIdAndDeletedAtIsNull` | `findByIdAndVigenteAt(UUID id, ZonedDateTime fecha)` |
| `findByMedico_IdAndDeletedAtIsNull` | `findByMedico_IdAndVigenteAt(UUID medicoId, ZonedDateTime fecha)` |
| `existsByMedico_IdAndPrestacion_IdAndDeletedAtIsNull` | `existsSolapamiento(UUID medicoId, UUID prestacionId, ZonedDateTime desde, ZonedDateTime hasta)` |

Predicado de vigencia, en todas: `fechaInicioVigencia <= :fecha AND (fechaFinVigencia IS
NULL OR :fecha < fechaFinVigencia)`.

Agregar además, para Agenda y para el piso duro del corte:

- `existsVigenteEnFecha(UUID medicoId, UUID prestacionId, ZonedDateTime fecha)`
- `findMaxFechaHoraInicioTurnoVivo(UUID medicoId, UUID prestacionId, Collection<EstadoTurno> finales)`
  — vive en `TurnoRepository`, no acá (un repo por entidad).

**`MedicoPrestacionDomainService`**:

- `findMedicoPrestacionVigenteById(UUID id, ZonedDateTime fecha)` reemplaza a `findMedicoPrestacionActivaById`.
- `findAsignacionesVigentesByMedico(UUID medicoId, ZonedDateTime fecha)`.
- `validateSinSolapamiento(UUID medicoId, UUID prestacionId, ZonedDateTime desde, ZonedDateTime hasta)`
  reemplaza a `validateSinAsignacionActiva`. Código de error `MEDICO_PRESTACION_SOLAPADA`.
- `cerrarVigenciaMedicoPrestacion(MedicoPrestacion, ZonedDateTime fechaCorte)` reemplaza a
  `softDeleteMedicoPrestacion`.
- `validateEspecialidadCoincide` queda igual.

**Piso duro del corte** (§5 MED): `fechaFinVigencia` no puede ser anterior al
`fechaHoraInicio` de ningún turno vivo del par. La consulta va en `TurnoRepository`; la
orquestación (pedirle la fecha a `TurnoDomainService` y comparar) va en
`MedicoPrestacionApp`, **no** en el DomainService — un DomainService no llama a otro.
Error `MEDICO_PRESTACION_CORTE_ANTERIOR_A_TURNO`, con la fecha del último turno en el mensaje.

**`MedicoPrestacionApp`**:

- `assignPrestacion` recibe `fechaInicioVigencia` (default: ahora) y valida solapamiento.
- `unassignPrestacion(UUID id, ZonedDateTime fechaCorte)` reemplaza al actual, valida el
  piso duro y cierra la vigencia.

**Records**: `AssignMedicoPrestacionRequest` suma `fechaInicioVigencia`. Nuevo
`UnassignMedicoPrestacionRequest` con `id` + `fechaFinVigencia`.
`SoftDeleteMedicoPrestacionResponse` → `UnassignMedicoPrestacionResponse`.
`GetMedicoPrestacionResponse` expone las dos fechas.

**Controller**: `DELETE /MedicoPrestacion/{id}` → `PATCH /MedicoPrestacion/Vigencia/{id}`
con body. Desasignar dejó de ser una baja: es cerrar un período.

### A2. `Clinica`: horizonte de reserva

**Migración** — `20260815HHMMSS-Clinica.xml`, changeset
`updated-table-Clinica-horizonte-reserva`:

1. `addColumn` `dias_maximos_anticipacion_reserva integer`.
2. Backfill con el valor actual de `dias_maximos_vigencia_agenda`.
3. `addNotNullConstraint` + `ck_clinica_dias_maximos_anticipacion_reserva` (`>= 1`).
4. `dropColumn` `dias_minimos_vigencia_agenda`, `dias_maximos_vigencia_agenda`.

**Entidad**: los dos campos derogados se reemplazan por
`diasMaximosAnticipacionReserva` (`@NotNull @Min(1) Integer`).

**`ClinicaDomainService`**: `validateDiasVigenciaAgenda` se elimina; la cota `>= 1` la
cubre Bean Validation. Se conserva `horarioInicioAtencion < horarioFinAtencion`.

**`ClinicaApp.updateClinica`**: se borra el bloque de "días efectivos" de los dos
parámetros viejos.

**Records**: `UpdateClinicaRequest` y `GetClinicaResponse` cambian los dos campos por uno.

### A3. `MotivoCancelacion`

Eliminar el valor `BAJA_DE_PRESTACION` del enum. No hay changeset: se persiste como texto
y ninguna fila lo usa todavía (no hay turnos). Si en el futuro hubiera datos, haría falta
un `UPDATE` previo.

Verificar que ningún `switch` ni `Set` lo referencie antes de borrar.

### A4. Cascada de `disablePrestacion`

Hoy hace 4 de los 5 pasos de §6. Orden correcto, con lo que se agrega marcado:

1. **(agregar)** Cerrar `fechaFinVigencia = ahora` de las `MedicoPrestacion` vigentes de la
   prestación. Habilitado por A1. Nuevo método de repo
   `findByPrestacion_IdAndVigenteAt` + `cerrarVigenciasByPrestacion` en el DomainService.
2. **(agregar, tras Fase B)** Baja de los `AgendaHorarios` futuros libres. Requiere el
   stack de escritura de Agenda: se completa al final de la Fase B.
3. Cerrar `IndicacionPrestacion` vigentes — ya está.
4. **(hecho)** Baja de las coberturas `ObraSocialPlanPrestacion` de la prestación, vía
   `ObraSocialPlanPrestacionDomainService.softDeleteByPrestacion`.
5. Cerrar el tramo del histórico y abrir `DESHABILITADA` — ya está.

Las dos precondiciones restrictivas ya están (`validateSinTurnosVivos`,
`validateSinAgendaFuturaOcupada`) y corren antes de escribir. No tocarlas.

### A5. Cascada de `disablePlan`

Hoy solo valida turnos vivos y cambia el estado. Faltan, según §6:

1. **(hecho)** Baja de sus `ObraSocialPlanPrestacion`, vía
   `ObraSocialPlanPrestacionDomainService.softDeleteByPlan`.
2. **(agregar)** Baja de las `ObraSocialPaciente` que lo referencian. `ObraSocialPacienteRepository`
   y su DomainService ya existen: agregar `findByPlan_IdAndDeletedAtIsNull` y
   `softDeleteByPlan`, y llamarlo desde `PlanApp.disablePlan`.

Mismo agregado en `ObraSocialApp.softDeleteObraSocial`, que hoy deshabilita los planes
sin arrastrar sus coberturas.

### A6. `updatePrestacion` y los slots futuros

§5 PREST: cambiar las duraciones o `tiempoToleranciaSolicitud` alcanza a los
`AgendaHorarios` futuros **libres**. Los ocupados no se tocan nunca.

| Campo modificado | Efecto |
|---|---|
| `duracionMinima` / `duracionMaxima` | Revalidar `horaHasta − horaDesde` contra el rango nuevo; los que quedan fuera se dan de baja |
| `tiempoToleranciaSolicitud` | Recalcular `fechaLimiteReserva = inicio del slot − tolerancia nueva`; los que quedan con plazo vencido se dan de baja |

Requiere el stack de escritura de `AgendaHorarios`: **se implementa al final de la Fase B**,
no antes. `UpdatePrestacionResponse` suma `cantidadHorariosRecalculados` y
`cantidadHorariosDadosDeBaja`.

---

## Fase B — Feature de Agenda

### Contexto de negocio

Sin agenda no hay turnos: `AgendaHorarios` es el único objeto reservable del sistema. Esta
feature crea la oferta que después consumen el panel y el chatbot.

- **Quiénes la usan**: personal de clínica desde el panel (admin, y médico sobre su propia
  agenda vía §11.1). El chatbot solo consume `listHorariosDisponibles`.
- **Front**: `createAgendaMedico` sale de un wizard (médico → período → patrón semanal o
  días sueltos → preview con el conteo de slots → `Confirmar`). `listAgendaMedico` alimenta
  el selector de agendas; `listHorariosAgenda` pinta el calendario del período elegido.

### Lo que ya existe

Las tres entidades y sus changelogs están completos y correctos: `AgendaMedico` por
vigencia sin baja lógica y con `EXCLUDE USING gist` de no solapamiento, `AgendaDia` y
`AgendaHorarios` con baja lógica, único parcial `(agenda_medico_id, fecha)`,
`ck_agenda_horarios_horas`. **No hay que crear ni modificar entidades ni changelogs de
agenda.**

Existe además un `AgendaHorariosRepository` y un `AgendaHorariosDomainService` **de solo
lectura**, creados para la precondición de baja de Prestación. Se **extienden**, no se
reemplazan.

### Endpoints

Todos en `AgendaMedicoController`, base `/accesmed-api/AgendaMedico`. `AgendaDia` y
`AgendaHorarios` no tienen controller propio: no tienen vida independiente del período.

| Método | Ruta | Status |
|---|---|---|
| `createAgendaMedico` | `POST /Agenda` | 201 |
| `updateAgendaMedico` | `PATCH /Agenda/{id}` | 200 |
| `updateVigenciaAgendaMedico` | `PATCH /Agenda/Vigencia/{id}` | 200 |
| `listAgendaMedico` | `GET /Agenda` | 200 |
| `getAgendaMedico` | `GET /Agenda/Buscar` | 200 |
| `listHorariosAgenda` | `GET /Horarios` | 200 |
| `listHorariosDisponibles` | `GET /HorariosDisponibles` | 200 |

#### `createAgendaMedico`

Request: `medicoId`, `fechaHoraInicioVigencia`, `fechaHoraFinVigencia`, y **exactamente uno**
de `patronSemanal` (lista de `diaSemana` + bloques) o `diasSueltos` (lista de `fecha` +
bloques). Cada bloque: `horaDesde`, `horaHasta`, `prestacionId`, `duracionTurno` (`Duration`).
`@AssertTrue` para la exclusividad de los dos modos.

El patrón **no se persiste**: se expande a `AgendaDia` + `AgendaHorarios` y se descarta.

Response: la agenda con días y horarios expandidos + `cantidadHorariosGenerados`.

#### `updateAgendaMedico`

Delta sobre los slots. Todos los campos opcionales, `null` = no tocar.

```json
PATCH /accesmed-api/AgendaMedico/Agenda/{id}
{
  "id": "a3f...",
  "horariosAAgregar": [
    { "fecha": "2026-09-01", "horaDesde": "08:00", "horaHasta": "12:00",
      "prestacionId": "7c1...", "duracionTurno": "PT30M" }
  ],
  "horariosAExcluir": [ "d17..." ],
  "diasAExcluir":     [ "b42..." ]
}
```

Reglas de forma:

- **El día es implícito.** `horariosAAgregar` lleva `fecha`; si no existe `AgendaDia` para
  esa fecha, el App lo crea (get-or-create, respaldado por el único parcial). Nunca se
  agrega un día vacío.
- Si al excluir horarios un `AgendaDia` queda sin horarios activos, **se da de baja solo**.
- `diasAExcluir` es el atajo del feriado: excluye el día y todos sus horarios de una.
- Excluir un día y agregar horarios en ese mismo día en el mismo request es contradictorio
  → `ValidacionException`.

#### `updateVigenciaAgendaMedico`

Recibe `fechaHoraInicioVigencia` y/o `fechaHoraFinVigencia`. Cubre las cuatro direcciones:

| Dirección | Guarda |
|---|---|
| Mover el inicio | Solo si la agenda no arrancó (`fechaHoraInicioVigencia > ahora`). Sin solapar con el período anterior |
| Adelantar el fin | Baja de los `AgendaDia` posteriores con sus horarios. Rechaza si alguno tiene slots ocupados |
| Atrasar el fin | Sin solapar con el período siguiente |

El no solapamiento lo garantiza además el `EXCLUDE USING gist`; `tstzrange` es `[)`, así
que dos períodos adyacentes no chocan.

#### Los tres `GET`

Usan el filtrado dinámico de `Services/QueryServices/Filtering` (ver `Docs/FILTRADO-DINAMICO.md`).

**`AgendaMedicoCriteria`** — `id`, `medicoId`, `especialidadId` (join `medico.especialidad`),
`fechaHoraInicioVigencia` (`RangeFilter`), `fechaHoraFinVigencia` (`RangeFilter`),
`vigenteAl` (derivado: `inicio <= X < fin`, default ahora).

Sin guarda fija de vigencia: el selector del front tiene que mostrar también los períodos
vencidos y los programados. Mismo criterio que `PrestacionQueryService`, que tampoco filtra
por estado implícitamente.

`listAgendaMedico` devuelve `PageResponse<ListAgendaMedicoResponse>` (`id`, datos del
médico, las dos fechas, `cantidadDias`, `cantidadHorarios`). `getAgendaMedico` usa
`findOneByCriteria` del `AbstractFiltroQueryService` y devuelve el objeto suelto, 404 si no
matchea — el reemplazo del `GET /{id}` según `FILTRADO-DINAMICO.md` §3.

**`AgendaHorariosCriteria`** — `id`, `agendaMedicoId`, `medicoId`, `prestacionId`,
`fecha` (`LocalDateFilter` sobre `agendaDia.fecha`), `horaDesde` (`LocalTimeFilter`),
`estaOcupada` (`BooleanFilter`).

Lo comparten los dos listados de horarios, que se diferencian **solo en las guardas fijas**:

| Endpoint | Guardas fijas |
|---|---|
| `listHorariosAgenda` (panel) | `deletedAt` vacío |
| `listHorariosDisponibles` (chatbot) | + `estaOcupada = false`, `ahora < fechaLimiteReserva`, `fecha <= hoy + diasMaximosAnticipacionReserva` |

Las guardas **no son filtros**: las aplica siempre el QueryService, igual que hoy los
listados de catálogo devuelven solo activos sin que el front lo pida. El front no puede
pedir un slot ocupado ni uno fuera del horizonte.

**Filtros nuevos** en `Services/QueryServices/Filtering`: `LocalDateFilter` y
`LocalTimeFilter` (hoy solo hay `InstantFilter`), ambos `extends RangeFilter<T>`.

**Filtros derivados**: `vigenteAl` de `AgendaMedicoCriteria` se resuelve como predicado
sobre columnas del propio root. El precedente de subconsulta, si hiciera falta, es
`PrestacionQueryService.buildEstadoVigenteSpecification`, que filtra `estadoActual` — que
no es columna — con un `EXISTS` contra el histórico.

#### Extensión de `MedicoCriteria` (no es endpoint de Agenda)

La consulta "médicos activos sin agenda vigente" (§5 AGEN) **no lleva endpoint propio**: se
resuelve con dos campos nuevos en `MedicoCriteria`, calcados de
`buildEstadoVigenteSpecification`:

- `tieneAgendaVigente` (`BooleanFilter`) → `EXISTS` / `NOT EXISTS` contra `agenda_medico`.
- `agendaVigenteAl` (fecha de referencia, default ahora) → resuelve "por vencer":
  `tieneAgendaVigente.equals=false&agendaVigenteAl.equals=<hoy+30>`.

Queda `GET /accesmed-api/Medico/Medico?tieneAgendaVigente.equals=false`.

### Reglas de negocio

**Generación de slots** (aplica a `createAgendaMedico` y a `horariosAAgregar`):

- `AgendaDia.fecha` dentro del período de vigencia de su `AgendaMedico`.
- `horaDesde < horaHasta`, ambas dentro de `horarioInicioAtencion` / `horarioFinAtencion`
  de la clínica.
- `duracionTurno` divide exactamente al bloque: `(horaHasta − horaDesde) % duracionTurno == 0`.
- `slot(n) = horaDesde + n × duracionTurno`.
- Duración del slot entre `duracionMinima` y `duracionMaxima` de la prestación.
- Prestación **Publicada** (estado vigente desde `HistoricoEstadoPrestacion`).
- `MedicoPrestacion` **vigente en `AgendaDia.fecha`** del par (médico de la agenda,
  prestación del slot). Habilitado por A1 — evaluar el período contra la fecha del día,
  no contra ahora.
- Los bloques del mismo día no se superponen, **incluidos los que ya están en base**.
- `fechaLimiteReserva = inicio del slot − prestacion.tiempoToleranciaSolicitud`.
- Tope de generación: `ReglaNegocioException` si un `Confirmar` supera N slots (constante
  en el App). Es guardarraíl operativo, no regla de negocio (§5 AGEN).

**Alta del período** (solo `createAgendaMedico`): `fechaHoraInicioVigencia >= ahora`,
menor que `fechaHoraFinVigencia`, y sin solapar con otros períodos del mismo médico.

**Restrictivo por turnos** (`updateAgendaMedico` y `updateVigenciaAgendaMedico`): excluir
un día, excluir horarios o adelantar el corte se **rechaza** si arrastra algún
`AgendaHorarios` con `estaOcupada = true`, informando cuántos y hasta qué fecha. La guarda
corre sobre la **unión** de todo lo que el request daría de baja, **antes de escribir nada**.

> Decisión explícita: §11.3 dice que estas operaciones cancelan los turnos afectados, pero
> el módulo Turno no tiene stack de escritura. Hasta que exista, la operación es
> restrictiva — mismo criterio que la v3 aplica en `Prestacion`, `Plan` y `Medico`. Cuando
> Turno esté, acá entra la cascada de cancelación.

**Acumulación de errores**: las validaciones de forma de un lote de slots se acumulan en un
solo `ValidacionException`, para que el front reciba todos los errores juntos.

### Orden de la transacción en `updateAgendaMedico`

| # | Paso |
|---|---|
| 1 | Validar coincidencia del `id` de ruta con el del body |
| 2 | Resolver todo lo que el request daría de baja (`diasAExcluir` + sus horarios + `horariosAExcluir`) |
| 3 | Guarda restrictiva sobre esa unión: si hay algún slot ocupado, rechazar sin escribir |
| 4 | Aplicar las bajas |
| 5 | Aplicar `horariosAAgregar`, con get-or-create del `AgendaDia` |
| 6 | Dar de baja los `AgendaDia` que quedaron sin horarios activos |

### Archivos

**Nuevos:**

```
Repositories/             AgendaMedicoRepository, AgendaDiaRepository
Records/AgendaMedico/
  Request/                CreateAgendaMedicoRequest, UpdateAgendaMedicoRequest,
                          UpdateVigenciaAgendaMedicoRequest,
                          BloqueHorarioRequest, DiaPatronRequest, DiaSueltoRequest,
                          HorarioAAgregarRequest
  Criteria/               AgendaMedicoCriteria, AgendaHorariosCriteria
  Response/               CreateAgendaMedicoResponse, UpdateAgendaMedicoResponse,
                          GetAgendaMedicoResponse, ListAgendaMedicoResponse,
                          ListAgendaHorarioResponse, ListHorarioDisponibleResponse
Services/Mappers/         AgendaMedicoMapper
Services/DomainServices/  AgendaMedicoDomainService, AgendaDiaDomainService
Services/QueryServices/   AgendaMedicoQueryService, AgendaHorariosQueryService
Services/QueryServices/Filtering/  LocalDateFilter, LocalTimeFilter
Services/Utils/           GeneradorSlotsAgenda
Application/              AgendaMedicoApp
Controllers/              AgendaMedicoController
```

**Modificados:** `AgendaHorariosRepository` y `AgendaHorariosDomainService` (extender con
escritura), `MedicoCriteria` y `MedicoQueryService` (los dos filtros derivados),
`PrestacionApp` (A4 paso 2 y A6).

`GeneradorSlotsAgenda` va en `Services/Utils` porque la expansión patrón → días → slots es
cálculo puro sin repositorio, la comparten `create` y `update`, y no le pertenece a ninguna
de las tres entidades.

**Regla de capas**: toda la orquestación multi-entidad (agenda + días + horarios +
prestación + médico + clínica) vive en `AgendaMedicoApp`, con `@Transactional`. Ningún
`DomainService` inyecta un repositorio ajeno ni llama a otro `DomainService`
(`ARQUITECTURA.md` §5.0).

### Tests

- Unit de `GeneradorSlotsAgenda` — es donde se concentra la lógica: divisibilidad del
  bloque, bordes del horario de clínica, superposición, cálculo de `fechaLimiteReserva`.
- Unit de `AgendaMedicoApp` con services mockeados: la guarda restrictiva de slots
  ocupados, el get-or-create del día, la baja automática del día vacío.
- Unit de los `DomainService` nuevos.
- Integración MockMvc del controller: happy path de los 7 endpoints + `AccesMedError` de
  slot ocupado y de prestación no publicada.
- Regresión de la Fase A: `MedicoPrestacionApp` (solapamiento y piso duro del corte) y
  `ClinicaApp`.

---

## Fase C — Impacto en el front (`AccesMed-FrontEnd`)

Repo separado: `E:\Facultad\5-Quinto\Proyecto Final\AccesMed-App\AccesMed-FrontEnd`.
React 19 + TypeScript + react-query + react-hook-form + zod. Cada feature vive en
`src/features/<nombre>/` con el mismo cuarteto: `types.ts` (espejo de los Records del
backend), `schema.ts` (zod, espejo de las Bean Validation), `api.ts`, `hooks.ts`.

**Alcance de esta fase: solo lo que NO es agenda.** Los cambios del front que dependen de
la feature de Agenda se hacen aparte, en su propio plan.

### Qué toca cada desajuste

| Desajuste | Impacto en el front |
|---|---|
| A1 `MedicoPrestacion` → vigencia | **Sí** — `features/medicoPrestacion` completo + `features/medicos` |
| A2 `Clinica` horizonte de reserva | **Sí** — `features/clinica` + `pages/Configuracion` |
| A3 `MotivoCancelacion` | **No** — se verificó: el identificador no aparece en `src/`. Cero cambios |
| A4 / A5 cascadas | **No** — son efectos internos, no cambian ningún contrato |
| A6 recálculo de slots | **Aplazado** — el response de Prestación sumará contadores de horarios; va con el plan de Agenda |

### C1. `MedicoPrestacion`: de baja lógica a vigencia

Es el cambio de fondo del front, y **no es solo renombrar campos**: desasignar deja de ser
una baja destructiva e instantánea y pasa a ser *cerrar un período*, que además se puede
programar a futuro.

**`src/features/medicoPrestacion/types.ts`**

```ts
export interface AssignMedicoPrestacionRequest {
  medicoId: string;
  prestacionId: string;
  atiendeParticular: boolean;
  precioParticular: number;
  fechaInicioVigencia?: string;   // NUEVO — ISO instant; ausente = ahora
}

export interface GetMedicoPrestacionResponse {
  id: string;
  medicoId: string;
  prestacionId: string;
  prestacionCodigo: string;
  prestacionNombre: string;
  atiendeParticular: boolean;
  precioParticular: number;
  fechaInicioVigencia: string;          // NUEVO
  fechaFinVigencia: string | null;      // NUEVO — null = vigente sin corte
}

// SoftDeleteMedicoPrestacionResponse se ELIMINA y se reemplaza por:
export interface UnassignMedicoPrestacionResponse {
  id: string;
  fechaFinVigencia: string;
}
```

**`src/features/medicoPrestacion/api.ts`** — cambia el verbo y la ruta:

```ts
// ANTES: DELETE /accesmed-api/MedicoPrestacion/{id}
// AHORA:
export function unassignMedicoPrestacion(body: UnassignMedicoPrestacionRequest) {
  return apiFetch<UnassignMedicoPrestacionResponse>(
    `${BASE}/Vigencia/${body.id}`, { method: "PATCH", body });
}
```

`assignMedicoPrestacion` conserva `POST ${BASE}/Asignar`.

**`src/features/medicoPrestacion/hooks.ts`** — `useUnassignMedicoPrestacion` cambia la
firma de `mutationFn` de `(id: string)` a `(body: UnassignMedicoPrestacionRequest)`.

**`src/features/medicoPrestacion/schema.ts`** — `medicoPrestacionSchema` suma
`fechaInicioVigencia` opcional. Ojo: este schema lo reusa `features/medicos/schema.ts`
para las prestaciones anidadas del alta de médico, así que el campo tiene que ser opcional
para no romper ese form.

**`src/features/medicoPrestacion/AssignPrestacionForm.tsx`** — campo opcional de fecha de
inicio ("Vigente desde", default hoy).

**`src/features/medicos/types.ts`** — `PrestacionAnidada` suma `fechaInicioVigencia` y
`fechaFinVigencia`. `AsignarPrestacionAnidada` suma `fechaInicioVigencia?`.

**`src/features/medicos/MedicoDetailModal.tsx`** — dos cambios reales, no cosméticos:

1. El botón "Desasignar" (línea ~91) hoy hace `unassign.mutate(p.id)`. Ahora necesita una
   fecha de corte. Default: ahora, con opción de programarla a futuro.
2. **La lista de prestaciones del médico ahora puede traer períodos ya cerrados.** Hay que
   decidir qué se muestra: solo las vigentes, o todas con las vencidas marcadas. La
   recomendación es filtrar por vigentes en la vista principal y dejar el histórico detrás
   de un toggle, porque un médico puede haber atendido la misma prestación en varios
   períodos (§5 MED: reasignar es crear una instancia nueva, no reabrir la vieja).

**Error nuevo a manejar**: `MEDICO_PRESTACION_CORTE_ANTERIOR_A_TURNO` — la fecha de corte
no puede ser anterior al último turno vivo de ese par. El mensaje del backend trae la fecha
del turno; conviene mostrarla tal cual. Se suma a `MEDICO_PRESTACION_SOLAPADA`, que sale al
asignar un período que se cruza con otro del mismo par.

> Mientras el módulo Turno no exista no hay turnos, así que el primero no se va a disparar
> todavía. Igual hay que contemplarlo: aparece solo cuando ya haya datos reales y sería un
> error silencioso en producción.

### C2. `Clinica`: horizonte de reserva

**`src/features/clinica/types.ts`** — en `GetClinicaResponse` y `UpdateClinicaRequest`, los
dos campos `diasMinimosVigenciaAgenda` / `diasMaximosVigenciaAgenda` se reemplazan por uno:
`diasMaximosAnticipacionReserva: number`.

**`src/features/clinica/schema.ts`** — dos cambios:

1. Los dos campos del `baseClinicaSchema` se reemplazan por
   `diasMaximosAnticipacionReserva: z.coerce.number().int().min(1, "Debe ser al menos 1.")`.
2. **Se elimina el segundo `superRefine`**, el que valida "los días máximos no pueden ser
   menores a los mínimos": esa regla quedó derogada junto con los parámetros. El
   `superRefine` del horario de atención se conserva.

**`src/pages/Configuracion/ConfiguracionPage.tsx`** — los dos inputs pasan a uno. El label
importa, porque el significado cambió: ya no acota el largo del período de agenda sino
**hasta cuándo se puede pedir un turno**. Sugerido: "Días máximos de anticipación para
reservar un turno".

### Orden

C2 es independiente y chico: va primero. C1 va inmediatamente después de A1 en el backend
— es un cambio de contrato, así que los dos repos tienen que moverse juntos o el front
queda roto contra `develop`.

---

## Documentación a actualizar

`CLAUDE.md` obliga a reflejar los cambios de convención en el mismo cambio.

| Documento | Qué cambia |
|---|---|
| `Docs/Features/Agenda.md` | Nuevo. Delegar en la skill `feature-documenter` |
| `Docs/Features/Medico.md` | La desasignación de prestación pasó de `DELETE` a `PATCH /Vigencia/{id}` |
| `Docs/Features/Clinica.md` | El parámetro de horizonte de reserva |
| `Docs/FILTRADO-DINAMICO.md` §5 | `AgendaMedico` y `AgendaHorarios`; los campos nuevos de `MedicoCriteria` |
| `Docs/Dominio/dominio-reglas-validaciones.md` §11.3 | Las 7 funciones AGEN mapean a 7 endpoints con otros nombres; `consultarMedicosSinAgendaVigente` se resolvió como filtro de `MedicoCriteria` |
| `Docs/ARQUITECTURA.md` §5.2 | **Cuarto caso de actualización**: delta de composición |

### El cuarto caso de `ARQUITECTURA.md` §5.2

§5.2 define hoy `fullUpdate<Entidad>` (PUT), `partialUpdate<Entidad>` (PATCH genérico,
`null` = no tocar) y `update<Concepto><Entidad>` (PATCH de un grupo conocido).
`updateAgendaMedico` **no es ninguno de los tres**: el request no describe campos del
recurso sino altas y bajas de sus hijos. Forzarle el nombre `partialUpdate` mentiría,
porque `horariosAAgregar` no es un campo de `AgendaMedico`.

Se agrega el caso: **actualización por delta de composición** — `update<Entidad>`, `PATCH
/<Recurso>/{id}`, para agregados donde el request lleva colecciones `<algo>AAgregar` /
`<algo>AExcluir`. Va a reaparecer en Turno.

---

## Orden de ejecución

1. **A2** (`Clinica`) — independiente, chico. Desbloquea el horizonte de reserva.
2. **C2** (front de `Clinica`) — cierra el contrato de A2 antes de seguir.
3. **A3** (`MotivoCancelacion`) — independiente, una línea. Sin front.
4. **A1** (`MedicoPrestacion` → vigencia) — desbloquea la regla del slot y el paso 1 de A4.
5. **C1** (front de `MedicoPrestacion`) — **inmediatamente después de A1**: es un cambio de
   contrato, si los dos repos no se mueven juntos el front queda roto contra `develop`.
6. **A4 pasos 1 y 4**, **A5** — cascadas, con los `// TODO` de coberturas.
7. **Fase B** — la feature de Agenda completa.
8. **A4 paso 2** y **A6** — los recálculos de `AgendaHorarios`, que necesitan el stack de
   escritura que crea la Fase B.
9. Documentación.

El front de la feature de Agenda (pantalla `src/pages/Agenda`, features nuevas) **no entra
en este plan**: va en uno propio, después del paso 8.

Cada paso compila (`./mvnw compile`) y corre su migración antes de pasar al siguiente.