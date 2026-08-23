# Feature: Obras Sociales y Planes

## Contexto
- **Para qué es**: el módulo de financiadores. Una `ObraSocial` agrupa uno o más `Plan` (ej. "OSDE" con planes "210", "310", "410"). Los planes son lo que efectivamente cubre un paciente y lo que se asocia a una cobertura de turno. Cada plan, a su vez, declara qué `Prestacion` cubre y en qué condiciones (`ObraSocialPlanPrestacion`: modalidad de cobertura, porcentaje y coseguro) — es el catálogo de coberturas que más adelante va a usar el módulo Turno para calcular `montoAPagar` según la obra social del paciente.
- **Para qué sirve**: el administrador da de alta obras sociales junto con sus planes iniciales (alta atómica), y gestiona el ciclo de vida de cada plan (publicar/despublicar/deshabilitar) de forma independiente. También asigna o desasigna las prestaciones que cubre cada plan, ya sea en el mismo alta (anidado) o después (endpoint dedicado). `ObraSocial` conserva baja lógica; `Plan` se retira por estados, igual que `Prestacion`; `ObraSocialPlanPrestacion` conserva baja lógica, igual que `ObraSocialPaciente`.
- **Quiénes la usan**: exclusivamente el personal de la clínica (rol administrador) desde el panel web interno.

---

## Funciones

### Crear obra social (con planes) — `POST /accesmed-api/ObraSocial/ObraSocial`

Alta atómica: la obra social y, opcionalmente, sus planes iniciales se crean en la misma
transacción. Si no se envían planes, la obra social nace sin ninguno y se agregan después
con el alta individual (`POST /accesmed-api/Plan/Plan`). Cada plan nace en estado
`NO_PUBLICADO`.

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` de la obra social sean únicos entre activas.
2. Crea la obra social.
3. Si vino el array de planes: para cada uno valida que su `codigo`/`nombre` sean únicos
   dentro de la obra social (entre no deshabilitados), lo crea en `NO_PUBLICADO` y abre su
   primer tramo de histórico de estados.
4. Si algún plan trajo `coberturas`: para cada una valida que la prestación exista activa
   y que la modalidad/porcentaje/coseguro sean coherentes, y crea la cobertura
   (`ObraSocialPlanPrestacion`). Si algo falla acá, se rechaza toda el alta — no queda una
   obra social ni un plan a medio crear.
5. Devuelve la obra social creada con sus planes y las coberturas de cada uno (si se enviaron).

**Request para el front — `CreateObraSocialRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único entre obras sociales activas. |
| `nombre` | String (máx. 150) | Sí | Nombre comercial, único entre activas. |
| `razonSocial` | String (máx. 200) | Sí | Razón social. |
| `planes` | Array | No | Planes iniciales, opcionales. Si se omite o va vacío, la obra social se crea sin planes. Cada elemento: `codigo` (máx. 20), `nombre` (máx. 150), únicos dentro de esta obra social, y opcionalmente `coberturas` (ver abajo). |
| `planes[].coberturas` | Array | No | Prestaciones que cubre ese plan desde el alta. Cada elemento: `prestacionId` (UUID de una prestación existente y activa), `modalidadCobertura` (`TOTAL`\|`CARGO_FIJO`\|`PORCENTUAL`), `porcentajeCobertura` (0-100; si la modalidad es `TOTAL` debe ser 100), `coseguro` (>= 0; si la modalidad es `TOTAL` debe ser 0). |

