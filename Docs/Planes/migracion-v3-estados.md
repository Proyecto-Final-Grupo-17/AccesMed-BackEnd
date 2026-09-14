# Plan — Migración v2→v3: Prestaciones, Especialidades y Obras Sociales

## Contexto

El 2026-08-05 se subió el nuevo modelo de dominio (`Docs/Dominio/dominio-reglas-validaciones.md` v3
+ `modelo_acces_med.json`). Los docs están en v3 pero **el código sigue en v2**. La regla que ordena
todo v3: **tres ejes de "retiro" mutuamente excluyentes** — baja lógica (`deletedAt`), vigencia
(`fechaInicioVigencia/fechaFinVigencia`) o estados (histórico de tramos). Ninguna clase usa más de uno.

Este plan cubre los **tres módulos núcleo** pedidos:

- **Prestacion**: deja de ser bajable → pasa a **estados** (*No Publicada / Publicada / Deshabilitada*).
  Desaparecen `fechaHabilitacion`, `habilitada`, el CU "Habilitar" y el `DELETE` soft. La baja
  (deshabilitar) es **restrictiva**, no cascada.
- **Especialidad**: entidad ya existe; falta el stack completo (ABM) + **baja restrictiva**.
- **ObraSocial + Plan**: `ObraSocial` conserva baja lógica; `Plan` pasa a **estados** (mismo esquema
  que Prestacion). Alta de OS crea OS + ≥1 Plan atómico. Bajas restrictivas.

### Decisiones tomadas (respuestas del usuario)
1. **Alcance**: solo núcleo (ABM catálogo + máquina de estados + baja restrictiva). **NO** se tocan
   `MedicoPrestacion`, `ObraSocialPaciente` ni las coberturas (`ObraSocialPlanPrestacion`) en este plan.
2. **Estados como enum Java, no entidad-catálogo — para las TRES máquinas**: se **eliminan**
   `EstadoPrestacion`, `EstadoPlan` **y `EstadoTurno`** como entidades/tablas y sus seeds. Los estados
   se modelan con enums Java (`@Enumerated(STRING)`, `varchar(20)` + `CHECK IN (...)`). Los históricos
   llevan una **columna enum `estado`** (no FK a catálogo). ⚠️ Diverge del doc/JSON v3 y del DTE → hay
   que **actualizar `dominio-reglas-validaciones.md`, `modelo_acces_med.json` y `modelo_dte_turno.json`**.
3. **Estado actual materializado + unicidad por índice**: columna **`estado_actual`** (enum) en
   `prestacion`, `plan` **y `turno`**, mantenida por el service de transición. Con **índice** para
   filtrar rápido (publicadas / no deshabilitadas / turnos por estado) sin tocar el histórico, y en
   Prestación/Plan **unicidad real** vía índice único parcial `WHERE estado_actual <> 'DESHABILITADA'` +
   validación en el `DomainService`. (Reemplaza la idea previa de "unicidad solo en aplicación": ahora
   hay garantía a nivel base.) El histórico queda como **auditoría** del ciclo de vida (tramos +
   `motivo`), no como fuente de la consulta caliente. Motivo de incluir Turno: muchos CU —incluidas las
   precondiciones restrictivas de este plan— consultan el **estado actual del turno**. NOTA:
   `turno.estado_actual` no tiene mantenedor hasta que exista el módulo Turno (hoy no hay stack ni turnos
   en dev); se convierte ahora para dejar el patrón y las consultas listas.
4. **Chequeos restrictivos**: **enforcement real** de las precondiciones vía repositorios de
   solo-lectura (`Turno`, `AgendaHorarios`, `Medico`); las **cascadas de escritura** que tocan tablas
   fuera de alcance quedan como `TODO`.
5. **Migración**: **editar changelogs in-place** (dev, base se recrea) + agregar tablas de histórico.

### Máquinas de estado (aclaradas con el usuario)
- Prestación y Plan comparten la **misma máquina simétrica**: `No Publicada ⇄ Publicada → Deshabilitada`.
- **Publicar / Despublicar**: reversible, sin restricciones. Publicar **NO** exige médico asignado.
- **Deshabilitar**: transición **terminal e irreversible = la baja lógica** del eje "estados". Restrictiva
  (rechaza con turnos vivos) y con cascada de catálogo. "Revivir" = crear de nuevo (el `codigo` se libera
  porque la unicidad es entre no-deshabilitadas).
