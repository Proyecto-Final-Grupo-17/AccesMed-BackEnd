# Arquitectura del Backend — AccesMed

Java 25 (LTS) · Spring Boot 4.1.0 · Maven · PostgreSQL 16 — versiones completas en [`STACK.md`](STACK.md)

Este documento fija las decisiones de arquitectura, la estructura del repositorio y las
convenciones. Es la fuente de verdad; `CLAUDE.md` es el resumen que lee el agente.

---

## 1. Decisiones de arquitectura

Estas son las decisiones **definitivas** del proyecto, con su justificación. No son
preguntas abiertas: cualquier código nuevo debe seguirlas.

| Decisión | Justificación |
|----------|----------|
| DTO = **`record`** de Java | Inmutables por diseño, `equals/hashCode/toString` gratis, menos boilerplate que una clase DTO con Lombok. Un `record` por endpoint: solo los campos que viajan. |
| Sufijo del nombre del DTO: `<Accion><Entidad>Request` / `Response` | Sin `Record`/`DTO` al final: es ruido. El sufijo `Request`/`Response` ya dice qué es. |
| Carpeta de los records: **`Records`** (no `dto`) | El nombre de la carpeta refleja el tipo Java que contiene. |
| Capa de controlador: carpeta **`Controllers`**, sufijo de clase `Controller` | Es el estándar de Spring (`@RestController`). `Resource` es del mundo JAX-RS; `Rest` no aporta. Menos fricción con docs y ejemplos. |
| Capa de aplicación en paquete propio **`Application`**, clases `<Entidad>App`, anotadas `@Service` | Es un rol arquitectónico distinto (orquestación) del DomainService (lógica de una entidad). Meterla dentro de `Services` mezcla dos responsabilidades. |
| `@Service` (no `@Component`) para el App | Semánticamente es lógica de negocio de aplicación. `@Component` es genérico; `@Service` documenta la intención. Funcionalmente son equivalentes. |
| `Domain` contiene **solo las entidades JPA** (`Medico`, `Turno`, `Prestacion`...) | `Domain` es el modelo puro. Los `DomainService`/`QueryService` (lógica y consultas) viven en `Services`. |
| `Mapper` dentro de **`Services/Mappers`** | Un `Mapper` (MapStruct) no tiene lógica de negocio, pero es un componente de soporte que solo usan los services/apps para convertir Record↔entidad. Anidarlo en `Services` evita un package suelto a nivel raíz. |
| Subdivisión de `Services` **obligatoria desde el inicio**: `DomainServices/`, `QueryServices/`, `Mappers/` | No esperar a que crezca: subdividir de entrada para que `Services` no sea un cajón de sastre. |
| Excepción "clásica" (error + descripción): `AccesMedException(Class<?> origen, String codigo, String mensaje)` no chequeada | Con `origen` para diagnóstico (ver fila siguiente). |
| Nombre de la excepción base: **`AccesMedException`** | Branding del proyecto: queda la pareja `AccesMedException` (interna, capas bajas) + `AccesMedError` (contrato al front). La excepción **nunca** llega al front tal cual — el handler arma `AccesMedError` de cero a partir de ella. |
| Cómo sabe la excepción qué Service la lanzó: **`getClass()` pasado explícito en el throw** (Opción A) | Explícito y sin magia; como es el tipo real (no un string a mano) nunca se desincroniza con un rename de la clase. Fácil de testear. Se pasa solo en las subclases concretas (`RecursoNoEncontradoException`, `ReglaNegocioException`, `ValidacionException`), nunca en algo que viaje al front. Descartadas: autocaptura por stack trace (frágil si la excepción se envuelve) y constante de texto por Service (se desincroniza en un rename). |
| `origen` **no** se expone al front | Se sigue guardando en la excepción aunque el log se haga en el Service (no en el handler). El nombre de una clase interna no le sirve al front; no viaja en `AccesMedError`. Se conserva por si algo más adelante inspecciona la excepción fuera del punto donde se logueó. |
| Excepción con **lista de errores**: `ValidacionException(Class<?> origen, List<String> errores)` | Para acumular varias validaciones de negocio y devolverlas juntas. El manejador global la aplana en el mismo `AccesMedError`. |
| Sin una clase de error por Service (`PrestacionServiceError`, `MedicoServiceError`...) | Se mantiene la jerarquía fija de 3 tipos (`RecursoNoEncontrado`/`ReglaNegocio`/`Validacion`) — un "título + descripción" reutilizable, dividido por **tipo de problema** (para saber qué HTTP status devolver), no por entidad. No crece nunca por agregar entidades nuevas. |
| Excepciones viven en **`Services/Errors/`** | Se originan ahí: las lanzan los `DomainService`/`QueryService` (y a veces el `App`). No es un cajón transversal, es del rol que las produce. |
| Manejador global en **`Controllers/Errors/`** | `GlobalExceptionHandler` es un `@RestControllerAdvice`, o sea que intercepta en la capa de Controllers. Ahí mismo vive `AccesMedError`, el contrato único que ve el front. |
| Nombre del contrato de error: **`AccesMedError`** | Branding del proyecto. |
| Manejador global: `@RestControllerAdvice GlobalExceptionHandler` (en `Controllers/Errors`) → `AccesMedError` único | El front recibe **siempre la misma forma**, en español, incluidos los errores de Bean Validation. |
| Un error de negocio (`AccesMedException`) se loguea **en el Service, en el punto del `throw`** — no en el `GlobalExceptionHandler` | El Service ya tiene todo el contexto en ese momento; loguear también en el handler duplicaría la misma línea de error dos veces. |
| Nivel de log para 404/409/422: **`log.warn`**, no `log.error` | Son datos mal enviados por el cliente o reglas de negocio, no una falla del sistema. `log.error` queda reservado para el 500 genérico (algo realmente roto). |
| Bean Validation la loguea el **`GlobalExceptionHandler`**, con `log.warn` + mensaje claro de qué campo falló | No tiene un "Service de origen" (lo lanza el propio framework en el borde HTTP), así que se atrapa y loguea centralizado, junto con la traducción a `AccesMedError`. |
| Sin carpeta `Shared` — se reparte por rol: excepciones a `Services/Errors`, advice+contrato a `Controllers/Errors`, `Auditable` a `Domain`, `Util` a `Services/Utils` | Un cajón "transversal" genérico mezcla responsabilidades; cada cosa va donde se origina o se usa. |
| `Util` dentro de **`Services/Utils`** | Los helpers genéricos también son un tipo de soporte que usan los services/apps, igual que los `Mapper`. Se agrupa con el resto en vez de quedar suelto en la raíz. |
| Build tool: **Maven** | Para un proyecto académico/colaborativo: es el default de Spring Initializr, más ejemplos, más predecible, menos curva. Gradle es más rápido pero suma complejidad que acá no rinde. |
| Estructura de carpetas por **capa** en el núcleo + **slice vertical** para `Security` y `Agente` | Con pocas entidades, package-by-layer es legible. Seguridad y agente sí se aíslan porque son subsistemas con reglas propias. |
| CRUD de usuarios/seguridad en su propio slice **`Security/`** | Auth es cohesiva y separable; conviene tenerla junta (entidades, servicios, filtros JWT, config). |
| El agente tiene carpeta aparte **solo para la entrada**, reutiliza los mismos App/Service | El agente no duplica negocio: solo cambia el punto de entrada (controllers y records propios, otra auth). La lógica es la misma. |
| Nombres de package en mayúsculas: `Domain`, `Services`, `Controllers`, `Application`, `Repositories`, `Records`, `Config`, `Security`, `Agente` | Convención de proyecto. **Nota técnica breve**: la convención estándar de Java es minúsculas para packages (el JLS solo lo recomienda, no lo exige); en mayúsculas compila y funciona igual, pero algunas herramientas/linters de estilo Java pueden marcarlo como advertencia. No es un problema funcional. |
| Subpaquete por entidad dentro de un paquete de tipo, recién cuando supere ~8-10 archivos (ej. `Services/DomainServices/Medico/`) | No crear carpetas de un solo archivo por adelantado. |
| Plural en `Controllers`, `Services`, `Repositories` (y sus subcarpetas `DomainServices`/`QueryServices`/`Mappers`/`Errors`/`Utils`) | Son colecciones de clases del mismo rol (todos los controllers, todos los services, todos los repositories). `Domain`, `Application`, `Records`, `Config`, `Security`, `Agente` quedan como estaban. |
| El `QueryService` se inyecta y se llama **desde el App, nunca desde el Controller** — ni siquiera para lecturas simples | Mantiene un único punto de entrada a la lógica (el App) para toda la capa web, sin una excepción "para lecturas" que la rompa. El Controller sigue sin conocer `Services` en absoluto; el App resuelve el mapeo con el `Mapper` antes de devolver el response. |

