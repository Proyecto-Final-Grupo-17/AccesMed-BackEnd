# CLAUDE.md — Contexto del proyecto AccesMed (Backend)

> Este archivo es el contexto que Claude Code lee al iniciar. **No** repite toda la
> documentación: describe qué es la app, las convenciones no negociables y dónde
> buscar el detalle. El detalle vive en `Docs/ARQUITECTURA.md`.

## Qué es AccesMed

Sistema de gestión de turnos para una clínica médica. Dos frentes de uso:

- **Chatbot de WhatsApp** (paciente) — a través de un agente (Flowise) que consume
  endpoints del backend vía Custom Tools.
- **Panel web interno** (personal de la clínica: administrador, médico).

El backend es una API REST en **Java 25 (LTS) + Spring Boot 4**. AccesMed es el **proyecto
final de carrera** (UTN, Diseño de Sistemas), no un trabajo de una materia — se
construye con criterio profesional, pensado para sostenerse en el tiempo.

## Dominio (entidades núcleo)

`Medico`, `Paciente`, `Prestacion`, `MedicoPrestacion` (clase asociación:
`atiendeParticular`, `precioParticular`), `Turno`, `AgendaMedico`,
`AgendaMedicoHorariosDia`, `AgendaMedicoHorariosRango`, `HistoricoEstadoTurno`,
`ProcesoAgente`, `MensajeClave`, `TipoMensajeClave`, `ObraSocial`, `Plan`, `Clinica`.
Seguridad: `Admin`/`Usuario`, `Rol`, `Permiso`.

Reglas de dominio que hay que respetar (son verdad de terreno, salen del diagrama de clases):

- **Soft delete** en las entidades que lo necesitan: columna `deleted_at` (Instant, NULL activo).
  Nunca borrar físico. NO es parte de `Auditable`: cada entidad lo agrega si la regla lo pide.
- Todas las entidades son **`Auditable`** (`created_at`, `updated_at`, `created_by`, `updated_by`
  en UTC como `Instant`).
- **Nomenclatura en BD**: `snake_case` para todas las columnas (ej. `created_at`, `updated_by`,
  `deleted_at`). En Java: `camelCase` (ej. `createdAt`, `updatedBy`, `deletedAt`).
- `Turno N→1 AgendaMedicoHorariosRango` y `Turno N→1 MedicoPrestacion`.
  A `Medico` y `Prestacion` desde `Turno` **solo se llega vía `MedicoPrestacion`**
  (asociaciones derivadas de solo lectura, sin FK redundante).
- Estados del `Turno` (DTE): `Pendiente → EsperaValidacion → Confirmado →
  Iniciado/Ausente → Finalizado/Cancelado`, con `EnSalaDeEspera` entre Confirmado e
  Iniciado/Ausente. El estado activo es el `HistoricoEstadoTurno` con `finishedAt` vacío.
- **El estado vigente NO se materializa** en `Prestacion`, `Plan` ni `Turno` (no hay
  columna `estado_actual`): es siempre el tramo del `HistoricoEstado*` con `fecha_hora_fin`
  vacío. La relación entidad↔histórico es **unidireccional** (solo el `@ManyToOne` del
  histórico la mapea); las consultas por estado se escriben desde el histórico o con una
  subconsulta `EXISTS`. Los Response siguen exponiendo `estadoActual` (contrato intacto),
  alimentado por el `App`. La unicidad de `codigo`/`nombre` "entre no deshabilitados"
  (Prestacion/Plan) se valida **solo en la capa de aplicación** (consulta al histórico
  vigente), sin índice único parcial de BD.
- Los horarios de los turnos se **calculan al vuelo** (`slot(n) = startTime + n ×
  durationMinutes`), nunca se persisten como catálogo.
- **Atomicidad**: 1 `Confirmar` = 1 operación atómica = 1 endpoint/transacción.

## Convenciones no negociables

Estas convenciones se aplican SIEMPRE. El detalle y ejemplos están en `Docs/ARQUITECTURA.md`.

**Arquitectura por capas (CRUD + capa de aplicación):**
`Controller → App (capa de aplicación) → DomainService/QueryService → Repository`.

- El **Controller** solo recibe, dispara Bean Validation y delega. Nada de lógica.
- El **App** (`@Service`, package `Application`) orquesta el flujo del caso de uso:
  valida reglas de negocio (existencia, estados) llamando a los services, y coordina.
- Los **DomainService/QueryService** (`Services/DomainServices`, `Services/QueryServices`)
  encapsulan lógica y consultas de una entidad. `Domain` contiene **solo las entidades**,
  no lógica. Los `Mapper` (MapStruct) viven en `Services/Mappers`.
- Un **record por endpoint**, en `Records/<Entidad>/Request` y `Records/<Entidad>/Response`.
  Los campos inmutables no viajan en el request.
- `Controllers` tiene dos subpaquetes fijos: **`Controllers/Errors`** (`GlobalExceptionHandler`,
  `AccesMedError`) y **`Controllers/ControllersConfig`** (config propia de la capa web:
  `OpenApiConfig`, CORS, interceptores). `Config/` queda para lo transversal de
  infraestructura (`SecurityConfig`, `JpaAuditingConfig`).

**Nomenclatura:**

