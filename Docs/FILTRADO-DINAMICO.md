# Filtrado dinámico — guía para el frontend

Complemento de [`FRONTEND-GUIA.md`](FRONTEND-GUIA.md): mientras esa guía cubre el contrato
general de la API (errores, auth, convenciones), este documento explica **un mecanismo
puntual** que usan los listados y las búsquedas puntuales de las entidades de catálogo:
`Especialidad`, `ObraSocial`, `Plan`, `Prestacion`, `TipoIndicacionPrestacion` e
`IndicacionPrestacion`. El detalle de implementación (Specifications, metamodelo JPA) está
en [`ARQUITECTURA.md §7`](ARQUITECTURA.md); acá solo el contrato que ve el front.

---

## 1. Dos endpoints por entidad, mismo lenguaje de filtros

Cada una de las 6 entidades expone (o va a exponer) dos `GET` que **aceptan los mismos
query params** — la única diferencia es cuántos resultados devuelven:

| Endpoint | Devuelve | Para qué lo usás |
|----------|----------|-------------------|
| `GET /accesmed-api/<Entidad>/<Entidad>` | página de resultados (`PageResponse<T>`) | listados, grillas, tablas con scroll/paginado |
| `GET /accesmed-api/<Entidad>/<Entidad>/Buscar` | **un único objeto**, sin envolver | traer un registro puntual — reemplaza al clásico `GET /{id}` |

Los filtros se arman igual en los dos: no hay un lenguaje de query params para listar y
otro distinto para buscar uno solo. Aprendés el formato una vez y lo usás en ambos.

> **Cobertura actual**: `Especialidad`, `ObraSocial`, `Plan`, `TipoIndicacionPrestacion` e
> `IndicacionPrestacion` ya tienen los dos endpoints. `Prestacion` todavía solo tiene el de
> listado (`GET /Prestacion/Prestacion`) — su `/Buscar` está pendiente.

---

## 2. Cómo se arma un filtro: `campo.operador=valor`

Cada campo filtrable de la entidad se manda como `campo.operador=valor` en la query string.
Mandar varios filtros (o varios campos) los combina con **AND** — no hay forma de pedir OR
entre campos distintos.

### 2.1 Operadores según el tipo de campo

| Tipo de campo | Operadores disponibles | Ejemplo |
|---|---|---|
| Texto (`codigo`, `nombre`, `razonSocial`...) | `equals`, `notEquals`, `contains`, `in`, `notIn`, `specified` | `nombre.contains=cardio` |
| Identificador (`id`, `especialidadId`, `obraSocialId`, `prestacionId`, `tipoIndicacionPrestacionId`) | `equals`, `notEquals`, `in`, `notIn`, `specified` | `id.equals=3fa85f64-5717-4562-b3fc-2c963f66afa6` |
| Estado / enum (`estadoActual`) | `equals`, `notEquals`, `in`, `notIn`, `specified` | `estadoActual.equals=PUBLICADA` |
| Booleano (`requiereValidacion`) | `equals`, `notEquals`, `specified` | `requiereValidacion.equals=true` |
| Fecha de auditoría (`createdDate`, `lastModifiedDate`) | `equals`, `notEquals`, `greaterThan`, `lessThan`, `greaterThanOrEqual`, `lessThanOrEqual`, `specified` | `createdDate.greaterThanOrEqual=2026-01-01T00:00:00Z` |

**Qué significa cada operador:**

| Operador | Significado |
|---|---|
| `equals` | igual exacto |
| `notEquals` | distinto |
| `contains` | contiene el texto, **sin distinguir mayúsculas/minúsculas** (solo texto) |
| `in` | está en una lista de valores — se manda separado por comas: `estadoActual.in=PUBLICADA,NO_PUBLICADA` |
| `notIn` | no está en esa lista |
| `specified` | `true` = el campo no es `null` en la base; `false` = el campo es `null`. Sirve para "traer solo los que tienen X cargado" |
| `greaterThan` / `lessThan` | estrictamente mayor/menor (solo fechas) |
| `greaterThanOrEqual` / `lessThanOrEqual` | mayor o igual / menor o igual (solo fechas) |

### 2.2 Ejemplos

```
GET /accesmed-api/Prestacion/Prestacion?nombre.contains=consulta&estadoActual.equals=PUBLICADA
GET /accesmed-api/Plan/Plan?obraSocialId.equals=3fa85f64-5717-4562-b3fc-2c963f66afa6&estadoActual.notEquals=DESHABILITADO
GET /accesmed-api/Especialidad/Especialidad?codigo.in=CARD,TRAU,CLIN
GET /accesmed-api/IndicacionPrestacion/IndicacionPrestacion?requiereValidacion.equals=true&prestacionId.equals=3fa85f64-...
```