**Response para el front — `CreateObraSocialResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la obra social. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `razonSocial` | String | Confirmación de la razón social. |
| `planes` | Array de `{id, codigo, nombre, estadoActual, coberturas}` | Planes creados (vacío si no se enviaron), cada uno en `NO_PUBLICADO`. Para navegar a la gestión de cada plan. |
| `planes[].coberturas` | Array de `{id, prestacionId, prestacionCodigo, prestacionNombre, modalidadCobertura, porcentajeCobertura, coseguro}` | Coberturas creadas para ese plan (vacío si no se enviaron). `id` sirve para desasignar la cobertura después. |

**Errores posibles:**
- `OBRA_SOCIAL_CODIGO_DUPLICADO` / `OBRA_SOCIAL_NOMBRE_DUPLICADO` (409).
- `PLAN_CODIGO_DUPLICADO` / `PLAN_NOMBRE_DUPLICADO` (409): algún plan del array repite código o nombre dentro de la obra social.
- `PRESTACION_NO_ENCONTRADA` (404): alguna cobertura anidada referencia una prestación inexistente o no activa.
- Validación Bean (422): campos vacíos o exceso de longitud (código, nombre, razón social, o algún plan/cobertura del array).

---

### Actualizar obra social — `PATCH /accesmed-api/ObraSocial/ObraSocial/{id}`

**Flujo simplificado:**
1. Valida que la obra social exista.
2. Si vino `codigo`/`nombre`, valida unicidad (excluyendo esta obra social) y actualiza.
3. Si vino `razonSocial`, la actualiza.
4. Devuelve la obra social actualizada con sus planes actuales.

**Request para el front — `UpdateObraSocialRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe coincidir con el `id` de la ruta (422 si difieren). |
| `codigo` | String (máx. 20) | No | `null` o ausente = no lo toques. |
| `nombre` | String (máx. 150) | No | |
| `razonSocial` | String (máx. 200) | No | |

**Response para el front — `GetObraSocialResponse`**

Mismo formato que la respuesta de obtención (ver abajo).

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404).
- `OBRA_SOCIAL_CODIGO_DUPLICADO` / `OBRA_SOCIAL_NOMBRE_DUPLICADO` (409).

---

### Buscar obra social — `GET /accesmed-api/ObraSocial/ObraSocial/Buscar`

Filtrado dinámico — reemplaza al clásico "obtener por id". Ver
[`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md) para el formato completo de filtros.

**Flujo simplificado:**
1. Busca la única obra social activa que cumple el criteria (típico: `id.equals=<uuid>`).
2. Carga todos sus planes (cualquier estado) con las coberturas activas de cada uno.
3. Devuelve los datos completos, o 404 si ninguna matchea.

**Query params** — `ObraSocialCriteria`: `id`, `codigo`, `nombre`, `razonSocial`,
`createdDate`, `lastModifiedDate`.

**Response para el front — `GetObraSocialResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la obra social. |
| `codigo` | String | Código. |
| `nombre` | String | Nombre. |
| `razonSocial` | String | Razón social. |
| `planes` | Array de `{id, codigo, nombre, estadoActual, coberturas}` | Para listar los planes de la obra social y su estado (publicar/despublicar/deshabilitar). |
| `planes[].coberturas` | Array de `{id, prestacionId, prestacionCodigo, prestacionNombre, modalidadCobertura, porcentajeCobertura, coseguro}` | Prestaciones activas que cubre cada plan, sin otra consulta. |

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404): ninguna obra social activa cumple el criteria.

---

### Listar obras sociales — `GET /accesmed-api/ObraSocial/ObraSocial`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).

**Flujo simplificado:**
1. Recupera las obras sociales activas que cumplen el criteria (sin filtros = todas).
2. Devuelve una página de resultados (sin planes).

**Query params** — `ObraSocialCriteria` (`id`, `codigo`, `nombre`, `razonSocial`,
`createdDate`, `lastModifiedDate`) + paginación (`page`, `size`, `sort`).