---

## 2. Flujo de una petición (las 5 capas de defensa)

```
HTTP Request
   │
   ▼
┌──────────────────────────────────────────────────────────────┐
│ 1. Controllers  (@RestController)                              │
│    - Recibe el <Accion><Entidad>Request                       │
│    - @Valid dispara Bean Validation (campos básicos: notNull, │
│      tamaño, formato, que un update traiga id, etc.)          │
│    - Delega en el App. NO tiene lógica.                   │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. App  (@Service, package Application)                    │
│    - Orquesta el caso de uso completo                         │
│    - Valida reglas de negocio: existencia, estados válidos,   │
│      unicidad, navegabilidad (llamando a los services)        │
│    - @Transactional acá (1 caso de uso = 1 transacción)       │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. DomainService / QueryService  (@Service, package Services) │
│    - Lógica y consultas de UNA entidad                        │
│    - Ej: validateCodigoPrestacionIsUnique(codigo)             │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────┐
│ 4. Repositories + anotaciones Hibernate/JPA                   │
│    - Al guardar, @NotNull, @Column(unique), @Size actúan como │
│      segunda malla de validación                              │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌──────────────────────────────────────────────────────────────┐
│ 5. PostgreSQL — constraints (UNIQUE, FK, CHECK, NOT NULL)     │
│    - Última línea de defensa a nivel de esquema (Liquibase)   │
└──────────────────────────────────────────────────────────────┘

Cualquier excepción en cualquier capa → GlobalExceptionHandler → AccesMedError (JSON único)
```

