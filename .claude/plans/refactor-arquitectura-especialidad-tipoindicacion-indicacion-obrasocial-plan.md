# Replicar el patrón de Prestación: skills, Especialidad, TipoIndicacionPrestacion, IndicacionPrestacion (create en lote), ObraSocial y Plan

## Contexto

El refactor de arquitectura hecho sobre `Prestacion` (domain services que solo tocan su
propio repositorio, `@Transactional` en el App, `find<Entidad>ActivaById` para flujos de
mutación, comentarios paso a paso, retorno con variable nombrada) dejó un patrón validado
que hay que:

1. **Fijar en las skills** para que se aplique solo de acá en adelante, no de memoria.
2. **Replicar en el resto de las entidades**, que hoy tienen exactamente las mismas
   violaciones que tenía `Prestacion` antes del refactor (confirmado por auditoría).

Además, dos pedidos puntuales de negocio:
- `IndicacionPrestacion`: el create standalone pasa de un item a una lista (crear varias
  indicaciones juntas). El softDelete sigue siendo de a una.
- `Especialidad` y `TipoIndicacionPrestacion`: solo arquitectura + comentarios, sin tocar
  reglas de negocio.
- `ObraSocial` y `Plan`: mismo patrón completo que `Prestacion` (incluye separar el
  histórico de estado de `Plan` en su propio domain service, igual que se hizo con
  `HistoricoEstadoPrestacionDomainService`).

## Decisión confirmada con el usuario

La doc de creación de `IndicacionPrestacion` menciona una validación de "prestación en
borrador" (`PRESTACION_INDICACION_HABILITADA`) que **no existe en el código actual**. No
se implementa ahora — se corrige la documentación para reflejar el comportamiento real.

---

## 1. Skills — fijar los patrones aprendidos

### `.claude/skills/java-springboot-code-style/SKILL.md`

