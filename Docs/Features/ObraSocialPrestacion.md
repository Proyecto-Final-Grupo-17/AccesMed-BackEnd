# Feature: ObraSocialPrestacion

## Contexto
- Para qué es: registrar qué prestaciones cubre cada plan de una obra social, y en qué
  condiciones (modalidad de cobertura, porcentaje cubierto, coseguro fijo a cargo del
  paciente). Antes de esta feature no existía ese registro: un plan no tenía forma de
  declarar sus coberturas.
- Para qué sirve: es la pieza que le va a permitir al módulo Turno calcular
  `montoAPagar` según la obra social del paciente cuando exista el stack de escritura de
  Turno. Hoy sirve para dejar armado el catálogo de coberturas.
- Quiénes la usan: personal administrativo desde el panel web, al configurar los planes
  de una obra social. No la consume el chatbot.

## Funciones

### Asignar prestación a un plan — `POST /accesmed-api/ObraSocialPrestacion/Asignar`

**Flujo simplificado:**
1. Valida que el plan y la prestación existan y estén activos.
2. Valida que el plan no tenga ya una cobertura activa de esa misma prestación.
3. Valida que la combinación modalidad/porcentaje/coseguro sea coherente: `TOTAL` exige
   100% de cobertura y coseguro cero.
4. Crea la cobertura y la devuelve.

**Request para el front — `AssignObraSocialPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| planId | UUID | Sí | Plan existente al que se le asigna la prestación |
| prestacionId | UUID | Sí | Prestación existente a cubrir |
| modalidadCobertura | `TOTAL` \| `CARGO_FIJO` \| `PORCENTUAL` | Sí | Determina qué combinación de `porcentajeCobertura`/`coseguro` es válida |
| porcentajeCobertura | number (0-100) | Sí | Si la modalidad es `TOTAL`, debe ser 100 |
| coseguro | number (>= 0) | Sí | Si la modalidad es `TOTAL`, debe ser 0 |

**Response para el front — `GetObraSocialPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| id | UUID | Identificador de la cobertura, para desasignarla después |
| planId | UUID | Referencia al plan |
| planCodigo, planNombre | string | Mostrar el plan sin otra consulta |
| obraSocialId | UUID | Navegar a la obra social dueña del plan |
| prestacionId | UUID | Referencia a la prestación |
| prestacionCodigo, prestacionNombre | string | Mostrar la prestación sin otra consulta |
| modalidadCobertura, porcentajeCobertura, coseguro | — | Mostrar las condiciones de la cobertura recién creada |

---

### Desasignar prestación de un plan — `PATCH /accesmed-api/ObraSocialPrestacion/Desasignar/{id}`

Sin body: alcanza con el `id` de la cobertura en la ruta.

**Flujo simplificado:**
1. Busca la cobertura activa por `id`.
2. Verifica que no haya turnos vivos (no finalizados/cancelados) del par plan-prestación
   de esa cobertura. Si los hay, rechaza la baja.
3. Da de baja lógica la cobertura y devuelve la confirmación.

**Response para el front — `UnassignObraSocialPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| id | UUID | Confirmar qué cobertura se dio de baja |
| deletedAt | Instant | Mostrar cuándo se desasignó |
| deletedReason | string | Mostrar el motivo de la baja |

> Si la baja se rechaza por turnos vivos, el front recibe `AccesMedError` con código
> `OBRA_SOCIAL_PLAN_PRESTACION_CON_TURNOS_VIVOS` — mostrar que hay turnos pendientes que
> impiden retirar la cobertura.

---

### Listar coberturas plan-prestación — `GET /accesmed-api/ObraSocialPrestacion/ObraSocialPrestacion`

**Flujo simplificado:**
1. Recibe filtros de query string (`ObraSocialPrestacionCriteria`) y pagina el resultado.
2. Devuelve solo coberturas activas (la baja lógica nunca se lista).

**Filtros disponibles** (todos opcionales, sintaxis `campo.operador=valor`):

| Filtro | Tipo | Notas |
|--------|------|-------|
| id | UUID | Buscar una cobertura puntual |
| planId | UUID | **El más usado**: `planId.equals=<uuid>` trae las prestaciones cubiertas por un plan |
| prestacionId | UUID | Ver en qué planes está cubierta una prestación |
| obraSocialId | UUID | Filtra por transitividad (vía `plan.obraSocial`) |
| modalidadCobertura | enum | `TOTAL` \| `CARGO_FIJO` \| `PORCENTUAL` |

**Response para el front — `PageResponse<ListObraSocialPrestacionResponse>`**

Cada elemento trae los mismos campos que `GetObraSocialPrestacionResponse` (ver arriba),
más los campos estándar de paginación (`totalElements`, `totalPages`, etc.).

---

### Asignación anidada al crear un plan o una obra social

No son endpoints propios: extienden altas ya existentes.

- `POST /accesmed-api/Plan/Plan` (`AddPlanRequest`) acepta un campo opcional
  `coberturas: AsignarCoberturaAnidadaRequest[]` para dejar el plan con sus prestaciones
  ya cubiertas en la misma alta.
- `POST /accesmed-api/ObraSocial/ObraSocial` (`CreateObraSocialRequest.planes[]`, cada
  plan es un `CreatePlanAnidadoRequest`) acepta el mismo campo `coberturas` un nivel más
  adentro, para armar obra social + planes + coberturas en una sola operación atómica.

**`AsignarCoberturaAnidadaRequest`** (elemento de la lista `coberturas`)

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| prestacionId | UUID | Sí | Prestación existente a cubrir |
| modalidadCobertura | `TOTAL` \| `CARGO_FIJO` \| `PORCENTUAL` | Sí | Misma regla de coherencia que el endpoint standalone |
| porcentajeCobertura | number (0-100) | Sí | — |
| coseguro | number (>= 0) | Sí | — |

Si alguna prestación no existe o no está activa, o la combinación no es coherente, toda
la operación de alta (plan u obra social) se rechaza — no queda un plan a medio crear.

---

### Lectura anidada de coberturas al traer un plan

`GetPlanResponse` (respuesta de crear, actualizar y buscar un plan por
`GET /accesmed-api/Plan/Plan/Buscar`) y `GetPlanAnidadoResponse` (el plan anidado dentro
de las respuestas de `ObraSocial`) incluyen un campo `coberturas:
GetCoberturaAnidadaResponse[]` con las prestaciones que el plan cubre actualmente.

**`GetCoberturaAnidadaResponse`** (elemento de la lista `coberturas`)

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| id | UUID | Identificador de la cobertura, para desasignarla vía `ObraSocialPrestacion/Desasignar/{id}` |
| prestacionId, prestacionCodigo, prestacionNombre | — | Mostrar la prestación cubierta sin otra consulta |
| modalidadCobertura, porcentajeCobertura, coseguro | — | Mostrar las condiciones de la cobertura |

Al actualizar un plan (`PATCH /accesmed-api/Plan/Plan/{id}`), el response también trae
`coberturas` con el estado activo actual — no hace falta un `GET` aparte después de
actualizar para refrescar la lista.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
