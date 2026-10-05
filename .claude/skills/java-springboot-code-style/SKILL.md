---
name: java-springboot-code-style
description: >
  Convenciones de estilo de código para el backend Java 25 + Spring Boot 4 de AccesMed
  (y aplicable a cualquier proyecto Java/Spring Boot con la misma filosofía). Úsala
  SIEMPRE que escribas, generes o revises clases Java del backend: controllers, casos de
  uso, services, repositories, entidades JPA, DTOs (records) o mappers. Cubre nomenclatura
  (verbo en inglés + concepto en español), nombres de parámetros y variables, estructura
  por capas, inyección de dependencias, uso de records para DTOs y manejo de excepciones.
  Actívala antes de crear código nuevo o al hacer code review de estilo.
---

# Estilo de código — Java + Spring Boot (AccesMed)

Aplicá estas reglas al escribir o revisar cualquier clase Java del backend. Si generás
código, ya tiene que salir con este estilo; no lo dejes para un paso posterior.

> **Preferencias en evolución**: estas convenciones reflejan el gusto personal de Franco y
> pueden cambiar con el tiempo. Si pide un ajuste de estilo, actualizá esta skill (no solo
> el código nuevo) para que quede como criterio vigente.

## 1. Nomenclatura de métodos: verbo EN inglés + concepto EN español

El verbo de la acción va en **inglés**; el sustantivo del negocio va en **español**.

```java
createMedico(...)                         // ✅
saveMedico(...)                           // ✅
findTurnoById(...)                        // ✅
updatePrestacion(...)                     // ✅
validateCodigoPrestacionIsUnique(...)     // ✅
confirmarTurno(...)                       // ❌ verbo en español
createDoctor(...)                         // ❌ negocio en inglés
```

Verbos habituales: `create`, `save`, `update`, `delete` (soft), `find`, `get`, `list`,
`exists`, `validate`, `map`, `build`, `check`.

**Actualizaciones: tres verbos distintos, no uno solo reciclado.** `update` a secas no
alcanza para distinguir un reemplazo completo de uno parcial:

```java
fullUpdateMedico(...)                 // PUT: reemplaza el recurso completo
partialUpdateMedico(...)              // PATCH genérico: subconjunto arbitrario de campos
updateToleranciasPrestacion(...)      // PATCH de un grupo de campos específico y conocido de antemano
```

Ver §5.1 para el detalle de ruta/verbo HTTP de cada uno y §5.2 de `ARQUITECTURA.md` para la
semántica de `null` en cada caso (cuándo significa "no lo toques" y cuándo "vacíalo").

## 2. Nombres de parámetros: camelCase del tipo

Un parámetro se llama como su tipo en camelCase. Que quede obvio qué es cada cosa.

```java
public CreateMedicoResponse createMedico(CreateMedicoRequest createMedicoRequest) { ... }
public void savePrestacion(Prestacion prestacion) { ... }
public TurnoResponse confirmTurno(ConfirmTurnoRequest confirmTurnoRequest) { ... }
```

## 3. Variables de entidad en memoria: español descriptivo del estado

Cuando traés o transformás una entidad, el nombre describe **en qué estado está**.

```java
Medico medicoExistente   = medicoRepository.findById(id)...   // recién leída de BD
Medico medicoActualizado = medicoMapper.update(medicoExistente, request);
Medico medicoGuardado    = medicoDomainService.saveMedico(medicoActualizado);
Turno  turnoConfirmado   = ...;
Prestacion prestacionNueva = prestacionMapper.toEntity(request);
```

Nada de `m`, `x`, `tmp`, `entity1`. El nombre cuenta la historia del flujo.

## 4. Records = `record`, uno por endpoint, agrupados por entidad

- Un `record` por endpoint, en `Records/<Entidad>/Request/` o `Records/<Entidad>/Response/`
  — **primero la entidad, después el sentido**. Los campos inmutables **no aparecen** en el
  request.

