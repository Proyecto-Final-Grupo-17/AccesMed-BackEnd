---
name: springboot-feature-generator
description: >
  Genera una feature/módulo COMPLETO del backend AccesMed, de controller a repositorio,
  siguiendo la arquitectura por capas (Controller → App → DomainService/QueryService →
  Repository) con DTOs record por endpoint, MapStruct, manejo de errores no chequeado,
  logging y Liquibase. Úsala cuando el usuario pida "crear una entidad/módulo/feature",
  "armar el CRUD de X", "generar los endpoints de X" o describa un flujo nuevo a
  implementar. La skill PREGUNTA el flujo (endpoints, campos, reglas) antes de generar, y
  aplica las skills java-springboot-code-style, java-springboot-javadoc y
  java-springboot-logging en todo el código.
---

# Generador de features — backend AccesMed

> **Preferencias en evolución**: esta skill aplica el estilo, javadoc y logging de las
> otras tres skills de código, que reflejan el gusto personal de Franco y pueden cambiar
> con el tiempo. Si alguna cambia, este generador debe seguir el criterio vigente.

Crea una feature entera y coherente, de la capa de controller a la de repositorio. **No
generes nada hasta haber preguntado y confirmado el flujo.** Aplicá siempre las skills
`java-springboot-code-style`, `java-springboot-javadoc` y `java-springboot-logging`.

## Fase 1 — Preguntar el flujo (obligatorio antes de generar)

Preguntá y esperá respuesta. Si algo se puede inferir del diagrama de clases / `../../../CLAUDE.md`,
proponé un default y pedí confirmación. Cubrí:

1. **Entidad**: nombre (ej. `Medico`). ¿Ya existe la entidad JPA o hay que crearla?
2. **Endpoints del flujo**: ¿cuáles? (crear, actualizar, baja lógica, obtener por id,
   listar, y acciones de negocio propias como `confirmarTurno`). Por cada uno:
   - Verbo HTTP y ruta.
   - Qué campos entran (request) y qué campos NO (inmutables que no viajan).
   - Qué devuelve (response).
3. **Campos de la entidad**: nombre, tipo, obligatoriedad, únicos, relaciones (con
   navegabilidad y multiplicidad), y validaciones de Bean Validation.
4. **Reglas de negocio**: unicidad, existencia de relacionados, estados requeridos,
   validaciones cruzadas. ¿Alguna que deba acumular varios errores (`ValidacionException`)?
5. **Relaciones y navegabilidad**: respetar el diagrama (ej. a `Medico`/`Prestacion` desde
   `Turno` solo vía `MedicoPrestacion`). ¿FK reales o asociación derivada de solo lectura?
6. **Soft delete y auditoría**: por defecto sí (extiende `Auditable`, filtra por
   `fechaHoraBaja IS NULL`). Confirmar.
7. **¿La consume el agente?**: si sí, ¿necesita controller/records propios en `Agente/` que
   reutilicen el mismo App?

Resumí el flujo entendido y pedí un OK antes de escribir código.

## Fase 2 — Generar los archivos (en este orden)

Por cada feature, generá y ubicá según `docs/ARQUITECTURA.md §4` (packages en mayúsculas,
`Controllers`/`Services`/`Repositories` en plural).
Recordá: `Domain` es **solo** la entidad; toda la lógica de esa entidad va en `Services`,
que siempre está subdividido en `Services/DomainServices/`, `Services/QueryServices/` y
`Services/Mappers/` — nunca archivos sueltos directo en `Services/`.

1. **Entidad JPA** — `Domain/<Entidad>.java`
   - Solo el modelo: extiende `Auditable`. Soft delete. Relaciones con la navegabilidad correcta.
   - Constraints a nivel entidad (`@Column(nullable, unique)`, `@Size`, etc.). Sin lógica de negocio acá.
