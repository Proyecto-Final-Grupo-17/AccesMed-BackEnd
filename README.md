# AccesMed — Backend

API REST para la gestión de turnos de una clínica médica. Da servicio a dos frentes: un
**chatbot de WhatsApp** (agente) para pacientes y un **panel web interno** para el
personal de la clínica.

AccesMed es el **proyecto final de carrera** (UTN, Diseño de Sistemas).

## Stack

Todas las versiones son **estables (GA)**, sobre la línea **Spring Boot 4**.

| Tecnología | Versión |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | 4.1.0 |
| Maven | 3.9.x (wrapper `./mvnw`) |
| PostgreSQL | 16.x (`postgres:16-alpine`) |
| Liquibase | gestionada por el BOM |
| Spring Data JPA / Hibernate ORM | 7.4.x (BOM) |
| Spring Security | BOM |
| jjwt (`jjwt-api`/`impl`/`jackson`) | 0.13.0 |
| Lombok | 1.18.46 |
| MapStruct | 1.6.3 |
| `lombok-mapstruct-binding` | 0.2.0 |
| hibernate-processor | 7.4.x (metamodelo estático JPA; se llamaba `hibernate-jpamodelgen` hasta Hibernate 6) |
| springdoc-openapi (Swagger) | 3.0.3 |

Las dependencias gestionadas por el BOM de Spring Boot se declaran **sin `<version>`**.
Detalle completo, criterios y fuentes en [`Docs/STACK.md`](Docs/STACK.md).

## Requisitos

- Java 25 (LTS)
- Maven 3.9+ (o el wrapper `./mvnw`)
- Docker + Docker Compose

## Setup del proyecto

1. **Clonar y levantar Postgres local:**

   ```bash
   cp docker/dev/.env.example docker/dev/.env   # completar credenciales
   cd docker/dev && docker compose up -d
   ```

2. **Arrancar la app (perfil `dev`):**

   ```bash
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```

   En Windows (PowerShell):

   ```powershell
   $env:SPRING_PROFILES_ACTIVE="dev"; ./mvnw spring-boot:run
   ```

3. **Verificar:**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health: http://localhost:8080/actuator/health

Liquibase aplica las migraciones de esquema automáticamente al arrancar (`ddl-auto:
validate`, nunca `update`/`create`: el esquema lo maneja Liquibase, no Hibernate).

## Comandos necesarios

### Correr la aplicación

La forma recomendada es la flag `-Dspring-boot.run.profiles`, del propio plugin de
Maven: es **el mismo comando en Windows (cmd/PowerShell/Git Bash), Linux y Mac**, sin
depender de la sintaxis de variables de entorno de cada shell.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=staging
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Alternativa por variable de entorno (`SPRING_PROFILES_ACTIVE`), si se prefiere:

