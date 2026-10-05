# Migrar IndicacionPrestacion al eje de vigencia

## Contexto

`Docs/Dominio/dominio-reglas-validaciones.md` documenta `IndicacionPrestacion` en el eje
**vigencia** (`fechaInicioVigencia`/`fechaFinVigencia`, sin baja lógica — se retira
cerrando `fechaFinVigencia`, que admite fecha futura para poder programar la baja). Es una
decisión deliberada de la migración v2→v3: como `IndicacionPrestacionTurno` lee el texto
de la indicación por navegabilidad (no lo copia), la vigencia es lo que permite programar
un "relevo" (cerrar la vieja, abrir la nueva) sin romper turnos ya reservados.

El código actual todavía implementa `IndicacionPrestacion` con baja lógica
(`deletedAt`/`deletedBy`/`deletedReason`), y `TipoIndicacionPrestacionApp` valida su baja
restrictiva contra indicaciones "activas" (`deletedAt IS NULL`) en vez de "vigentes". Franco
confirmó que este desvío entre doc y código es real y hay que corregirlo.

**Alcance de esta migración** (confirmado con Franco: lo chico primero, esto es la parte
grande): migrar la entidad al eje de vigencia de punta a punta — esquema, entidad, repo,
service, mapper, records, app, controller — y corregir el chequeo cruzado desde
`TipoIndicacionPrestacionApp`. **Fuera de alcance, para un pase posterior**:
- La restricción de "no modificar mientras haya `IndicacionPrestacionTurno` con turno en
  estado no final" (doc, requiere cruzar con el dominio de `Turno`).

**Hecho en un segundo pase, mismo día**: se cableó la cascada de
`PrestacionApp.disablePrestacion` — cierra `fechaFinVigencia = ZonedDateTime.now()` en las
`IndicacionPrestacion` vigentes de la prestación (`indicacionPrestacionDomainService
.cerrarVigenciaIndicacionesPrestacionByPrestacion(id, ZonedDateTime.now())`). Es seguro
usar "ahora" sin comparar contra la fecha de ningún turno porque `disablePrestacion` ya
llama `turnoDomainService.validateSinTurnosVivos(id)` antes: si hay turnos vivos, la
operación entera se rechaza, así que para cuando se llega a cerrar la vigencia no queda
ningún turno vivo que proteger (a diferencia de `MedicoPrestacion`, que sí necesita el piso
`fechaFinVigencia ≥ fechaHoraInicio del último turno vivo` porque su desasignación no es
restrictiva). El resto de la cascada (`MedicoPrestacion` vigentes, `AgendaHorarios` libres,
`ObraSocialPlanPrestacion`) sigue fuera de alcance: esos módulos no están construidos
todavía.

## Cambios por archivo

### 1. Liquibase — `src/main/resources/liquibase-db-changelogs/changelogs/20260731215700-IndicacionPrestacion.xml`

Estamos en `dev`: se edita el changeset ya ejecutado in place (no se agrega uno nuevo),
igual que están escritos `AgendaMedico`/`UsuarioRol` desde el principio. Este changelog
corre **antes** que el de `AgendaMedico` (ver `master.xml`), así que la extensión
`btree_gist` no está garantizada todavía — hay que habilitarla acá también (idempotente).

- Quitar columnas `deleted_at`, `deleted_by`, `deleted_reason` y el índice
  `uq_indicacion_prestacion_prestacion_nombre` (`WHERE deleted_at IS NULL`).
- Agregar `fecha_inicio_vigencia timestamptz NOT NULL` y `fecha_fin_vigencia timestamptz`
  (nullable — abierta = todavía vigente).
- Agregar changeset previo `20260731215700-enabled-extension-btree-gist` (idéntico al de
  `AgendaMedico`, `20260731215800-AgendaMedico.xml` líneas 9-14); el `added-table` pasa a
  `20260731215701-added-table-IndicacionPrestacion` para no chocar de id.
- `ck_indicacion_prestacion_vigencia`: `CHECK (fecha_fin_vigencia IS NULL OR fecha_fin_vigencia > fecha_inicio_vigencia)`
  (mismo patrón que `UsuarioRol`, `20260731220400-UsuarioRol.xml` línea 34).
- `ex_indicacion_prestacion_no_solapamiento`: `EXCLUDE USING gist (prestacion_id WITH =, nombre WITH =, tstzrange(fecha_inicio_vigencia, fecha_fin_vigencia) WITH &&)`
  (mismo patrón que `AgendaMedico` líneas 44-50, adaptado a dos columnas de igualdad).
  Reemplaza la unicidad vieja: dos indicaciones del mismo `(prestacion, nombre)` ya no
  pueden solaparse en el tiempo, pero sí convivir en tramos distintos (relevo).
