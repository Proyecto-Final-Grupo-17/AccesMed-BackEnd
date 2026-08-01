---
name: domain-schema-generator
description: >
  Modela el dominio del backend AccesMed de punta a punta: entidad JPA
  (`Domain/<Entidad>.java`) + migración Liquibase (XML) generadas o editadas de forma
  coordinada. Pregunta atributos, tipos, relaciones (multiplicidad/navegabilidad) y
  restricciones, traduciendo cada restricción tanto a anotación Bean Validation
  (`@NotNull`, `@NotBlank`, `@Pattern`, `@Size`, etc.) como a la constraint de esquema
  equivalente (`NOT NULL`, `CHECK`, longitud de columna, `UNIQUE`). Sigue la convención
  de `docs/ARQUITECTURA.md §6`: un archivo por entidad, nombre `YYYYMMDDHHMMSS-<Entidad>.xml`,
  vocabulario de changeset `added-table-<Entidad>`/`updated-table-<Entidad>-<detalle>`/
  `deleted-table-<Entidad>`, nomenclatura estándar de PK/FK/UNIQUE/CHECK/INDEX/TRIGGER.
  Úsala cuando el usuario pida "creá la entidad X", "modelá el dominio de X", "agregá una
  columna a X", "creá el changelog de X", "sacá/renombrá una columna", "agregá un
  índice/constraint a X", o "modificá el último changeset de X" (esto último solo en
  `dev`). Se usa sola o invocada por `springboot-feature-generator`, que delega en esta
  skill todo el modelado de dominio (Fase 1, atributos/relaciones/constraints) y la
  generación de `Domain/<Entidad>.java` + migración (Fase 2), quedándose ella con el
  resto de capas (Records, Mapper, DomainService, App, Controller).
---

# Modelador de dominio y changelogs — backend AccesMed

Modela una entidad (nueva o existente) y su esquema de forma coordinada: la entidad JPA
en `Domain/<Entidad>.java` y la migración Liquibase en
`src/main/resources/liquibase-db-changelogs/changelogs/` **nunca se generan por
separado** — toda restricción de negocio existe en ambos lados a la vez (capas 1 y 5 de
defensa de `ARQUITECTURA.md §2`). No generes ni edites nada hasta confirmar el modelo
completo con el usuario cuando algo no sea inequívoco (nombre de entidad, tipo de
columna, si la constraint va con nombre estándar o uno ya existente que hay que
respetar).

## Fase 1 — Preguntar

1. **Entidad afectada**: nombre (ej. `Medico`). ¿Existe ya `Domain/<Entidad>.java`?
2. **¿El archivo de changelog de esa entidad ya existe?**
   - Buscar `src/main/resources/liquibase-db-changelogs/changelogs/*-<Entidad>.xml`.
   - Si no existe: es un changeset `added-table-<Entidad>` (primer changeset del archivo)
     → seguir con el punto 3 (entidad nueva).
   - Si existe: es un changeset `updated-table-<Entidad>-<detalle>` que se agrega al
     final del archivo existente → seguir con el punto 4 (entidad existente).

### 3. Entidad nueva — modelado completo

1. **Atributos**: por cada uno, nombre, tipo Java y tipo de columna.
2. **Relaciones**: con qué entidad, multiplicidad y navegabilidad — respetando el
   diagrama de clases (ej. a `Medico`/`Prestacion` desde `Turno` solo se llega vía
   `MedicoPrestacion`, nunca FK redundante).
3. **Soft delete**: ¿la entidad lo necesita? Si sí, columna `deleted_at` (Instant, NULL
   activo) — no es parte de `Auditable`, se agrega aparte.
4. **Auditoría**: por defecto extiende `Auditable` (`created_at`, `updated_at`,
   `created_by`, `updated_by`). Confirmar si aplica.