- Records = **`record`** de Java, agrupados por entidad:
  `Records/<Entidad>/Request/` y `Records/<Entidad>/Response/`.
  Request: `<Accion><Entidad>Request`. Response: `<Accion><Entidad>Response`.
  **La acción va en inglés** (`Create`, `Update`, `Get`, `List`, `Enable`, `Delete`...).
  Ej: `Records/Prestacion/Request/CreatePrestacionRequest`,
  `Records/Prestacion/Response/CreatePrestacionResponse`.
- Controladores con sufijo **`Controller`**: `PrestacionController`.
- Casos de uso con sufijo **`App`**: `PrestacionApp`.
- Métodos: **verbo en inglés + concepto de negocio en español**:
  `createMedico`, `saveMedico`, `validateCodigoPrestacionIsUnique`.
- Parámetros = nombre camelCase del tipo: `createMedico(CreateMedicoRequest createMedicoRequest)`.
- Variables de entidad en memoria en español descriptivo: `medicoExistente`,
  `medicoActualizado`, `turnoConfirmado`.

**Rutas y verbos HTTP de los controllers:**

- Ruta base de la clase: `@RequestMapping("/accesmed-api/<Entidad>")` — entidad en
  PascalCase singular (`/accesmed-api/Prestacion`, `/accesmed-api/AgendaMedico`).
- Cada método agrega el **recurso concreto** sobre el que opera:
  `createPrestacion` → `/Prestacion`, `createAgenda` → `/Agenda`.
- **PUT y PATCH con body**: llevan `@PathVariable Long id` **además** del record, y el
  **Controller valida que el `id` de la ruta coincida con el del record** antes de
  delegar en el `App` (`ValidacionException` si no).
- **PATCH sin body**: para actualizar un campo puntual, solo `@PathVariable Long id`.
- **Soft delete = `DELETE`** (`@DeleteMapping("/<Recurso>/{id}")`, responde 204).

Detalle y ejemplos en `docs/ARQUITECTURA.md §5`.

**Errores:** excepciones **no chequeadas** (`extends RuntimeException`), base
`AccesMedException` (antes `AppException`). Jerarquía fija de 3 tipos
(`RecursoNoEncontrado`/`ReglaNegocio`/`Validacion`) en `Services/Errors` — nunca una clase
por entidad. Cada una recibe `getClass()` en el `throw` (campo `origen`, solo para
diagnóstico, nunca al front). Un **`@RestControllerAdvice` global**
(`Controllers/Errors/GlobalExceptionHandler`) las traduce a un único formato
`AccesMedError` (mismo paquete) para el front. Nunca `try/catch` de control de flujo en controllers.

**Logging:** `@Slf4j` (Lombok) en cada clase. Los errores de negocio (`AccesMedException`
y subclases) se loguean con `log.warn` **en el Service, en el `throw`** — no en el
handler (evita loguear el mismo error dos veces). El `GlobalExceptionHandler` solo loguea
lo que él mismo atrapa: Bean Validation (`warn`) y la excepción genérica (`error`).

**Estilo y Javadoc:** hay skills dedicadas —
`.claude/skills/java-springboot-code-style`, `.claude/skills/java-springboot-javadoc` y
`.claude/skills/java-springboot-logging`. Úsalas al escribir o revisar código.

**Generar features nuevas:** usar la skill `.claude/skills/springboot-feature-generator`,
que pregunta el flujo y arma de controller a repositorio con estilo aplicado.

**Modelar dominio y migraciones de esquema:** usar la skill
`.claude/skills/domain-schema-generator`, que pregunta atributos, relaciones y
restricciones (traduciendo cada una a la vez a Bean Validation y a constraint de
esquema) y genera la entidad JPA junto con la migración Liquibase, aplicando la
convención de `docs/ARQUITECTURA.md §6` (nombre de archivo/changeset, nomenclatura de
constraints e índices, comentarios de sección). Se usa tanto sola (crear una entidad
nueva o agregar una columna a una existente) como invocada por
`springboot-feature-generator` al crear o evolucionar una entidad.

## Stack

Java 25 (LTS) · Maven · Spring Boot 4.1.0 (Web, Data JPA, Validation, Security, Actuator) ·
PostgreSQL 16 · Liquibase · Hibernate ORM 7.4 · Lombok · MapStruct 1.6.3 ·
hibernate-jpamodelgen · springdoc-openapi 3.0.3 · jjwt 0.13.0.

Todas las versiones son estables (GA) y están detalladas en `Docs/STACK.md`. Las que
gestiona el BOM de Spring Boot se declaran sin `<version>`.

## Perfiles

`dev` (local, Postgres por docker-compose), `staging`, `prod` (se definen luego).
Activar con `SPRING_PROFILES_ACTIVE`. Config en `application-<perfil>.yml`.

## Ramas

`main` (producción, `prod`) ← `staging` (`staging`) ← `develop` (`dev`). Dentro de
`develop`, cada feature se rama como `feature/<Entidad o funcionalidad>` (ej.
`feature/Prestacion`) y vuelve a `develop` por PR/merge — nunca se rama directo desde
`staging` ni `main`. Detalle en `README.md`.

## Idioma

Documentación y comunicación en **español**. Nombres de código según la regla de arriba
(verbo EN + negocio ES). Javadoc en español.

## Mantener la documentación actualizada

Este archivo y `Docs/ARQUITECTURA.md` son la fuente de verdad de la arquitectura. Si un
cambio de código implica un cambio de convención, estructura o decisión (nueva capa, nueva
dependencia, cambio de nomenclatura, etc.), **hay que reflejarlo en el documento
correspondiente en el mismo cambio** — no dejarlo para después. Documentación desactualizada
es peor que no tener documentación.
