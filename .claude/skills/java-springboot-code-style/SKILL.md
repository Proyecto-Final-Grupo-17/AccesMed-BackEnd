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

## 2. Nombres de parámetros: camelCase del tipo

Un parámetro se llama como su tipo en camelCase. Que quede obvio qué es cada cosa.

```java
public CrearMedicoResponse createMedico(CrearMedicoRequest crearMedicoRequest) { ... }
public void savePrestacion(Prestacion prestacion) { ... }
public TurnoResponse confirmTurno(ConfirmarTurnoRequest confirmarTurnoRequest) { ... }
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

## 4. Records = `record`, uno por endpoint, carpeta `Records/`

- Un `record` por endpoint, en `Records/Request/` o `Records/Response/`. Los campos
  inmutables **no aparecen** en el request.
- Nombre: `<Accion><Entidad>Request` / `<Accion><Entidad>Response`. Sin sufijo `Record`/`DTO`.
- La validación básica va con Bean Validation en el propio record.

```java
public record CrearPrestacionRequest(
        @NotBlank(message = "El código es obligatorio.")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio.")
        String nombre,
        @NotNull @Positive(message = "La duración debe ser mayor a cero.")
        Integer duracionTurnoMinutos
) {}

public record CrearPrestacionResponse(
        Long id,
        String codigo,
        String nombre,
        Integer duracionTurnoMinutos
) {}
```

En un update, el `id` viaja pero un campo inmutable (ej. `codigo`) no se incluye:

```java
public record ActualizarPrestacionRequest(
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

`Controllers` también tiene su subcarpeta fija `Controllers/Errors/`, con el
`GlobalExceptionHandler` (`@RestControllerAdvice`) y `AccesMedError` (el contrato de error
que ve el front) — viven ahí porque el handler es, en esencia, parte de la capa de controller.

Nota: el sufijo de la clase (`Controller`, `Repository`, `DomainService`) queda en
singular; solo el nombre de la **carpeta** va en plural.

El controller nunca llama al repository ni al domain service directo: pasa por el App.

```java
@RestController
@RequestMapping("/api/prestaciones")
@RequiredArgsConstructor
public class PrestacionController {

    private final PrestacionApp prestacionApp;

    @PostMapping
    public ResponseEntity<CrearPrestacionResponse> createPrestacion(
            @Valid @RequestBody CrearPrestacionRequest crearPrestacionRequest) {

        CrearPrestacionResponse response = prestacionApp.createPrestacion(crearPrestacionRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }
}
```

(Ejemplo simplificado: en el código real van además las `//region` y el `@Slf4j`, ver §10.)

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
if (crearTurnoRequest.fechaDesde().isAfter(crearTurnoRequest.fechaHasta()))
    errores.add("La fecha desde no puede ser posterior a la fecha hasta.");
if (medicoExistente == null)
    errores.add("El médico indicado no existe o está dado de baja.");
if (!errores.isEmpty()) {
    log.warn("Turno inválido: {}", errores);
    throw new ValidacionException(getClass(), errores);
}
```

## 8. Reglas de dominio de AccesMed que el código debe respetar

- **Soft delete** siempre: filtrar por `fechaHoraBaja IS NULL` / `fechaHoraFinVigencia`.
  Nunca `delete` físico salvo caso justificado.
- Entidades extienden `Auditable`.
- Navegabilidad estricta: a `Medico`/`Prestacion` desde `Turno` **solo vía `MedicoPrestacion`**.
- Estado del turno = `HistoricoEstadoTurno` con `fechaHoraFin` vacío. Las transiciones son
  operaciones/endpoints propios, no un setter de `estado`.
- `@Transactional` en el App (nivel de caso de uso), no en el controller.

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

public CrearPrestacionResponse createPrestacion(CrearPrestacionRequest crearPrestacionRequest) {
    ...
}

//endregion

//region ========== Métodos auxiliares privados ==========

private void trimCrearPrestacionRequest(CrearPrestacionRequest crearPrestacionRequest) {
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
```

```java
// bloque if simple: todo junto, sin espaciado interno
if (crearPrestacionRequest.nombre() == null) {
    throw new ValidacionException(getClass(), List.of("El nombre es obligatorio."));
}
```

### 10.3 Comentarios paso a paso dentro del método

Sirven para narrar el flujo de negocio como una lista de pasos — especialmente valioso en
el **App**, porque el método literalmente ES el flujo completo del caso de uso.

**Regla: comentar el *porqué*, no el *qué*.** Si el nombre del método/variable ya lo dice,
el comentario no aporta nada y es ruido.

```java
// ✅ explica una regla de negocio que no es obvia solo con el nombre
//Validar que el nombre no sea nulo antes de mapear, para no persistir datos incompletos
if (crearPrestacionRequest.nombre() == null) { ... }

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
```

El Javadoc **no cambia**: sigue arriba del método, completo, según la skill
`java-springboot-javadoc` (una línea por `@param`/`@return`/`@throws`, tipos en
`{@code}`). El paso a paso es información distinta y convive adentro del método — el
Javadoc describe el contrato, el paso a paso narra la implementación.

## Checklist de revisión de estilo

- [ ] Método = verbo inglés + negocio español.
- [ ] Parámetro = camelCase del tipo.
- [ ] Variable de entidad = español descriptivo del estado.
- [ ] Record en `Records/`, `<Accion><Entidad>Request/Response`, sin campos inmutables en updates.
- [ ] Controller sin lógica; App orquesta y es `@Transactional`.
- [ ] Inyección por constructor (`@RequiredArgsConstructor`, `final`).
- [ ] Excepciones no chequeadas; sin try/catch de control; `ValidacionException` para listas.
- [ ] `AccesMedException` logueada con `log.warn` en el Service (no en el handler); handler solo loguea lo que él mismo atrapa.
- [ ] Soft delete y `Auditable` respetados; navegabilidad correcta.
- [ ] Javadoc según la skill `java-springboot-javadoc`; logging según `java-springboot-logging`.
- [ ] Secciones con `//region`/`//endregion` (banner de ancho fijo `==========`), con línea en blanco después de abrir y antes de cerrar: Dependencias, Métodos, Métodos auxiliares privados (o Atributos/Relaciones en entidades).
- [ ] Espaciado dentro del método: línea en blanco tras la firma, antes de la llave de cierre, y entre cada paso comentado (bloques `if`/`for` simples quedan compactos).
- [ ] Comentarios paso a paso solo donde explican el *porqué*; nada que repita lo obvio del código.
