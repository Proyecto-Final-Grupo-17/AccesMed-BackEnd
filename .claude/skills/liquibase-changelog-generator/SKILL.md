---
name: liquibase-changelog-generator
description: >
  Genera o edita migraciones Liquibase (XML) del backend AccesMed siguiendo la convención
  de `docs/ARQUITECTURA.md §6`: un archivo por entidad, nombre `YYYYMMDDHHMMSS-<Entidad>.xml`,
  vocabulario de changeset `added-table-<Entidad>`/`updated-table-<Entidad>-<detalle>`/
  `deleted-table-<Entidad>`, nomenclatura estándar de PK/FK/UNIQUE/CHECK/INDEX/TRIGGER, y
  reflejo del cambio en la entidad JPA correspondiente (`Domain/<Entidad>.java`). Úsala
  cuando el usuario pida "agregá una columna a X", "creá el changelog de X", "sacá/renombrá
  una columna", "agregá un índice/constraint a X", o "modificá el último changeset de X"
  (esto último solo en `dev`). Se usa sola o invocada por `springboot-feature-generator`
  en el paso de migración al crear una entidad nueva.
---

# Generador de changelogs Liquibase — backend AccesMed

Crea o edita changelogs XML de Liquibase aplicando la convención de
`docs/ARQUITECTURA.md §6`. No generes ni edites nada hasta confirmar el cambio con el
usuario cuando algo no sea inequívoco (nombre de entidad, tipo de columna, si la constraint
va con nombre estándar o uno ya existente que hay que respetar).

## Fase 1 — Preguntar

1. **Entidad afectada**: nombre (ej. `Medico`). ¿Existe ya `Domain/<Entidad>.java`?
2. **¿El archivo de changelog de esa entidad ya existe?**
   - Buscar `src/main/resources/liquibase-db-changelogs/changelogs/*-<Entidad>.xml`.
   - Si no existe: es un changeset `added-table-<Entidad>` (primer changeset del archivo).
   - Si existe: es un changeset `updated-table-<Entidad>-<detalle>` que se agrega al final
     del archivo existente.
3. **Tipo de cambio**: tabla nueva / columna nueva / columna eliminada / columna
   modificada (tipo, nullable, default) / columna renombrada / constraint (UNIQUE, CHECK,
   FK) / índice / trigger / baja de tabla completa.
4. **¿El cambio de esquema implica tocar la entidad JPA?** Confirmar el campo o relación
   exacta antes de editar `Domain/<Entidad>.java` — el pedido puede ser solo de base de
   datos (raro) o también de dominio (lo habitual).
5. **Excepción dev**: si el pedido es "modificar" (no agregar) un changeset ya ejecutado,
   confirmar que el entorno es `dev` (nunca en staging/prod) y advertir que hace falta
   `docker compose -f docker/dev/docker-compose.yml down -v && docker compose -f
   docker/dev/docker-compose.yml up -d` después, para que `DATABASECHANGELOG` quede
   consistente.

## Fase 2 — Generar o editar

1. **Archivo nuevo** (`added-table-<Entidad>`):
   - Crear `src/main/resources/liquibase-db-changelogs/changelogs/YYYYMMDDHHMMSS-<Entidad>.xml`,
     timestamp = fecha/hora actual, fija para siempre (no cambia aunque se agreguen más
     changesets después).
   - Primer `<changeSet id="YYYYMMDDHHMMSS-added-table-<Entidad>" author="...">`
     (mismo timestamp que el nombre del archivo) con el `<createTable>`: PK, columnas de
     negocio, FKs si corresponde, y las columnas de auditoría de `Auditable`
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
2. **Archivo existente** (`updated-table-<Entidad>-<detalle>`):
   - Agregar un `<changeSet>` nuevo al final del archivo, con su propio timestamp (momento
     actual) y un `<detalle>` descriptivo del cambio (`added-column-<nombre>`,
     `dropped-column-<nombre>`, `modified-column-<nombre>`, `renamed-column-<nombre>-to-<nuevoNombre>`,
     `added-constraint-<nombre>`, `added-index-<nombre>`).
   - **No** tocar `master.xml` — el `<include>` ya existe.
3. **Nomenclatura estándar de objetos de esquema** (ver tabla completa en
   `ARQUITECTURA.md §6`), siempre `snake_case`:
   - PK: `pk_<tabla>` · FK: `fk_<tabla_origen>_<tabla_destino>` · UNIQUE: `uq_<tabla>_<columna(s)>`
   - CHECK: `ck_<tabla>_<regla>` · INDEX: `idx_<tabla>_<columna(s)>` · TRIGGER: `trg_<tabla>_<momento>_<evento>`
   - Función/procedimiento: `fn_<acción_negocio>` / `sp_<acción_negocio>`
4. **Reflejar el cambio en `Domain/<Entidad>.java`** cuando corresponda: agregar el campo
   con su anotación JPA (`@Column`, `@ManyToOne`, etc.), respetando que `Domain` es solo
   modelo (sin lógica) y la navegabilidad ya definida en el diagrama de clases (ej.
   `Turno` llega a `Medico`/`Prestacion` solo vía `MedicoPrestacion`).
5. **Baja de tabla** (`deleted-table-<Entidad>`): changeset con `<dropTable>`. Confirmar
   con el usuario — es poco común dado el criterio de soft delete del proyecto.

## Fase 3 — Verificar

- [ ] Nombre de archivo `YYYYMMDDHHMMSS-<Entidad>.xml` y de changeset
      `YYYYMMDDHHMMSS-<acción>` siguen la convención de `ARQUITECTURA.md §6`.
- [ ] Si el archivo es nuevo: el `<include>` está en `master.xml`, una sola vez.
- [ ] Si el archivo ya existía: `master.xml` no se tocó.
- [ ] Todo objeto de esquema (PK/FK/UNIQUE/CHECK/INDEX/TRIGGER) tiene nombre explícito
      siguiendo la convención estándar, no un nombre autogenerado por Liquibase.
- [ ] `Domain/<Entidad>.java` quedó sincronizada con el cambio de esquema (si aplicaba).
- [ ] El changelog corre limpio (`./mvnw spring-boot:run` con `dev` activo, o test de
      contexto) — Liquibase no tira error de sintaxis ni de checksum.
- [ ] Si se editó un changeset ya ejecutado (excepción dev): se avisó que hace falta
      resetear el entorno (`docker compose down -v && up -d`).
