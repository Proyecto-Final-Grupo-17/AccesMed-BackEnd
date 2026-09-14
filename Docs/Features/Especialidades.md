# Feature: Especialidades

## Contexto
- **Para qué es**: la especialidad médica es el eje de clasificación que agrupa médicos y prestaciones (ej. "Cardiología", "Clínica Médica"). Es la entidad de catálogo más simple, y sienta el patrón de baja lógica restrictiva del resto del sistema.
- **Para qué sirve**: el administrador arma el catálogo de especialidades antes de cargar médicos o prestaciones, ya que ambos exigen una especialidad existente.
- **Quiénes la usan**: exclusivamente el personal de la clínica (rol administrador) desde el panel web interno.

---

## Funciones

### Crear especialidad — `POST /accesmed-api/Especialidad/Especialidad`

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` sean únicos entre especialidades activas.
2. Crea la especialidad.
3. Devuelve la especialidad creada con su `id` asignado.

**Request para el front — `CreateEspecialidadRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único entre especialidades activas. |
| `nombre` | String (máx. 100) | Sí | Nombre descriptivo, único entre especialidades activas. |

**Response para el front — `CreateEspecialidadResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la especialidad, necesario para asignarla a médicos y prestaciones. |
| `codigo` | String | Confirmación del código creado. |
| `nombre` | String | Confirmación del nombre. |

**Errores posibles:**
- `ESPECIALIDAD_CODIGO_DUPLICADO` (409): ya existe una especialidad activa con ese código.
- `ESPECIALIDAD_NOMBRE_DUPLICADO` (409): ya existe una especialidad activa con ese nombre.

---

### Actualizar especialidad — `PATCH /accesmed-api/Especialidad/Especialidad/{id}`

**Flujo simplificado:**
1. Valida que la especialidad exista.
2. Si vino `codigo`, valida que sea único (excluyendo esta especialidad) y lo actualiza.
3. Si vino `nombre`, valida que sea único (excluyendo esta especialidad) y lo actualiza.
4. Devuelve la especialidad actualizada.

**Request para el front — `UpdateEspecialidadRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `codigo` | String (máx. 20) | No | `null` o ausente = no lo toques. |
| `nombre` | String (máx. 100) | No | `null` o ausente = no lo toques. |

**Response para el front — `GetEspecialidadResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |

**Errores posibles:**
- `ESPECIALIDAD_NO_ENCONTRADA` (404): la especialidad no existe.
- `ESPECIALIDAD_CODIGO_DUPLICADO` (409): otra especialidad activa ya tiene ese código.
- `ESPECIALIDAD_NOMBRE_DUPLICADO` (409): otra especialidad activa ya tiene ese nombre.

---

### Buscar especialidad — `GET /accesmed-api/Especialidad/Especialidad/Buscar`

Filtrado dinámico — reemplaza al clásico "obtener por id". Ver
[`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md) para el formato completo de filtros.

**Flujo simplificado:**
1. Busca la única especialidad activa que cumple el criteria (típico: `id.equals=<uuid>`).
2. Devuelve sus datos, o 404 si ninguna matchea.

**Query params** — `EspecialidadCriteria`: `id`, `codigo`, `nombre`, `createdDate`, `lastModifiedDate`.

**Response para el front — `GetEspecialidadResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la especialidad. |
| `codigo` | String | Código de la especialidad. |
| `nombre` | String | Nombre. |

**Errores posibles:**
- `ESPECIALIDAD_NO_ENCONTRADA` (404): ninguna especialidad activa cumple el criteria.

---

### Listar especialidades — `GET /accesmed-api/Especialidad/Especialidad`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).

**Flujo simplificado:**
1. Recupera las especialidades activas que cumplen el criteria (sin filtros = todas).
2. Devuelve una página de resultados.

**Query params** — `EspecialidadCriteria` (`id`, `codigo`, `nombre`, `createdDate`,
`lastModifiedDate`) + paginación (`page`, `size`, `sort`).

**Response para el front — `PageResponse<ListEspecialidadResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar a detalle/edición, o para usar como `especialidadId` al crear un médico/prestación. |
| `content[].codigo` | String | Código de la especialidad. |
| `content[].nombre` | String | Nombre, para mostrar en selects/listados. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

### Dar de baja especialidad — `DELETE /accesmed-api/Especialidad/Especialidad/{id}`

Baja lógica **restrictiva**: no se puede dar de baja una especialidad en uso.

**Flujo simplificado:**
1. Valida que la especialidad exista.
2. **Precondición restrictiva real**: rechaza si hay algún `Medico` activo con esa
   especialidad, o alguna `Prestacion` no deshabilitada con esa especialidad.
3. Ejecuta la baja lógica (`deletedAt = ahora`).
4. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeleteEspecialidadResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación de la especialidad dada de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `ESPECIALIDAD_NO_ENCONTRADA` (404): la especialidad no existe o ya está de baja.
- `ESPECIALIDAD_CON_MEDICOS_ACTIVOS` (409): hay médicos activos con esta especialidad.
- `ESPECIALIDAD_CON_PRESTACIONES_ACTIVAS` (409): hay prestaciones no deshabilitadas con esta especialidad.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