2. **Migración Liquibase** — `resources/db/changelog/changes/YYYYMMDDHHMMSS-<Entidad>.yaml`
   - Un archivo por entidad (no por cambio). Primer changeset con `id:
     YYYYMMDDHHMMSS-created` (misma fecha que el nombre del archivo), crea la tabla con
     PK, FKs, UNIQUE, NOT NULL, CHECK necesarios. Incluir **una vez** en el master
     changelog — los changesets que se agreguen después a este mismo archivo no requieren
     tocar el master de nuevo. Ver vocabulario de ids de changeset en `ARQUITECTURA.md §6`.
3. **Repository** — `Repositories/<Entidad>Repository.java`
   - `extends JpaRepository<...>`. Queries derivadas con filtro de baja
     (`existsByCodigoAndFechaHoraBajaIsNull`, `findByIdAndFechaHoraBajaIsNull`).
4. **Records** — `Records/Request/` y `Records/Response/`
   - Uno por endpoint: `<Accion><Entidad>Request` / `<Accion><Entidad>Response`.
   - Bean Validation en el request, con `message` en español. Inmutables fuera del request.
5. **Mapper (MapStruct)** — `Services/Mappers/<Entidad>Mapper.java`
   - `toEntity`, `toResponse`, y `update(entidad, request)` para no pisar campos inmutables.
6. **DomainService** — `Services/DomainServices/<Entidad>DomainService.java`
   - `@Slf4j`. Persistencia y lógica de la entidad: `save<Entidad>`, `validate...`, `find<Entidad>ById`.
   - Lanza `RecursoNoEncontradoException` / `ReglaNegocioException` (de `Services/Errors/`,
     **no** crear una excepción nueva por entidad) según corresponda, pasando `getClass()`
     como `origen`. **Loguear con `log.warn` justo antes de cada `throw`** (ver skill
     `java-springboot-logging`); `log.debug` para detalle interno no crítico.
   - (Si hay lecturas: `<Entidad>QueryService` en `Services/QueryServices/`. Se llama
     **siempre desde el App**, nunca directo desde el Controller — ni un `getById` simple.)
7. **App** — `Application/<Entidad>App.java`
   - `@Service`, `@Slf4j`, `@Transactional`. `log.info` al iniciar cada método (orquestación
     del caso de uso). Un método por endpoint (incluidos los de solo lectura: el App
     delega en el `QueryService` y mapea con el `Mapper` antes de devolver el response).
     Orquesta: valida negocio (acumulando en `ValidacionException` cuando aplique, con
     `getClass()` como `origen` y `log.warn` antes del throw), mapea, delega en el domain
     service o query service.
8. **Controller** — `Controllers/<Entidad>Controller.java`
   - `@RestController`, `@Slf4j`, `@RequestMapping("/api/<entidades>")`. `log.info` al
     recibir cada request (sin datos sensibles si la entidad los tiene, ej. `Paciente`).
     `@Valid` en el body. Solo delega. Status coherentes (201 en create, 200 en update/get, 204 en baja).
9. **(Opcional) Agente** — `Agente/Controllers/` + `Agente/Records/`
   - Solo si la feature la consume el agente. Reutiliza el mismo App; no duplica lógica.
10. **Tests** — espejo en `src/test/...`
    - Unit del App (mock de services) y del DomainService; test de integración del
      controller (MockMvc) cubriendo el happy path y al menos un error (`AccesMedError`).

## Fase 3 — Verificar

- [ ] Compila (`./mvnw compile`).
- [ ] La migración Liquibase corre y crea la tabla con sus constraints.
- [ ] Swagger muestra los endpoints con sus request/response.
- [ ] Happy path devuelve el status y response correctos; un error devuelve `AccesMedError`.
- [ ] Code-style y Javadoc aplicados (ver ambas skills).
- [ ] Soft delete, `Auditable` y navegabilidad respetados.
- [ ] Ninguna excepción nueva se creó "por entidad" (`<Entidad>ServiceError`); se reutilizó
      la jerarquía fija de `Services/Errors/`.