### Ejemplo concreto: crear una Prestación

```java
// 1. Controller (package Controllers)
@PostMapping
public ResponseEntity<CrearPrestacionResponse> createPrestacion(
        @Valid @RequestBody CrearPrestacionRequest crearPrestacionRequest) {
    CrearPrestacionResponse response = prestacionApp.createPrestacion(crearPrestacionRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

// 2. App (package Application)
@Transactional
public CrearPrestacionResponse createPrestacion(CrearPrestacionRequest crearPrestacionRequest) {

    prestacionDomainService.validateCodigoPrestacionIsUnique(crearPrestacionRequest.codigo());
    Prestacion prestacionNueva = prestacionMapper.toEntity(crearPrestacionRequest);
    Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);
    return prestacionMapper.toCrearResponse(prestacionGuardada);

}

// 3. DomainService (package Services/DomainServices)
public void validateCodigoPrestacionIsUnique(String codigo) {

    if (prestacionRepository.existsByCodigoAndFechaHoraBajaIsNull(codigo)) {
        log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
        throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
                "Ya existe una prestación activa con el código " + codigo);
    }

}
```

> El espaciado (línea en blanco tras la firma, antes del cierre, entre pasos) sigue la
> convención de `java-springboot-code-style` §10.2.

---

## 3. Manejo de errores

El manejo de errores se divide **por capa**, no en una carpeta transversal: las
excepciones que se lanzan viven donde se originan (`Services`); el traductor final y el
contrato que ve el front viven donde se interceptan (`Controllers`, porque el handler es
un `@ControllerAdvice`).

```
Services/Errors/                          Controllers/Errors/
├── AccesMedException (abstract)          ├── GlobalExceptionHandler (@RestControllerAdvice)
│   - String codigo                       └── AccesMedError (record, el contrato al front)
│   - int httpStatus
│   - String origen        (solo logging, nunca viaja al front)
├── RecursoNoEncontradoException  → 404
├── ReglaNegocioException         → 409
└── ValidacionException           → 422 (List<String> errores)
```

### Jerarquía de excepciones (`Services/Errors/`, todas `RuntimeException`)

Una clase por **tipo de problema**, no por entidad ni una única clase plana con status a
mano. Con 3-4 clases fijas alcanza para todo el sistema, nunca crece con el dominio:

- **`AccesMedException`**: base abstracta (antes `AppException`). Constructor
  `(Class<?> origen, String codigo, String mensaje)`. Guarda el `httpStatus` para que el
  advice sepa qué devolver, y `origen` (`origen.getSimpleName()`) para diagnóstico — ver
  el detalle abajo. **Nunca se serializa ni llega al front**: el handler arma
  `AccesMedError` de cero a partir de ella.
- **`RecursoNoEncontradoException`** (404): un `findById` que no existe.
- **`ReglaNegocioException`** (409): un solo error de negocio (unicidad, estado inválido, etc.).
- **`ValidacionException`** (422): para cuando querés juntar varios errores y devolverlos
  de una. Constructor `(Class<?> origen, List<String> errores)`. Patrón de uso recomendado:

```java
List<String> errores = new ArrayList<>();
if (request.fechaDesde().isAfter(request.fechaHasta()))
    errores.add("La fecha desde no puede ser posterior a la fecha hasta.");
if (medicoExistente == null)
    errores.add("El médico indicado no existe o está dado de baja.");
if (!errores.isEmpty()) {
    log.warn("Turno inválido: {}", errores);
    throw new ValidacionException(getClass(), errores);   // se lanza una sola vez, con todo junto
}
```

> Por qué esto resuelve tu preocupación de "se hace difícil de manejar": vos solo
> **acumulás strings y lanzás una vez**. Toda la complejidad de darle forma la absorbe
> el manejador global. No hacés `try/catch` en ningún lado.

> **Nota sobre la granularidad**: pensaste en una clase de error por Service
> (`PrestacionServiceError`, `MedicoServiceError`...) o en una única `ServiceError` plana
> con título y descripción. Lo de arriba es, en la práctica, esa segunda idea — un
> "título + descripción" reutilizable — pero partido en 3 según el tipo de problema, que
> es la mínima información extra que necesita el handler para elegir el HTTP status.

### Campo `origen`: qué service lanzó la excepción (para diagnóstico, no para el front)