5. **Restricciones por atributo/relación** — preguntar y traducir a la vez a anotación
   Bean Validation **y** a constraint de esquema (ninguna reemplaza a la otra; son capas
   de defensa distintas, ver `ARQUITECTURA.md §2`):

   | Restricción | Bean Validation (entidad/record) | Constraint de esquema |
   |---|---|---|
   | Obligatorio | `@NotNull` (tipos no-String) / `@NotBlank` (String) | `NOT NULL` |
   | Formato | `@Pattern` | `CHECK` si Postgres lo puede validar razonablemente; si no, queda solo como validación de aplicación (documentarlo) |
   | Longitud | `@Size(min, max)` | `varchar(n)` |
   | Unicidad simple | `@Column(unique = true)` | `UNIQUE` (`uq_<tabla>_<columna>`) |
   | Unicidad "entre activos" (con soft delete) | no se resuelve con `@Column(unique)` — es regla de negocio en el `DomainService` (ej. `validateCodigoIsUnique`) | sin `UNIQUE` de columna plano; opcional `idx_<tabla>_<columna>` para performance de la query de validación |
   | Rango / regla cruzada entre columnas | validación a mano en el request o `@AssertTrue` | `CHECK` (`ck_<tabla>_<regla>`) |

   Ejemplo concreto: un campo `codigo` obligatorio, máximo 50 caracteres, único entre
   prestaciones activas → `@NotBlank @Size(max = 50)` en el record/entidad, columna
   `varchar(50) NOT NULL` en el changelog, sin `UNIQUE` de columna (la unicidad "entre
   activas" la valida `PrestacionDomainService.validateCodigoPrestacionIsUnique`), más un
   índice opcional `idx_prestacion_codigo` si la consulta lo justifica.

### 4. Entidad existente — tipo de cambio

Tabla nueva (no aplica, ver punto 3) / columna nueva / columna eliminada / columna
modificada (tipo, nullable, default) / columna renombrada / constraint (UNIQUE, CHECK,
FK) / índice / trigger / baja de tabla completa. Si el cambio agrega o quita una
restricción, aplicar el mismo mapeo Bean Validation ↔ constraint de la tabla del punto 3
(ej. agregar `NOT NULL` a una columna existente implica sumar `@NotNull`/`@NotBlank` en
la entidad).

### 5. Confirmaciones transversales

- **¿El cambio de esquema implica tocar la entidad JPA?** Confirmar el campo o relación
  exacta antes de editar `Domain/<Entidad>.java` — el pedido puede ser solo de base de
  datos (raro) o también de dominio (lo habitual, y siempre en el caso de entidad nueva).
- **Excepción dev**: si el pedido es "modificar" (no agregar) un changeset ya ejecutado,
  confirmar que el entorno es `dev` (nunca en staging/prod) y advertir que hace falta
  `docker compose -f docker/dev/docker-compose.yml down -v && docker compose -f
  docker/dev/docker-compose.yml up -d` después, para que `DATABASECHANGELOG` quede
  consistente.

Resumí el modelo completo entendido (atributos, relaciones, restricciones y su mapeo a
constraint) y pedí un OK antes de generar.

## Fase 2 — Generar o editar

1. **Entidad nueva**:
   - **`Domain/<Entidad>.java`**: extiende `Auditable` si corresponde, atributos con sus
     anotaciones Bean Validation, relaciones con la navegabilidad confirmada en Fase 1,
     `deleted_at` si tiene soft delete. Solo modelo — sin lógica (la lógica de unicidad
     "entre activos", validaciones cruzadas, etc. va al `DomainService`, fuera del
     alcance de esta skill salvo que el usuario pida generarlo también).
   - **Changelog**: crear
     `src/main/resources/liquibase-db-changelogs/changelogs/YYYYMMDDHHMMSS-<Entidad>.xml`,
     timestamp = fecha/hora actual, fija para siempre (no cambia aunque se agreguen más
     changesets después).
   - Primer `<changeSet id="YYYYMMDDHHMMSS-added-table-<Entidad>" author="...">`
     (mismo timestamp que el nombre del archivo) con el `<createTable>`: PK, columnas de
     negocio con sus constraints (`NOT NULL`, `varchar(n)`, `CHECK`, `UNIQUE` según el
     mapeo de Fase 1), FKs si corresponde, y las columnas de auditoría de `Auditable`
     (`created_at`, `updated_at`, `created_by`, `updated_by`) más `deleted_at` si la
     entidad tiene soft delete.
   - Dividir el `createTable` con comentarios banner si tiene varias secciones:
     ```xml
     <!-- ====== Relaciones ====== -->
     <!-- ====== Columnas ====== -->
     <!-- ====== Columnas auditoría (incluye a la baja) ====== -->
     ```
   - Agregar el `<include file="changelogs/YYYYMMDDHHMMSS-<Entidad>.xml"
     relativeToChangelogFile="true"/>` en `master.xml` — **una sola vez**, en este momento.
   - Generar entidad y changelog **en el mismo paso**, no uno como consecuencia tardía
     del otro: el `createTable` y la entidad JPA deben quedar consistentes de punta a
     punta antes de pasar a Fase 3.
2. **Entidad existente** (`updated-table-<Entidad>-<detalle>`):
   - Agregar un `<changeSet>` nuevo al final del archivo, con su propio timestamp
     (momento actual) y un `<detalle>` descriptivo del cambio (`added-column-<nombre>`,
     `dropped-column-<nombre>`, `modified-column-<nombre>`,
     `renamed-column-<nombre>-to-<nuevoNombre>`, `added-constraint-<nombre>`,
     `added-index-<nombre>`).
   - **No** tocar `master.xml` — el `<include>` ya existe.
   - Reflejar el cambio en `Domain/<Entidad>.java` (campo, anotación JPA y Bean
     Validation) en el mismo paso que el changeset.
3. **Nomenclatura estándar de objetos de esquema** (ver tabla completa en
   `ARQUITECTURA.md §6`), siempre `snake_case`:
   - PK: `pk_<tabla>` · FK: `fk_<tabla_origen>_<tabla_destino>` · UNIQUE: `uq_<tabla>_<columna(s)>`
   - CHECK: `ck_<tabla>_<regla>` · INDEX: `idx_<tabla>_<columna(s)>` · TRIGGER: `trg_<tabla>_<momento>_<evento>`
   - Función/procedimiento: `fn_<acción_negocio>` / `sp_<acción_negocio>`
4. **Baja de tabla** (`deleted-table-<Entidad>`): changeset con `<dropTable>`. Confirmar
   con el usuario — es poco común dado el criterio de soft delete del proyecto.

## Fase 3 — Verificar

- [ ] Nombre de archivo `YYYYMMDDHHMMSS-<Entidad>.xml` y de changeset
      `YYYYMMDDHHMMSS-<acción>` siguen la convención de `ARQUITECTURA.md §6`.
- [ ] Si el archivo es nuevo: el `<include>` está en `master.xml`, una sola vez.
- [ ] Si el archivo ya existía: `master.xml` no se tocó.
- [ ] Todo objeto de esquema (PK/FK/UNIQUE/CHECK/INDEX/TRIGGER) tiene nombre explícito
      siguiendo la convención estándar, no un nombre autogenerado por Liquibase.
- [ ] Cada restricción de Bean Validation en `Domain/<Entidad>.java` tiene su
      equivalente en el changelog, o está documentado explícitamente por qué no aplica
      (ej. unicidad "entre activos" que no es `UNIQUE` de columna).
- [ ] Las relaciones respetan la navegabilidad del diagrama de clases (sin FK
      redundante donde la asociación debe ser derivada de solo lectura).
- [ ] `Domain/<Entidad>.java` quedó sincronizada con el cambio de esquema.
- [ ] El changelog corre limpio (`./mvnw spring-boot:run` con `dev` activo, o test de
      contexto) — Liquibase no tira error de sintaxis ni de checksum.
- [ ] Si se editó un changeset ya ejecutado (excepción dev): se avisó que hace falta
      resetear el entorno (`docker compose down -v && up -d`).