- **Guarda de `deshabilitarPlan`**: **solo** la precondición de turno vivo apuntando al plan.
  **Se elimina** la regla "último plan no deshabilitado de una obra social activa" — ⚠️ **borrarla del
  doc v3** (`§5 OS` y `§6 Plan — deshabilitar`, verificar `modelo_acces_med.json`) en el mismo cambio.

### Ejecución
- La codificación se hace en **Sonnet** (preferencia registrada). Se aplican las skills
  `springboot-feature-generator`, `domain-schema-generator`, `java-springboot-code-style`,
  `java-springboot-javadoc`, `java-springboot-logging`, y `feature-documenter` al cierre.
- Patrón de referencia a espejar: el módulo **Prestacion** actual y los enums existentes
  (`Domain/MotivoCancelacion.java`, `ModalidadCobertura.java`) para los nuevos enums de estado.
- **Orden de ejecución**: **Parte D (Turno) → Parte B (Prestacion) → Parte A (Especialidad) →
  Parte C (ObraSocial+Plan)**. Especialidad depende de `Prestacion.estadoActual` para su baja
  restrictiva, y Prestacion depende de que Turno ya exponga `estado_actual`/estados finales.
- **Leer primero** `Domain/AgendaHorarios.java` y `Domain/ObraSocialPaciente.java` para los nombres
  exactos de columnas/relaciones que usarán los repos read-only `AgendaHorariosRepository` y
  `TurnoRepository` (no se leyeron en la verificación previa).

### Patrones confirmados en el código (a reutilizar)
- `Domain/Auditable.java` → `createdAt/updatedAt/createdBy/updatedBy` (Instant). Soft-delete se declara
  por clase (`deletedAt` Instant, `deletedBy` UUID, `deletedReason` varchar 500).
- Histórico a espejar (estructura de tramos): `Domain/HistoricoEstadoTurno.java` — `id` UUID,
  `fechaHoraInicio`/`fechaHoraFin` `ZonedDateTime`, `extends Auditable`, índice único parcial
  `uq_<tabla>_vigente ... WHERE fecha_hora_fin IS NULL` (garantiza un solo tramo abierto). **Diferencia**:
  el estado va como **columna enum** `estado`, no como FK a un catálogo.
- Errores: `Services/Errors/{RecursoNoEncontrado,ReglaNegocio,Validacion}Exception`.
- Changelogs: `resources/liquibase-db-changelogs/changelogs/YYYYMMDDHHMMSS-<Entidad>.xml`, incluidos por
  fase en `master.xml`.

---

## Parte A — Especialidad (feature nueva, la más simple; sienta el patrón)

Entidad, `EspecialidadRepository` (find) y `EspecialidadDomainService` (find) ya existen. Falta:

- **Records** `Records/Especialidad/Request|Response/`:
  `CreateEspecialidadRequest` (codigo, nombre), `UpdateEspecialidadRequest` (id + codigo + nombre,
  parciales), `CreateEspecialidadResponse`, `GetEspecialidadResponse`, `ListEspecialidadResponse`,
  `SoftDeleteEspecialidadResponse`.
- **`EspecialidadMapper`** (MapStruct), **`EspecialidadQueryService`** (list/get activos).
- **Ampliar `EspecialidadRepository`**: `existsByCodigoAndDeletedAtIsNull(+IdNot)`,
  `existsByNombre...`, `findAllByDeletedAtIsNull`.
- **Ampliar `EspecialidadDomainService`**: `save`, validadores de unicidad (codigo·nombre entre
  activos, excluyendo self en update), `softDeleteEspecialidad`.
- **`EspecialidadApp`**: create, update, findById, findAll, softDelete.
- **`EspecialidadController`** `@RequestMapping("/accesmed-api/Especialidad")`: `POST /Especialidad`,
  `PATCH /Especialidad/{id}`, `GET /Especialidad/{id}`, `GET /Especialidad`, `DELETE /Especialidad/{id}`.
- **Baja restrictiva** (enforcement real): antes de soft-delete, rechazar (`ReglaNegocioException`) si
  - existe algún `Medico` **activo** con esa especialidad → nuevo `MedicoRepository`
    (`existsByEspecialidadIdAndDeletedAtIsNull`), o
  - existe alguna `Prestacion` **no Deshabilitada** con esa especialidad →
    `existsByEspecialidadIdAndEstadoActualNot(DESHABILITADA)` (consulta directa por `estado_actual`).
- Changelog `20260731214600-Especialidad.xml`: sin cambios de columnas; solo confirmar índices.

---