Decisión: **Opción A** — pasar `getClass()` explícito en cada `throw`, no autocapturarlo
por stack trace ni duplicarlo como constante de texto en cada Service.

```java
// Services/Errors/AccesMedException.java
public abstract class AccesMedException extends RuntimeException {
    private final String codigo;
    private final int httpStatus;
    private final String origen;

    protected AccesMedException(Class<?> origen, String codigo, String mensaje, int httpStatus) {
        super(mensaje);
        this.origen = origen.getSimpleName();
        this.codigo = codigo;
        this.httpStatus = httpStatus;
    }
    // getters: getCodigo(), getHttpStatus(), getOrigen()
}

// Services/Errors/ReglaNegocioException.java
public class ReglaNegocioException extends AccesMedException {
    public ReglaNegocioException(Class<?> origen, String codigo, String mensaje) {
        super(origen, codigo, mensaje, 409);
    }
}
```

Se lanza así, desde cualquier Service — **logueando en el mismo punto**, antes del throw:

```java
log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
        "Ya existe una prestación activa con el código " + codigo);
```

**Por qué esta forma y no otra:**

- **No autocaptura por stack trace** (`new Throwable().getStackTrace()[1]`): es frágil si
  la excepción se envuelve o relanza en otra capa, y acopla el comportamiento al mecanismo
  interno de stack traces — dificulta debug si algo cambia en el medio.
- **No una constante `ORIGEN` de texto en cada Service**: duplica el nombre de la clase
  como string suelto; si renombrás la clase y te olvidás de actualizar la constante,
  queda desincronizado.
- **`getClass()` en el throw site**: explícito, sin magia, no depende del stack trace, y
  como es el tipo real (no un string a mano) nunca se desincroniza con un rename. Es
  fácil de testear (`assertEquals("PrestacionDomainService", ex.getOrigen())`).

**`origen` es solo para diagnóstico, nunca se serializa en `AccesMedError`.** El nombre de
una clase interna no le sirve al front y es una fuga de detalle de implementación
innecesaria. Se conserva en la excepción aunque el log de negocio ahora se haga en el
Service (no en el handler, ver más abajo) — sirve por si en algún momento algo más
inspecciona la excepción fuera de ese punto.

### Contrato de respuesta de error (`AccesMedError`, en `Controllers/Errors/`)

El front **siempre** recibe esta forma, con HTTP status coherente:

```json
{
  "timestamp": "2026-07-22T10:15:30Z",
  "status": 422,
  "codigo": "VALIDACION",
  "mensaje": "La solicitud tiene errores de validación.",
  "errores": [
    "La fecha desde no puede ser posterior a la fecha hasta.",
    "El médico indicado no existe o está dado de baja."
  ],
  "path": "/api/turnos"
}
```

`errores` es **siempre una lista** (con un solo elemento cuando es un error simple). Así
el front tiene un único camino de parseo.

### El GlobalExceptionHandler (`Controllers/Errors/GlobalExceptionHandler.java`) traduce, entre otras:

- `MethodArgumentNotValidException` (Bean Validation `@Valid`) → arma `errores` con los
  mensajes campo a campo, en español (esto es lo que "sale feo y en inglés" — el advice
  lo deja prolijo), **y loguea con `log.warn`** el detalle de qué campo falló. Es el único
  caso de log dentro del handler: Bean Validation no tiene un "Service de origen" (la
  lanza el propio framework en el borde HTTP), así que se atrapa y loguea acá mismo.
- `ConstraintViolationException` (validación a nivel de parámetros / persistencia) → mismo criterio: `log.warn` + traducción a `AccesMedError`.
- `AccesMedException` y subclases (de `Services/Errors/`) → usa su `codigo` y
  `httpStatus` para armar la respuesta. **No la loguea de nuevo acá**: ya se logueó en
  el Service en el momento del `throw` (ver §3, "Campo `origen`"). Loguearla también en
  el handler duplicaría la misma línea de error dos veces.
- `Exception` genérica → 500 con mensaje neutro (sin filtrar stacktrace al front), y
  **`log.error`** (este sí, porque es una falla real del sistema, no un dato mal enviado).

### Logging: resumen de niveles y dónde loguear

El detalle completo y ejemplos por capa están en la skill `java-springboot-logging`. Reglas clave:

| Situación | Nivel | Dónde |
|-----------|-------|-------|
| Controller recibe una request | `info` | Controller, al inicio del método |
| App arranca la orquestación | `info` | App (`Application`), al inicio del método |
| Detalle interno de un Service (cálculos, decisiones) | `debug` | DomainService/QueryService |
| `RecursoNoEncontradoException` / `ReglaNegocioException` / `ValidacionException` | `warn` | En el Service, justo antes del `throw` — **no** en el handler |
| Bean Validation / `ConstraintViolationException` | `warn` | En el `GlobalExceptionHandler` (no tienen Service de origen) |
| `Exception` genérica (500) | `error` | En el `GlobalExceptionHandler` |

