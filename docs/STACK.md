# Stack tecnológico — AccesMed Backend

Todas las versiones de este documento son **estables (GA)**: nada de beta, milestone ni
release candidate. Verificadas en julio de 2026.

**Línea elegida: Spring Boot 4**, sobre **Spring Framework 7**. Se migró desde la línea 3
porque, a mitad de 2026, 3.x ya está saliendo de soporte open-source y 4.x es la línea
activa — arrancar un proyecto final de carrera directo en la línea vigente evita heredar
una migración pendiente el día de la entrega.

## Principio: dejar que el BOM gobierne las versiones

`spring-boot-starter-parent` trae un BOM que ya fija versiones compatibles entre sí para
la mayoría de las dependencias. **Las dependencias gestionadas por el BOM se declaran sin
`<version>`** — así se evita romper la compatibilidad interna del stack.

Solo se fija versión explícita para lo que el BOM **no** gestiona: MapStruct,
`lombok-mapstruct-binding`, springdoc-openapi y jjwt.

## Lenguaje y build

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| Java | **25 (LTS)** | `<java.version>25</java.version>`. Spring Boot 4.1 requiere mínimo Java 17, pero recomienda la última LTS disponible — 25 es la más reciente (GA septiembre 2025). |
| Maven | **3.9.x** | Wrapper `./mvnw` versionado en el repo |

## Framework

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| Spring Boot | **4.1.0** | `spring-boot-starter-parent`. Publicado el 10 de junio de 2026; verificar si hay un patch más nuevo (4.1.x) antes de fijar el `pom.xml`. |
| Spring Framework | 7.0.x | BOM |
| Spring MVC | — | `spring-boot-starter-webmvc` (BOM). Spring Boot 4 renombró `spring-boot-starter-web` a `spring-boot-starter-webmvc` para distinguirlo de WebFlux; el nombre viejo queda deprecado. |
| Spring Data JPA | — | `spring-boot-starter-data-jpa` (BOM) |
| Hibernate ORM | 7.4.x | BOM (viene con `spring-boot-starter-data-jpa`) |
| Spring Security | — | `spring-boot-starter-security` (BOM) |
| Spring Validation | — | `spring-boot-starter-validation` (BOM) |
| Spring Actuator | — | `spring-boot-starter-actuator` (BOM) |

> **Nota de migración**: Spring Boot 4 / Spring Framework 7 no cambia el namespace
> `jakarta.*` (eso ya pasó en la migración a Spring Boot 3), así que no hay otro salto de
> paquetes que dar. Los cambios relevantes para este proyecto son de versión de Hibernate
> (6.6 → 7.4) y de baseline de Java. Antes de generar el proyecto, conviene revisar la guía
> oficial de migración de Spring Boot 4.0 por si algún starter cambió de nombre o de
> configuración por defecto.

## Base de datos y migraciones

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| PostgreSQL | **16.x** (imagen `postgres:16-alpine`) | `docker/dev/docker-compose.yml`. Última versión estable de Postgres: 18.4 — se mantiene 16 por continuidad con lo ya planificado; no depende de la versión de Spring Boot. |
| Driver JDBC `postgresql` | — | BOM |
| Liquibase (`liquibase-core`) | gestionada por el BOM de Spring Boot 4.1 | No fijar versión explícita; usar la que trae `spring-boot-starter-parent`. |