## Parte B — Prestacion (rework v2→v3, el corazón del plan)

### B.1 Enum, entidades y esquema
- **`Domain/EstadoPrestacion.java`** (nuevo, **enum**): `NO_PUBLICADA`, `PUBLICADA`, `DESHABILITADA`.
- **`Domain/Prestacion.java`**: quitar `fechaHabilitacion` y **todo el bloque soft-delete**. Agregar
  `estadoActual` (`@NotNull @Enumerated(STRING)`, `estado_actual varchar(20)`). Se conservan `codigo`
  (inmutable), `nombre`, las 9 duraciones `Duration`, y la FK `especialidad` (pasa a `updatable=false`,
  inmutable tras el alta).
- **`Domain/HistoricoEstadoPrestacion.java`** (nuevo): espejo de `HistoricoEstadoTurno` pero con
  `estado` como **columna enum** (`@Enumerated(STRING)`) en vez de FK; FK a `Prestacion`;
  `fechaHoraInicio/Fin` ZonedDateTime; `motivo varchar(500)` nullable; índice único parcial
  `uq_historico_estado_prestacion_vigente WHERE fecha_hora_fin IS NULL`.
- **Changelogs** (in-place + nuevo):
  - `20260731215300-Prestacion.xml`: quitar `fecha_habilitacion` y columnas de baja; agregar
    `estado_actual varchar(20) NOT NULL` + `CHECK (estado_actual IN ('NO_PUBLICADA','PUBLICADA','DESHABILITADA'))`;
    `idx_prestacion_estado_actual`; los únicos parciales `WHERE deleted_at IS NULL` → índices únicos
    parciales `uq_prestacion_codigo|nombre ... WHERE estado_actual <> 'DESHABILITADA'`.
  - `20260731215310-HistoricoEstadoPrestacion.xml` (tras Prestacion), con `estado varchar(20)` + CHECK.
  - **Sin** tabla `estado_prestacion` ni seed. Actualizar los `<include>` en `master.xml`.

### B.2 Records (reemplazan el vocabulario "habilitada"/"borrador")
- Conservar `CreatePrestacionRequest` y `CreateIndicacionPrestacionAnidadaRequest`.
- **Borrar** `UpdatePrestacionNoHabilitadaRequest/Response`, `UpdateToleranciasPrestacionRequest/Response`,
  `EnablePrestacionResponse`, `SoftDeletePrestacionResponse`.
- **Nuevos**: `UpdatePrestacionRequest` (parcial: `nombre` + 9 minutos, todos nullable — se puede
  modificar siempre), `DeshabilitarPrestacionRequest` (`motivo` opcional),
  `CambioEstadoPrestacionResponse` (id, codigo, nombre, `estadoActual`).
- Responses `Create/Get/List` exponen `estadoActual` (enum) en lugar de `fechaHabilitacion`/`habilitada`.

### B.3 App / Services / Repositorios
- **`PrestacionDomainService`**: quitar `habilitarPrestacion`, `validatePrestacionIsBorrador`,
  `softDeletePrestacion`. Agregar máquina de estados:
  `abrirTramoEstado(prestacion, estadoNuevo, motivo)` — cierra el tramo vigente del histórico
  (`fechaHoraFin=now`), abre uno nuevo con `estado=estadoNuevo`, y **setea `prestacion.estadoActual`**
  (cache + histórico en la misma transacción). Validadores de transición (No Publicada⇄Publicada
  reversible; desde `DESHABILITADA` **no hay transición**). Unicidad codigo·nombre **entre
  no-deshabilitadas** vía `existsByCodigoAndEstadoActualNot(DESHABILITADA[, idExcluido])` (respaldada
  por el índice único parcial). `validateToleranciasPrestacion` se conserva (se revalida en cada modif).