Regla general: `warn` es para datos mal enviados o reglas de negocio incumplidas (culpa del
cliente, no del sistema); `error` se reserva para lo que realmente indica que algo se rompió.

---

## 4. Estructura del repositorio

```
accesmed-backend/
├── CLAUDE.md                      # contexto para Claude Code
├── README.md
├── pom.xml
├── docs/
│   ├── ARQUITECTURA.md            # este archivo
│   ├── PLAN-SETUP-CLAUDE-CODE.md
│   └── FRONTEND-GUIA.md
├── docker/
│   ├── Dockerfile                 # build multi-stage de la app
│   └── dev/
│       ├── docker-compose.yml     # Postgres local
│       └── .env.example
├── .claude/
│   └── skills/
│       ├── java-springboot-code-style/
│       ├── java-springboot-javadoc/
│       ├── java-springboot-logging/
│       └── springboot-feature-generator/
└── src/
    ├── main/
    │   ├── java/com/accesmed/backend/
    │   │   ├── AccesMedApplication.java
    │   │   │
    │   │   ├── Config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   └── JacksonConfig.java
    │   │   │
    │   │   ├── Controllers/
    │   │   │   ├── <NombreEntidad>Controller.java
    │   │   │   └── Errors/
    │   │   │       ├── GlobalExceptionHandler.java
    │   │   │       └── AccesMedError.java
    │   │   │
    │   │   ├── Application/
    │   │   │   └── <NombreEntidad>App.java
    │   │   │
    │   │   ├── Domain/
    │   │   │   ├── Auditable.java
    │   │   │   └── <NombreEntidad>.java
    │   │   │
    │   │   ├── Services/
    │   │   │   ├── DomainServices/
    │   │   │   │   └── <NombreEntidad>DomainService.java
    │   │   │   ├── QueryServices/
    │   │   │   │   └── <NombreEntidad>QueryService.java
    │   │   │   ├── Mappers/
    │   │   │   │   └── <NombreEntidad>Mapper.java
    │   │   │   ├── Errors/
    │   │   │   │   ├── AccesMedException.java
    │   │   │   │   ├── RecursoNoEncontradoException.java
    │   │   │   │   ├── ReglaNegocioException.java
    │   │   │   │   └── ValidacionException.java
    │   │   │   └── Utils/
    │   │   │       └── <HelperGenerico>.java
    │   │   │
    │   │   ├── Repositories/
    │   │   │   └── <NombreEntidad>Repository.java
    │   │   │
    │   │   ├── Records/
    │   │   │   ├── Request/
    │   │   │   │   └── <Accion><NombreEntidad>Request.java
    │   │   │   └── Response/
    │   │   │       └── <Accion><NombreEntidad>Response.java
    │   │   │
    │   │   ├── Security/
    │   │   │   ├── Controllers/
    │   │   │   │   └── AuthController.java
    │   │   │   ├── Application/
    │   │   │   │   └── AuthApp.java
    │   │   │   ├── Domain/
    │   │   │   │   ├── Usuario.java
    │   │   │   │   ├── Rol.java
    │   │   │   │   └── Permiso.java
    │   │   │   ├── Services/
    │   │   │   │   ├── DomainServices/
    │   │   │   │   │   └── UsuarioDomainService.java
    │   │   │   │   ├── QueryServices/
    │   │   │   │   │   └── UsuarioQueryService.java
    │   │   │   │   ├── Mappers/
    │   │   │   │   │   └── UsuarioMapper.java
    │   │   │   │   └── Errors/
    │   │   │   │       └── (reutiliza Services/Errors del núcleo)
    │   │   │   ├── Repositories/
    │   │   │   │   └── UsuarioRepository.java
    │   │   │   ├── Records/
    │   │   │   │   ├── Request/
    │   │   │   │   │   └── LoginRequest.java
    │   │   │   │   └── Response/
    │   │   │   │       └── LoginResponse.java
    │   │   │   ├── Jwt/
    │   │   │   │   ├── JwtService.java
    │   │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   │   └── JwtProvider.java
    │   │   │   └── Config/
    │   │   │       └── SecurityFilterChainConfig.java
    │   │   │
    │   │   └── Agente/
    │   │       ├── Controllers/
    │   │       │   └── <NombreEntidad>AgenteController.java
    │   │       └── Records/
    │   │           ├── Request/
    │   │           │   └── <Accion><NombreEntidad>AgenteRequest.java
    │   │           └── Response/
    │   │               └── <Accion><NombreEntidad>AgenteResponse.java
    │   │       (reutiliza Application/ y Services/ del núcleo; no duplica lógica)
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-staging.yml
    │       ├── application-prod.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/
    │               ├── 20260301120000-Prestacion.yaml
    │               └── 20260305093000-Medico.yaml
    └── test/
        └── java/com/accesmed/backend/      # espejo de la estructura de main
```

