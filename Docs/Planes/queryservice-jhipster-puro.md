# Plan — QueryService estilo JHipster puro (Controller → QueryService, sin App en lecturas)

> **Estado al 2026-08-23.** **Fases 1, 2 y 3 ejecutadas y revisadas** — `Especialidad`,
> `Paciente`, `TipoIndicacionPrestacion`, `IndicacionPrestacion`, `Medico`, `AgendaMedico`,
> `AgendaHorariosDia`, `ObraSocial` migradas al patrón `Controller → QueryService (con
> Mapper) → Repository` para lecturas; `App` solo escritura. Fix de `ObraSocialApp`
> aplicado (usa `PlanDomainService` en vez de `PlanQueryService` en los flujos de
> escritura). Cambios en un worktree aislado (`.claude/worktrees/agent-a7774964930cbfb4b`,
> branch `worktree-agent-a7774964930cbfb4b`), compilando limpio, sin commitear. Ejecutado
> por un agente Haiku con supervisión estrecha: en 3 oportunidades reintrodujo inyecciones
> de `DomainService` en un `QueryService` (`Paciente`, `AgendaHorariosDia`, `ObraSocial`)
> pese a instrucción explícita — las tres se revisaron y corrigieron a mano, junto con
> imports desprolijos/duplicados, código muerto (métodos movidos que quedaron sin borrar
> en `PlanQueryService`, un campo `AgendaHorariosDiaQueryService` sin uso en
> `AgendaMedicoApp`) y una regresión funcional real (`AgendaHorariosDiaQueryService
> .findHorariosDisponibles` swalleaba en silencio la ausencia de la fila de configuración
> de `Clinica` con `.orElse(0)` en vez de lanzar `RecursoNoEncontradoException` como el
> `ClinicaDomainService.findClinica()` original). Fases 1-3 mergeadas a `develop`
> (commits `ca13ec8`, `a160582`, `71a2f92`).
>
> **Fase 4 ejecutada y revisada** (`Plan`, `Prestacion`), directo sobre `develop`
> (commit `338b0ae`): no hizo falta el left join ad-hoc mencionado más abajo — se
> resolvió con el mismo patrón ya probado en `ObraSocialQueryService` (`Repository` del
> histórico inyectado directo + `Map<UUID, Estado>` armado en memoria con una sola
> consulta batch), sin necesidad de mecanismo nuevo. Un solo error real en la revisión
> (import duplicado en `PrestacionApp`), corregido a mano; sin violaciones de la regla
> "QueryService nunca inyecta DomainService" esta vez. **Plan completo: las 10 entidades
> migradas al patrón `Controller → QueryService (con Mapper) → Repository` para
> lecturas.**