- `HistoricoEstadoPrestacionRepository` (tramo vigente por prestación, para auditoría/`fechaHoraFin`).
- **`PrestacionApp`** casos de uso:
  - `createPrestacion`: crea Prestacion con `estadoActual=NO_PUBLICADA` + abre tramo histórico
    *No Publicada* + indicaciones anidadas (atómico).
  - `updatePrestacion`: modifica nombre/duraciones/tolerancias en cualquier estado; revalida cadena de
    tolerancias. `TODO`: recalcular/dar de baja `AgendaHorarios` futuros libres afectados.
  - `publicarPrestacion` / `despublicarPrestacion`: transición reversible (PATCH sin body). Publicar
    **no** exige médico asignado.
  - `deshabilitarPrestacion` (restrictiva, terminal):
    - **Precondición real** (antes de escribir): rechazar si hay `Turno` de la prestación con estado
      vigente no final, o `AgendaHorarios` futuro con `estaOcupada=true`. Mensaje con cantidad y fecha
      máxima. Vía repos read-only `TurnoRepository`/`AgendaHorariosRepository` (count/exists).
    - Cumplida: `abrirTramoEstado(..., DESHABILITADA, motivo)`. `TODO` cascada de escritura: cerrar
      `MedicoPrestacion` vigentes, bajar `AgendaHorarios` libres, cerrar `IndicacionPrestacion` vigentes,
      bajar `ObraSocialPlanPrestacion` (módulos fuera de alcance).
  - `findPrestacionById`, `findPrestaciones` (filtros por `especialidadId` y por `estadoActual`).
- **`PrestacionController`**: `POST /Prestacion`, `PATCH /Prestacion/{id}` (modificar),
  `PATCH /Prestacion/{id}/Publicar`, `PATCH /Prestacion/{id}/Despublicar`,
  `PATCH /Prestacion/{id}/Deshabilitar` (con `DeshabilitarPrestacionRequest`), `GET /Prestacion/{id}`,
  `GET /Prestacion`. Se elimina `PATCH .../Habilitacion` y `DELETE`.
- **`PrestacionRepository`**: reemplazar los derivados de `deletedAt`/`fechaHabilitacion` por derivados
  de `estadoActual` (`findAllByEstadoActual...`, `existsByCodigoAndEstadoActualNot...`, etc.).
- **`PrestacionMapper`**: exponer `estadoActual`; quitar `habilitada`/`fechaHabilitacion`; conservar
  `toDuration/toMinutos`.