## Mapeo, documentación y utilidades

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| Lombok | **1.18.46** | BOM (se puede declarar sin versión). Compatible con Java 25 (soporte agregado desde 1.18.40). |
| MapStruct (`mapstruct` + `mapstruct-processor`) | **1.6.3** | Versión explícita en `<properties>`. La 1.7 sigue en Beta: **no usar**. No depende de la versión de Spring Boot. |
| `lombok-mapstruct-binding` | **0.2.0** | Versión explícita. Necesario para que Lombok y MapStruct convivan como annotation processors. |
| `hibernate-processor` | 7.4.x (misma que Hibernate) | BOM. Scope `provided` + declarado en `annotationProcessorPaths`. Genera el metamodelo estático (`Turno_`, `Prestacion_`) para las `Specification`. Se llamaba `hibernate-jpamodelgen` hasta Hibernate 6; Hibernate ORM 7 lo renombró a `hibernate-processor` (no está en el BOM de Spring Boot 4.1 bajo el nombre viejo). |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | **3.0.3** | Versión explícita. Es la serie compatible con Spring Boot 4 (la serie 2.x quedó para Spring Boot 3). Verificar el último patch en [springdoc.org](https://springdoc.org) al fijar la dependencia. |
| jjwt (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) | **0.13.0** | Versión explícita en `<properties>`. No depende de la versión de Spring Boot. |

### Annotation processors: el orden importa

Lombok, MapStruct y `hibernate-processor` son los tres annotation processors del
proyecto. Hay que declararlos **explícitamente** en `annotationProcessorPaths` del
`maven-compiler-plugin` (y en ese orden: Lombok → `lombok-mapstruct-binding` → MapStruct →
`hibernate-processor`), porque si se dejan al descubrimiento automático pueden pisarse
entre sí y generar código incompleto.

## Testing

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| JUnit 5, Mockito, AssertJ | — | `spring-boot-starter-test` (BOM), scope `test` |
| Testcontainers (`spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql`) | 2.0.5 (BOM) | Scope `test`. Levanta una Postgres 16 efímera y aislada para `AccesMedApplicationTests` (vía `@ServiceConnection`), sin depender de `docker/dev/docker-compose.yml` — requiere Docker corriendo, pero no el compose de dev levantado a mano. En Testcontainers 2.0.x el artifact del módulo Postgres se renombró de `postgresql` a `testcontainers-postgresql`; ojo si se busca en documentación vieja. |

## Conveniencia de desarrollo

| Tecnología | Versión | Cómo se fija |
|---|---|---|
| `spring-boot-devtools` | — | BOM. Scope `runtime`, `optional`. Restart automático en `dev`; se excluye del artefacto final por el propio plugin de Boot. |
| `spring-boot-docker-compose` | — | BOM. Scope `runtime`, `optional`. Detecta `docker/dev/docker-compose.yml` y levanta Postgres solo al correr `./mvnw spring-boot:run` en `dev`. |

## Dependencias descartadas a propósito

El Initializr ofrece por defecto otras dependencias que **no** forman parte del stack — si
se regenera el proyecto, no agregarlas:

- `spring-boot-starter-data-jdbc`, `spring-boot-starter-data-r2dbc` / `r2dbc-postgresql`:
  el acceso a datos del proyecto es JPA, no JDBC directo ni reactivo.
- `spring-boot-starter-data-rest`: auto-expone los repositorios como endpoints REST
  (HATEOAS), lo que choca directo con la arquitectura `Controller → App → Service`.
- `spring-boot-starter-restclient`: nada lo consume todavía; se agrega el día que una
  feature necesite llamar a un servicio HTTP externo (ej. WhatsApp/Flowise saliente).
- `spring-boot-starter-restdocs` / `spring-restdocs-mockmvc` / `asciidoctor-maven-plugin`:
  Spring REST Docs es redundante con springdoc-openapi, que ya es la documentación viva
  del proyecto (ver `FRONTEND-GUIA.md §3`).

## Infraestructura

| Tecnología | Versión | Notas |
|---|---|---|
| Docker Engine | reciente | `docker/Dockerfile`, build multi-stage (Maven → JRE 25), usuario no-root, healthcheck |
| Docker Compose | v2 | `docker/dev/docker-compose.yml`: Postgres local + pgAdmin opcional |

## Fuentes consultadas (julio 2026)

- [Spring Boot 4.1.0 available now](https://spring.io/blog/2026/06/10/spring-boot-4/)
- [Spring Boot | endoflife.date](https://endoflife.date/spring-boot)
- [What is the Minimum Java Version Required for Spring Boot 4? — BSWEN](https://docs.bswen.com/blog/2026-03-04-spring-boot-4-java-version/)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Managed Dependency Coordinates — Spring Boot](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [springdoc-openapi releases / springdoc.org v4](https://springdoc.org/v4/)
- [MapStruct 1.7.0.Beta1 (por qué se queda en 1.6.3)](https://mapstruct.org/news/2026-02-01-mapstruct-1_7_0_Beta1-is-out/)
- [jjwt releases](https://github.com/jwtk/jjwt/releases)
- [PostgreSQL 18.4, 17.10, 16.14, 15.18 y 14.23 released](https://www.postgresql.org/about/news/postgresql-184-1710-1614-1518-and-1423-released-3297/)
- [Lombok changelog](https://projectlombok.org/changelog)