```
Records/
├── Prestacion/
│   ├── Request/    CreatePrestacionRequest, UpdatePrestacionRequest
│   └── Response/   CreatePrestacionResponse, UpdatePrestacionResponse
└── Turno/
    ├── Request/    CreateTurnoRequest, ConfirmTurnoRequest
    └── Response/   CreateTurnoResponse, ConfirmTurnoResponse
```

- Nombre: `<Accion><Entidad>Request` / `<Accion><Entidad>Response`. Sin sufijo `Record`/`DTO`.
- La validación básica va con Bean Validation en el propio record.

```java
public record CreatePrestacionRequest(
        @NotBlank(message = "El código es obligatorio.")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,
        @NotNull @Positive(message = "La duración debe ser mayor a cero.")
        Integer duracionTurnoMinutos
) {}

public record CreatePrestacionResponse(
        Long id,
        String codigo,
        String nombre,
        Integer duracionTurnoMinutos
) {}
```

En un update, el `id` **sí viaja** (además de ir en la ruta — ver §5.1) pero un campo
inmutable (ej. `codigo`) no se incluye:

```java
public record UpdatePrestacionRequest(
        @NotNull Long id,
        @NotBlank String nombre,
        @NotNull @Positive Integer duracionTurnoMinutos
        // sin `codigo`: es inmutable, no se manda
) {}
```

## 5. Estructura por capas — cada clase en su lugar

`Controller → App (package Application) → DomainService/QueryService (package Services) → Repository`.

Los packages van en **mayúsculas**, y **`Controllers`, `Services`, `Repositories` en
plural** (`Domain`, `Application`, `Records` quedan en singular). No hay carpeta `Shared`:
cada cosa transversal vive en el rol que la produce o la consume (ver punto 7 para errores).

`Domain` contiene **solo entidades JPA** (incluida su base `Auditable`); la lógica de una
entidad y todo su soporte viven en `Services`, que a su vez se subdivide **siempre** en
cinco subcarpetas fijas:

```
Services/
├── DomainServices/   <Entidad>DomainService
├── QueryServices/    <Entidad>QueryService
├── Mappers/          <Entidad>Mapper (MapStruct)
├── Errors/           AccesMedException y sus subclases (ver punto 7)
└── Utils/            helpers genéricos sin dueño de entidad
```

- **Controller** (`<Entidad>Controller`, `@RestController`, package `Controllers`): recibe, `@Valid`, delega. **Sin lógica.**
- **App** (`<Entidad>App`, `@Service`, package `Application`): orquesta el flujo,
  valida reglas de negocio llamando a services, `@Transactional`.
- **DomainService** (`<Entidad>DomainService`, package `Services/DomainServices`): lógica y persistencia de una entidad.
- **QueryService** (`<Entidad>QueryService`, package `Services/QueryServices`): consultas de lectura de una entidad.
  Se inyecta y se llama **siempre desde el App, nunca desde el Controller** — ni para un
  `getById` simple. El Controller no conoce `Services` en absoluto.
- **Mapper** (`<Entidad>Mapper`, package `Services/Mappers`): MapStruct, sin lógica de negocio, solo conversión Record↔entidad.
- **Repository** (`<Entidad>Repository`, package `Repositories`): Spring Data JPA.
- **Entidad** (`<Entidad>`, package `Domain`): solo el modelo JPA, sin lógica de negocio.

`Services/Utils` sigue el mismo criterio que `Services/Mappers`: un helper genérico
(formateo de fechas, generación de códigos, etc.) es un componente de soporte que solo
usan los services y apps, no un cajón transversal aparte.

`Controllers` también tiene **dos** subcarpetas fijas:

```
Controllers/
├── <Entidad>Controller.java
├── Errors/             GlobalExceptionHandler (@RestControllerAdvice) + AccesMedError
└── ControllersConfig/  config propia de la capa web: OpenApiConfig, CORS, interceptores, MVC
```

