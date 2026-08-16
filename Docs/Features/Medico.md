# Feature: Médico

## Contexto

- **Para qué es**: el catálogo de médicos es quién atiende los turnos. Cada médico tiene
  una única especialidad y, opcionalmente, un conjunto de prestaciones existentes que
  atiende (con sus condiciones de atención particular: si atiende de forma particular y a
  qué precio).

- **Para qué sirve**: el administrador da de alta un médico junto con las prestaciones que
  va a atender desde el arranque, y después ajusta esa lista con altas y bajas puntuales
  sin tocar el resto del médico. La especialidad de cada prestación asignada tiene que
  coincidir siempre con la especialidad del médico.

- **Quiénes la usan**: exclusivamente el personal de la clínica (rol administrador) desde
  el panel web interno.

---

## Funciones

### Crear médico — `POST /accesmed-api/Medico/Medico`

**Flujo simplificado:**
1. Valida que la matrícula, el DNI y el email sean únicos entre médicos activos.
2. Busca la especialidad indicada (debe existir y estar activa) y crea el médico.
3. Por cada prestación del array `prestaciones`, busca la prestación (debe existir y no
   estar deshabilitada), valida que su especialidad coincida con la del médico recién
   creado, y crea el vínculo médico-prestación con sus condiciones particulares.
4. Devuelve el médico creado, con la lista de prestaciones que quedó asignada.

**Request para el front — `CreateMedicoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `matricula` | String (máx. 30) | Sí | Única entre médicos activos. |
| `dni` | String (máx. 15) | Sí | Único entre médicos activos. |
| `nombre` | String (máx. 100) | Sí | |
| `apellido` | String (máx. 100) | Sí | |
| `email` | String (máx. 150, formato email) | Sí | Único entre médicos activos. |
| `numeroTelefono` | String (máx. 30) | Sí | |
| `especialidadId` | UUID | Sí | Debe ser una especialidad activa existente. |
| `prestaciones` | Array de objetos | No | Puede venir vacío. Ver tabla siguiente. |

**Prestación anidada — `AsignarPrestacionAnidadaRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `prestacionId` | UUID | Sí | Debe ser una prestación existente y no deshabilitada, con la misma especialidad que el médico. |
| `atiendeParticular` | Boolean | Sí | Para mostrar el ícono/badge de atención particular. |
| `precioParticular` | BigDecimal (> 0) | Sí | Para mostrar el precio particular de esa prestación con este médico. |
| `fechaInicioVigencia` | ZonedDateTime | No | Desde cuándo rige la asignación. Ausente = el instante del alta. |

**Response para el front — `CreateMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador único del médico, necesario para editar, dar de baja o asignarle más prestaciones. |
| `matricula`, `dni`, `nombre`, `apellido`, `email`, `numeroTelefono` | String | Para mostrar en listados y fichas. |
| `especialidadId` | UUID | Para mostrar la especialidad o resolverla contra el catálogo. |
| `prestaciones` | Array de `GetPrestacionAnidadaResponse` | Para mostrar de entrada las prestaciones que atiende. Cada ítem trae `id` (de la asignación, usado para cerrarle la vigencia), `prestacionId`, `prestacionCodigo`, `prestacionNombre`, `atiendeParticular`, `precioParticular`, `fechaInicioVigencia` y `fechaFinVigencia`. Solo llegan las **vigentes**. |

---

### Actualizar médico — `PATCH /accesmed-api/Medico/Medico/{id}`

**Flujo simplificado:**
1. Verifica que el `id` de la ruta coincida con el del body.
2. Busca el médico activo y valida unicidad solo de los campos que vinieron con valor.
3. Aplica los cambios (un campo en `null` deja ese dato sin tocar); si vino
   `especialidadId`, busca la nueva especialidad activa y la reemplaza.
4. Devuelve el médico actualizado, con sus prestaciones asignadas actuales (no se tocan acá).

**Request para el front — `UpdateMedicoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe ser el mismo que el `{id}` de la URL; si difieren, el backend responde 422. |
| `matricula` | String (máx. 30) | No | `null` deja la matrícula sin tocar. |
| `dni` | String (máx. 15) | No | `null` deja el DNI sin tocar. |
| `nombre` | String (máx. 100) | No | `null` deja el nombre sin tocar. |
| `apellido` | String (máx. 100) | No | `null` deja el apellido sin tocar. |
| `email` | String (máx. 150, formato email) | No | `null` deja el email sin tocar. |
| `numeroTelefono` | String (máx. 30) | No | `null` deja el número de teléfono sin tocar. |
| `especialidadId` | UUID | No | `null` deja la especialidad sin tocar. No reevalúa las prestaciones ya asignadas. |

No incluye `prestaciones`: se gestionan por los endpoints de Médico-Prestación (más abajo).

**Response para el front — `GetMedicoResponse`**

Mismo contrato que `CreateMedicoResponse`.

---

### Buscar médico — `GET /accesmed-api/Medico/Medico/Buscar`

