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
Detalle completo, criterios y fuentes en [`docs/STACK.md`](docs/STACK.md).

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

## Comandos útiles

```bash
./mvnw clean compile     # compilar
./mvnw test              # tests
./mvnw spring-boot:run   # levantar (con el perfil que corresponda)

cd docker/dev
docker compose up -d     # levantar Postgres
docker compose down      # bajar Postgres (conserva el volumen de datos)
docker compose down -v   # bajar y BORRAR los datos
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
| [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) | Arquitectura, capas, estructura del repo, decisiones |
| [`docs/STACK.md`](docs/STACK.md) | Stack tecnológico detallado, con versiones |
| [`.claude/plans/PLAN-SETUP-CLAUDE-CODE.md`](.claude/plans/PLAN-SETUP-CLAUDE-CODE.md) | Plan de setup paso a paso |
| [`docs/FRONTEND-GUIA.md`](docs/FRONTEND-GUIA.md) | Contrato de API para el frontend |

## Arquitectura en una línea

`Controller → App (aplicación) → DomainService/QueryService → Repository`, con un
`record` (DTO) por endpoint y un manejador global de errores que responde un `AccesMedError`
único. Detalle en [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

## Estructura del repositorio

Ver el árbol completo en [`docs/ARQUITECTURA.md §4`](docs/ARQUITECTURA.md). En corto:

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
    │   │   ├── Config/
    │   │   ├── Controllers/    (+ Errors/: GlobalExceptionHandler, AccesMedError)
    │   │   ├── Application/    (<Entidad>App)
    │   │   ├── Domain/         (entidades + Auditable)
    │   │   ├── Services/{DomainServices,QueryServices,Mappers,Errors,Utils}/
    │   │   ├── Repositories/
    │   │   ├── Records/{Request,Response}/
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