`Errors/` vive ahí porque el handler es, en esencia, parte de la capa de controller.
`ControllersConfig/` porque configura la superficie HTTP. La configuración **transversal de
infraestructura que no es mecanismo de seguridad** (`JpaAuditingConfig`, `SchedulingConfig`,
cache, async) queda en `Config/` en la raíz: no pertenece a la capa web. El filter chain de
seguridad (`SecurityFilterChainConfig`) vive en `Security/Config/`, junto al resto del
mecanismo de auth.

Nota: el sufijo de la clase (`Controller`, `Repository`, `DomainService`) queda en
singular; solo el nombre de la **carpeta** va en plural.

El controller nunca llama al repository ni al domain service directo: pasa por el App.

### 5.0 Un DomainService solo toca su propio repositorio

Un `DomainService` **nunca** inyecta el repositorio de otra entidad ni llama a otro
`DomainService`. Si necesita datos de una entidad relacionada por asociación JPA, la
navega en vez de pedirla (ej. `historicoEstadoPrestacionVigente.getPrestacion()`); si
necesita coordinar varias entidades (existencia de una, regla de negocio de otra), esa
orquestación vive en el **App**, que sí puede inyectar varios `DomainService`.

```java
// ❌ PrestacionDomainService inyectando el repo de otra entidad
private final PrestacionRepository prestacionRepository;
private final TurnoRepository turnoRepository;   // ajeno — viola la regla

// ✅ cada DomainService con lo suyo; el App orquesta
// PrestacionApp.java
private final PrestacionDomainService prestacionDomainService;
private final TurnoDomainService turnoDomainService;   // domain service propio de Turno
...
turnoDomainService.validateSinTurnosVivos(id);
```

`@Transactional` vive en el **App** — es el límite real de "1 caso de uso = 1
transacción" (ver `CLAUDE.md`). Los `DomainService` no llevan `@Transactional` propio,
salvo que el método sea de solo lectura standalone o necesite abrir su propia transacción
independiente de la del caso de uso que lo llama.

### 5.0.1 `find<Entidad>Activa(o)ById`: buscar y validar el estado en un solo paso

Cuando un flujo de **mutación** (update, transición de estado) necesita la entidad, el
`find` que usa debe filtrar su estado terminal o soft-delete, no un `findById` a secas:

```java
// Repository
Optional<Prestacion> findByIdAndEstadoActualNot(UUID id, EstadoPrestacion estadoActual);

// DomainService
public Prestacion findPrestacionActivaById(UUID id) {
    return prestacionRepository.findByIdAndEstadoActualNot(id, EstadoPrestacion.DESHABILITADA)
            .orElseThrow(() -> new RecursoNoEncontradoException(getClass(), "PRESTACION_NO_ENCONTRADA",
                    "No existe una prestación activa con el id " + id));
}
```

El `find<Entidad>ById` simple sin filtro se reserva para lecturas que sí necesitan ver el
registro en cualquier estado (ej. un `GET` de detalle que muestra hasta las deshabilitadas).

```java
@RestController
@RequestMapping("/accesmed-api/Prestacion")
@RequiredArgsConstructor
public class PrestacionController {

    private final PrestacionApp prestacionApp;

    @PostMapping("/Prestacion")
    public ResponseEntity<CreatePrestacionResponse> createPrestacion(
            @Valid @RequestBody CreatePrestacionRequest createPrestacionRequest) {

        CreatePrestacionResponse response = prestacionApp.createPrestacion(createPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }
}
```

(Ejemplo simplificado: en el código real van además las `//region` y el `@Slf4j`, ver §10.)

### 5.1 Rutas y verbos HTTP del controller

- **Ruta de la clase**: `@RequestMapping("/accesmed-api/<Entidad>")`, entidad en PascalCase
  singular — `/accesmed-api/Prestacion`, `/accesmed-api/AgendaMedico`.
- **Ruta del método**: el **recurso concreto** que toca esa operación. En un controller de
  una sola entidad coincide con ella (`createPrestacion` → `/Prestacion`); en uno de
  agregado, cada método nombra el suyo (`createAgenda` → `/Agenda`, `createHorario` → `/Horario`).