Cambia el punto de entrada de todas las lecturas del backend: hoy es
`Controller → App → QueryService → Mapper (en el App)`; pasa a ser
`Controller → QueryService (con Mapper adentro) → Repository`, sin el `App` en el medio.
El `App` sigue existiendo, pero queda **solo para los casos de uso de escritura**
(`create`/`update`/`softDelete`/cambios de estado). Es una inversión de la regla fija hoy
en `Docs/ARQUITECTURA.md` línea 55 ("el QueryService se llama desde el App, nunca desde el
Controller"), no una extensión.

Convenciones de código: `.claude/skills/java-springboot-code-style`,
`java-springboot-javadoc`, `java-springboot-logging`. Estructura: `Docs/ARQUITECTURA.md`.

---

## Motivación

El `App` no aporta nada en el camino de lectura actual: cada método `find<Entidad>*`/
`get<Entidad>*` es un passthrough puro (`queryService.find(...)` → `mapper.toResponse(...)`
→ `return`), sin ninguna validación ni orquestación. Es ceremonia. El modelo JHipster
(Criteria + `QueryService` con el `Mapper` adentro, inyectado directo por el `Controller`)
es el mismo mecanismo que ya se vendorizó a mano en `Services/QueryServices/Filtering/` —
solo falta terminar de alinear la capa que lo consume.

## Relevamiento del estado actual (verificado en código)

- **Ningún `QueryService` inyecta un `DomainService` hoy.** Los 10 `QueryService`
  existentes (`Especialidad`, `ObraSocial`, `Paciente`, `TipoIndicacionPrestacion`,
  `IndicacionPrestacion`, `Medico`, `AgendaMedico`, `AgendaHorariosDia`, `Plan`,
  `Prestacion`) inyectan únicamente su propio `Repository`. Esa parte de la premisa
  inicial ("los QueryService usan DomainService") no se confirmó en el código — la única
  mezcla real es la inversa: **`ObraSocialApp` llama a `PlanQueryService` desde dos flujos
  de escritura** (`updateObraSocial`, `softDeleteObraSocial`), ver más abajo.
- Ya usan `Specification` + `JpaSpecificationExecutor` + metamodelo estático
  (`hibernate-processor`), igual que el mecanismo de JHipster. Confirmado además contra un
  `QueryService` real de otro proyecto JHipster que aportó el usuario
  (`NumeroTelefonoComercioQueryService`): mismo patrón base — `Specification` armada en
  `createSpecification`, joins por campo de una asociación mapeada con
  `root.join(Entidad_.asociacion, JoinType.LEFT)`, clase `@Transactional(readOnly = true)`.
  Lo único que falta para igualar ese ejemplo es mover el `Mapper` adentro del
  `QueryService` (hoy vive en el `App`) y que `findByCriteria`/`findOneByCriteria`
  devuelvan `Page<Response>`/`Response` en vez de `Page<Entidad>`/`Entidad`.
- **Ningún `Mapper` depende de `QueryService` ni de `DomainService`** — son `@Mapper`
  puros, sin problema de dirección de dependencia.
- **Controllers nunca llaman a un `QueryService` hoy** (consistente con la regla actual).

---

## Decisiones tomadas

1. **El Controller reemplaza al App en los endpoints de lectura.** Para `GET` (list y
   `/Buscar`), el `Controller` inyecta directo el `<Entidad>QueryService`, que ya devuelve
   el `Response` mapeado. El `App` deja de tener métodos `find*`/`get*` en las entidades
   migradas — solo orquesta escritura.
2. **`ObraSocialApp` deja de usar `PlanQueryService`.** Los dos métodos que hoy llama desde
   flujos de escritura (`findPlanesByObraSocial`, `findPlanesNoDeshabilitadosByObraSocial`)
   se mueven a `PlanDomainService` (devolviendo entidades, no DTOs). Mantiene la regla
   "el QueryService nunca se usa fuera de un `GET`" sin excepciones.
3. **El left join se diseña y se aplica a las 10 entidades existentes en este mismo plan**,
   no como piloto de 1-2 entidades.

---

## Patrón target

### `<Entidad>QueryService`

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EspecialidadQueryService extends AbstractFiltroQueryService<Especialidad, EspecialidadCriteria> {

    private final EspecialidadRepository especialidadRepository;
    private final EspecialidadMapper especialidadMapper;

    @Override
    protected JpaSpecificationExecutor<Especialidad> getRepository() {
        return especialidadRepository;
    }

    public GetEspecialidadResponse findEspecialidadByCriteria(EspecialidadCriteria criteria) {
        Especialidad especialidad = findOneByCriteria(criteria)
                .orElseThrow(() -> { log.warn(...); return new RecursoNoEncontradoException(...); });
        return especialidadMapper.toGetResponse(especialidad);
    }

    public PageResponse<ListEspecialidadResponse> findEspecialidades(EspecialidadCriteria criteria, Pageable pageable) {
        Page<Especialidad> pagina = findByCriteria(criteria, pageable);
        return PageResponse.from(pagina, especialidadMapper::toListResponse);
    }

    @Override
    protected Specification<Especialidad> createSpecification(EspecialidadCriteria criteria) { /* sin cambios */ }
}
```

- `@Transactional(readOnly = true)` pasa de estar en cada método del `App` a la clase del
  `QueryService` (como en el ejemplo JHipster aportado).
- `AbstractFiltroQueryService` **no cambia**: sigue devolviendo `Page<ENTIDAD>`/
  `Optional<ENTIDAD>` — el mapeo a `Response`/`PageResponse` pasa a vivir en el método
  público de cada subclase (`findEspecialidadByCriteria`, `findEspecialidades`), que hoy
  vive casi calcado en el `App`. Es mover el método, no reescribirlo.
- `countByCriteria` del ejemplo JHipster no hace falta: `PageResponse.from(page, ...)` ya
  usa el `totalElements` que trae el `Page` devuelto por `findAll(specification, pageable)`.

### `Controller`

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/accesmed-api/Especialidad")
public class EspecialidadController {

    private final EspecialidadApp especialidadApp;       // create/update/softDelete
    private final EspecialidadQueryService especialidadQueryService; // GET

    @GetMapping("/Especialidad/Buscar")
    public ResponseEntity<GetEspecialidadResponse> findEspecialidadByCriteria(@ParameterObject EspecialidadCriteria criteria) {
        return ResponseEntity.ok(especialidadQueryService.findEspecialidadByCriteria(criteria));
    }

    @GetMapping("/Especialidad")
    public ResponseEntity<PageResponse<ListEspecialidadResponse>> findEspecialidades(
            @ParameterObject EspecialidadCriteria criteria, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(especialidadQueryService.findEspecialidades(criteria, pageable));
    }

    // createEspecialidad / updateEspecialidad / softDeleteEspecialidad siguen delegando en especialidadApp
}
```

### `App`

Pierde `findEspecialidadByCriteria`/`findEspecialidades` (y ya no inyecta
`EspecialidadQueryService` ni `EspecialidadMapper` si ningún método de escritura lo
necesita — varios sí lo siguen necesitando para el `Response` de create/update/delete, eso
no cambia).

---

## Caso especial: estado vigente por lote (`Plan`, `Prestacion`)

`Plan` y `Prestacion` no tienen `deletedAt`: se retiran por estado, y el estado vigente
**no está materializado** en la entidad — es siempre el tramo de `HistoricoEstado*` con
`fechaHoraFin` vacío (regla de dominio firme, ver `CLAUDE.md`). La relación
`Plan ↔ HistoricoEstadoPlan` es unidireccional a propósito (solo el histórico mapea el
`@ManyToOne`), así que hoy `estadoActual` se resuelve con una **consulta batch aparte**
(`HistoricoEstado{Plan,Prestacion}DomainService.getEstadosVigentes(ids)`, un `IN` que
arma un `Map<UUID, Estado>`), llamada desde el `App` después de `findByCriteria`. Ese
método vive en el `DomainService`, así que moverlo tal cual violaría la decisión 2
(el `QueryService` no llama `DomainService`).

**Resolución**: no hace falta la consulta batch aparte. Con Hibernate ORM 7.4 (JPA 3.1+)
existe el join ad-hoc entre entidades sin asociación mapeada
(`From.join(Class, JoinType)` + `Join.on(predicate)`), que arma un
`LEFT JOIN historico_estado_plan h ON h.plan_id = plan.id AND h.fecha_hora_fin IS NULL`
sin necesitar el back-reference en `Plan`. Como el tramo vigente es efectivamente una
relación a-uno (nunca hay dos tramos abiertos), este join no rompe la paginación de
`findByCriteria` (el problema de fan-out con `fetch`+`Page` es solo con colecciones
`*-to-many`).

- `PlanQueryService`/`PrestacionQueryService` agregan el join en `createSpecification` (ya
  existe hoy como subconsulta `EXISTS` para el filtro `estadoActual` — se reemplaza por el
  join real, que sirve tanto para filtrar como para proyectar).
- La lectura pasa de `findByCriteria(criteria, pageable)` simple a una query que también
  selecciona `h.estado` (multiselect/Tuple sobre el join), sin pasar por otro `Service`.
- El resultado se arma con los mismos métodos de `Mapper` que ya existen y ya reciben el
  estado como parámetro aparte (`prestacionMapper.toListResponse(prestacion, estado)`,
  `planMapper.toGetPlanAnidadoResponse(plan, estado)`) — **no cambia la forma del
  `Response` ni la firma del `Mapper`**, solo de dónde sale el `estado` que se le pasa.
- `HistoricoEstado{Plan,Prestacion}DomainService.getEstadosVigentes` **no se toca**: lo
  siguen usando los flujos de escritura que lo necesitan hoy (`ObraSocialApp` vía
  `PlanDomainService` tras la decisión 2, `PrestacionApp.disablePrestacion`, etc.). No se
  duplica lógica de negocio, solo se deja de usar desde el camino de lectura.

## Fix: `ObraSocialApp` deja de inyectar `PlanQueryService`

- `PlanQueryService.findPlanesByObraSocial(UUID)` y
  `PlanQueryService.findPlanesNoDeshabilitadosByObraSocial(UUID)` se mueven tal cual a
  `PlanDomainService` (siguen devolviendo `List<Plan>`, siguen llamando al mismo método de
  `PlanRepository` — `findAllByObraSocialId`/`findAllByObraSocialIdAndEstadoVigenteNot`).
- `ObraSocialApp` cambia la inyección `planQueryService` → ya tiene `planDomainService`
  inyectado (lo usa en `createObraSocial`), solo agrega las dos llamadas ahí.
- `ObraSocialApp.findObraSocialByCriteria`/`findObrasSociales` (los dos métodos de
  lectura reales) se mueven al nuevo `ObraSocialQueryService.findObraSocialByCriteria`/
  `findObrasSociales`, que para armar los planes anidados usa el mecanismo de left join
  descripto arriba contra `PlanRepository` (no contra `PlanDomainService` ni
  `PlanQueryService` — evita QueryService→QueryService cruzando entidades. Si en la
  práctica resulta más simple que `ObraSocialQueryService` sí llame a
  `PlanQueryService.findPlanesByObraSocial` — ahora un método de solo lectura reubicado —
  se decide al implementar; ambas opciones respetan la regla de la decisión 2).

---

## Migración entidad por entidad

| Entidad | `QueryService` | Join especial | `App` pierde | `Controller` gana |
|---|---|---|---|---|
| `Especialidad` | inyecta Mapper, `@Transactional(readOnly=true)` | — | `findEspecialidadByCriteria`, `findEspecialidades` | inyección directa de `EspecialidadQueryService` |
| `ObraSocial` | ídem + planes anidados | left join `Plan` (asociación mapeada, no el caso estado-vigente) | ídem + ya no inyecta `PlanQueryService` | ídem |
| `Paciente` | ídem | — | ídem | ídem |
| `TipoIndicacionPrestacion` | ídem | — | ídem | ídem |
| `IndicacionPrestacion` | ídem | — | ídem | ídem |
| `Medico` | ídem | left join existente (`tieneAgendaVigente`) sin cambios | ídem | ídem |
| `AgendaMedico` | ídem | — | ídem | ídem |
| `AgendaHorariosDia` | ídem, incl. `findHorariosDisponibles` | sin cambios | `findHorariosAgenda`, `findHorariosDisponibles` (en `AgendaMedicoApp`) | ídem |
| `Plan` | ídem | **caso especial estado vigente** (ver arriba) | ídem | ídem |
| `Prestacion` | ídem | **caso especial estado vigente** (ver arriba) | `findPrestaciones` | ídem |

Cada fila implica tocar: `<Entidad>QueryService` (agrega Mapper + métodos públicos que
hoy están en el App), `<Entidad>App` (borra los métodos movidos y su inyección de
`QueryService`/`Mapper` si ya no la necesita), `<Entidad>Controller` (agrega la inyección
de `QueryService` y cambia el cuerpo de los métodos `GET` para llamarlo directo).

---

## Documentación y skills a actualizar en el mismo cambio

- **`Docs/ARQUITECTURA.md`**:
  - Línea 55 (tabla de decisiones): invertir la regla — el `QueryService` se inyecta y se
    llama **desde el Controller para lecturas**; el `App` no participa en el camino de
    lectura.
  - §7 "Filtrado dinámico": agregar el patrón de left join ad-hoc para campos derivados
    de una tabla no asociada (caso `estadoActual` de `Plan`/`Prestacion`), y actualizar el
    ejemplo de `<Entidad>QueryService`/`<Entidad>App`/`Controller` para reflejar que el
    `Mapper` vive en el `QueryService`.
  - "`find<Entidad>ById` se retira..." (línea 1022 en adelante): el punto "`<Entidad>App.
    find<Entidad>ByCriteria(criteria)`: delega en el QueryService y mapea" se retira — ya
    no hay paso intermedio por el App.
- **`.claude/skills/springboot-feature-generator/SKILL.md`**: sección DomainService/App
  (líneas ~109-130) — actualizar quién inyecta el `QueryService` (Controller, no App) y
  quién inyecta el `Mapper` (QueryService, no App) para que las features nuevas generadas
  ya salgan con el patrón nuevo.
- **`.claude/skills/domain-schema-generator/SKILL.md`**: no menciona `QueryService`
  explícitamente — sin cambios necesarios.

---

## Orden de ejecución

**Fase 1 — Patrón base + piloto completo en `Especialidad`.** Entidad más simple (sin
joins, sin anidados). Sirve para validar el patrón de punta a punta: `QueryService` con
Mapper, `Controller` sin `App` en `GET`, actualizar `ARQUITECTURA.md` §5/§7 y el skill
generador en el mismo cambio (no dejar la doc desactualizada ni un ciclo).

**Fase 2 — Resto de entidades simples**: `Paciente`, `TipoIndicacionPrestacion`,
`IndicacionPrestacion`, `Medico`, `AgendaMedico`, `AgendaHorariosDia`. Mismo patrón que la
Fase 1, sin sorpresas de diseño.

**Fase 3 — `ObraSocial`**: incluye el fix de `ObraSocialApp` (mover las dos lecturas a
`PlanDomainService`) porque toca el mismo archivo que la migración de sus propios métodos
de lectura.

**Fase 4 — `Plan` y `Prestacion`**: el caso del left join ad-hoc para estado vigente. Se
deja al final porque es la parte de diseño no probada en este codebase — conviene tener
las Fases 1-3 como referencia estable antes de resolverlo.

## Riesgos a validar durante la ejecución

- Confirmar en código (Hibernate 7.4 / Jakarta Persistence del BOM de Spring Boot 4.1)
  que el ad-hoc join (`From.join(Class, JoinType)` con `Join.on(...)`) está disponible tal
  cual se lo describe acá antes de comprometerse al diseño de la Fase 4 — validar con un
  test de integración chico antes de migrar `Plan`/`Prestacion` enteras.
  Ver `Docs/STACK.md`.
- No hay tests que llamen a los métodos `App.find*`/`get*` que se eliminan (se verificó:
  `src/test/` solo tiene `AccesMedApplicationTests`, `TestcontainersConfiguration` y
  `GeneradorSlotsAgendaTest`, ninguno los toca) — bajo riesgo de romper suite existente,
  pero repetir la verificación por entidad antes de borrar el método.
- Revisar `Docs/FILTRADO-DINAMICO.md` (contrato del front) por si documenta la ruta
  `/Buscar` o el listado como servida "a través del App" de forma explícita — si lo hace,
  actualizar ese documento también (el contrato HTTP no cambia, pero la doc interna sí).