> **Nota**: `<NombreEntidad>` es un placeholder — se repite un archivo de cada tipo por
> cada entidad real (`PrestacionController`, `MedicoController`, `TurnoController`, etc.).
> Los archivos sin placeholder (`AccesMedApplication.java`, `Auditable.java`,
> `GlobalExceptionHandler.java`, `AccesMedError.java`, la jerarquía de `AccesMedException`,
> los de `Security/Jwt/` y `Security/Config/`) son únicos y siempre deben existir.

### Notas sobre la estructura

**`Domain` es solo modelo.** La carpeta `Domain` contiene **únicamente las entidades
JPA** (`Medico.java`, `Turno.java`, `Prestacion.java`...) y su clase base `Auditable`
(`@MappedSuperclass` con los campos de auditoría y el criterio de soft delete). Nada de
lógica ni de acceso a datos ahí adentro: son clases de datos con sus anotaciones de
persistencia y relaciones.

**`Services` concentra la lógica de entidad y todo su soporte, subdividida por tipo desde
el inicio.** `Services` no tiene archivos sueltos: se divide en subcarpetas fijas.

- `Services/DomainServices/` — lógica + persistencia de una entidad
  (`PrestacionDomainService.savePrestacion`, `validateCodigoPrestacionIsUnique`, ...).
- `Services/QueryServices/` — consultas de lectura de una entidad (`PrestacionQueryService`).
  **Siempre se llama desde el App**, nunca directo desde el Controller: incluso un
  `getById` simple pasa por el App, que delega en el `QueryService` y mapea con el
  `Mapper`. No hay atajo "para lecturas".
- `Services/Mappers/` — MapStruct (`PrestacionMapper`). Un mapper no tiene lógica de
  negocio, pero es un componente de soporte que solo usan los services y apps para
  convertir Record↔entidad; por eso vive junto a los demás, no como package suelto en la raíz.
- `Services/Errors/` — `AccesMedException` y sus subclases (ver §3).
- `Services/Utils/` — helpers genéricos sin dueño de entidad (formateo de fechas,
  generación de códigos, etc.). Mismo razonamiento que con `Mapper`: es un componente de
  soporte que usan los services y apps, así que se agrupa ahí en vez de quedar suelto
  como package a nivel raíz.

Es la carpeta que más va a crecer.

**Por qué la capa de aplicación NO va dentro de `Services`.** Son dos roles distintos: el
App orquesta un flujo (puede tocar varias entidades), el DomainService conoce una
entidad. Tenerlos separados hace obvio dónde vive cada cosa y evita que `Services` se
convierta en un cajón de sastre.

**Cómo crece cada subcarpeta sin desordenarse.** Dentro de `DomainServices/`,
`QueryServices/` o `Mappers/` los archivos van sueltos por entidad
(`MedicoDomainService`, `TurnoDomainService`, ...). Cuando una de estas subcarpetas
supere ~8-10 archivos de la misma entidad, recién ahí se crea un subpaquete por entidad
(`Services/DomainServices/Medico/`). No antes: no vale la pena tener carpetas de un solo archivo.

**Servicios que usa un solo flujo.** Si un helper lo usa únicamente un App y no
representa lógica de dominio reutilizable, puede vivir como método privado dentro del
App. Se promueve a DomainService recién cuando un segundo flujo lo necesita.

**Los errores se reparten por capa, no en una carpeta transversal.** `Services/Errors/`
tiene las excepciones (`AccesMedException` y sus 3 subclases) porque ahí es donde se
originan. `Controllers/Errors/` tiene el `GlobalExceptionHandler` y `AccesMedError` porque
el handler es un `@ControllerAdvice`: intercepta en el borde HTTP, no en el dominio. El
log de un error de negocio se hace en el Service, en el `throw`; el handler solo loguea lo
que él mismo atrapa (Bean Validation, 500 genérico). Ver detalle en §3.

**No hay carpeta `Shared`.** Antes existía un package transversal genérico; se eliminó y
su contenido se repartió por dueño: excepciones a `Services/Errors`, el advice y el
contrato de error a `Controllers/Errors`, `Auditable` a `Domain` (es parte del modelo de
entidad), y los helpers genéricos a `Services/Utils` (mismo criterio que los `Mapper`:
son soporte que consumen services y apps, no un cajón aparte).

**`Security` como slice vertical.** Usuarios, roles, permisos y auth tienen su propia
mini-estructura por capas dentro de `Security/` (mismo patrón: `Domain` solo entidades,
`Services/{DomainServices,QueryServices,Mappers,Errors}` con la lógica, `Application` con
los casos de uso). Sus excepciones propias (si las hay) viven en `Security/Services/Errors`;
las traduce el mismo `GlobalExceptionHandler` global, no hace falta uno propio salvo
que la seguridad tenga necesidades muy particulares de respuesta. Esto los mantiene
juntos y permitiría, si algún día hiciera falta, extraerlos a un módulo aparte sin tocar
el resto.