| Operación | Método App | Firma | Status |
|---|---|---|---|
| Crear | `create<Entidad>` | `@PostMapping("/<Recurso>")` + `@Valid @RequestBody` | 201 |
| Obtener por id | `get<Entidad>ById` | `@GetMapping("/<Recurso>/{id}")` + `@PathVariable Long id` | 200 |
| Listar | `list<Entidad>` | `@GetMapping("/<Recurso>")` | 200 |
| Actualizar completo (`PUT`) | `fullUpdate<Entidad>` | `@PutMapping("/<Recurso>/{id}")` + `@PathVariable Long id` + `@Valid @RequestBody` | 200 |
| Actualizar parcial genérico (`PATCH`, cualquier subconjunto de campos) | `partialUpdate<Entidad>` | `@PatchMapping("/<Recurso>/{id}")` + `@PathVariable Long id` + `@Valid @RequestBody` | 200 |
| Actualizar un grupo de campos específico (`PATCH`, conocido de antemano) | `update<Concepto><Entidad>` | `@PatchMapping("/<Recurso>/<Concepto>/{id}")` + `@PathVariable Long id` + `@Valid @RequestBody` | 200 |
| `PATCH` de un campo puntual (sin body) | `<verbo><Concepto><Entidad>` | `@PatchMapping("/<Recurso>/{id}")` + solo `@PathVariable Long id` | 200 |
| Baja lógica (soft delete) | `delete<Entidad>` | `@DeleteMapping("/<Recurso>/{id}")` + `@PathVariable Long id` | 204 |

**`fullUpdate` vs `partialUpdate` vs `update<Concepto>`**: el primero (`PUT`) reemplaza el
recurso entero, así que no hay ambigüedad de `null` (siempre viajan todos los campos). El
segundo (`PATCH` genérico) acepta cualquier subconjunto de campos editables y trata un
campo en `null` como **"no lo toques"** — nunca "vacíalo". El tercero es un endpoint propio
para un grupo de campos ya conocido (ej. `updateToleranciasPrestacion`), y se crea **solo
cuando el requisito de negocio pide poder vaciar alguno de esos campos**; ahí sí se declara
`JsonNullable<T>` (`org.openapitools:jackson-databind-nullable`) campo por campo, para
distinguir "no vino" de "vino en `null`", documentando en el Javadoc qué significa el `null`
de cada campo. No se generaliza `JsonNullable` a todo el proyecto — se paga ese costo de
tipado solo donde hay un requisito real de vaciar un campo. Detalle y ejemplo completo en
`ARQUITECTURA.md §5.2`.

**`PUT`/`PATCH` con body reciben el `id` por `@PathVariable` *además* del record**, y hay
que **validar que ambos coincidan**. La validación va en el **App** (el Controller no tiene
lógica), como primer paso del método:

```java
// Controllers/PrestacionController.java
@PutMapping("/Prestacion/{id}")
public ResponseEntity<UpdatePrestacionResponse> updatePrestacion(
        @PathVariable Long id,
        @Valid @RequestBody UpdatePrestacionRequest updatePrestacionRequest) {

    log.info("Solicitud recibida: actualizar prestación id={}", id);

    return ResponseEntity.ok(prestacionApp.updatePrestacion(id, updatePrestacionRequest));

}

// Application/PrestacionApp.java
//El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
if (!id.equals(updatePrestacionRequest.id())) {
    log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePrestacionRequest.id());
    throw new ValidacionException(getClass(),
            List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
}
```

**`PATCH` sin body**: si el cambio es de un campo puntual y no necesita datos, el método
recibe **solo el id** — no se crea un record vacío.

```java
@PatchMapping("/Prestacion/{id}/Activacion")
public ResponseEntity<ActivarPrestacionResponse> activatePrestacion(@PathVariable Long id) { ... }
```

**Soft delete = `DELETE`**, con 204. El `deleted_at` es implementación interna; no cambia el verbo.

## 6. Inyección de dependencias: por constructor, siempre

Usá `@RequiredArgsConstructor` (Lombok) con campos `private final`. Nunca `@Autowired` en campo.