Todos los filtros son **opcionales**. Sin ningún query param, el endpoint de listado trae
todo (paginado) y el de `/Buscar` — al no poder identificar un único resultado salvo que
uses un campo que sea único de por sí — normalmente se usa siempre con al menos un filtro.

---

## 3. Caso de uso: traer una sola instancia (`/Buscar`)

Este es el reemplazo del clásico "obtener por id" (`GET /Prestacion/{id}`), que **ya no
existe** como ruta separada. En su lugar:

```
GET /accesmed-api/Plan/Plan/Buscar?id.equals=3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Diferencias clave respecto al listado:**

- **La respuesta es el objeto suelto**, no un `PageResponse`. Por ejemplo, `GET
  /Plan/Plan/Buscar?id.equals=...` devuelve directamente un `GetPlanResponse`
  (`{ "id": ..., "codigo": ..., "nombre": ..., ... }`), no `{ "content": [...], ... }`.
- **Si nada matchea, es 404** (`PLAN_NO_ENCONTRADO`, `ESPECIALIDAD_NO_ENCONTRADA`, etc. —
  mismo contrato `AccesMedError` de siempre, ver `FRONTEND-GUIA.md §1`), no una lista vacía.
- **Si el criteria matchea más de un resultado, el backend igual devuelve solo uno** (el
  primero que encuentre) — `/Buscar` está pensado para criterios que identifican **un único
  registro** (típicamente `id.equals`, o un campo único como `codigo.equals`). No lo uses
  para "traer el primero de una lista": para eso, usá el endpoint de listado con `size=1`.

**Por qué no quedó un `GET /{id}` de toda la vida**: `/Buscar` reusa exactamente el mismo
mecanismo de filtros que el listado (mismo Criteria, misma clase, mismo parseo de query
params del lado del backend). Reemplazar `id` por cualquier otro campo del Criteria — un
`codigo.equals`, una combinación de dos campos — funciona sin que el backend tenga que
exponer un endpoint nuevo por cada combinación.

### 3.1 Ejemplo completo

Buscar la especialidad con código `"CARD"`:

```
GET /accesmed-api/Especialidad/Especialidad/Buscar?codigo.equals=CARD
```

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "codigo": "CARD",
  "nombre": "Cardiología"
}
```

Si no existe ninguna especialidad activa con ese código:

```json
{
  "timestamp": "2026-08-06T10:15:30Z",
  "status": 404,
  "codigo": "ESPECIALIDAD_NO_ENCONTRADA",
  "mensaje": "No existe una especialidad activa que cumpla el criteria proporcionado.",
  "errores": ["No existe una especialidad activa que cumpla el criteria proporcionado."],
  "path": "/accesmed-api/Especialidad/Especialidad/Buscar"
}
```

---

## 4. Caso de uso: listar con filtros y paginación

```
GET /accesmed-api/Prestacion/Prestacion?estadoActual.equals=PUBLICADA&page=0&size=20&sort=nombre,asc
```

**Query params de paginación** (además de los filtros de la §2), estándar de Spring:

| Parámetro | Default | Significado |
|---|---|---|
| `page` | `0` | número de página, **base 0** |
| `size` | `20` | tamaño de página |
| `sort` | `nombre,asc` (varía por entidad) | campo y dirección de orden. Repetible: `sort=estadoActual,asc&sort=nombre,asc` ordena por varios campos |

**Response — `PageResponse<T>`:**