- Mantener `idx_indicacion_prestacion_tipo_indicacion_prestacion`.
- Actualizar el `<rollback>` acorde.

### 2. `Domain/IndicacionPrestacion.java`

Reemplazar la región `//region ========== Baja ==========` (deletedAt/deletedBy/deletedReason)
por `//region ========== Vigencia ==========` con `fechaInicioVigencia`
(`ZonedDateTime`, `@NotNull`, `@Setter`) y `fechaFinVigencia` (`ZonedDateTime`, `@Setter`,
nullable) — mismo patrón de campos que `Domain/UsuarioRol.java`. Cambiar el import de
`Instant` a `ZonedDateTime`. Actualizar el Javadoc de la clase (ya no "admite alta y baja
lógica": ahora "acotado por vigencia, no tiene baja lógica").

### 3. `Repositories/IndicacionPrestacionRepository.java`

Reemplazar los 4 métodos `...DeletedAtIsNull` por consultas `@Query` JPQL parametrizadas
por el instante a evaluar (la vigencia no se puede expresar con derivación de nombre por
la combinación AND/OR con paréntesis):

```java
@Query("SELECT i FROM IndicacionPrestacion i WHERE i.id = :id " +
       "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
Optional<IndicacionPrestacion> findVigenteById(UUID id, ZonedDateTime ahora);

@Query("SELECT i FROM IndicacionPrestacion i WHERE i.prestacion.id = :prestacionId " +
       "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
List<IndicacionPrestacion> findAllVigentesByPrestacionId(UUID prestacionId, ZonedDateTime ahora);

@Query("SELECT i FROM IndicacionPrestacion i " +
       "WHERE i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
List<IndicacionPrestacion> findAllVigentes(ZonedDateTime ahora);

@Query("SELECT COUNT(i) > 0 FROM IndicacionPrestacion i WHERE i.tipoIndicacionPrestacion.id = :tipoIndicacionPrestacionId " +
       "AND i.fechaInicioVigencia <= :ahora AND (i.fechaFinVigencia IS NULL OR i.fechaFinVigencia > :ahora)")
boolean existsVigenteByTipoIndicacionPrestacionId(UUID tipoIndicacionPrestacionId, ZonedDateTime ahora);
```

### 4. `Services/DomainServices/IndicacionPrestacionDomainService.java`

- `findIndicacionPrestacionById` → `findIndicacionPrestacionVigenteById(UUID id)` (mismo
  patrón `find<Entidad>Activa(o)ById` de la skill §5.0.1, aplicado al eje vigencia), llama
  `findVigenteById(id, ZonedDateTime.now())`.
- `existsIndicacionesActivasByTipo` → `existsIndicacionesVigentesByTipo(UUID tipoIndicacionPrestacionId)`,
  llama `existsVigenteByTipoIndicacionPrestacionId(tipoId, ZonedDateTime.now())`.
- `findIndicacionesPrestacionByPrestacionId` → `findIndicacionesPrestacionVigentesByPrestacionId(UUID prestacionId)`,
  llama `findAllVigentesByPrestacionId(prestacionId, ZonedDateTime.now())`.
- `softDeleteIndicacionPrestacion(entidad, motivo)` → `cerrarVigenciaIndicacionPrestacion(IndicacionPrestacion indicacionPrestacion, ZonedDateTime fechaFinVigencia)`:
  valida que `fechaFinVigencia` sea posterior a `fechaInicioVigencia` (`ValidacionException`
  si no), setea el campo y guarda. Sin `motivo`: ese concepto no existe en el eje vigencia.
- `softDeleteIndicacionesPrestacionByPrestacion` → `cerrarVigenciaIndicacionesPrestacionByPrestacion(UUID prestacionId, ZonedDateTime fechaFinVigencia)`:
  mismo bucle, ahora sobre `findIndicacionesPrestacionVigentesByPrestacionId`. No tiene
  callers hoy (el TODO de `PrestacionApp.disablePrestacion` queda igual, fuera de
  alcance) — se deja lista para cuando se cablee esa cascada.

### 5. `Services/QueryServices/IndicacionPrestacionQueryService.java`

`findAllIndicacionesPrestacion`/`findIndicacionesPrestacionByPrestacion` pasan a llamar
`findAllVigentes`/`findAllVigentesByPrestacionId` (con `ZonedDateTime.now()`) en vez de los
métodos `...DeletedAtIsNull`.

### 6. `Services/Mappers/IndicacionPrestacionMapper.java`

- En `toEntity` (create anidada) y `updateIndicacionPrestacion`: quitar los `@Mapping(target = "deletedAt/deletedBy/deletedReason", ignore = true)`
  (ya no existen en la entidad) y agregar `@Mapping(target = "fechaInicioVigencia", ignore = true)`
  + `@Mapping(target = "fechaFinVigencia", ignore = true)` — se setean explícitamente en el
  App (alta: `fechaInicioVigencia = ZonedDateTime.now()`; update no los toca).
- Quitar `toSoftDeleteResponse(IndicacionPrestacion)`. Agregar
  `toScheduleBajaResponse(IndicacionPrestacion)` → `ScheduleBajaIndicacionPrestacionResponse`
  (mapea `id` y `fechaFinVigencia`).

### 7. Records

- **Borrar** `Records/IndicacionPrestacion/Response/SoftDeleteIndicacionPrestacionResponse.java`.
- **Nuevo** `Records/IndicacionPrestacion/Request/ScheduleBajaIndicacionPrestacionRequest.java`:
  `record ScheduleBajaIndicacionPrestacionRequest(UUID id, ZonedDateTime fechaFinVigencia)` —
  `id` con `@NotNull` (se valida contra la ruta, mismo patrón que los demás Update);
  `fechaFinVigencia` **opcional** (sin `@NotNull`): `null` en el request significa "ahora",
  documentado en Javadoc del campo.
- **Nuevo** `Records/IndicacionPrestacion/Response/ScheduleBajaIndicacionPrestacionResponse.java`:
  `record ScheduleBajaIndicacionPrestacionResponse(UUID id, ZonedDateTime fechaFinVigencia)`.

### 8. `Application/IndicacionPrestacionApp.java`

- `createIndicacionesPrestacion`: al resolver cada indicación nueva (mismo bucle que ya
  resuelve `prestacion`/`tipoIndicacionPrestacion`), setear también
  `.setFechaInicioVigencia(ZonedDateTime.now())`.
- `updateIndicacionPrestacion`: cambia el call interno a
  `indicacionPrestacionDomainService.findIndicacionPrestacionVigenteById(id)`.
- `softDeleteIndicacionPrestacion(UUID id)` → `scheduleBajaIndicacionPrestacion(ScheduleBajaIndicacionPrestacionRequest scheduleBajaIndicacionPrestacionRequest)`:
  extrae `id` del request (mismo patrón sin-id-duplicado ya aplicado al resto de la app),
  busca vigente, resuelve `fechaFinVigencia = request.fechaFinVigencia() != null ? ... : ZonedDateTime.now()`,
  llama `cerrarVigenciaIndicacionPrestacion`, devuelve `toScheduleBajaResponse` con
  variable nombrada completa (§10.4).
- `findIndicacionPrestacionById`/`findIndicacionesPrestacion`: mismo nombre público, solo
  cambia la llamada interna al DomainService/QueryService renombrado.

### 9. `Controllers/IndicacionPrestacionController.java`

Reemplazar `@DeleteMapping("/IndicacionPrestacion/{id}") softDeleteIndicacionPrestacion(@PathVariable UUID id)`
por `@PatchMapping("/IndicacionPrestacion/{id}/Baja") scheduleBajaIndicacionPrestacion(@PathVariable UUID id, @Valid @RequestBody ScheduleBajaIndicacionPrestacionRequest scheduleBajaIndicacionPrestacionRequest)`
— sigue el patrón `update<Concepto><Entidad>` de la skill §5.1 (PATCH con concepto propio
en la ruta, `Concepto = Baja`). Igual que el resto de los PATCH con body: valida que el
`id` de la ruta coincida con `scheduleBajaIndicacionPrestacionRequest.id()`
(`ValidacionException` si no) antes de delegar en el App, mismo bloque que ya usan
`updateEspecialidad`/`updatePlan`/etc.

### 10. `Application/TipoIndicacionPrestacionApp.java` — el fix que motivó todo esto

En `softDeleteTipoIndicacionPrestacion`, línea de la validación restrictiva: cambiar
`indicacionPrestacionDomainService.existsIndicacionesActivasByTipo(id)` por
`existsIndicacionesVigentesByTipo(id)`. Actualizar el mensaje de log/excepción
("indicaciones activas" → "indicaciones vigentes").

## Verificación

`mvnw -o clean compile` → debe dar `BUILD SUCCESS`. No hay tests automatizados en el
proyecto todavía, así que la verificación es de compilación + revisión manual del
changelog (no hay entorno Postgres local corriendo para levantar el schema en esta sesión).