```java
@Service
@RequiredArgsConstructor
public class PrestacionApp {
    private final PrestacionDomainService prestacionDomainService;
    private final PrestacionMapper prestacionMapper;
}
```

## 7. Excepciones: no chequeadas, sin try/catch de control de flujo, ubicadas por capa

- Lanzá subclases de `AccesMedException` (`RecursoNoEncontradoException`,
  `ReglaNegocioException`, `ValidacionException`), definidas en `Services/Errors/`. Todas
  `RuntimeException`. `AccesMedException` **nunca** llega al front — el
  `GlobalExceptionHandler` arma `AccesMedError` de cero a partir de ella.
- **Nunca** una clase de error por entidad/service (nada de `PrestacionServiceError`,
  `MedicoServiceError`...): la jerarquía es fija y se organiza por tipo de problema
  (404/409/422), no por entidad. No crece con el dominio.
- **Siempre pasá `getClass()` como primer argumento** al lanzar: todas las subclases de
  `AccesMedException` reciben `Class<?> origen` para saber qué Service la lanzó. Es solo
  para diagnóstico — **nunca** se serializa en `AccesMedError`.
- **Logueá con `log.warn` justo antes del `throw`, en el mismo Service** — no en el
  `GlobalExceptionHandler`. El Service ya tiene todo el contexto en ese momento; loguear
  también en el handler duplicaría la misma línea de error dos veces. Ver skill
  `java-springboot-logging` para niveles y formato.
- **No** uses `try/catch` para controlar flujo ni la cláusula `throws` en la firma.
- Para varias validaciones, acumulá en una `List<String>` y lanzá `ValidacionException` una sola vez.
- El `GlobalExceptionHandler` (`Controllers/Errors/`, `@RestControllerAdvice`) se encarga
  de traducir cualquier `AccesMedException` al contrato `AccesMedError`, sin volver a
  loguearla. Sí loguea (con `log.warn`) lo que él mismo atrapa y no viene de un Service:
  Bean Validation y `ConstraintViolationException`.

```java
log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
        "Ya existe una prestación activa con el código " + codigo);
```

```java
List<String> errores = new ArrayList<>();
if (createTurnoRequest.fechaDesde().isAfter(createTurnoRequest.fechaHasta()))
    errores.add("La fecha desde no puede ser posterior a la fecha hasta.");
if (medicoExistente == null)
    errores.add("El médico indicado no existe o está dado de baja.");
if (!errores.isEmpty()) {
    log.warn("Turno inválido: {}", errores);
    throw new ValidacionException(getClass(), errores);
}
```

## 8. Reglas de dominio de AccesMed que el código debe respetar

- **Soft delete** siempre: cada entidad que lo necesite agrega `deletedAt` (Instant).
  Nunca `delete` físico salvo caso justificado.
- Entidades extienden `Auditable` (`createdDate`, `lastModifiedDate`, `createdBy`, `lastModifiedBy`).

## 9. Formato general

- Un tipo público por archivo. Imports ordenados, sin `*` wildcards.
- Constantes en `UPPER_SNAKE_CASE`; clases en `PascalCase`; métodos/variables en `camelCase`.
- Ancho de línea razonable (~120). Métodos cortos y con una sola responsabilidad.
- Evitá lógica en el controller y en las entidades; la lógica vive en services y apps.

## 10. Comentarios: secciones plegables, espaciado y narración del flujo

### 10.1 Secciones de la clase con `//region` / `//endregion`

Cada clase se organiza en bloques con un banner de ancho fijo (10 signos `=` de cada
lado, sin importar el largo del texto — así no hay que recalcular el padding cada vez que
se renombra algo) envuelto en `//region` / `//endregion` (IntelliJ los pliega en el editor).
**Siempre con una línea en blanco después de abrir el región y antes de cerrarlo**:

**Controllers, App, DomainService, QueryService** (en ese orden):

```java
//region ========== Dependencias o inyecciones ==========

private final PrestacionDomainService prestacionDomainService;
private final PrestacionMapper prestacionMapper;

//endregion

//region ========== Métodos ==========

public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {
    ...
}

//endregion

//region ========== Métodos auxiliares privados ==========

private void trimCreatePrestacionRequest(CreatePrestacionRequest createPrestacionRequest) {
    ...
}

//endregion
```