Recibe filtros de [filtrado dinámico](../FILTRADO-DINAMICO.md) (`id`, `matricula`, `dni`,
`nombre`, `apellido`, `email`, `especialidadId`, `createdDate`, `lastModifiedDate`) y
devuelve el primer médico activo que los cumple, con el mismo contrato que
`GetMedicoResponse`. Pensado para criterios puntuales (ej. `id.equals`).

### Listar médicos — `GET /accesmed-api/Medico/Medico`

Recibe los mismos filtros dinámicos, paginados. Cada ítem usa `ListMedicoResponse`: igual
que `GetMedicoResponse` pero **sin** la lista de prestaciones (evita N+1 en listados).

### Dar de baja médico — `DELETE /accesmed-api/Medico/Medico/{id}`

**Flujo simplificado:**
1. Busca el médico activo.
2. Valida que no tenga turnos vivos (estado actual no final). Si tiene, rechaza la baja.
3. Marca el médico como dado de baja (`deletedAt`/`deletedReason`).
4. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeleteMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmar qué médico se dio de baja. |
| `deletedAt` | Instant | Para mostrar cuándo se dio de baja. |
| `deletedReason` | String | Para mostrar el motivo. |

---

## Médico-Prestación (asignar/desasignar prestaciones)

No es CRUD completo: solo dos acciones para modificar la lista de prestaciones de un
médico ya creado, fuera del alta atómica.

### Asignar prestación a médico — `POST /accesmed-api/MedicoPrestacion/Asignar`

> **`MedicoPrestacion` se rige por vigencia, no por baja lógica.** Una asignación es un
> período (`fechaInicioVigencia` → `fechaFinVigencia`), no una fila que se borra.
> Desasignar es **cerrar el período**, y reasignar es **crear una instancia nueva**, nunca
> reabrir la vieja. Consecuencia para el front: un médico puede tener varias asignaciones
> de la misma prestación a lo largo del tiempo, y las listas pueden traer períodos ya
> cerrados — filtrar por `fechaFinVigencia === null` para ver solo las vigentes.

**Flujo simplificado:**
1. Busca el médico y la prestación (ambos deben existir y estar activos).
2. Valida que la especialidad de la prestación coincida con la del médico.
3. Valida que el período nuevo no se solape con otro del mismo par médico-prestación.
4. Crea el vínculo y lo devuelve.

**Request para el front — `AssignMedicoPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `medicoId` | UUID | Sí | Médico activo existente. |
| `prestacionId` | UUID | Sí | Prestación existente y no deshabilitada, con la misma especialidad que el médico. |
| `atiendeParticular` | Boolean | Sí | |
| `precioParticular` | BigDecimal (> 0) | Sí | |
| `fechaInicioVigencia` | ZonedDateTime | No | Desde cuándo rige la asignación. Ausente = ahora. Admite fecha futura: así se programa un alta. |

**Response para el front — `GetMedicoPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la asignación, necesario para cerrarle la vigencia después. |
| `medicoId`, `prestacionId` | UUID | Para relacionar la asignación con médico y prestación. |
| `prestacionCodigo`, `prestacionNombre` | String | Para mostrar sin otra consulta. |
| `atiendeParticular`, `precioParticular` | Boolean / BigDecimal | Para mostrar las condiciones particulares. |
| `fechaInicioVigencia` | ZonedDateTime | Desde cuándo rige. |
| `fechaFinVigencia` | ZonedDateTime \| null | Hasta cuándo. `null` = vigente sin corte. |

**Errores posibles:**
- `MEDICO_PRESTACION_ESPECIALIDAD_DISTINTA` (422): la especialidad de la prestación no coincide con la del médico.
- `MEDICO_PRESTACION_SOLAPADA` (422): el período pedido se cruza con otra vigencia del mismo par médico-prestación.

### Desasignar prestación de médico — `PATCH /accesmed-api/MedicoPrestacion/Vigencia/{id}`

Cierra el período de vigencia de la asignación identificada por su propio `id` (el que
devolvió el `Asignar`, o el que trae cada ítem de `prestaciones` en las respuestas de
Médico). **No borra nada**: la fila queda con su `fechaFinVigencia` puesta.

**Request — `UnassignMedicoPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe coincidir con el `id` de la ruta. |
| `fechaFinVigencia` | ZonedDateTime | No | Fecha de corte. Ausente = ahora. Admite fecha futura: así se programa la baja. |

Responde con `UnassignMedicoPrestacionResponse` (`id`, `fechaFinVigencia`).

**Errores posibles:**
- `MEDICO_PRESTACION_NO_ENCONTRADA` (404): no hay una asignación vigente con ese id.
- `MEDICO_PRESTACION_CORTE_ANTERIOR_A_TURNO` (422): la fecha de corte es anterior al
  `fechaHoraInicio` de algún turno vivo de ese par. El mensaje trae la fecha del turno más
  lejano; la salida es cancelar o reprogramar esos turnos primero.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