```json
{
  "content": [
    { "id": "...", "codigo": "CONS01", "nombre": "Consulta general", "estadoActual": "PUBLICADA", ... }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

| Campo | Tipo | Para qué lo usás |
|---|---|---|
| `content` | Array | los resultados de esta página — mismo record `List<...>Response` que ya conocías (`ListPrestacionResponse`, etc.) |
| `page` | number | página actual, para resaltar en el paginador |
| `size` | number | tamaño de página pedido |
| `totalElements` | number | total de resultados que matchean el filtro (sin paginar) — para el contador "57 resultados" |
| `totalPages` | number | para saber cuándo deshabilitar "siguiente" |

---

## 5. Campos filtrables por entidad

| Entidad | Ruta base | Campos del Criteria |
|---|---|---|
| `Especialidad` | `/accesmed-api/Especialidad/Especialidad` | `id`, `codigo`, `nombre`, `createdDate`, `lastModifiedDate` |
| `ObraSocial` | `/accesmed-api/ObraSocial/ObraSocial` | `id`, `codigo`, `nombre`, `razonSocial`, `createdDate`, `lastModifiedDate` |
| `Plan` | `/accesmed-api/Plan/Plan` | `id`, `codigo`, `nombre`, `estadoActual`, `obraSocialId`, `createdDate`, `lastModifiedDate` |
| `Prestacion` | `/accesmed-api/Prestacion/Prestacion` | `id`, `codigo`, `nombre`, `estadoActual`, `especialidadId`, `createdDate`, `lastModifiedDate` |
| `TipoIndicacionPrestacion` | `/accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion` | `id`, `codigo`, `nombre`, `createdDate`, `lastModifiedDate` |
| `IndicacionPrestacion` | `/accesmed-api/IndicacionPrestacion/IndicacionPrestacion` | `id`, `nombre`, `requiereValidacion`, `prestacionId`, `tipoIndicacionPrestacionId`, `createdDate`, `lastModifiedDate` |
| `Medico` | `/accesmed-api/Medico/Medico` | `id`, `matricula`, `dni`, `nombre`, `apellido`, `email`, `especialidadId`, `tieneAgendaVigente`, `agendaVigenteAl`, `createdDate`, `lastModifiedDate` |
| `AgendaMedico` | `/accesmed-api/AgendaMedico/Agenda` | `id`, `medicoId`, `especialidadId`, `fechaHoraInicioVigencia`, `fechaHoraFinVigencia`, `vigenteAl` |
| `AgendaHorariosDia` | `/accesmed-api/AgendaMedico/Horarios` (panel) y `/accesmed-api/AgendaMedico/HorariosDisponibles` (chatbot) | `id`, `agendaMedicoId`, `medicoId`, `prestacionId`, `fecha`, `horaDesde`, `estaOcupada` |

`Medico.tieneAgendaVigente` (`BooleanFilter`) y `agendaVigenteAl` (fecha de referencia,
default "ahora" si no se envía) son un filtro **derivado**: no son columnas de `Medico`,
se resuelven con un `EXISTS`/`NOT EXISTS` contra `agenda_medico`. Reemplazan al endpoint
"médicos sin agenda vigente" de §5 AGEN — se resuelve con
`GET /accesmed-api/Medico/Medico?tieneAgendaVigente.equals=false`. Combinando
`agendaVigenteAl` con una fecha futura se resuelve también el caso "por vencer":
`tieneAgendaVigente.equals=false&agendaVigenteAl.equals=<hoy+30>`.

`AgendaMedico.vigenteAl` es otro filtro derivado (`inicio <= vigenteAl < fin`), pero **sin
valor por defecto**: a diferencia de `agendaVigenteAl`, si no se envía no se aplica ningún
filtro de vigencia — el selector de agendas del front tiene que poder listar también los
períodos vencidos y los programados a futuro, igual que `Prestacion` no filtra por estado
implícitamente.

`AgendaHorariosDia` no tiene guarda fija de "activo" configurable por el front: la aplica
siempre el `QueryService`. `listHorariosAgenda` exige `deletedAt` vacío; `listHorariosDisponibles`
suma además `estaOcupada = false`, `ahora < fechaLimiteReserva` y
`fecha <= hoy + diasMaximosAnticipacionReserva` (horizonte configurado en `Clinica`) — el
front no puede pedir un slot ocupado ni uno fuera del horizonte de reserva.

Los campos de auditoría (`createdDate`/`lastModifiedDate`) y de baja/vigencia (`deletedAt`,
`fechaFinVigencia`) que la entidad excluye automáticamente **no aparecen como filtro**: los
listados y `/Buscar` solo devuelven registros activos/vigentes por diseño, sin que el front
tenga que pedirlo. Ver `ARQUITECTURA.md §7` para el detalle de qué eje (baja lógica, estado
o vigencia) usa cada entidad.

---

## 6. Errores

Mismo contrato de siempre — ver `FRONTEND-GUIA.md §1`. Los códigos específicos de "no
encontrado" por entidad que vas a ver desde `/Buscar`:

| Entidad | Código |
|---|---|
| `Especialidad` | `ESPECIALIDAD_NO_ENCONTRADA` |
| `ObraSocial` | `OBRA_SOCIAL_NO_ENCONTRADA` |
| `Plan` | `PLAN_NO_ENCONTRADO` |
| `TipoIndicacionPrestacion` | `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` |
| `IndicacionPrestacion` | `INDICACION_PRESTACION_NO_ENCONTRADA` |
| `Medico` | `MEDICO_NO_ENCONTRADO` |
| `AgendaMedico` | `AGENDA_MEDICO_NO_ENCONTRADA` |

---

## 7. Checklist rápido

- [ ] Para un listado: `GET /<Entidad>/<Entidad>?campo.operador=valor&page=&size=&sort=`, leer `content` + los metadatos de paginación.
- [ ] Para traer un registro puntual: `GET /<Entidad>/<Entidad>/Buscar?campo.operador=valor`, leer la respuesta directo (sin `content`), manejar 404.
- [ ] Combinar varios filtros = AND. No hay OR entre campos.
- [ ] `in`/`notIn` van separados por comas, sin espacios.
- [ ] Fechas en ISO-8601 UTC, igual que en el resto de la API (`FRONTEND-GUIA.md §6`).
- [ ] No asumir qué campos son filtrables — confirmar contra la tabla de la §5 o contra Swagger (los parámetros de cada Criteria aparecen aplanados ahí).