**Entidades (`Domain`)**: separar campos escalares de relaciones JPA:

```java
//region ========== Atributos ==========

private Long id;
private String codigo;
private String nombre;

//endregion

//region ========== Relaciones ==========

@OneToMany(mappedBy = "prestacion")
private List<MedicoPrestacion> medicoPrestaciones;

//endregion
```

Records, Repositories y Mappers no necesitan estas secciones — son clases cortas y de un
solo propósito, el banner ahí sería ruido.

### 10.2 Espaciado y tabulación dentro del método

Regla fija para todo método: **el cuerpo va tabulado un nivel respecto a la firma**, la
firma queda separada del cuerpo por una línea en blanco, y la última línea del cuerpo
queda separada de la llave de cierre por otra línea en blanco. Si el método narra un
flujo paso a paso (ver 10.3), cada paso comentado también se separa del anterior con una
línea en blanco — así el bloque de comentario + código se lee como un párrafo propio.

Los bloques simples (`if`, `for`, etc.) se dejan compactos, **sin** este espaciado, salvo
que tengan varios pasos internos que también merezcan narrarse — en ese caso aplican la
misma convención puertas adentro del bloque.

```java
public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {

    log.info("Creación de prestación iniciada: código={}", createPrestacionRequest.codigo());

    //Validar que el código no esté repetido entre las prestaciones activas
    prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());

    //Mapear el request a entidad
    Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);

    //Persistir la prestación
    Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);

    //Devolver el response mapeado
    return prestacionMapper.toCreateResponse(prestacionGuardada);

}
```

```java
// bloque if simple: todo junto, sin espaciado interno
if (createPrestacionRequest.nombre() == null) {
    throw new ValidacionException(getClass(), List.of("El nombre es obligatorio."));
}
```

### 10.3 Comentarios paso a paso dentro del método

Sirven para narrar el flujo de negocio como una lista de pasos — especialmente valioso en
el **App**, porque el método literalmente ES el flujo completo del caso de uso.

**Solo en métodos con varios pasos reales.** Un método de un solo paso (`save<Entidad>`,
`find<Entidad>ById`, un `validate*` con un único `if`) no lleva comentario: el nombre del
método ya lo dice y el comentario sería ruido redundante. Los comentarios se ganan el
lugar en métodos que encadenan varias acciones — típicamente todo método del App, y los
métodos del DomainService con más de un paso (ej. `validateToleranciasPrestacion`, que
valida duraciones, cadena de tolerancias, anuncio y recordatorio en bloques distintos).

**Regla: comentar el *porqué*, no el *qué*.** Si el nombre del método/variable ya lo dice,
el comentario no aporta nada y es ruido.

```java
// ✅ explica una regla de negocio que no es obvia solo con el nombre
//Validar que el nombre no sea nulo antes de mapear, para no persistir datos incompletos
if (createPrestacionRequest.nombre() == null) { ... }

// ❌ solo repite lo que el código ya dice
//Guardar la prestación
Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);
```

**Dónde van los pasos de cada flujo**, según a quién le corresponde la responsabilidad (no
mezclar todo en un único método, como en un Service plano):

- **App**: pasos de orquestación — resolver asociaciones llamando a otros
  DomainServices, mapear, delegar el guardado, armar la respuesta.
- **DomainService**: pasos de validación y persistencia de **una sola** entidad —
  unicidad, existencia, guardar.

```java
// Application/PrestacionApp.java
@Transactional
public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {

    log.info("Creación de prestación iniciada: código={}", createPrestacionRequest.codigo());

    //Validar que el código no esté repetido entre las prestaciones activas
    prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());

    //Mapear el request a entidad
    Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);

    //Persistir la prestación
    Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);

    //Mapear a create Prestacion Response
    CreatePrestacionResponse createPrestacionResponse = prestacionMapper.toCreateResponse(prestacionGuardada);

    //Retornar Respuesta
    return createPrestacionResponse;

}
```