**`Agente` sin duplicar negocio.** El agente hace, en el fondo, las mismas operaciones de
turnos que el sistema interno. Entonces **reutiliza los mismos `App` y `Service`**.
Lo único propio del agente es la **entrada**: sus controllers (con la forma de request
que le conviene a Flowise) y sus records. Así, si mañana cambia una regla de negocio de
turnos, se cambia en un solo lugar y sirve para el panel y para el bot.

---

## 5. Convenciones de código (resumen)

El detalle y la revisión automática están en la skill `java-springboot-code-style`. Lo esencial:

- **Nombres de método**: verbo en inglés + concepto en español —
  `createMedico`, `saveMedico`, `findTurnoById`, `validateCodigoPrestacionIsUnique`.
- **Parámetros**: nombre = camelCase del tipo — `createMedico(CrearMedicoRequest crearMedicoRequest)`.
- **Variables de entidad**: español descriptivo del estado — `medicoExistente`,
  `medicoActualizado`, `turnoConfirmado`, `prestacionGuardada`.
- **Records**: uno por endpoint, `<Accion><Entidad>Request/Response`, en `Records/Request` y `Records/Response`.
- **Controllers**: sufijo `Controller`, solo delegan.
- **App**: sufijo `App`, `@Service`, `@Transactional`, orquestan.
- **Inyección** por constructor (con `@RequiredArgsConstructor` de Lombok), nunca por campo.
- **Javadoc**: según la skill `java-springboot-javadoc`.
- **Logging**: `@Slf4j` (Lombok) en cada clase, niveles y ubicación según la skill
  `java-springboot-logging` (ver también §3).

---

## 6. Perfiles y configuración

Tres perfiles: `dev`, `staging`, `prod`. Config en `application-<perfil>.yml`; lo común en
`application.yml`. Se activa con la variable de entorno `SPRING_PROFILES_ACTIVE`.

- **dev** (único configurado por ahora): Postgres local levantada con
  `docker/dev/docker-compose.yml`. Liquibase corre al arrancar. `ddl-auto: validate`
  (nunca `update`/`create`: el esquema lo maneja Liquibase, no Hibernate).
- **staging / prod**: se definen más adelante. La estructura ya queda lista para sumarlos.

Los secretos (password de BD, secret de JWT) **nunca** en el `.yml` versionado: van por
variable de entorno / `.env` (ver `docker/dev/.env.example`).

### Correspondencia con las ramas

Cada perfil tiene su rama: `develop` → `dev`, `staging` → `staging`, `main` → `prod`. Las
features salen de `develop` como `feature/<Entidad o funcionalidad>` y vuelven ahí por PR.
Detalle en `README.md`.

### Convención de changelogs Liquibase

**Un archivo por entidad**, no un archivo por cambio. El archivo acumula toda la historia
de esa tabla: se le van agregando changesets nuevos con el tiempo, nunca se modifica un
changeset ya ejecutado.

**Nombre del archivo**: `YYYYMMDDHHMMSS-NombreEntidad.yaml`, donde el timestamp es la
**fecha de creación del archivo** (el primer changeset), no de la última modificación —
aunque se le sigan agregando changesets después, el nombre no cambia. Ejemplo:
`20260301120000-Prestacion.yaml`.

**Id de cada changeset dentro del archivo**: `YYYYMMDDHHMMSS-<acción>`, con timestamp
propio (la fecha en que ESE changeset se agregó, no la del archivo). Vocabulario fijo de acciones:

- `created` — el primer changeset del archivo (crea la tabla); misma fecha que el nombre del archivo.
- `added-column-<nombre>` — agrega una columna.
- `dropped-column-<nombre>` — elimina una columna.
- `modified-column-<nombre>` — cambia tipo/nullabilidad/default de una columna.
- `added-constraint-<nombre>` — agrega una constraint (UNIQUE, CHECK, FK).
- `added-index-<nombre>` — agrega un índice.
- `renamed-column-<nombre>-to-<nuevoNombre>` — renombra una columna.
- `dropped-table` — elimina la tabla completa (poco común, dado el criterio de soft delete del proyecto).

Ejemplo de archivo con varios changesets acumulados:

```yaml
databaseChangeLog:
  - changeSet:
      id: 20260301120000-created
      author: fsorrentino
      changes:
        - createTable:
            tableName: prestacion
            columns: [...]
  - changeSet:
      id: 20260315091500-added-column-tiempoToleranciaAnuncio
      author: fsorrentino
      changes:
        - addColumn:
            tableName: prestacion
            columns:
              - column:
                  name: tiempo_tolerancia_anuncio
                  type: int
```

**Ventaja práctica**: el `db.changelog-master.yaml` solo necesita un `include` **una vez
por entidad** (cuando se crea el archivo). Los changesets que se agreguen después al mismo
archivo se incluyen automáticamente — no hay que tocar el master de nuevo. Con un archivo
por cambio, en cambio, cada columna nueva implicaría editar el master.

