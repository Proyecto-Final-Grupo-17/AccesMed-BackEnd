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
| Records agrupados **por entidad primero**: `Records/<Entidad>/Request/` y `Records/<Entidad>/Response/` | Todo lo de una feature queda en una sola carpeta, igual que el resto de la arquitectura (`PrestacionApp`, `PrestacionController`, `PrestacionMapper`). Con `Request/<Entidad>` el request y el response del mismo endpoint quedan en ramas distintas del árbol. Escala mejor: una entidad nueva = un paquete nuevo, no dos carpetas tocadas. |
| `Controllers` tiene tres subpaquetes fijos: **`Errors/`**, **`ControllersConfig/`** y **`Validators/`** | `Errors/` es el borde HTTP del manejo de errores (advice + contrato). `ControllersConfig/` es la configuración propia de la capa web (`OpenApiConfig`, y más adelante CORS, interceptores, config de MVC): describe la superficie HTTP, así que vive con los controllers. `Validators/` tiene las constraints custom de Bean Validation (anotación + `ConstraintValidator`) que se aplican a nivel de record — Bean Validation es responsabilidad del Controller (dispara `@Valid`), así que sus constraints propias viven ahí, no en `Services`. |
| `Config/` en la raíz queda solo para configuración **transversal de infraestructura** (`SecurityConfig`, `JpaAuditingConfig`) | Seguridad es un filter chain que atraviesa toda la app y el auditing es de persistencia: no son "de controllers". Ahí van también las próximas configs de infraestructura (JWT, cache, async). |
| Ruta base del controller: **`/accesmed-api/<Entidad>`** (PascalCase singular) y cada método agrega **su recurso** (`/Prestacion`, `/Agenda`) | El prefijo `accesmed-api` identifica la API del proyecto; la ruta de clase agrupa por entidad y la del método nombra el recurso concreto que esa operación toca, que en controllers de agregado no siempre es la entidad raíz (ej. `AgendaMedicoController` → `/Agenda`, `/Horario`). |
| **PUT/PATCH con body**: `@PathVariable id` + record, y el Controller valida que ambos ids coincidan | El id en la ruta es lo que identifica el recurso en REST; el del body es el que llega del front. Es una inconsistencia de transporte HTTP, no una regla de negocio, así que la corta el Controller antes de llegar al App (422), en vez de actualizar en silencio el recurso equivocado. |
| **PATCH sin body** (solo `@PathVariable id`) para actualizar un campo puntual | Cuando el cambio no lleva datos (marcar una bandera, avanzar un estado), un record vacío sería ruido: el id alcanza. |
| **Soft delete se expone como `DELETE`**, método `softDelete<Entidad>` en las 3 capas, responde `200` con un body chico (`id`, `deletedAt`, `deletedReason`) | Para el front la semántica es "dar de baja este recurso"; que internamente sea un `deleted_at` y no un borrado físico es un detalle de implementación que no debe filtrarse al verbo HTTP. El nombre del método sí lo dice: `softDelete<Entidad>`, no `delete<Entidad>` a secas, para que sea autoexplicativo sin depender solo del Javadoc. El body devuelve la confirmación de la baja, no queda en silencio con `204`. |
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
| El `QueryService` se inyecta y se llama **desde el Controller para lecturas (`GET`)**; el `App` no participa en el camino de lectura | Los endpoints de lectura (`GET /list`, `GET /Buscar`) inyectan el `QueryService` directo; es un passthrough puro (no hay validación, orquestación, ni cambio de estado). El `App` queda solo para mutaciones (`POST`, `PATCH`, `DELETE`), donde sí hay orquestación. El `QueryService` devuelve `Response` mapeado (el `Mapper` vive adentro). Mantiene la separación de responsabilidades: lecturas ≠ mutaciones. |
| Nombres de método de actualización: **`fullUpdate<Entidad>`** (PUT), **`partialUpdate<Entidad>`** (PATCH genérico) y **`update<Concepto><Entidad>`** (PATCH de un grupo de campos específico, ej. `updateToleranciasPrestacion`) | El nombre distingue de entrada tres casos distintos: reemplazo completo, actualización de un subconjunto arbitrario de campos, y un caso de uso acotado que ya sabe de antemano qué campos toca (no es "parcial genérico" con dos campos, es una operación de negocio propia). |
| `partialUpdate<Entidad>` (PATCH genérico): un campo en `null` significa **"no lo toques"**, nunca "vacíalo" | Evita la ambigüedad JSON entre "campo ausente" y "campo enviado en `null`" sin necesitar un wrapper (`JsonNullable`) en todos los campos nullable del record. Si un campo necesita poder vaciarse de verdad, no se fuerza acá: se resuelve con la fila siguiente. |
| `update<Concepto><Entidad>` (PATCH de campos específicos) puede declarar, campo por campo, que `null` signifique **"vaciar"** — usando `JsonNullable<T>` (`org.openapitools:jackson-databind-nullable`) solo en esos campos del record, y solo cuando el requisito de negocio lo pide | Es un endpoint acotado y conocido de antemano (ej. tolerancias de una prestación), así que se puede documentar explícitamente en el Javadoc qué significa `null` para cada campo. No se generaliza `JsonNullable` a todo el proyecto: se paga ese costo únicamente donde hay un requisito real de vaciar el campo. |

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
// 1. Controller (package Controllers) — @RequestMapping("/accesmed-api/Prestacion") en la clase
@PostMapping("/Prestacion")
public ResponseEntity<CreatePrestacionResponse> createPrestacion(
        @Valid @RequestBody CreatePrestacionRequest createPrestacionRequest) {
    CreatePrestacionResponse response = prestacionApp.createPrestacion(createPrestacionRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

// 2. App (package Application)
@Transactional
public CreatePrestacionResponse createPrestacion(CreatePrestacionRequest createPrestacionRequest) {

    prestacionDomainService.validateCodigoPrestacionIsUnique(createPrestacionRequest.codigo());
    Prestacion prestacionNueva = prestacionMapper.toEntity(createPrestacionRequest);
    Prestacion prestacionGuardada = prestacionDomainService.savePrestacion(prestacionNueva);
    return prestacionMapper.toCreateResponse(prestacionGuardada);

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
  "path": "/accesmed-api/Turno/Turno"
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
│   ├── FRONTEND-GUIA.md
│   └── feature/
│       └── <Entidad-o-Funcionalidad>.md  # doc funcional por feature (springboot-feature-generator / feature-documenter)
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
│       ├── springboot-feature-generator/
│       ├── domain-schema-generator/
│       └── feature-documenter/
└── src/
    ├── main/
    │   ├── java/com/accesmed/backend/
    │   │   ├── AccesMedApplication.java
    │   │   │
    │   │   ├── Config/                    # config transversal de infraestructura
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── JpaAuditingConfig.java
    │   │   │
    │   │   ├── Controllers/
    │   │   │   ├── <NombreEntidad>Controller.java
    │   │   │   ├── Errors/
    │   │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   │   └── AccesMedError.java
    │   │   │   └── ControllersConfig/      # config propia de la capa web
    │   │   │       └── OpenApiConfig.java  # (+ CORS, interceptores, config MVC)
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
    │   │   │   └── <NombreEntidad>/
    │   │   │       ├── Request/
    │   │   │       │   └── <Accion><NombreEntidad>Request.java
    │   │   │       └── Response/
    │   │   │           └── <Accion><NombreEntidad>Response.java
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
    │   │   │   │   └── Auth/
    │   │   │   │       ├── Request/
    │   │   │   │       │   └── LoginRequest.java
    │   │   │   │       └── Response/
    │   │   │   │           └── LoginResponse.java
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
    │   │           └── <NombreEntidad>/
    │   │               ├── Request/
    │   │               │   └── <Accion><NombreEntidad>AgenteRequest.java
    │   │               └── Response/
    │   │                   └── <Accion><NombreEntidad>AgenteResponse.java
    │   │       (reutiliza Application/ y Services/ del núcleo; no duplica lógica)
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-staging.yml
    │       ├── application-prod.yml
    │       └── liquibase-db-changelogs/
    │           ├── master.xml
    │           └── changelogs/
    │               └── YYYYMMDDHHMMSS-<NombreEntidad>.xml
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

**Alta de una entidad con máquina de estados (estado + histórico): coreografía
`save → openHistorico`, en ese orden.** Entidades como `Prestacion` o `Plan` llevan su
ciclo de vida por estados. El estado vigente **no se materializa** en la entidad (no hay
columna `estadoActual`): la **única fuente** es el tramo de su tabla de histórico
(`HistoricoEstadoPrestacion`, `HistoricoEstadoPlan`...) con `fecha_hora_fin` vacío. La
relación entidad↔histórico es **unidireccional**: solo el `@ManyToOne` del histórico la
mapea. El `DomainService` del histórico (`HistoricoEstadoPrestacionDomainService`,
`HistoricoEstadoPlanDomainService`) expone `openHistoricoInicial<Entidad>` para el alta y
`getEstadoVigente(id)` / `getEstadosVigentes(ids)` para leer el estado; el `App` orquesta:

1. `<entidad>DomainService.save<Entidad>(entidadNueva)` — persiste la entidad. Recién acá
   tiene un `id`.
2. `openHistoricoInicial<Entidad>(entidadGuardada)` — crea y guarda el primer tramo del
   histórico (estado inicial `NO_PUBLICADA`/`NO_PUBLICADO`), referenciando la entidad ya
   persistida. **Se llama después de guardar.** Si se llama antes, el histórico referencia
   una entidad transitoria (sin `id`) y Hibernate rechaza el insert
   (`TransientPropertyValueException`).

Ya no hay un paso `seedEstadoInicial` previo al `save`: no existe columna que sembrar. En
los responses, el estado que el front espera (`estadoActual`) lo alimenta el `App`: para
un alta o una transición es una constante conocida (`NO_PUBLICADA`, `PUBLICADA`...); para
una lectura puntual lo pide con `getEstadoVigente(id)`; y para un listado carga todos los
estados de la página en una sola consulta (`getEstadosVigentes(ids) → Map<UUID, EstadoX>`,
sin N+1) y lo pasa al mapper. El orquestador es siempre el `App`, dentro de su
`@Transactional`; los `DomainService` no se llaman entre sí (ver §4). Cualquier entidad
nueva con este mismo patrón debe seguir esta coreografía.

**Transición de estado (`change<Estado><Entidad>`): cerrar el tramo vigente con
`saveAndFlush`, nunca con `save`.** El esquema protege "como máximo un tramo vigente por
entidad" con un índice único parcial (`uq_historico_estado_prestacion_vigente`,
`uq_historico_estado_plan_vigente`, y a futuro `uq_historico_estado_turno_vigente` en
`Turno`): `UNIQUE (<entidad>_id) WHERE fecha_hora_fin IS NULL`. El método de transición
cierra el tramo vigente (`UPDATE`, setea `fechaHoraFin`) y abre uno nuevo (`INSERT`, con
`fechaHoraFin` en `null`) dentro de la misma transacción. Si ambos se guardan con
`save()`, Hibernate no los ejecuta en el orden del código: agrupa las acciones del flush
por tipo y manda **todos los `INSERT` antes que los `UPDATE`**, así que el `INSERT` del
tramo nuevo llega a la base antes que el `UPDATE` que cierra el viejo — por un instante
hay dos filas vigentes para la misma entidad y el índice único parcial lo rechaza
(`ConstraintViolationException`). La solución es forzar el flush del cierre antes de
crear el tramo nuevo: `historicoEstadoXRepository.saveAndFlush(tramoVigente)` en vez de
`save(tramoVigente)`. Cualquier entidad nueva con este mismo patrón (`Turno` incluido)
tiene que replicar este `saveAndFlush` en su transición de estado.

**Los errores se reparten por capa, no en una carpeta transversal.** `Services/Errors/`
tiene las excepciones (`AccesMedException` y sus 3 subclases) porque ahí es donde se
originan. `Controllers/Errors/` tiene el `GlobalExceptionHandler` y `AccesMedError` porque
el handler es un `@ControllerAdvice`: intercepta en el borde HTTP, no en el dominio. El
log de un error de negocio se hace en el Service, en el `throw`; el handler solo loguea lo
que él mismo atrapa (Bean Validation, 500 genérico). Ver detalle en §3.

**`Records` se agrupa por entidad, no por sentido.** El primer nivel es la entidad y recién
adentro van `Request/` y `Response/`: `Records/Prestacion/Request/CreatePrestacionRequest`,
`Records/Prestacion/Response/CreatePrestacionResponse`. Así todos los records de una feature
quedan juntos (y el request y el response de un mismo endpoint, a un paso de distancia),
igual que el resto de la arquitectura, que ya está organizada por entidad dentro de cada
rol (`PrestacionController`, `PrestacionApp`, `PrestacionMapper`). Agregar una entidad
nueva crea un paquete, no toca la estructura existente. Vale lo mismo dentro de `Security/`
y `Agente/`: sus `Records/` también se agrupan por entidad o funcionalidad.

**`Controllers` tiene dos subcarpetas fijas: `Errors/` y `ControllersConfig/`.**
`Errors/` ya se explicó arriba. `ControllersConfig/` es la **configuración propia de la
capa web**: `OpenApiConfig` (metadata de Swagger, o sea la descripción de la superficie
HTTP) y, cuando hagan falta, CORS, interceptores y config de MVC. `Config/` en la raíz
queda para la configuración **transversal de infraestructura** (`SecurityConfig`,
`JpaAuditingConfig`, y más adelante JWT, cache, async): la seguridad es un filter chain que
atraviesa toda la app y el auditing es de persistencia — meterlos bajo `Controllers/` diría
algo falso sobre a qué capa pertenecen.

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
- **Parámetros**: nombre = camelCase del tipo — `createMedico(CreateMedicoRequest createMedicoRequest)`.
- **Variables de entidad**: español descriptivo del estado — `medicoExistente`,
  `medicoActualizado`, `turnoConfirmado`, `prestacionGuardada`.
- **Records**: uno por endpoint, `<Accion><Entidad>Request/Response` con la **acción en inglés**
  (`Create`, `Update`, `Get`, `List`, `Enable`, `Delete`...), en
  `Records/<Entidad>/Request` y `Records/<Entidad>/Response`.
- **Controllers**: sufijo `Controller`, solo delegan. Rutas y verbos: ver §5.1.
- **App**: sufijo `App`, `@Service`, `@Transactional`, orquestan.
- **Inyección** por constructor (con `@RequiredArgsConstructor` de Lombok), nunca por campo.
- **Javadoc**: según la skill `java-springboot-javadoc`.
- **Logging**: `@Slf4j` (Lombok) en cada clase, niveles y ubicación según la skill
  `java-springboot-logging` (ver también §3).

### 5.1 Rutas y verbos HTTP de los controllers

**Ruta base de la clase**: `@RequestMapping("/accesmed-api/<Entidad>")`, con la entidad en
PascalCase singular — `/accesmed-api/Prestacion`, `/accesmed-api/AgendaMedico`.

**Ruta de cada método**: el **recurso concreto** sobre el que opera esa operación. En un
controller de una sola entidad coincide con ella (`createPrestacion` → `/Prestacion`); en
uno de agregado, cada método nombra su propio recurso (`createAgenda` → `/Agenda`,
`createHorario` → `/Horario`).

| Operación | Método App | Firma | Status |
|---|---|---|---|
| Crear | `create<Entidad>` | `@PostMapping("/<Recurso>")` + `@Valid @RequestBody` | 201 |
| Obtener por id | `get<Entidad>ById` | `@GetMapping("/<Recurso>/{id}")` + `@PathVariable UUID id` | 200 |
| Listar | `list<Entidad>` | `@GetMapping("/<Recurso>")` | 200 |
| Actualizar completo (reemplaza el recurso entero) | `fullUpdate<Entidad>` | `@PutMapping("/<Recurso>/{id}")` + `@PathVariable UUID id` + `@Valid @RequestBody` | 200 |
| Actualizar parcial genérico (subconjunto arbitrario de campos) | `partialUpdate<Entidad>` | `@PatchMapping("/<Recurso>/{id}")` + `@PathVariable UUID id` + `@Valid @RequestBody` | 200 |
| Actualizar un grupo de campos específico y conocido de antemano | `update<Concepto><Entidad>` | `@PatchMapping("/<Recurso>/<Concepto>/{id}")` + `@PathVariable UUID id` + `@Valid @RequestBody` | 200 |
| Actualizar un campo puntual (sin body) | `<verbo><Concepto><Entidad>` (ej. `activate<Entidad>`) | `@PatchMapping("/<Recurso>/{id}")` + solo `@PathVariable UUID id` | 200 |
| Baja lógica (soft delete) | `softDelete<Entidad>` | `@DeleteMapping("/<Recurso>/{id}")` + `@PathVariable UUID id` | 200 |

**`PUT`/`PATCH` con body llevan el `id` en la ruta *además* del record**, y el `id` de la
ruta y el del record **tienen que coincidir**. La comprobación la hace el **Controller**,
como primer paso del método, antes de delegar en el App: es una inconsistencia de
transporte HTTP, no una regla de negocio, así que no tiene sentido que la capa de
aplicación se entere de ella.

```java
// Controllers/PrestacionController.java
@PutMapping("/Prestacion/{id}")
public ResponseEntity<UpdatePrestacionResponse> updatePrestacion(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePrestacionRequest updatePrestacionRequest) {

    log.info("Solicitud recibida: actualizar prestación id={}", id);

    //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
    if (!id.equals(updatePrestacionRequest.id())) {
        log.warn("Id de ruta ({}) distinto al del body ({})", id, updatePrestacionRequest.id());
        throw new ValidacionException(getClass(),
                List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
    }

    return ResponseEntity.ok(prestacionApp.updatePrestacion(id, updatePrestacionRequest));

}

// Application/PrestacionApp.java
@Transactional
public UpdatePrestacionResponse updatePrestacion(UUID id,
        UpdatePrestacionRequest updatePrestacionRequest) {

    log.info("Actualización de prestación iniciada: id={}", id);

    ...

}
```

**`PATCH` sin body**: cuando el cambio es de un campo puntual y no necesita datos, el
método recibe solo el `id` — no se crea un record vacío.

```java
@PatchMapping("/Prestacion/{id}/Activacion")
public ResponseEntity<ActivarPrestacionResponse> activatePrestacion(@PathVariable UUID id) { ... }
```

**Baja lógica = `DELETE`**: el soft delete se expone como `DELETE`, el método se llama
`softDelete<Entidad>` en las 3 capas (Controller/App/DomainService) y responde `200` con
un body chico (`id`, `deletedAt`, `deletedReason`) en vez de un `204` vacío — es la
confirmación de la baja, no metadata genérica de auditoría. Que internamente sea un
`deleted_at` y no un borrado físico es un detalle de implementación que no cambia el verbo.

### 5.2 `fullUpdate` vs `partialUpdate` vs `update<Concepto>`: quién es cada uno y qué significa un `null`

Tres operaciones de "actualizar" distintas, con nombre y semántica propios — no una sola
firma genérica reutilizada para todo:

- **`fullUpdate<Entidad>`** (`PUT`): reemplaza el recurso completo. El record trae todos los
  campos editables; no hay ambigüedad de `null` porque siempre viajan todos.
- **`partialUpdate<Entidad>`** (`PATCH` genérico): el record acepta cualquier subconjunto de
  campos editables. **Un campo en `null` significa "no lo toques"**, nunca "vacíalo" — el
  Mapper/App ignora los campos `null` al aplicar el update. Simple, sin wrappers, cubre el
  caso general de "actualizar lo que mandaste".
- **`update<Concepto><Entidad>`** (`PATCH`, ruta propia `/<Recurso>/<Concepto>/{id}`): un
  endpoint acotado para un grupo de campos ya conocido de antemano (ej. las tolerancias de
  una prestación), que se crea **solo cuando el requisito de negocio pide poder vaciar
  alguno de esos campos**. Ahí sí se declara `JsonNullable<T>`
  (`org.openapitools:jackson-databind-nullable`) campo por campo, para distinguir "no vino"
  de "vino en `null`" — y se documenta en el Javadoc qué significa el `null` de cada campo.

```java
// Records/Prestacion/Request/UpdateToleranciasPrestacionRequest.java
public record UpdateToleranciasPrestacionRequest(
        @NotNull Long id,
        JsonNullable<Integer> tiempoToleranciaAnuncio,   // null explícito = vaciar el campo
        JsonNullable<Integer> tiempoToleranciaAusencia   // ausente = no tocar
) {}

// Controllers/PrestacionController.java
@PatchMapping("/Prestacion/Tolerancias/{id}")
public ResponseEntity<UpdateToleranciasPrestacionResponse> updateToleranciasPrestacion(
        @PathVariable Long id,
        @Valid @RequestBody UpdateToleranciasPrestacionRequest updateToleranciasPrestacionRequest) {

    //El id de la ruta identifica el recurso: si el body trae otro, el request es inconsistente
    if (!id.equals(updateToleranciasPrestacionRequest.id())) {
        throw new ValidacionException(getClass(),
                List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
    }

    return ResponseEntity.ok(prestacionApp.updateToleranciasPrestacion(id, updateToleranciasPrestacionRequest));

}

// Application/PrestacionApp.java
/**
 * Actualiza las tolerancias de una prestación. Un campo enviado en {@code null} vacía esa
 * tolerancia; un campo ausente del JSON la deja sin tocar.
 *
 * @param id {@code Long} id del recurso, tomado de la ruta
 * @param updateToleranciasPrestacionRequest {@code UpdateToleranciasPrestacionRequest} tolerancias a aplicar
 * @return {@code UpdateToleranciasPrestacionResponse} la prestación con las tolerancias actualizadas
 */
@Transactional
public UpdateToleranciasPrestacionResponse updateToleranciasPrestacion(Long id,
        UpdateToleranciasPrestacionRequest updateToleranciasPrestacionRequest) {

    Prestacion prestacionExistente = prestacionDomainService.findPrestacionById(id);

    //isPresent() distingue "vino en el JSON" (con o sin null) de "no vino"
    if (updateToleranciasPrestacionRequest.tiempoToleranciaAnuncio().isPresent()) {
        prestacionExistente.setTiempoToleranciaAnuncio(
                updateToleranciasPrestacionRequest.tiempoToleranciaAnuncio().orElse(null));
    }

    ...

}
```

No se generaliza `JsonNullable` a todos los records: se paga ese costo de tipado solo en el
endpoint puntual que de verdad necesita vaciar un campo. El resto del proyecto sigue con
tipos planos y la regla simple de `partialUpdate` ("`null` = no tocar").

### 5.2.1 Cuarto caso: actualización por delta de composición

Ninguno de los tres casos anteriores describe `updateAgendaMedico`: el request no trae
campos del recurso raíz (`AgendaMedico` no tiene `horariosAAgregar` como columna), trae
**altas y bajas de sus hijos** (`AgendaHorariosDia`). Forzarle el nombre `partialUpdate`
mentiría sobre qué actualiza.

- **`update<Entidad>`** (`PATCH`, ruta propia `/<Recurso>/{id}`): para agregados donde el
  request describe el delta de una composición, no el estado final del recurso. El record
  lleva colecciones con el sufijo **`<algo>AAgregar`**/**`<algo>AExcluir`** (ej.
  `horariosAAgregar`, `horariosAExcluir`, `fechasAExcluir`), nunca el objeto completo. `null`
  o ausente en una de esas colecciones significa "sin cambios en ese frente" — igual que
  `partialUpdate`, pero aplicado a colecciones de hijos en vez de a campos escalares.

```java
// Records/AgendaMedico/Request/UpdateAgendaMedicoRequest.java
public record UpdateAgendaMedicoRequest(
        @NotNull UUID id,
        List<@Valid HorarioAAgregarRequest> horariosAAgregar,
        List<UUID> horariosAExcluir,
        List<UUID> diasAExcluir
) {}

// Controllers/AgendaMedicoController.java
@PatchMapping("/Agenda/{id}")
public ResponseEntity<UpdateAgendaMedicoResponse> updateAgendaMedico(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAgendaMedicoRequest updateAgendaMedicoRequest) {

    if (!id.equals(updateAgendaMedicoRequest.id())) {
        throw new ValidacionException(getClass(),
                List.of("El id de la ruta no coincide con el id enviado en el cuerpo del request."));
    }

    return ResponseEntity.ok(agendaMedicoApp.updateAgendaMedico(updateAgendaMedicoRequest));

}
```

El `App` resuelve primero **toda** la unión de lo que el delta daría de baja (bajas
directas más las arrastradas por una baja en cascada, ej. excluir un día arrastra sus
horarios), valida las guardas restrictivas contra esa unión **antes de escribir nada**, y
recién después aplica bajas y altas en el mismo orden que documenta la feature (ver
`Docs/Features/Agenda.md`). El response no devuelve el recurso completo: devuelve los
conteos de cada efecto aplicado (`cantidadHorariosAgregados`, `cantidadHorariosExcluidos`,
...), porque el cliente ya conoce el delta que mandó.

Este cuarto caso va a reaparecer en `Turno` (altas/bajas de `HistoricoEstadoTurno` o de
adjuntos, cuando exista `Archivo`).

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

Formato **XML**. **Un archivo por entidad**, no un archivo por cambio. El archivo acumula
toda la historia de esa tabla: se le van agregando changesets nuevos con el tiempo. La
skill `.claude/skills/domain-schema-generator` aplica esta convención al crear o editar
changelogs, generándolos siempre coordinados con la entidad JPA correspondiente (mapeo
Bean Validation ↔ constraint de esquema incluido) — se usa sola o invocada por
`springboot-feature-generator`.

**Nombre del archivo**: `YYYYMMDDHHMMSS-NombreEntidad.xml`, donde el timestamp es la
**fecha de creación del archivo** (el primer changeset), no de la última modificación —
aunque se le sigan agregando changesets después, el nombre no cambia. Ejemplo:
`20260301120000-Prestacion.xml`.

**Id de cada changeset dentro del archivo**: `YYYYMMDDHHMMSS-<acción>`, con timestamp
propio (la fecha en que ESE changeset se agregó, no la del archivo — excepto el primero,
que comparte fecha con el nombre del archivo). Vocabulario fijo de acciones, centrado en
la tabla (no en la columna individual):

- `added-table-<Entidad>` — primer changeset del archivo (crea la tabla); misma fecha que el nombre del archivo.
- `updated-table-<Entidad>-<detalle>` — cualquier alteración posterior a la tabla ya creada
  (agregar/quitar/modificar/renombrar columna, agregar constraint, agregar índice...).
  `<detalle>` es libre y descriptivo, tantas veces se repita el patrón como haga falta:
  `updated-table-Medico-added-column-telefono`,
  `updated-table-Turno-added-index-idx-turno-fecha`,
  `updated-table-Prestacion-added-constraint-uq-prestacion-codigo`.
- `deleted-table-<Entidad>` — elimina la tabla completa (poco común, dado el criterio de soft delete del proyecto).

**Nomenclatura estándar de objetos de esquema** — siempre `snake_case`, sin excepción
(tablas, columnas, triggers, constraints, índices, funciones/procedimientos):

| Objeto | Convención | Ejemplo |
|---|---|---|
| Primary key | `pk_<tabla>` | `pk_prestacion` |
| Foreign key | `fk_<tabla_origen>_<tabla_destino>` (o `fk_<tabla>_<columna>` si hay más de una FK a la misma tabla destino) | `fk_turno_agenda_medico_horarios_rango` |
| Unique | `uq_<tabla>_<columna(s)>` | `uq_prestacion_codigo` |
| Check | `ck_<tabla>_<regla>` | `ck_turno_fecha_hasta_posterior` |
| Index | `idx_<tabla>_<columna(s)>` | `idx_turno_fecha` |
| Trigger | `trg_<tabla>_<momento>_<evento>` | `trg_turno_before_update` |
| Función / procedimiento | `fn_<acción_negocio>` / `sp_<acción_negocio>` | `fn_calcular_slot_turno` |

**Comentarios dentro del changeset**: cuando un `createTable` (u otro changeset largo)
tiene varias secciones, se dividen con un comentario XML banner de ancho fijo, por ejemplo:

```xml
<!-- ====== Relaciones ====== -->
<!-- ====== Columnas ====== -->
<!-- ====== Columnas auditoría (incluye a la baja) ====== -->
```

Ejemplo de archivo con varios changesets acumulados:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="20260301120000-added-table-Prestacion" author="fsorrentino">
        <createTable tableName="prestacion">
            <!-- ====== Columnas ====== -->
            <column name="id" type="bigint">
                <constraints primaryKey="true" primaryKeyName="pk_prestacion"/>
            </column>
            <column name="codigo" type="varchar(50)">
                <constraints nullable="false"/>
            </column>
            <!-- ====== Columnas auditoría (incluye a la baja) ====== -->
            <column name="created_at" type="timestamptz">
                <constraints nullable="false"/>
            </column>
            <column name="deleted_at" type="timestamptz"/>
        </createTable>
    </changeSet>

    <changeSet id="20260315091500-updated-table-Prestacion-added-column-tiempo-tolerancia-anuncio" author="fsorrentino">
        <addColumn tableName="prestacion">
            <column name="tiempo_tolerancia_anuncio" type="int"/>
        </addColumn>
    </changeSet>

</databaseChangeLog>
```

**Include en el master**: el `master.xml` solo necesita un `<include>` **una vez por
entidad** (cuando se crea el archivo), con `relativeToChangelogFile="true"`. Los
changesets que se agreguen después al mismo archivo se incluyen automáticamente — no hay
que tocar el master de nuevo. Con un archivo por cambio, en cambio, cada columna nueva
implicaría editar el master.

```xml
<include file="changelogs/20260301120000-Prestacion.xml" relativeToChangelogFile="true"/>
```

**Mutabilidad — regla distinta según el entorno**: en `staging`/`prod`, un changeset ya
ejecutado **nunca** se modifica (se agrega uno nuevo) — es la regla estándar de Liquibase.
En **`dev`** se permite editar directamente un changeset ya ejecutado, porque la base es
solo de prueba: alcanza con resetear el entorno (`docker compose -f docker/dev/docker-compose.yml
down -v && docker compose -f docker/dev/docker-compose.yml up -d`) para que el tracking
de Liquibase (`DATABASECHANGELOG`) quede consistente con el changelog editado.

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
- `org.openapitools:jackson-databind-nullable` (**se agrega recién cuando el primer
  `update<Concepto><Entidad>` lo necesite**, no de entrada) — provee `JsonNullable<T>`
  para distinguir "campo ausente" de "campo enviado en `null`" en los `PATCH` de campo
  específico que necesitan poder vaciar un valor (ver §5.2).
- `spring-boot-starter-{data-jpa,liquibase,security,webmvc}-test` — JUnit 5, Mockito,
  AssertJ y los test slices de cada starter correspondiente (scope test). Spring Boot 4
  modularizó también el soporte de test: cada starter principal tiene su `-test` propio en
  vez de un `spring-boot-starter-test` único.
- `spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql` (scope
  test) — Postgres 16 real y efímera para `AccesMedApplicationTests`
  (`TestcontainersConfiguration`, `@ServiceConnection`): valida Liquibase/`ddl-auto`
  contra un contenedor descartable, aislado de la Postgres de `docker/dev/docker-compose.yml`

> Hibernate no se agrega aparte: viene dentro de `spring-boot-starter-data-jpa`.

> **Descartadas a propósito** (las ofrece Initializr por defecto, no son parte del stack):
> `spring-boot-starter-data-jdbc`, `-data-r2dbc`/`r2dbc-postgresql` (el acceso a datos es
> JPA, no JDBC directo ni reactivo), `-data-rest` (auto-expone repos como REST, choca con
> `Controller → App → Service`), `-restclient` (nada lo consume todavía), `-restdocs` /
> `spring-restdocs-mockmvc` / `asciidoctor-maven-plugin` (redundante con springdoc-openapi,
> que ya es la documentación viva). Detalle en `STACK.md`.

### Filtrado dinámico: JPA Specifications + metamodelo estático (modelo JHipster)

Para listados con múltiples filtros opcionales (`findEspecialidades` en el `QueryService`), se usa
**Spring Data JPA Specifications** (`Specification<T>` + `JpaSpecificationExecutor<T>` en el
repository), combinadas con el **metamodelo estático de JPA** generado por
`hibernate-processor` para no usar nombres de campo como string literal. El diseño sigue el
mecanismo de JHipster (Criteria + `QueryService`), vendorizado a mano en
`Services/QueryServices/Filtering/` en vez de sumar la dependencia
`tech.jhipster:jhipster-framework` completa.

- **`Filter<T>`** (`Services/QueryServices/Filtering/Filter.java`): filtro genérico de un
  campo, con los operadores `equals`/`notEquals`/`in`/`notIn`/`specified` (presencia/
  ausencia). Dos subclases agregan operadores propios del tipo de dato:
  **`RangeFilter<T extends Comparable<? super T>>`** suma `greaterThan`/`lessThan`/
  `greaterThanOrEqual`/`lessThanOrEqual` (rangos: fechas, números); **`StringFilter`** suma
  `contains` (`LIKE` case-insensitive).
- **Reificaciones concretas** (`UUIDFilter`, `InstantFilter extends RangeFilter<Instant>`,
  `BooleanFilter`, y una por cada enum de dominio que entre en un Criteria — ej.
  `EstadoPrestacionFilter extends Filter<EstadoPrestacion>`): Spring necesita un tipo
  concreto, no uno genérico, para bindear los query params anidados de un `GET`
  (`campo.equals=x`) y para que el esquema de OpenAPI sea legible.
- **`<Entidad>Criteria`** (`Records/<Entidad>/Criteria/<Entidad>Criteria.java`): clase
  mutable con un campo `Filter`/`RangeFilter`/`StringFilter` (o su reificación) por campo
  filtrable — excepción a la regla `<Accion><Entidad>Request` (no es un endpoint de
  escritura sino un objeto de filtro para un `GET`) y a "records para DTOs" (necesita
  setters + constructor vacío para que Spring la bindee, no un `record`). Sin Bean
  Validation: todo campo es opcional. Anotada con
  `@org.springdoc.core.annotations.ParameterObject` para que Swagger la aplane como
  parámetros de query individuales en vez de mostrarla como un objeto anidado.
- **`<Entidad>QueryService extends AbstractFiltroQueryService<Entidad, Criteria>`**
  (`Services/QueryServices/<Entidad>QueryService.java`): inyecta el `Mapper` de la entidad,
  decorada con `@Transactional(readOnly = true)`. La base (`AbstractFiltroQueryService`)
  implementa `findByCriteria(criteria, pageable)` (devuelve `Page<Entidad>`) y expone los
  helpers `buildSpecification`/`buildRangeSpecification`/`buildStringSpecification`, que
  traducen cada `Filter` a un fragmento de `Specification` usando el metamodelo
  (`Prestacion_.nombre`) o, para relaciones, un `root.join(...)` (ej. `especialidadId` sobre
  `Prestacion_.especialidad`). Cada subclase implementa `getRepository()`, `createSpecification(criteria)`,
  y además expone dos métodos públicos que devuelven **Response mapeados**:
  - **`find<Entidad>ByCriteria(criteria)`**: devuelve `Get<Entidad>Response` (singular).
  - **`find<Entidad>s(criteria, pageable)`**: devuelve `PageResponse<List<Entidad>Response>` (paginado).
  El mapeo a Response vive en el `QueryService`, no en el `App` — el `Controller` inyecta
  el `QueryService` directo (no el `App`) para `GET`.
- **Metamodelo** (`hibernate-processor`, se declara junto a Lombok en
  `annotationProcessorPaths` del `maven-compiler-plugin` para evitar conflicto entre
  procesadores de anotaciones): usar `Prestacion_.codigo` en vez de
  `root.get("codigo")` — seguro ante refactors, error de compilación si el campo no existe.
- **`PageResponse<T>`** (`Services/QueryServices/Filtering/PageResponse.java`): envelope de
  paginación (`content`, `page`, `size`, `totalElements`, `totalPages`) que devuelven todos
  los endpoints de listado, armado con `PageResponse.from(page, mapper::toListResponse)`. Se
  prefiere a serializar `Page` directo (formato inestable, Spring lo desaconseja) o a mandar
  la paginación por headers HTTP (más incómodo de consumir para el bot de WhatsApp/Flowise
  que para el panel). El `Controller` recibe además `@ParameterObject Pageable pageable`
  (con `@PageableDefault` para el tamaño/orden por defecto).
- Excepción de ubicación: `Filtering/` y `PageResponse` viven en `Services/QueryServices/`
  y no en `Records/`, porque son infraestructura transversal a *todos* los listados, no de
  una entidad puntual — el proyecto no tiene carpeta `Shared` (§5), así que esto vive en el
  rol que lo produce (`QueryServices`), no en uno aparte.

### `find<Entidad>ById` se retira: se resuelve con el mismo Criteria, en singular (sin paso por el App)

Las entidades con filtrado dinámico **no tienen un `GET /<Entidad>/{id}` aparte**. Traer un
único registro (por id o por cualquier otro campo que lo identifique, ej. `codigo`) usa el
mismo `<Entidad>Criteria` que el listado, con una ruta y una capa de servicio propias que
devuelven un único objeto en vez de una página. El flujo es `Controller → QueryService → Repository` (sin App):

- **`AbstractFiltroQueryService.findOneByCriteria(criteria)`**: genérico, devuelve
  `Optional<ENTIDAD>` — arma la `Specification` igual que `findByCriteria` y delega en
  `JpaSpecificationExecutor.findOne(Specification)`.
- **`<Entidad>QueryService.find<Entidad>ByCriteria(criteria)`**: por entidad, inyecta el
  `Mapper`. Envuelve `findOneByCriteria` con `.orElseThrow(...)` y el `log.warn` correspondiente
  (regla de siempre: el Service loguea y lanza, no el App). Devuelve `Get<Entidad>Response`
  mapeado. Ej. `EspecialidadQueryService.findEspecialidadByCriteria`.
- **`Controller`**: inyecta el `QueryService` directo (no el `App`), sin ir por un paso
  intermedio en el App. `@GetMapping("/<Entidad>/Buscar")`, recibe `@ParameterObject
  <Entidad>Criteria` (mismo tipo que el `GetMapping("/<Entidad>")` de listado), delega a
  `queryService.find<Entidad>ByCriteria(criteria)`, devuelve `ResponseEntity<Get<Entidad>Response>`.

Aplicado en `Especialidad`, `Plan`, `ObraSocial`, `TipoIndicacionPrestacion` e
`IndicacionPrestacion`. `Prestacion` no tenía un `GET /{id}` previo — queda pendiente sumar
su `/Buscar` si hace falta (requiere resolver también las indicaciones anidadas del
response, como hace `ObraSocialQueryService.findObraSocialByCriteria` con sus planes).

Contrato para el front: ver `Docs/FILTRADO-DINAMICO.md`.

**Soft delete y vigencia: filtro implícito, no expuesto en el Criteria.** Resolvía la nota
"pendiente de definir" de una versión anterior de esta sección: no es un helper genérico en
`AbstractFiltroQueryService`, porque no todas las entidades excluyen registros "de baja" de
la misma manera:

- Entidades con `deleted_at` (`Especialidad`, `ObraSocial`, `TipoIndicacionPrestacion`,
  `IndicacionPrestacion`... salvo que tengan otro eje, ver siguiente punto): su
  `createSpecification` agrega siempre `cb.isNull(root.get(Entidad_.deletedAt))`, sin
  exponer `deletedAt` como campo del Criteria (para que ningún filtro externo pueda listar
  bajas lógicas).
- Entidades que se retiran por estado (`Plan`, `Prestacion`): no llevan ningún filtro
  implícito. El estado vigente no está materializado en la entidad, así que el filtro
  `estadoActual` del Criteria (el contrato de query string no cambia) se traduce a una
  **subconsulta correlacionada `EXISTS`** sobre el histórico (`HistoricoEstadoPrestacion`/
  `HistoricoEstadoPlan`) con `fecha_hora_fin IS NULL`, no a un `root.get(...)` directo (la
  relación es unidireccional, no hay `join` desde la entidad al histórico).
- `IndicacionPrestacion` no tiene baja lógica ni estados: se retira cerrando
  `fechaFinVigencia` (admite fecha futura para programar el retiro). Su
  `createSpecification` agrega siempre la condición de vigencia al momento de la consulta
  (`fechaInicioVigencia <= ahora AND (fechaFinVigencia IS NULL OR fechaFinVigencia > ahora)`),
  el equivalente funcional a `deletedAt IS NULL` para este eje, tampoco expuesto en el Criteria.

Cada entidad nueva que sume filtrado dinámico decide a cuál de estos tres grupos pertenece
(o si necesita uno propio) antes de escribir su `createSpecification`.