```bash
# Linux / Mac / Git Bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

```powershell
# PowerShell
$env:SPRING_PROFILES_ACTIVE="dev"; ./mvnw spring-boot:run
```

Sin perfil activo, Spring Boot arranca con el perfil `default` (ninguno de
`dev`/`staging`/`prod`) y **no** aplica `application-dev.yml` — el docker-compose de
`dev` no se detecta y el arranque falla. Perfiles disponibles: `dev`, `staging`, `prod`
(ver tabla en [Perfiles](#perfiles)).

**Si se ejecuta con el botón Run del IDE (IntelliJ):** el perfil hay que configurarlo
a mano en la Run Configuration antes de darle Run — si no, corre en `default` y falla
igual que sin la variable de entorno por consola.

1. **Run → Edit Configurations…** → seleccionar `AccesMedApplication`.
2. Completar el campo **"Active profiles"** con `dev` (si no aparece ese campo,
   agregar en **Environment variables**: `SPRING_PROFILES_ACTIVE=dev`).
3. Apply → OK.

Para no repetir el paso cada vez, conviene duplicar la Run Configuration una vez por
perfil (`AccesMed-dev`, `AccesMed-staging`, `AccesMed-prod`) y elegir cuál correr desde
el dropdown del IDE.

### Maven

```bash
./mvnw clean              # borra target/
./mvnw compile            # compila el código principal
./mvnw test                # corre los tests
./mvnw install             # compila, testea e instala el artefacto en el repo local (~/.m2)
./mvnw verify              # corre el ciclo completo, incluidas las fases de integración/checks post-test
./mvnw clean install       # combinación típica antes de un PR: limpio + reconstruyo + testeo
```

**Tests específicos:**

```bash
./mvnw test -Dtest=PrestacionServiceTest              # una clase
./mvnw test -Dtest=PrestacionServiceTest#shouldCreate  # un método de una clase
./mvnw test -Dtest=Prestacion*Test                     # por patrón de nombre
```

**Saltear tests:**

```bash
./mvnw install -DskipTests        # compila los tests pero no los ejecuta
./mvnw install -Dmaven.test.skip=true  # ni compila ni ejecuta los tests
```

`-DskipTests` es la opción recomendada para uso normal (deja el código de test
compilado y detecta errores de compilación en los tests aunque no se ejecuten).
`-Dmaven.test.skip=true` se reserva para casos puntuales (por ejemplo, un módulo de test
roto que bloquea un build urgente).

**`AccesMedApplicationTests` necesita Docker corriendo (Docker Desktop u otro daemon
accesible), pero NO el `docker compose up -d` de dev:** el test levanta su propia Postgres
16 efímera con Testcontainers (`TestcontainersConfiguration`, `@ServiceConnection`),
aislada de la base de `docker/dev/docker-compose.yml` — Liquibase valida el esquema real
contra ese contenedor descartable, no contra la base de desarrollo. Si Docker no está
disponible, `./mvnw test`/`verify` falla con `Could not find a valid Docker environment`,
no por un bug en el código.

**Warning de Lombok al compilar (`sun.misc.Unsafe` / `lombok.permit.Permit`):** en JDK 24+
es un warning conocido y no bloqueante — Lombok todavía usa `Unsafe` internamente para
generar código durante el annotation processing y JDK 25 lo marca como deprecado. No es un
error de este proyecto ni afecta el build ni el runtime; se resuelve del todo cuando Lombok
migre esa parte internamente (no hay fecha). Mientras tanto, `.mvn/jvm.config` trae
`--sun-misc-unsafe-memory-access=allow`, que silencia el warning en todo el equipo sin
tocar nada más — no hace falta configurar `MAVEN_OPTS` a mano.

### Docker

```bash
cd docker/dev
docker compose up -d          # levantar Postgres en background
docker compose ps             # ver estado de los contenedores
docker compose logs -f        # seguir logs en vivo
docker compose down           # bajar Postgres (conserva el volumen de datos)
docker compose down -v        # bajar y BORRAR los datos (reinicia el volumen)
docker compose restart        # reiniciar el contenedor sin recrearlo
```

## Perfiles y ramas

### Perfiles

| Perfil | Estado | Base de datos |
|--------|--------|---------------|
| `dev` | Activo | Postgres local (docker-compose) |
| `staging` | Por definir | — |
| `prod` | Por definir | — |

Se selecciona con la variable de entorno `SPRING_PROFILES_ACTIVE`. Config en
`application-<perfil>.yml`; lo común en `application.yml`.

### Ramas

`main` es la rama estándar de Git y se mantiene como tal — representa **producción**
(`prod`). No hace falta renombrarla: es la convención que reconoce cualquier herramienta
(GitHub, CI, etc.) sin configuración extra.

| Rama | Perfil | Rol |
|------|--------|-----|
| `main` | `prod` | Código en producción. Solo recibe merges desde `staging`. |
| `staging` | `staging` | Integración pre-producción. Recibe merges desde `develop`. |
| `develop` | `dev` | Integración activa del equipo. Base de las ramas de feature. |

Las features se ramifican desde `develop` como `feature/<Entidad o funcionalidad>` (ej.
`feature/Prestacion`) y vuelven a `develop` por PR/merge — nunca se rama directo desde
`staging` o `main`.

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [`CLAUDE.md`](CLAUDE.md) | Contexto y convenciones para Claude Code |
| [`Docs/ARQUITECTURA.md`](Docs/ARQUITECTURA.md) | Arquitectura, capas, estructura del repo, decisiones |
| [`Docs/STACK.md`](Docs/STACK.md) | Stack tecnológico detallado, con versiones |
| [`.claude/plans/PLAN-SETUP-CLAUDE-CODE.md`](.claude/plans/PLAN-SETUP-CLAUDE-CODE.md) | Plan de setup paso a paso |
| [`Docs/FRONTEND-GUIA.md`](Docs/FRONTEND-GUIA.md) | Contrato de API para el frontend |

## Arquitectura en una línea

`Controller → App (aplicación) → DomainService/QueryService → Repository`, con un
`record` (DTO) por endpoint —agrupados en `Records/<Entidad>/{Request,Response}`— y un
manejador global de errores que responde un `AccesMedError` único. Los endpoints cuelgan de
`/accesmed-api/<Entidad>` y cada método agrega su recurso (`POST /accesmed-api/Prestacion/Prestacion`).
Detalle en [`Docs/ARQUITECTURA.md`](Docs/ARQUITECTURA.md).

## Estructura del repositorio

Ver el árbol completo en [`docs/ARQUITECTURA.md §4`](Docs/ARQUITECTURA.md). En corto:

```
accesmed-backend/
├── CLAUDE.md
├── README.md
├── pom.xml
├── docs/
│   ├── ARQUITECTURA.md
│   ├── STACK.md
│   ├── PLAN-SETUP-CLAUDE-CODE.md
│   └── FRONTEND-GUIA.md
├── docker/
│   ├── Dockerfile
│   └── dev/
│       ├── docker-compose.yml
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
    │   │   ├── Config/         (transversal: SecurityConfig, JpaAuditingConfig)
    │   │   ├── Controllers/    (+ Errors/: GlobalExceptionHandler, AccesMedError
    │   │   │                    + ControllersConfig/: OpenApiConfig, CORS, interceptores)
    │   │   ├── Application/    (<Entidad>App)
    │   │   ├── Domain/         (entidades + Auditable)
    │   │   ├── Services/{DomainServices,QueryServices,Mappers,Errors,Utils}/
    │   │   ├── Repositories/
    │   │   ├── Records/<Entidad>/{Request,Response}/
    │   │   ├── Security/       (slice vertical de auth, mismo patrón)
    │   │   └── Agente/         (entrada del agente; reutiliza el núcleo)
    │   └── resources/
    └── test/
```

## Skills de Claude Code

En `.claude/skills/`:

- **java-springboot-code-style** — convenciones de estilo del proyecto.
- **java-springboot-javadoc** — estilo de Javadoc.
- **java-springboot-logging** — niveles de log, dónde loguear cada cosa y el criterio de
  no duplicar errores entre el Service y el `GlobalExceptionHandler`.
- **springboot-feature-generator** — genera una feature completa (de controller a
  repositorio) preguntando el flujo, aplicando estilo, javadoc y logging.

Las cuatro reflejan preferencias personales de Franco que pueden evolucionar — se
actualizan cuando cambie el criterio, no solo el código nuevo.

## Convenciones

Documentación y Javadoc en **español**. Nombres de método: verbo en inglés + concepto en
español (`createMedico`, `validateCodigoPrestacionIsUnique`). Detalle en `CLAUDE.md`.