- **§5 (Estructura por capas)**: agregar una subsección explícita de arquitectura:
  - Un `DomainService` **nunca** inyecta el repositorio de otra entidad ni llama a otro
    `DomainService`. Si necesita datos de una entidad relacionada por asociación JPA, la
    navega (ej. `historico.getPrestacion()`); si necesita coordinar varias entidades
    (existencia + regla de negocio de otra entidad), esa orquestación vive en el App.
  - `@Transactional` vive en el **App** (es el límite real de "1 caso de uso = 1
    transacción" que ya exige `CLAUDE.md`). Los `DomainService` no llevan `@Transactional`
    propio salvo que el método sea de solo lectura standalone o necesite abrir su propia
    transacción independiente.
  - Patrón `find<Entidad>Activa(o)ById`: cuando un flujo de **mutación** (update,
    transición de estado) necesita la entidad, el `find` usado debe filtrar su estado
    terminal / soft-delete (`findByIdAndEstadoActualNot(...)` o
    `findByIdAndDeletedAtIsNull(...)`), no un `findById` a secas. El `findById` simple
    sin filtro se reserva para lecturas que sí necesitan ver el registro en cualquier
    estado.
- **§10.3 (Comentarios paso a paso)**: aclarar que los métodos de **un solo paso real**
  (`save<Entidad>`, `find<Entidad>ById`, un `validate*` con un único `if`) **no** llevan
  comentario — el nombre del método ya lo dice. Los comentarios van en métodos con
  **varios pasos reales** (ej. `validateToleranciasPrestacion`, cualquier método del App).
- **Nueva regla — retorno con variable nombrada**: aunque MapStruct/el compilador no lo
  exijan, cada método no trivial de Controller/App/DomainService asigna el valor a
  devolver a una variable con nombre descriptivo antes del `return`, en vez de retornar la
  expresión directa — aunque parezca redundante. Ejemplo:
  ```java
  CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionMapper.toCambioEstadoResponse(prestacionPublicada);
  return cambioEstadoPrestacionResponse;
  ```
  en vez de `return prestacionMapper.toCambioEstadoResponse(prestacionPublicada);`. Esto
  reemplaza el ejemplo actual de la skill (que hoy muestra retorno directo) — hay que
  actualizar el bloque de código de §10.3 para que coincida.
- **Checklist final**: agregar los dos ítems nuevos (ningún domain-to-domain / repo ajeno;
  `@Transactional` solo en App; retorno con variable nombrada).

### `.claude/skills/springboot-feature-generator/SKILL.md`

- **Fase 2, paso 5 (DomainService)**: agregar la misma regla de "solo su propio
  repositorio, nunca otro DomainService" con referencia a §5 de `java-springboot-code-style`.
- **Fase 2, paso 6 (App)**: aclarar que `@Transactional` vive acá.
- **"Plantilla de referencia"**: actualizar el bloque de código de `PrestacionApp` para que
  sus `return` usen variable nombrada (hoy el ejemplo de la skill retorna directo,
  inconsistente con lo que se pide arriba).
- **Fase 3 (Verificar)**: agregar ítems al checklist: "Ningún DomainService inyecta el
  repositorio de otra entidad ni llama a otro DomainService" y "`@Transactional` solo en
  el App".

---

## 2. Especialidad y TipoIndicacionPrestacion — solo arquitectura + comentarios

### Especialidad

- **`EspecialidadDomainService`**: hoy inyecta `MedicoRepository` y `PrestacionRepository`
  (ajenos) para `validateSinUsoVigente(especialidad)`. Se elimina ese método y esas dos
  dependencias.
- **Nuevo `MedicoDomainService`** (mínimo, de solo lectura, mismo patrón que
  `TurnoDomainService`/`AgendaHorariosDomainService`): envuelve `MedicoRepository` con
  `validateSinMedicosActivos(UUID especialidadId)` (usa
  `medicoRepository.existsByEspecialidadIdAndDeletedAtIsNull(...)`, ya existente).
- **`PrestacionDomainService`**: se agrega `validateSinPrestacionesActivas(UUID especialidadId)`
  usando `prestacionRepository.existsByEspecialidadIdAndEstadoActualNot(...)` (ya existe
  en `PrestacionRepository`).
- **`EspecialidadApp.softDeleteEspecialidad`**: pasa a llamar
  `medicoDomainService.validateSinMedicosActivos(id)` y
  `prestacionDomainService.validateSinPrestacionesActivas(id)` en vez de
  `especialidadDomainService.validateSinUsoVigente(especialidad)`.
- Renombrar `findActiveEspecialidadById` → `findEspecialidadActivaById` (consistencia de
  idioma con `findPrestacionActivaById`) y actualizar su único caller
  (`PrestacionApp.createPrestacion`).
- Agregar comentarios paso a paso en `EspecialidadController`, `EspecialidadApp` (ninguno
  hoy) y `EspecialidadDomainService`, y aplicar retorno con variable nombrada en App/Controller.

### TipoIndicacionPrestacion

- **`TipoIndicacionPrestacionDomainService`**: hoy inyecta `IndicacionPrestacionRepository`
  (ajeno) para `validateTipoIndicacionPrestacionIsNotInUse`. Se elimina esa dependencia y
  el método pasa a vivir apoyado en un nuevo método de `IndicacionPrestacionDomainService`:
  `existsIndicacionesActivasByTipo(UUID tipoIndicacionPrestacionId)` (envuelve
  `indicacionPrestacionRepository.existsByTipoIndicacionPrestacionIdAndDeletedAtIsNull`,
  ya existente).
- **`TipoIndicacionPrestacionApp.softDeleteTipoIndicacionPrestacion`**: pasa a llamar
  `indicacionPrestacionDomainService.existsIndicacionesActivasByTipo(id)` y lanzar el
  `ReglaNegocioException` ahí mismo (en el App, ya que es una validación cruzada entre dos
  entidades) en vez de delegar el throw al DomainService de Tipo.
- Renombrar `findTipoIndicacionPrestacionById` → `findTipoIndicacionPrestacionActivoById`
  (ya filtra `deletedAt`, el nombre no lo comunicaba) y actualizar sus dos callers
  (`PrestacionApp`, `IndicacionPrestacionApp`).
- Agregar comentarios paso a paso en `TipoIndicacionPrestacionController` (casi ninguno) y
  `TipoIndicacionPrestacionDomainService` (ninguno). El App ya los tiene — solo revisar
  que use retorno con variable nombrada.

---

## 3. IndicacionPrestacion — create standalone pasa a lote

- **Nuevo record** `Records/IndicacionPrestacion/Request/CreateIndicacionesPrestacionRequest.java`:
  `UUID prestacionId` + `List<CreateIndicacionPrestacionAnidadaRequest> indicaciones`
  (`@NotEmpty`, `@Valid` en la lista) — **reutiliza** el record anidado que ya existe en
  `Records/Prestacion/Request/CreateIndicacionPrestacionAnidadaRequest.java` para cada
  item, igual que hace `CreatePrestacionRequest`. Evita duplicar un DTO idéntico.
- **Nuevo record** `Records/IndicacionPrestacion/Response/CreateIndicacionesPrestacionResponse.java`:
  `List<CreateIndicacionPrestacionResponse>` (reusa el response singular ya existente).
- **Se eliminan** (quedan sin uso): `CreateIndicacionPrestacionRequest.java` y el método
  `IndicacionPrestacionMapper.toEntity(CreateIndicacionPrestacionRequest)`.
- **`IndicacionPrestacionMapper`**: agregar
  `List<CreateIndicacionPrestacionResponse> toCreateResponses(List<IndicacionPrestacion>)`
  (MapStruct la autogenera reusando `toCreateResponse`). El método
  `toEntities(List<CreateIndicacionPrestacionAnidadaRequest>)` **ya existe** (se agregó en
  el refactor de Prestacion) y se reutiliza tal cual.
- **`IndicacionPrestacionApp`**: `createIndicacionPrestacion` → `createIndicacionesPrestacion(CreateIndicacionesPrestacionRequest)`,
  siguiendo 1:1 el patrón ya probado en `PrestacionApp.createPrestacion`:
  1. Buscar la prestación (`prestacionDomainService.findPrestacionById`).
  2. Mapear la lista con `indicacionPrestacionMapper.toEntities(request.indicaciones())`.
  3. Resolver por índice el `tipoIndicacionPrestacion` de cada item (loop, como en
     `PrestacionApp`) y setear `prestacion`/`tipoIndicacionPrestacion`.
  4. Guardar con `indicacionPrestacionDomainService.saveIndicacionesPrestacion(...)` (ya
     acepta lista).
  5. Mapear con `toCreateResponses`, envolver en `CreateIndicacionesPrestacionResponse`,
     retornar con variable nombrada.
- **`IndicacionPrestacionController`**: mismo endpoint (`POST /IndicacionPrestacion/IndicacionPrestacion`),
  cambia el tipo de request/response al de lote.
- **`softDeleteIndicacionPrestacion`**: sin cambios (sigue de a una).
- **Doc**: corregir `Docs/Features/Prestaciones.md` (sección "Crear indicación de
  prestación") — nuevo shape de request/response en lote, y sacar la mención a
  `PRESTACION_INDICACION_HABILITADA`/"debe estar en borrador" (no implementado). Reflejar
  también en `Docs/Dominio/modelo_acces_med.json` si ese bloque la menciona.

---

## 4. ObraSocial — solo comentarios

Ya está arquitectónicamente limpia (`ObraSocialDomainService` solo inyecta
`ObraSocialRepository`; `@Transactional` ya solo en el App; el Controller ya valida
id-ruta-vs-body él mismo). Se agregan comentarios paso a paso en
`ObraSocialController`, `ObraSocialApp` y `ObraSocialDomainService`, y se aplica retorno
con variable nombrada donde falte. `ObraSocialApp` se actualiza además por los cambios de
`Plan` del punto siguiente (deja de llamar a `planDomainService.validateSinUsoVigente` y
`planDomainService.abrirTramoEstado`).

---

## 5. Plan — mismo refactor completo que Prestacion (histórico de estado separado)

`PlanDomainService` hoy mezcla `Plan` + `HistoricoEstadoPlan` (inyecta
`HistoricoEstadoPlanRepository` y `TurnoRepository` directamente) — es exactamente el
estado de `PrestacionDomainService` antes del refactor. Se aplica el mismo split:

- **Nuevo `HistoricoEstadoPlanDomainService`** (espejo de
  `HistoricoEstadoPrestacionDomainService`): mueve `abrirTramoInicial` →
  `setInitialEstadoForNewPlan(Plan)` y `abrirTramoEstado` → `changeEstadoPlan(UUID planId,
  EstadoPlan estadoNuevo, String motivo)`. Este último ya no recibe la entidad `Plan`:
  busca el tramo vigente no `DESHABILITADO` por `planId` en su propio repo (nuevo método
  `HistoricoEstadoPlanRepository.findByPlanIdAndFechaHoraFinIsNullAndEstadoNot(UUID, EstadoPlan)`,
  igual que se hizo para Prestación), obtiene el `Plan` vía `tramo.getPlan()`, valida la
  transición y persiste — sin llamar a `PlanDomainService`.
- **`PlanDomainService`**: se queda solo con `PlanRepository`. Se eliminan
  `abrirTramoInicial`, `abrirTramoEstado`, `validateSinUsoVigente`, `validatePuedePublicar`,
  `validatePuedeDespublicar`, `validatePuedeDeshabilitar` (dead code tras el split — la
  validación de transición ya la hace `changeEstadoPlan`). Se agrega
  `findPlanActivoById(UUID id)` (usa nuevo `PlanRepository.findByIdAndEstadoActualNot(id, DESHABILITADO)`,
  mismo patrón que `findPrestacionActivaById`).
- **`TurnoDomainService`**: se agrega `validateSinTurnosVivosDePlan(UUID planId)`
  (reutiliza `turnoRepository.countByObraSocialPaciente_Plan_IdAndEstadoActualNotIn` /
  `findMaxFechaHoraInicioByPlanIdAndEstadoActualNotIn`, ambos ya existentes en
  `TurnoRepository`).
- **`PlanApp`**: inyecta `HistoricoEstadoPlanDomainService` y `TurnoDomainService`.
  - `addPlan`: `historicoEstadoPlanDomainService.setInitialEstadoForNewPlan(planGuardado)`.
  - `updatePlan`: usa `planDomainService.findPlanActivoById(id)` en vez de `findPlanById`
    (mismo criterio que se aplicó a `Prestacion`: `DESHABILITADO` es terminal, no se edita
    un plan deshabilitado). **Nota**: a diferencia de Prestación, no encontré una regla
    documentada explícita para Plan sobre esto — se aplica por consistencia/analogía y se
    deja documentado explícitamente en el mismo cambio.
  - `publicarPlan` / `despublicarPlan`: verifican existencia (`planDomainService.findPlanById(id)`)
    y delegan en `historicoEstadoPlanDomainService.changeEstadoPlan(id, ESTADO, null)`.
  - `deshabilitarPlan`: agrega `turnoDomainService.validateSinTurnosVivosDePlan(id)` antes
    de `changeEstadoPlan(id, DESHABILITADO, motivo)`.
  - Comentarios paso a paso + retorno con variable nombrada en los 6 métodos.
- **`ObraSocialApp.softDeleteObraSocial`**: el loop pasa de
  `planDomainService.validateSinUsoVigente(plan)` / `planDomainService.abrirTramoEstado(plan, DESHABILITADO, motivo)`
  a `turnoDomainService.validateSinTurnosVivosDePlan(plan.getId())` /
  `historicoEstadoPlanDomainService.changeEstadoPlan(plan.getId(), DESHABILITADO, "Baja de obra social")`.
  Inyecta `TurnoDomainService` y `HistoricoEstadoPlanDomainService` (además de
  `PlanDomainService`, que sigue haciendo falta para `validateCodigoPlanIsUnique`/`validateNombrePlanIsUnique`/`savePlan`
  en la creación anidada de planes).
- **`PlanController`**: agregar comentarios paso a paso (hoy no tiene).
- **Doc**: `Docs/Features/ObrasSociales.md` — documentar explícitamente que `updatePlan`
  ya no aplica sobre un plan `DESHABILITADO` (mismo tratamiento que se le dio a la doc de
  Prestación).

---

## Verificación

- `mvn compile` (con JDK 25: `JAVA_HOME="/c/Program Files/Java/jdk-25.0.4"`) sin errores
  después de cada bloque de cambios (skills no compilan, son `.md`).
- Revisar a mano: deshabilitar un Plan ya deshabilitado devuelve `PLAN_YA_DESHABILITADO`
  sin tocar `PlanDomainService.savePlan` directamente desde el histórico (dirty checking).
- Baja de ObraSocial con un plan que tiene turnos vivos: rechaza toda la operación sin
  deshabilitar ningún plan (transaccional).
- Crear 3 indicaciones en un solo `POST /IndicacionPrestacion/IndicacionPrestacion` con la
  lista nueva: las 3 quedan persistidas y asociadas a la misma prestación.
- Actualizar Especialidad/TipoIndicacionPrestacion siguen funcionando igual (sin cambio de
  contrato, solo de implementación interna).
