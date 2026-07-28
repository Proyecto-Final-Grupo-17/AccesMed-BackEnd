# Plan de setup del backend — para ejecutar con Claude Code

Este plan arma **solo el esqueleto** del proyecto AccesMed: estructura de carpetas y las
clases base que comparte todo el sistema (auditoría, errores, config). Pensado para
dárselo a Claude Code paso a paso. Cada paso termina en algo verificable (compila /
arranca / responde). Leé `../../CLAUDE.md` y `../../docs/ARQUITECTURA.md` antes de empezar: son las
reglas del proyecto.

> **Regla de oro: este plan NO genera features.** Ni Prestación, ni Security (como slice
> con entidades y JWT), ni Agente, ni ninguna otra entidad del dominio. Al terminar el
> Paso 6, la app arranca vacía: sin endpoints de negocio, sin entidades propias, solo el
> esqueleto y el manejo de errores/config funcionando. **Toda feature —incluida la
> primera— se genera después, una por una, con la skill `springboot-feature-generator`**,
> preguntando el flujo antes de escribir código. Ver el orden sugerido al final de este
> documento.

---

## Paso 0 — Prerrequisitos (verificar, no asumir)

- [ ] Java 25 LTS (`java -version`)
- [ ] Maven (`mvn -version`) o usar el wrapper `./mvnw`
- [ ] Docker + Docker Compose (`docker compose version`)
- [ ] Un IDE (IntelliJ recomendado)

---

## Paso 1 — Generar (o verificar) el proyecto base

Si el proyecto **todavía no existe**, generarlo con Spring Initializr (start.spring.io) o
el plugin del IDE, con:

- **Build**: Maven · **Language**: Java · **Java**: 25 (LTS) · **Spring Boot**: **4.1.0**
- **Group**: `com.accesmed` · **Artifact**: `backend` · **Package**: `com.accesmed.backend`
- **Dependencias iniciales**: Web, Data JPA, Validation, Security, PostgreSQL Driver,
  Lombok, Actuator, Liquibase.

Si el proyecto **ya fue generado** (pom.xml ya existe en el repo), **no volver a
generarlo**: en su lugar, hacer el chequeo de abajo.

### Chequeo del `../../pom.xml` contra `../../docs/STACK.md`

Comparar dependencia por dependencia el `../../pom.xml` real contra `../../docs/STACK.md` y
`docs/ARQUITECTURA.md §7`:

1. **Versión de Spring Boot**: confirmar que el `parent` sea `4.1.0` (o el patch más
   nuevo de la línea 4.1 si Initializr ofreció uno). Si Initializr trajo una versión
   distinta a la documentada, **no cambiarla sola/o silenciosamente** — señalarlo y
   preguntar cuál usar.
2. **Dependencias faltantes**: si algo de la lista de `ARQUITECTURA.md §7` no está
   (MapStruct + processor + `lombok-mapstruct-binding`, `hibernate-jpamodelgen`,
   springdoc-openapi, jjwt api/impl/jackson), agregarlas con la versión de `../../docs/STACK.md`.
3. **Dependencias de más**: si Initializr agregó algo no listado en `../../docs/STACK.md` (por
   ejemplo `spring-boot-devtools`, o una versión de starter distinta a la esperada por el
   BOM), **no borrarlo automáticamente** — señalarlo y preguntar si se mantiene o se saca.
4. **Versiones que no coinciden**: si alguna dependencia gestionada por el BOM aparece
   con `<version>` explícita distinta a la que trae Spring Boot 4.1.0 (Initializr a veces
   fija versiones puntuales), señalar el conflicto y preguntar si se deja fija o se pasa
   a manejo por BOM (sin `<version>`).
5. **Annotation processors**: confirmar que `maven-compiler-plugin` tenga declarado
   `annotationProcessorPaths` con Lombok → `lombok-mapstruct-binding` → MapStruct →
   `hibernate-jpamodelgen`, en ese orden (ver `../../docs/STACK.md`).

**Regla para cualquier discrepancia**: si hay un conflicto de versión o una dependencia
que no coincide con lo documentado, **preguntar antes de tocar nada** — no asumir cuál
versión "gana". Si todo coincide, no hace falta preguntar nada.

**Verificación:** `./mvnw clean compile` corre sin errores.

---

## Paso 2 — Estructura de paquetes vacía

Crear los paquetes de `ARQUITECTURA.md §4` bajo `com.accesmed.backend` (mayúsculas,
`Controllers`/`Services`/`Repositories` en plural, **sin carpeta `Shared`**):
`Config`, `Controllers` (+ `Controllers/Errors`), `Application`, `Domain` (solo
`Auditable`, todavía sin entidades — esas las trae cada feature), `Services/DomainServices`,
`Services/QueryServices`, `Services/Mappers`, `Services/Errors`, `Services/Utils`,
`Repositories`, `Records/Request`, `Records/Response`. **No crear todavía** `Security/` ni
`Agente/`: son slices que arma su propia feature más adelante (ver nota al final).

Dejar un `.gitkeep` o un package-info donde haga falta para versionarlos.

**Verificación:** el árbol coincide con la parte de `ARQUITECTURA.md §4` que no depende
de una entidad ni de una feature todavía sin generar.

---

## Paso 3 — Base de auditoría, manejo de errores y logging (por capa, sin `Shared`)

Esto **no es una feature**: es infraestructura compartida por todas las features futuras,
sin la cual ninguna puede compilar. Crear, aplicando las skills de estilo, javadoc y
logging:

1. `Domain/Auditable.java` — `@MappedSuperclass` con `fechaHoraAlta`,
   `fechaHoraModificacion`, `usuarioAlta`, `usuarioModificacion` y, como base del soft
   delete, el criterio de `fechaHoraBaja` donde aplique. Activar `@EnableJpaAuditing`.
2. `Services/Errors/AccesMedException.java` (abstract, `RuntimeException`, con `codigo`,
   `httpStatus` y `origen` — constructor `(Class<?> origen, String codigo, String
   mensaje, int httpStatus)`; `origen` guarda `origen.getSimpleName()`, solo para diagnóstico).
3. `Services/Errors/RecursoNoEncontradoException.java` (404).
4. `Services/Errors/ReglaNegocioException.java` (409).
5. `Services/Errors/ValidacionException.java` (422, recibe `Class<?> origen` y `List<String> errores`).
6. `Controllers/Errors/AccesMedError.java` (record: timestamp, status, codigo, mensaje, errores, path — **sin** `origen`).
7. `Controllers/Errors/GlobalExceptionHandler.java` (`@RestControllerAdvice`, `@Slf4j`) que
   mapea: subclases de `AccesMedException` (de `Services/Errors` — **sin** loguear, ya se
   loguea en el Service en el `throw`), `MethodArgumentNotValidException` y
   `ConstraintViolationException` (**sí** loguea con `log.warn`, con el detalle de qué
   falló), y `Exception` genérica (`log.error`, 500). Ver contrato en `ARQUITECTURA.md §3`.

**Verificación:** compila. Un test simple: un `@RestController` dummy (temporal, se borra
después) que lanza `ReglaNegocioException` devuelve el `AccesMedError` esperado con status
409, y el log de ese error aparece una sola vez (en el Service, no duplicado en el handler).

---

## Paso 4 — Configuración

1. `application.yml` (común): nombre de la app, config de Liquibase, `jpa.hibernate.ddl-auto: validate`, springdoc.
2. `application-dev.yml`: datasource apuntando a la Postgres local (host, puerto, credenciales por env), `show-sql: true`.
3. `application-staging.yml` / `application-prod.yml`: esqueleto mínimo (se completan luego).
4. `Config/OpenApiConfig.java` — metadata de Swagger.
5. `Config/SecurityConfig.java` — **mínimo de arranque, sin JWT todavía**: permitir
   `/swagger-ui/**` y `/actuator/health`, el resto autenticado con un `SecurityFilterChain`
   base (aunque no haya ningún endpoint propio protegido todavía). El JWT real, las
   entidades de usuario y el login **son la feature de Security**, se generan después
   (ver nota final) — acá solo dejamos el esqueleto de config para que la app arranque.

**Verificación:** `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` arranca (con la BD del Paso 5 levantada).

---

## Paso 5 — Base de datos local (Docker)

- [ ] Copiar `../../docker/dev/.env.example` a `docker/dev/.env` y completar.
- [ ] `cd docker/dev && docker compose up -d`
- [ ] Verificar: `docker compose ps` muestra Postgres healthy; conectarse con un cliente.

**Verificación:** la app del Paso 4 arranca conectada a esta BD y Liquibase crea sus
tablas de control (`databasechangelog`), aunque todavía no haya ningún changelog de
entidad propio.

---

## Paso 6 — Cierre del esqueleto

- [ ] La app arranca vacía (`./mvnw spring-boot:run`, perfil `dev`) sin errores.
- [ ] Swagger UI carga en `/swagger-ui.html` (sin endpoints de negocio todavía, eso es
      esperable acá).
- [ ] `/actuator/health` responde `UP`.
- [ ] El `@RestController` dummy del Paso 3 se **eliminó** (era solo para probar el
      `GlobalExceptionHandler`).
- [ ] `../../README.md` actualizado con el comando de arranque real, si difiere de lo documentado.
- [ ] Primer commit del esqueleto en `develop`, limpio, sin ninguna feature adentro.

Con esto el plan **termina**: no hay Paso 7 en adelante. Lo que sigue no es setup, es
desarrollo de features (ver abajo).

---

## Qué sigue después de este plan (fuera de su alcance)

Todo lo que es negocio se genera **después**, feature por feature, con la skill
`springboot-feature-generator` (que pregunta el flujo antes de escribir nada):

- **Security** como slice vertical (`Security/`): entidades `Usuario`/`Admin`, `Rol`,
  `Permiso`, `JwtService`, filtro de autenticación, endpoints de login. Conviene hacerla
  primero porque el resto de las features la necesita para estar realmente protegidas.
- **Agente** (`Agente/`): controllers y records que consume Flowise, reutilizando los
  `App` del núcleo — se arma recién cuando ya existan las features de negocio que expone.
- El resto de las entidades del dominio: `Prestacion`, `Medico`, `Paciente`,
  `MedicoPrestacion`, `ObraSocial`, `Plan`, `Turno` (+ su máquina de estados vía
  `HistoricoEstadoTurno`), `AgendaMedico` y relacionadas. `Turno` es la más compleja:
  conviene dejarla para el final, cuando ya haya una entidad de referencia hecha con el
  generador para copiar el patrón.

**Verificación por feature:** CRUD funcionando desde Swagger + los tests de cada capa;
`AccesMedError` en los casos de error.

---

## Orden sugerido de merge

Un PR por paso de este plan (Pasos 1 a 6), y después un PR por feature, cada uno en su
rama `feature/<Entidad o funcionalidad>` salida de `develop` y mergeada de vuelta a
`develop` (ver el modelo de ramas en `../../README.md`). Cada PR debe compilar, pasar tests y
no romper Swagger. Nada de PRs gigantes: son difíciles de revisar y de que el agente los
mantenga coherentes.