### B.4 Fuera de alcance de este plan (follow-up, se deja anotado)
- Rework de `IndicacionPrestacion` a **vigencia** (cambio #5 v3) y sus CU `modificarIndicacionPrestacion`
  / `programarBajaIndicacionPrestacion`. Por ahora `IndicacionPrestacion` queda como está (bajable) y la
  cascada de deshabilitar sobre indicaciones es `TODO`.

---

## Parte C — ObraSocial + Plan (feature nueva; Plan a estados)

### C.1 Enum, entidades y esquema
- **`Domain/EstadoPlan.java`** (nuevo, **enum**): `NO_PUBLICADO`, `PUBLICADO`, `DESHABILITADO`.
- **`Domain/Plan.java`**: quitar el bloque soft-delete (v2). Agregar `estadoActual`
  (`@Enumerated(STRING)`, `estado_actual varchar(20)`).
- **`Domain/HistoricoEstadoPlan.java`** (nuevo): espejo del de Prestacion (columna enum `estado`,
  FK a `Plan`, índice único parcial de tramo vigente).
- **Changelogs**:
  - `20260731215400-Plan.xml`: quitar columnas de baja; agregar `estado_actual varchar(20) NOT NULL` +
    CHECK; `idx_plan_estado_actual`; los `uq_plan_obra_social_codigo|nombre WHERE deleted_at IS NULL` →
    `... WHERE estado_actual <> 'DESHABILITADO'`.
  - `20260731215410-HistoricoEstadoPlan.xml` (tras Plan), con `estado varchar(20)` + CHECK. Actualizar
    `master.xml`. **Sin** tabla `estado_plan` ni seed.
  - `ObraSocial` (`20260731214900-ObraSocial.xml`) sin cambios de eje (sigue bajable).

### C.2 Stack ObraSocial (bajable)
- Records `Records/ObraSocial/...`: `CreateObraSocialRequest` (codigo, nombre, razonSocial + **lista de
  planes** `@Valid @NotEmpty` con `CreatePlanAnidadoRequest{codigo,nombre}`), `UpdateObraSocialRequest`
  (parciales), responses Create/Get/List/SoftDelete.
- `ObraSocialRepository`, `ObraSocialDomainService` (save, unicidad codigo·nombre entre activos,
  softDelete), `ObraSocialQueryService`, `ObraSocialMapper`.
- `ObraSocialApp`:
  - `createObraSocial`: crea OS + ≥1 Plan (cada uno `estadoActual=NO_PUBLICADO` + tramo histórico) en una
    transacción.
  - `updateObraSocial`, `findById`, `findAll`.
  - `softDeleteObraSocial` (restrictiva por transitividad): evalúa la precondición de deshabilitación de
    **cada** plan; si alguno no puede, rechaza. Cumplida: deshabilita en cascada sus planes no
    deshabilitados y setea `deletedAt`.
- `ObraSocialController` `@RequestMapping("/accesmed-api/ObraSocial")`: POST, PATCH `{id}`, GET `{id}`,
  GET (list), DELETE `{id}`.

### C.3 Stack Plan (estados)
- Records `Records/Plan/...`: `AddPlanRequest` (agregar plan a OS existente: `obraSocialId`, codigo,
  nombre — mismo patrón que `CreatePrestacionRequest.especialidadId`),
  `UpdatePlanRequest` (parciales), `DeshabilitarPlanRequest` (motivo opcional),
  responses Create/Get/List + `CambioEstadoPlanResponse` (id, codigo, nombre, `estadoActual`).
- `PlanRepository`, `HistoricoEstadoPlanRepository`.
- `PlanDomainService`: máquina de estados (espejo de Prestacion, `DESHABILITADO` terminal), unicidad
  `(obraSocial,codigo)` / `(obraSocial,nombre)` entre no-deshabilitados vía `estado_actual` (índice).
- `PlanApp`:
  - `addPlan` (a OS activa; nace `NO_PUBLICADO`), `updatePlan`, `publicarPlan`/`despublicarPlan`.
  - `deshabilitarPlan` (restrictiva, terminal): **única** precondición → rechazar si hay `Turno` con
    `obraSocialPaciente.plan = plan` y estado vigente no final (repo read-only sobre `Turno`).
    **Sin** la regla del "último plan". Cumplida: `abrirTramoEstado(..., DESHABILITADO, motivo)`; `TODO`
    cascada de baja de `ObraSocialPlanPrestacion` y `ObraSocialPaciente` (fuera de alcance).
  - `findById`, `findByObraSocial`.
- `PlanController` **propio** `@RequestMapping("/accesmed-api/Plan")` (no anidado bajo ObraSocial):
  POST (add), PATCH `{id}`, PATCH `{id}/Publicar`, `{id}/Despublicar`, `{id}/Deshabilitar`,
  GET `{id}`, GET (por obraSocial).

---

## Parte D — Turno (solo conversión de la representación de estado; NO se construye el módulo)

Se lleva Turno al mismo patrón de enum + `estado_actual`, **sin** construir su ABM/CU (fuera de alcance).
Como hoy `Turno` es entidad + changelog **sin stack** (nadie crea turnos ni transiciones), el cambio es
contenido y compila sin tocar lógica; deja listas las consultas por estado actual que ya usan este plan.

- **`Domain/EstadoTurno.java`**: pasar de **entidad** a **enum**. Set **confirmado** contra
  `modelo_dte_turno.json` (v3, `accesmed/dte/3.0`): intermedios `ESPERA_VALIDACION`, `PENDIENTE`,
  `CONFIRMADO`, `EN_SALA_DE_ESPERA`, `EN_CURSO` (ex *En Transcurso* del v2); finales `CANCELADO`,
  `REPROGRAMADO`, `AUSENTE`, `FINALIZADO`. Los **finales** van como constante de código
  (`Set<EstadoTurno> FINALES` / `esFinal()`), no como dato en BD — es lo que consumen las guardas
  "estado no final".
- **`Domain/HistoricoEstadoTurno.java`**: reemplazar la FK `estadoTurno` por columna enum `estado`
  (`@Enumerated(STRING)`). Se conservan `fechaHoraInicio/Fin` y el índice único parcial de tramo vigente.
- **`Domain/Turno.java`**: agregar `estadoActual` (`@Enumerated(STRING)`, `estado_actual varchar(20)`,
  `idx_turno_estado_actual`). Sin mantenedor hasta el módulo Turno; queda pre-posicionado.
- **Changelogs** (in-place): editar el de `historico_estado_turno` (columna `estado` enum + CHECK, quitar
  FK a `estado_turno`), el de `turno` (agregar `estado_actual` + índice), y **eliminar** el changelog
  de `estado_turno` (`20260731215100-EstadoTurno.xml`, tabla + seed con "En Transcurso"). Ajustar
  `master.xml` (quitar su `<include>`).

---

## Repositorios de solo-lectura nuevos (enforcement real de precondiciones)
- `MedicoRepository`: `existsByEspecialidadIdAndDeletedAtIsNull(UUID)`.
- `TurnoRepository`: contar/exists de turnos con **estado actual no final** por `prestacion` y por
  `obraSocialPaciente.plan` usando `turno.estado_actual NOT IN (<finales>)` (ya sin join al histórico),
  con fecha máxima.
- `AgendaHorariosRepository`: `existsByPrestacionIdAndEstaOcupadaTrueAndFechaFutura(...)`.

Consultas de lectura para hacer valer las bajas restrictivas desde el día uno; NO construyen los módulos
Turno/Agenda/Medico completos.

---

## Actualización de documentación (mismo cambio)
- **Modelar los estados como enum** (no entidad-catálogo) para las **tres** máquinas en
  `Docs/Dominio/dominio-reglas-validaciones.md`, `modelo_acces_med.json` y `modelo_dte_turno.json`:
  quitar `EstadoPrestacion`/`EstadoPlan`/`EstadoTurno` como clases y `HistoricoEstado*` como FK a catálogo
  → columna `estado` enum; agregar la columna derivada `estado_actual` (en prestación, plan y turno) y su
  índice/unicidad.
- **Eliminar la regla "último plan no deshabilitado"** (§5 OS y §6 "Plan — deshabilitar").
- `Docs/Features/Prestaciones.md`: reescribir a v3. Nuevas `Docs/Features/{Especialidades,ObrasSociales}.md`
  vía `feature-documenter`.

---

## Archivos principales a tocar/crear (representativos)
- **Crear**: `Domain/{EstadoPrestacion,EstadoPlan}.java` (enums),
  `Domain/{HistoricoEstadoPrestacion,HistoricoEstadoPlan}.java`; todo
  `Records/{Especialidad,ObraSocial,Plan}/...`; `Controllers/{Especialidad,ObraSocial,Plan}Controller.java`;
  `Application/{Especialidad,ObraSocial,Plan}App.java`; services/mappers/repos de esos módulos; repos
  read-only `{Medico,Turno,AgendaHorarios}Repository.java`; changelogs de histórico.
- **Editar in-place**: `Domain/{Prestacion,Plan,Turno}.java`; `Domain/{EstadoTurno,HistoricoEstadoTurno}.java`
  (EstadoTurno entidad→enum; HistoricoEstadoTurno FK→columna enum); `Records/Prestacion/*` (borrar/renombrar);
  `Controllers/PrestacionController.java`, `Application/PrestacionApp.java`,
  `Services/{DomainServices,QueryServices,Mappers}/Prestacion*.java`, `Repositories/PrestacionRepository.java`;
  changelogs `...-Prestacion.xml`, `...-Plan.xml`, `...-Turno.xml`, `...-HistoricoEstadoTurno.xml`,
  **borrar** `...-EstadoTurno.xml`, `master.xml`; doc v3 + `modelo_acces_med.json` + `modelo_dte_turno.json`.

---

## Verificación (end-to-end)
1. `mvn -q clean compile` — valida generación MapStruct + jpamodelgen y que no queden referencias a
   `fechaHabilitacion`/`habilitada`/records borrados ni a `EstadoTurno`/`EstadoPrestacion`/`EstadoPlan`
   como entidad (ahora enums).
2. Recrear DB dev: `docker compose down -v && docker compose up -d` (changelogs editados in-place → base
   limpia). Arrancar con `SPRING_PROFILES_ACTIVE=dev` → Liquibase aplica todo (sin seeds de estado).
3. Flujos con `curl`/OpenAPI (`/swagger-ui`):
   - **Especialidad**: crear → `DELETE` con una Prestacion no-deshabilitada asociada (rechaza) →
     deshabilitar/reasignar → `DELETE` OK.
   - **Prestacion**: crear (`estadoActual=NO_PUBLICADA`) → `Publicar` → `Despublicar` → modificar
     tolerancias (revalida cadena) → crear otra con el mismo `codigo` (rechaza por unicidad entre
     no-deshabilitadas) → `Deshabilitar` (precondición real; pasa a terminal) → verificar que
     **re-publicar/re-deshabilitar FALLA** y que ahora **sí** se puede reusar el `codigo`.
   - **ObraSocial**: crear con ≥1 plan (`NO_PUBLICADO`) → publicar plan → `deshabilitarPlan` con turno
     vivo apuntando al plan (rechaza) → sin turnos, deshabilitar OK → `DELETE` ObraSocial (restrictiva
     por transitividad + cascada). Confirmar que **ya NO** rige la regla del "último plan".
4. Revisar que cada baja restrictiva loguee `log.warn` en el `throw` del service (no en el handler) y que
   `GlobalExceptionHandler` traduzca a `AccesMedError`.