**Trade-off a tener presente**: si dos personas modifican la misma entidad en ramas
distintas al mismo tiempo, van a chocar en el mismo archivo al mergear (más probable que
con archivos separados por cambio). Para el tamaño de este equipo es un riesgo bajo y
manejable — alcanza con coordinar quién toca qué entidad en un momento dado.

---

## 7. Dependencias (pom.xml)

Las **versiones** de cada una están en [`STACK.md`](STACK.md) (todas estables, sobre la
línea Spring Boot 4). Acá va el qué y el porqué de cada dependencia.

- `spring-boot-starter-webmvc` — API REST. Spring Boot 4 renombró `spring-boot-starter-web`
  a `spring-boot-starter-webmvc` (separa MVC de WebFlux); el nombre viejo queda deprecado.
- `spring-boot-starter-data-jpa` — JPA + Hibernate
- `spring-boot-starter-validation` — Bean Validation (`@Valid`, `@NotNull`, ...)
- `spring-boot-starter-security` — autenticación/autorización
- `spring-boot-starter-actuator` — health checks (`/actuator/health` para docker)
- `postgresql` — driver
- `liquibase-core` — migraciones de esquema
- `lombok` — boilerplate (getters, constructores)
- `spring-boot-devtools` / `spring-boot-docker-compose` (scope `runtime`, `optional`) —
  conveniencia de `dev`: restart automático y auto-levantado de
  `docker/dev/docker-compose.yml` al correr `spring-boot:run`. No van a producción.
- `mapstruct` + `mapstruct-processor` + `lombok-mapstruct-binding` — mapeo Record↔entidad
- `hibernate-processor` (scope `provided`, `annotationProcessor`) — genera el **metamodelo
  estático JPA** (`Turno_`, `Prestacion_`...) para el filtrado dinámico con
  `Specification` sin usar strings literales (`root.get("campo")`), ver más abajo. Se
  llamaba `hibernate-jpamodelgen` hasta Hibernate 6; Hibernate ORM 7 lo renombró.
- `springdoc-openapi-starter-webmvc-ui` — Swagger UI (`/swagger-ui.html`)
- `jjwt-api` / `jjwt-impl` / `jjwt-jackson` — tokens JWT
- `spring-boot-starter-{data-jpa,liquibase,security,webmvc}-test` — JUnit 5, Mockito,
  AssertJ y los test slices de cada starter correspondiente (scope test). Spring Boot 4
  modularizó también el soporte de test: cada starter principal tiene su `-test` propio en
  vez de un `spring-boot-starter-test` único.
- `testcontainers` (opcional, scope test) — Postgres real en tests de integración

> Hibernate no se agrega aparte: viene dentro de `spring-boot-starter-data-jpa`.

> **Descartadas a propósito** (las ofrece Initializr por defecto, no son parte del stack):
> `spring-boot-starter-data-jdbc`, `-data-r2dbc`/`r2dbc-postgresql` (el acceso a datos es
> JPA, no JDBC directo ni reactivo), `-data-rest` (auto-expone repos como REST, choca con
> `Controller → App → Service`), `-restclient` (nada lo consume todavía), `-restdocs` /
> `spring-restdocs-mockmvc` / `asciidoctor-maven-plugin` (redundante con springdoc-openapi,
> que ya es la documentación viva). Detalle en `STACK.md`.

### Filtrado dinámico: JPA Specifications + metamodelo estático

Para búsquedas con múltiples filtros opcionales (`search<Entidad>` en el `QueryService`),
se usa **Spring Data JPA Specifications** (`Specification<T>` + `JpaSpecificationExecutor<T>`
en el repository), combinadas con el **metamodelo estático de JPA** generado por
`hibernate-processor` para no usar nombres de campo como string literal.

- **`<Entidad>Criteria`**: `record` con todos los campos opcionales (excepción a la regla
  `<Accion><Entidad>Request`, porque no es un endpoint de escritura sino un objeto de
  filtro). Sin Bean Validation: todo es opcional.
- **`<Entidad>Specifications`**: clase con métodos estáticos, uno por campo filtrable, cada
  uno devuelve `null` si el criterio no vino (Spring Data ignora los `Specification` nulos
  al combinarlos con `Specification.where(...).and(...)`).
- **Metamodelo** (`hibernate-processor`, se declara junto a Lombok en
  `annotationProcessorPaths` del `maven-compiler-plugin` para evitar conflicto entre
  procesadores de anotaciones): usar `Turno_.estadoActual` en vez de
  `root.get("estadoActual")` — seguro ante refactors, error de compilación si el campo no existe.

> **Pendiente de definir**: un helper genérico de soft delete (ej.
> `AuditableSpecifications.isActive()`) que filtre por `deleted_at IS NULL` de forma
> reutilizable. No se implementa todavía porque no está confirmado que **todas** las
> entidades usen `deleted_at` de la misma manera — a resolver antes de generalizarlo.