**Response para el front — `PageResponse<ListObraSocialResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar al detalle. |
| `content[].codigo` | String | Código. |
| `content[].nombre` | String | Nombre. |
| `content[].razonSocial` | String | Razón social. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

### Dar de baja obra social — `DELETE /accesmed-api/ObraSocial/ObraSocial/{id}`

Baja lógica **restrictiva por transitividad**: antes de dar de baja la obra social, evalúa
si **cada uno** de sus planes no deshabilitados puede deshabilitarse (misma precondición
que deshabilitar un plan individual). Si alguno no puede, rechaza toda la operación sin
tocar nada.

**Flujo simplificado:**
1. Valida que la obra social exista.
2. Para cada plan no deshabilitado de la obra social: valida que no tenga turnos vivos
   cubiertos por él. Si alguno falla, rechaza toda la operación.
3. Si todos pasan: para cada plan no deshabilitado, da de baja lógica sus coberturas
   (`ObraSocialPlanPrestacion`) y sus `ObraSocialPaciente`, y lo deshabilita (motivo "Baja
   de obra social"). Por último da de baja lógica la obra social.
4. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeleteObraSocialResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación de la obra social dada de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404).
- `PLAN_CON_TURNOS_VIVOS` (409): al menos un plan de la obra social tiene turnos vivos; no se da de baja nada.

---

### Agregar plan a obra social — `POST /accesmed-api/Plan/Plan`

Agrega un plan nuevo a una obra social **activa** ya existente. Nace en `NO_PUBLICADO`.

**Flujo simplificado:**
1. Valida que la obra social exista y esté activa.
2. Valida que el `codigo`/`nombre` del plan sean únicos dentro de la obra social (entre no deshabilitados).
3. Crea el plan en `NO_PUBLICADO` y abre su primer tramo de histórico de estados.
4. Si vino `coberturas`: para cada una valida que la prestación exista activa y que la
   modalidad/porcentaje/coseguro sean coherentes, y crea la cobertura.
5. Devuelve el plan creado, con sus coberturas (si se enviaron).

**Request para el front — `AddPlanRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `obraSocialId` | UUID | Sí | Obra social a la que se agrega el plan. |
| `codigo` | String (máx. 20) | Sí | Único dentro de la obra social entre planes no deshabilitados. |
| `nombre` | String (máx. 150) | Sí | Único dentro de la obra social entre planes no deshabilitados. |
| `coberturas` | Array | No | Prestaciones que cubre el plan desde el alta. Cada elemento: `prestacionId` (UUID de una prestación existente y activa), `modalidadCobertura` (`TOTAL`\|`CARGO_FIJO`\|`PORCENTUAL`), `porcentajeCobertura` (0-100; si la modalidad es `TOTAL` debe ser 100), `coseguro` (>= 0; si la modalidad es `TOTAL` debe ser 0). |

**Response para el front — `GetPlanResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador del plan. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `obraSocialId` | UUID | Confirmación de la obra social. |
| `obraSocialNombre` | String | Nombre de la obra social (para mostrar). |
| `estadoActual` | String (`NO_PUBLICADO`\|`PUBLICADO`\|`DESHABILITADO`) | Nace en `NO_PUBLICADO`. |
| `coberturas` | Array de `{id, prestacionId, prestacionCodigo, prestacionNombre, modalidadCobertura, porcentajeCobertura, coseguro}` | Coberturas creadas para el plan (vacío si no se enviaron). `id` sirve para desasignar la cobertura después. |

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404).
- `PLAN_CODIGO_DUPLICADO` / `PLAN_NOMBRE_DUPLICADO` (409).
- `PRESTACION_NO_ENCONTRADA` (404): alguna cobertura anidada referencia una prestación inexistente o no activa.

---

### Actualizar plan — `PATCH /accesmed-api/Plan/Plan/{id}`

Se puede modificar en `NO_PUBLICADO` o `PUBLICADO` — no hay bloqueo por publicación.
`DESHABILITADO` es terminal e irreversible, así que un plan en ese estado no admite más
cambios (mismo criterio que `Prestacion`).

**Flujo simplificado:**
1. Valida que el plan exista y no esté deshabilitado.
2. Si vino `codigo`/`nombre`, valida unicidad dentro de la obra social (excluyendo este plan) y actualiza.
3. Devuelve el plan actualizado, con sus coberturas activas actuales.

**Request para el front — `UpdatePlanRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe coincidir con el `id` de la ruta (422 si difieren). |
| `codigo` | String (máx. 20) | No | `null` o ausente = no lo toques. |
| `nombre` | String (máx. 150) | No | |

**Response para el front — `GetPlanResponse`**

Mismo formato que en "Agregar plan a obra social" — incluye `coberturas` con el estado
activo actual, así que no hace falta un `GET` aparte después de actualizar para refrescar
la lista de prestaciones cubiertas.

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404): el plan no existe o está deshabilitado.
- `PLAN_CODIGO_DUPLICADO` / `PLAN_NOMBRE_DUPLICADO` (409).

---

### Publicar plan — `PATCH /accesmed-api/Plan/Plan/{id}/Publicar`

Transición **reversible** `NO_PUBLICADO → PUBLICADO`.