El Javadoc **no cambia**: sigue arriba del método, completo, según la skill
`java-springboot-javadoc` (una línea por `@param`/`@return`/`@throws`, tipos en
`{@code}`). El paso a paso es información distinta y convive adentro del método — el
Javadoc describe el contrato, el paso a paso narra la implementación.

### 10.4 Retorno con variable nombrada

Aunque el compilador no lo exija, el valor que se retorna se asigna primero a una
variable con nombre descriptivo, y recién ahí se hace `return` — incluso cuando parece
redundante. Ayuda a leer de un vistazo qué devuelve el método sin tener que desarmar la
expresión, y da un punto natural para poner un breakpoint al debuggear.

```java
// ✅
CambioEstadoPrestacionResponse cambioEstadoPrestacionResponse = prestacionMapper.toCambioEstadoResponse(prestacionPublicada);
return cambioEstadoPrestacionResponse;

// ❌ retorno directo, aunque compile igual
return prestacionMapper.toCambioEstadoResponse(prestacionPublicada);
```

Aplica en Controller, App y DomainService, en cualquier método no trivial (no hace falta
en un getter de una línea que ya es solo `return campo;`).

## Checklist de revisión de estilo

- [ ] Método = verbo inglés + negocio español.
- [ ] Parámetro = camelCase del tipo.
- [ ] Variable de entidad = español descriptivo del estado.
- [ ] Record en `Records/<Entidad>/{Request,Response}/`, `<Accion><Entidad>Request/Response`,
      sin campos inmutables en updates.
- [ ] Controller sin lógica; App orquesta y es `@Transactional`.
- [ ] Ruta de clase `/accesmed-api/<Entidad>` y ruta de método con su recurso (§5.1).
- [ ] `PUT`/`PATCH` con body: `@PathVariable id` + record, y el App valida que los ids
      coincidan (`ValidacionException` si no). `PATCH` de un campo puntual: solo el id.
- [ ] Verbo de actualización correcto: `fullUpdate<Entidad>` (PUT), `partialUpdate<Entidad>`
      (PATCH genérico, `null` = no tocar) o `update<Concepto><Entidad>` (PATCH de campos
      específicos, `JsonNullable` solo si ese endpoint necesita vaciar un campo).
- [ ] Baja lógica expuesta como `DELETE` (204).
- [ ] Inyección por constructor (`@RequiredArgsConstructor`, `final`).
- [ ] Excepciones no chequeadas; sin try/catch de control; `ValidacionException` para listas.
- [ ] `AccesMedException` logueada con `log.warn` en el Service (no en el handler); handler solo loguea lo que él mismo atrapa.
- [ ] Soft delete y `Auditable` respetados; navegabilidad correcta.
- [ ] Javadoc según la skill `java-springboot-javadoc`; logging según `java-springboot-logging`.
- [ ] Secciones con `//region`/`//endregion` (banner de ancho fijo `==========`), con línea en blanco después de abrir y antes de cerrar: Dependencias, Métodos, Métodos auxiliares privados (o Atributos/Relaciones en entidades).
- [ ] Espaciado dentro del método: línea en blanco tras la firma, antes de la llave de cierre, y entre cada paso comentado (bloques `if`/`for` simples quedan compactos).
- [ ] Comentarios paso a paso solo en métodos con varios pasos reales; nada en métodos de
      un solo paso (`save<Entidad>`, `find<Entidad>ById` simple).
- [ ] Ningún `DomainService` inyecta el repositorio de otra entidad ni llama a otro
      `DomainService`; la orquestación multi-entidad vive en el App (§5.0).
- [ ] `@Transactional` solo en el App, salvo lectura standalone o transacción independiente.
- [ ] Flujos de mutación usan `find<Entidad>Activa(o)ById` cuando la entidad tiene estado
      terminal o soft-delete (§5.0.1), no un `findById` simple.
- [ ] El valor de retorno se asigna a una variable nombrada antes del `return` (§10.4).