- [ ] Logging aplicado según `java-springboot-logging`: `info` en Controller/App,
      `debug` en DomainService/QueryService, `warn` en el Service justo antes de cada
      `throw` (no duplicado en el handler), sin datos sensibles en `info`.
- [ ] El archivo de changelog sigue la convención `YYYYMMDDHHMMSS-<Entidad>.yaml` con un
      solo `include` en el master (ver `ARQUITECTURA.md §6`).
- [ ] Clases organizadas con `//region`/`//endregion` (Dependencias, Métodos, Métodos
      auxiliares privados / Atributos, Relaciones en entidades); comentarios paso a paso
      solo donde explican el *porqué*, distribuidos entre App y DomainService según
      a quién le corresponde el paso (ver `java-springboot-code-style` §10).

## Plantilla de referencia (Prestación) — patrón a seguir

Incluye `//region`/`//endregion` (banner de ancho fijo) y comentarios paso a paso que
explican el *porqué*, según la skill `java-springboot-code-style` §10.

```java
// Controllers/PrestacionController.java
@Slf4j
@RestController
@RequestMapping("/api/prestaciones")
@RequiredArgsConstructor
public class PrestacionController {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionApp prestacionApp;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una prestación nueva.
     *
     * @param crearPrestacionRequest {@code CrearPrestacionRequest} datos de la prestación
     * @return {@code ResponseEntity<CrearPrestacionResponse>} la prestación creada (201)
     */
    @PostMapping
    public ResponseEntity<CrearPrestacionResponse> createPrestacion(
            @Valid @RequestBody CrearPrestacionRequest crearPrestacionRequest) {

        log.info("Solicitud recibida: crear prestación código={}", crearPrestacionRequest.codigo());

        CrearPrestacionResponse response = prestacionApp.createPrestacion(crearPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    //endregion
}

// Application/PrestacionApp.java
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionApp {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionDomainService prestacionDomainService;
    private final PrestacionMapper prestacionMapper;

    //endregion

    //region ========== Métodos ==========

    /**
     * Crea una prestación validando que su código no esté repetido.
     *
     * @param crearPrestacionRequest {@code CrearPrestacionRequest} datos de la prestación
     * @return {@code CrearPrestacionResponse} la prestación creada, con su id
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el código ya existe
     */
    @Transactional
    public CrearPrestacionResponse createPrestacion(CrearPrestacionRequest crearPrestacionRequest) {

        log.info("Creación de prestación iniciada: código={}", crearPrestacionRequest.codigo());

        //Validar que el código no esté repetido entre las prestaciones activas
        prestacionDomainService.validateCodigoPrestacionIsUnique(crearPrestacionRequest.codigo());

        //Mapear el request a entidad
        Prestacion prestacionNueva = prestacionMapper.toEntity(crearPrestacionRequest);

        //Persistir la prestación
        Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);

        //Devolver el response mapeado
        return prestacionMapper.toCrearResponse(prestacionGuardada);

    }

    //endregion
}

// Services/DomainServices/PrestacionDomainService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final PrestacionRepository prestacionRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Valida que el código de prestación sea único entre las prestaciones activas.
     *
     * @param codigo {@code String} código a verificar
     * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación activa con ese código
     */
    public void validateCodigoPrestacionIsUnique(String codigo) {

        //Verificar que no exista otra prestación activa con el mismo código
        if (prestacionRepository.existsByCodigoAndFechaHoraBajaIsNull(codigo)) {
            log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
            throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
                    "Ya existe una prestación activa con el código " + codigo);
        }

    }

    //endregion
}
```

Seguí este patrón para cada entidad. Nombres, capas y errores según las skills de estilo,
javadoc y logging. Las secciones (`//region`) y los pasos comentados dentro del método van
en toda clase de `Controllers`, `Application` y `Services` — en entidades (`Domain`) las
secciones son `Atributos`/`Relaciones` en vez de `Dependencias`/`Métodos`.