**Este endpoint no lleva body en el request.**

**Response para el front — `CambioEstadoPlanResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del id. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `estadoActual` | String | `PUBLICADO` siempre en este response. |

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404).
- `PLAN_NO_PUBLICABLE` (409): el plan no está en `NO_PUBLICADO`.

---

### Despublicar plan — `PATCH /accesmed-api/Plan/Plan/{id}/Despublicar`

Transición **reversible** `PUBLICADO → NO_PUBLICADO`.

**Este endpoint no lleva body en el request.**

**Response para el front — `CambioEstadoPlanResponse`**

Igual formato que "Publicar plan", con `estadoActual = NO_PUBLICADO`.

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404).
- `PLAN_NO_DESPUBLICABLE` (409): el plan no está en `PUBLICADO`.

---

### Deshabilitar plan — `PATCH /accesmed-api/Plan/Plan/{id}/Deshabilitar`

Transición **terminal e irreversible**. Restrictiva: **única** precondición es que no haya
turnos vivos cubiertos por el plan. **No** rige la regla del "último plan no deshabilitado
de una obra social activa" (eliminada en v3): una obra social puede terminar con todos sus
planes deshabilitados sin que eso la afecte.

**Flujo simplificado:**
1. Valida que el plan exista.
2. Valida que no esté ya deshabilitado.
3. **Precondición restrictiva real**: rechaza si hay algún `Turno` con
   `obraSocialPaciente.plan` apuntando a este plan y estado no final.
4. Da de baja lógica las `ObraSocialPaciente` y las coberturas `ObraSocialPlanPrestacion`
   que referencian el plan.
5. Cierra el tramo vigente del histórico, abre uno nuevo en `DESHABILITADO` (con el
   `motivo`, si vino); el estado vigente se deriva del histórico, no se cachea.
6. Devuelve el plan deshabilitado.

**Request para el front — `DeshabilitarPlanRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe coincidir con el `id` de la ruta (422 si difieren). |
| `motivo` | String (máx. 500) | No | Se guarda en el histórico. |

**Response para el front — `CambioEstadoPlanResponse`**

Igual formato que "Publicar plan", con `estadoActual = DESHABILITADO`.

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404).
- `PLAN_YA_DESHABILITADO` (409).
- `PLAN_CON_TURNOS_VIVOS` (409): el mensaje incluye la cantidad y la fecha más lejana.

---

### Buscar plan — `GET /accesmed-api/Plan/Plan/Buscar`

