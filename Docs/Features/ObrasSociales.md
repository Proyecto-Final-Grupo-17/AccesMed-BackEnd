# Feature: Obras Sociales y Planes

## Contexto
- **Para qué es**: el módulo de financiadores. Una `ObraSocial` agrupa uno o más `Plan` (ej. "OSDE" con planes "210", "310", "410"). Los planes son lo que efectivamente cubre un paciente y lo que se asocia a una cobertura de turno.
- **Para qué sirve**: el administrador da de alta obras sociales junto con sus planes iniciales (alta atómica), y gestiona el ciclo de vida de cada plan (publicar/despublicar/deshabilitar) de forma independiente. `ObraSocial` conserva baja lógica; `Plan` se retira por estados, igual que `Prestacion`.
- **Quiénes la usan**: exclusivamente el personal de la clínica (rol administrador) desde el panel web interno.

---

## Funciones

### Crear obra social (con planes) — `POST /accesmed-api/ObraSocial/ObraSocial`

Alta atómica: la obra social y **al menos un plan** se crean en la misma transacción. Cada
plan nace en estado `NO_PUBLICADO`.

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` de la obra social sean únicos entre activas.
2. Crea la obra social.
3. Para cada plan del array: valida que su `codigo`/`nombre` sean únicos dentro de la obra
   social (entre no deshabilitados), lo crea en `NO_PUBLICADO` y abre su primer tramo de
   histórico de estados.
4. Devuelve la obra social creada con todos sus planes.

**Request para el front — `CreateObraSocialRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único entre obras sociales activas. |
| `nombre` | String (máx. 150) | Sí | Nombre comercial, único entre activas. |
| `razonSocial` | String (máx. 200) | Sí | Razón social. |
| `planes` | Array | Sí, al menos 1 | Planes iniciales. Cada elemento: `codigo` (máx. 20) y `nombre` (máx. 150), únicos dentro de esta obra social. |

**Response para el front — `CreateObraSocialResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la obra social. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `razonSocial` | String | Confirmación de la razón social. |
| `planes` | Array de `{id, codigo, nombre, estadoActual}` | Planes creados, cada uno en `NO_PUBLICADO`. Para navegar a la gestión de cada plan. |

**Errores posibles:**
- `OBRA_SOCIAL_CODIGO_DUPLICADO` / `OBRA_SOCIAL_NOMBRE_DUPLICADO` (409).
- `PLAN_CODIGO_DUPLICADO` / `PLAN_NOMBRE_DUPLICADO` (409): algún plan del array repite código o nombre dentro de la obra social.
- Validación Bean (422): array de planes vacío, campos vacíos o exceso de longitud.

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

### Obtener obra social — `GET /accesmed-api/ObraSocial/ObraSocial/{id}`

**Flujo simplificado:**
1. Busca la obra social activa por `id`.
2. Carga todos sus planes (cualquier estado).
3. Devuelve los datos completos.

**Response para el front — `GetObraSocialResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la obra social. |
| `codigo` | String | Código. |
| `nombre` | String | Nombre. |
| `razonSocial` | String | Razón social. |
| `planes` | Array de `{id, codigo, nombre, estadoActual}` | Para listar los planes de la obra social y su estado (publicar/despublicar/deshabilitar). |

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404): no existe o está de baja.

---

### Listar obras sociales — `GET /accesmed-api/ObraSocial/ObraSocial`

**Flujo simplificado:**
1. Recupera todas las obras sociales activas.
2. Devuelve una lista compacta (sin planes).

**Response para el front — `List<ListObraSocialResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Para navegar al detalle. |
| `codigo` | String | Código. |
| `nombre` | String | Nombre. |
| `razonSocial` | String | Razón social. |

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
3. Si todos pasan: deshabilita en cascada esos planes (motivo "Baja de obra social") y
   da de baja lógica la obra social.
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
4. Devuelve el plan creado.

**Request para el front — `AddPlanRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `obraSocialId` | UUID | Sí | Obra social a la que se agrega el plan. |
| `codigo` | String (máx. 20) | Sí | Único dentro de la obra social entre planes no deshabilitados. |
| `nombre` | String (máx. 150) | Sí | Único dentro de la obra social entre planes no deshabilitados. |

**Response para el front — `GetPlanResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador del plan. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `obraSocialId` | UUID | Confirmación de la obra social. |
| `obraSocialNombre` | String | Nombre de la obra social (para mostrar). |
| `estadoActual` | String (`NO_PUBLICADO`\|`PUBLICADO`\|`DESHABILITADO`) | Nace en `NO_PUBLICADO`. |

**Errores posibles:**
- `OBRA_SOCIAL_NO_ENCONTRADA` (404).
- `PLAN_CODIGO_DUPLICADO` / `PLAN_NOMBRE_DUPLICADO` (409).

---

### Actualizar plan — `PATCH /accesmed-api/Plan/Plan/{id}`

**Flujo simplificado:**
1. Valida que el plan exista.
2. Si vino `codigo`/`nombre`, valida unicidad dentro de la obra social (excluyendo este plan) y actualiza.
3. Devuelve el plan actualizado.

**Request para el front — `UpdatePlanRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe coincidir con el `id` de la ruta (422 si difieren). |
| `codigo` | String (máx. 20) | No | `null` o ausente = no lo toques. |
| `nombre` | String (máx. 150) | No | |

**Response para el front — `GetPlanResponse`**

Mismo formato que en "Agregar plan a obra social".

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404).
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
4. Cierra el tramo vigente del histórico, abre uno nuevo en `DESHABILITADO` (con el
   `motivo`, si vino) y actualiza `estadoActual`.
5. Devuelve el plan deshabilitado.

**Pendiente (TODO)**: bajar `ObraSocialPlanPrestacion` y `ObraSocialPaciente` asociados —
se implementa cuando esos módulos estén en alcance.

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

### Obtener plan — `GET /accesmed-api/Plan/Plan/{id}`

**Response para el front — `GetPlanResponse`**

Mismo formato que en "Agregar plan a obra social".

**Errores posibles:**
- `PLAN_NO_ENCONTRADO` (404).

---

### Listar planes de una obra social — `GET /accesmed-api/Plan/Plan?obraSocialId={id}`

**Query parameters**

| Parámetro | Tipo | Obligatorio | Significado |
|-----------|------|-------------|-------------|
| `obraSocialId` | UUID | Sí | Obra social cuyos planes se listan. |

**Response para el front — `List<ListPlanResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Para navegar al detalle o gestionar el estado. |
| `codigo` | String | Código del plan. |
| `nombre` | String | Nombre del plan. |
| `obraSocialId` | UUID | Confirmación de la obra social. |
| `obraSocialNombre` | String | Nombre de la obra social. |
| `estadoActual` | String | Para marcar visualmente o filtrar por estado. |

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