Filtrado dinámico — reemplaza al clásico "obtener por id". Ver
[`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md) para el formato completo de filtros.
Trae el plan en **cualquier estado**, incluidos los deshabilitados (a diferencia de otros
flujos del módulo, que solo ven planes no deshabilitados).

**Query params** — `PlanCriteria`: `id`, `codigo`, `nombre`, `estadoActual`, `obraSocialId`,
`createdDate`, `lastModifiedDate`.

**Response para el front — `GetPlanResponse`**

Mismo formato que en "Agregar plan a obra social".

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404): ningún plan cumple el criteria.

---

### Listar planes — `GET /accesmed-api/Plan/Plan`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).
`obraSocialId` **dejó de ser obligatorio**: sin filtros trae planes de todas las obras
sociales; para los planes de una sola, seguí mandando `obraSocialId.equals=<uuid>`.

**Query params** — `PlanCriteria` (`id`, `codigo`, `nombre`, `estadoActual`, `obraSocialId`,
`createdDate`, `lastModifiedDate`) + paginación (`page`, `size`, `sort`).

```
GET /accesmed-api/Plan/Plan?obraSocialId.equals=3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response para el front — `PageResponse<ListPlanResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar al detalle o gestionar el estado. |
| `content[].codigo` | String | Código del plan. |
| `content[].nombre` | String | Nombre del plan. |
| `content[].obraSocialId` | UUID | Confirmación de la obra social. |
| `content[].obraSocialNombre` | String | Nombre de la obra social. |
| `content[].estadoActual` | String | Para marcar visualmente o filtrar por estado. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

### Asignar prestación a un plan — `POST /accesmed-api/ObraSocialPrestacion/Asignar`

Asigna, fuera del alta, una prestación existente a un plan existente ya creado (la
alternativa al `coberturas` anidado de "Crear obra social" / "Agregar plan a obra social").

**Flujo simplificado:**
1. Valida que el plan y la prestación existan y estén activos.
2. Valida que el plan no tenga ya una cobertura activa de esa misma prestación.
3. Valida que la combinación modalidad/porcentaje/coseguro sea coherente (`TOTAL` exige
   100% de cobertura y coseguro cero).
4. Crea la cobertura y la devuelve.

**Request para el front — `AssignObraSocialPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `planId` | UUID | Sí | Plan existente al que se le asigna la prestación. |
| `prestacionId` | UUID | Sí | Prestación existente a cubrir. |
| `modalidadCobertura` | `TOTAL`\|`CARGO_FIJO`\|`PORCENTUAL` | Sí | Determina qué combinación de `porcentajeCobertura`/`coseguro` es válida. |
| `porcentajeCobertura` | number (0-100) | Sí | Si la modalidad es `TOTAL`, debe ser 100. |
| `coseguro` | number (>= 0) | Sí | Si la modalidad es `TOTAL`, debe ser 0. |

**Response para el front — `GetObraSocialPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la cobertura, para desasignarla después. |
| `planId`, `planCodigo`, `planNombre` | — | Mostrar el plan sin otra consulta. |
| `obraSocialId` | UUID | Navegar a la obra social dueña del plan. |
| `prestacionId`, `prestacionCodigo`, `prestacionNombre` | — | Mostrar la prestación sin otra consulta. |
| `modalidadCobertura`, `porcentajeCobertura`, `coseguro` | — | Mostrar las condiciones de la cobertura recién creada. |

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` / `PRESTACION_NO_ENCONTRADA` (404).
- `OBRA_SOCIAL_PLAN_PRESTACION_YA_ASIGNADA` (409): el plan ya tiene una cobertura activa de esa prestación.
- Validación Bean (422): combinación modalidad/porcentaje/coseguro incoherente, o campos faltantes.

---

### Desasignar prestación de un plan — `PATCH /accesmed-api/ObraSocialPrestacion/Desasignar/{id}`

Baja lógica de una cobertura. **Este endpoint no lleva body en el request** — alcanza con
el `id` de la cobertura en la ruta.

**Flujo simplificado:**
1. Busca la cobertura activa por `id`.
2. Verifica que no haya turnos vivos (no finalizados/cancelados) del par plan-prestación
   de esa cobertura. Si los hay, rechaza la baja.
3. Da de baja lógica la cobertura y devuelve la confirmación.

**Response para el front — `UnassignObraSocialPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmar qué cobertura se dio de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `OBRA_SOCIAL_PLAN_PRESTACION_NO_ENCONTRADA` (404).
- `OBRA_SOCIAL_PLAN_PRESTACION_CON_TURNOS_VIVOS` (409): hay turnos vivos del par plan-prestación; el mensaje incluye la fecha más lejana.

---

### Listar coberturas plan-prestación — `GET /accesmed-api/ObraSocialPrestacion/ObraSocialPrestacion`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md). Sin
endpoint `/Buscar` — para traer una cobertura puntual, filtrar por `id.equals=<uuid>` en
este mismo listado.

**Flujo simplificado:**
1. Recupera las coberturas activas que cumplen el criteria (sin filtros = todas).
2. Devuelve una página de resultados.

**Query params** — `ObraSocialPrestacionCriteria`: `id`, `planId`, `prestacionId`,
`obraSocialId` (derivado, vía `plan.obraSocial`), `modalidadCobertura`.

```
GET /accesmed-api/ObraSocialPrestacion/ObraSocialPrestacion?planId.equals=3fa85f64-5717-4562-b3fc-2c963f66afa6
```

La forma más común: `planId.equals=<uuid>` trae las prestaciones cubiertas por un plan
puntual (alternativa al `coberturas` que ya viene anidado al traer el plan).

**Response para el front — `PageResponse<ListObraSocialPrestacionResponse>`**

Mismo formato que `GetObraSocialPrestacionResponse` (ver "Asignar prestación a un plan")
por cada elemento de `content[]`, más los metadatos de paginación (`page`, `size`,
`totalElements`, `totalPages`).

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
