# Feature: Prestaciones e Indicaciones

## Contexto

- **Para qué es**: el catálogo de prestaciones es la configuración raíz del sistema de turnos. Define qué servicios ofrece la clínica, cuánto dura cada uno y, a través de siete tolerancias configurables, establece todo el calendario de vencimientos que después se congela en cada turno (fechas límite de validación, reprogramación, confirmación, cancelación, anuncio y recordatorio).

- **Para qué sirve**: el administrador arma el catálogo, le carga a cada prestación sus indicaciones previas (ayuno, estudios previos, etc., clasificadas por tipo), y la publica. Solo las prestaciones publicadas se ofrecen para dar y pedir turnos. El ciclo de vida es por estados (`NO_PUBLICADA ⇄ PUBLICADA → DESHABILITADA`): publicar/despublicar es reversible las veces que haga falta; deshabilitar es terminal e irreversible, y es restrictivo (rechaza si la prestación tiene turnos vivos o agenda futura ocupada).

- **Quiénes la usan**: exclusivamente el personal de la clínica (rol administrador) desde el panel web interno. El chatbot de WhatsApp **no escribe** en este módulo; a futuro solo consumirá prestaciones habilitadas.

---

## Funciones

### Crear tipo de indicación — `POST /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion`

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` sean únicos entre tipos activos.
2. Crea el tipo de indicación con ambos campos.
3. Devuelve el tipo creado con su `id` asignado.

**Request para el front — `CreateTipoIndicacionPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único e inmutable. Identifica el tipo en el sistema. |
| `nombre` | String (máx. 100) | Sí | Nombre descriptivo del tipo (ej. "Ayuno preoperatorio", "Estudios previos"). |

**Response para el front — `CreateTipoIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador único del tipo, necesario para asignarlo a indicaciones en la creación de prestación. |
| `codigo` | String | Para mostrar en listados y confirmaciones. |
| `nombre` | String | Para mostrar al usuario. |

**Errores posibles:**
- `TIPO_INDICACION_PRESTACION_CODIGO_DUPLICADO` (422): ya existe un tipo activo con ese código.
- `TIPO_INDICACION_PRESTACION_NOMBRE_DUPLICADO` (422): ya existe un tipo activo con ese nombre.
- Validación Bean (422): campos vacíos o exceden longitud máxima.

---

### Actualizar tipo de indicación — `PUT /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Valida que el tipo exista.
2. Valida que el `codigo` y `nombre` sean únicos, excluyendo la instancia actual.
3. Actualiza el tipo.
4. Devuelve el tipo actualizado.

**Request para el front — `UpdateTipoIndicacionPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (validación 422 si difieren). |
| `codigo` | String (máx. 20) | Sí | Código único, excluyendo este tipo. Inmutable desde el alta, pero editables en actualizaciones. |
| `nombre` | String (máx. 100) | Sí | Nombre descriptivo, único, excluyendo este tipo. |

**Response para el front — `UpdateTipoIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del tipo actualizado. |
| `codigo` | String | Confirmación del nuevo código. |
| `nombre` | String | Confirmación del nuevo nombre. |

**Errores posibles:**
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): el tipo no existe.
- `TIPO_INDICACION_PRESTACION_CODIGO_DUPLICADO` (422): otro tipo ya tiene ese código.
- `TIPO_INDICACION_PRESTACION_NOMBRE_DUPLICADO` (422): otro tipo ya tiene ese nombre.
- Validación Bean (422): campos vacíos o exceden longitud máxima.

---

### Dar de baja tipo de indicación — `DELETE /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Valida que el tipo exista.
2. **Valida que no hay indicaciones activas que lo referencien** (única baja restrictiva del sistema).
3. Ejecuta la baja lógica (marca `deletedAt`).
4. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeleteTipoIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del tipo dado de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): el tipo no existe.
- `TIPO_INDICACION_PRESTACION_EN_USO` (422): hay indicaciones activas que lo referencian. Hay que darlas de baja primero.

---

### Buscar tipo de indicación — `GET /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion/Buscar`

Filtrado dinámico — reemplaza al clásico "obtener por id". Ver
[`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md) para el formato completo de filtros.

**Query params** — `TipoIndicacionPrestacionCriteria`: `id`, `codigo`, `nombre`,
`createdDate`, `lastModifiedDate`.

**Response para el front — `GetTipoIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador del tipo. |
| `codigo` | String | Código para mostrar en detalle. |
| `nombre` | String | Nombre para mostrar en detalle. |

**Errores posibles:**
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): ningún tipo activo cumple el criteria.

---

### Listar tipos de indicación — `GET /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).

**Query params** — `TipoIndicacionPrestacionCriteria` (`id`, `codigo`, `nombre`,
`createdDate`, `lastModifiedDate`) + paginación (`page`, `size`, `sort`).

**Response para el front — `PageResponse<ListTipoIndicacionPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Identificador para seleccionar en dropdowns al crear indicaciones. |
| `content[].codigo` | String | Código para mostrar en listados. |
| `content[].nombre` | String | Nombre descriptivo para mostrar al usuario. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

### Crear prestación — `POST /accesmed-api/Prestacion/Prestacion`

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` sean únicos entre prestaciones **no deshabilitadas**.
2. Valida que la especialidad exista.
3. **Valida las reglas de tolerancia** (ver sección "Reglas de tolerancia" abajo).
4. Crea la prestación en estado **`NO_PUBLICADA`** y abre el primer tramo de su histórico de estados.
5. Si hay indicaciones anidadas en el request, las crea y las asocia en la misma transacción.
6. Devuelve la prestación creada con sus indicaciones.

**Request para el front — `CreatePrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único e inmutable. Al deshabilitar una prestación el código queda libre (la unicidad rige entre no deshabilitadas). |
| `nombre` | String (máx. 150) | Sí | Nombre descriptivo. Editable en `NO_PUBLICADA` o `PUBLICADA`; no en `DESHABILITADA` (terminal). |
| `duracionMinimaMinutos` | Integer | Sí | Minutos. Mayor a cero. Debe ser ≤ `duracionMaximaMinutos`. |
| `duracionMaximaMinutos` | Integer | Sí | Minutos. Mayor a cero. Debe ser ≥ `duracionMinimaMinutos`. |
| `tiempoToleranciaSolicitudMinutos` | Integer | Sí | Minutos (≥ 0). Antelación mínima para reservar un turno. **Techo de la cadena de tolerancias.** |
| `tiempoToleranciaValidacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo se pueden validar indicaciones. Debe ser ≤ tolerancia de solicitud. |
| `tiempoToleranciaReprogramacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo se puede reprogramar. Debe ser ≤ tolerancia de validación. |
| `tiempoToleranciaConfirmacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo el paciente confirma por su cuenta. Debe ser ≤ tolerancia de reprogramación. |
| `tiempoToleranciaCancelacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo el paciente cancela. Debe ser ≤ tolerancia de confirmación. |
| `tiempoToleranciaAnuncioMinutos` | Integer | Sí | Minutos (≥ 0). Semiancho de la ventana de anuncio en recepción. **No sigue el orden de la cadena.** |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | Sí | Minutos (≥ 0). Cuánto antes se envía el recordatorio. Debe ser > tolerancia de confirmación y ≤ tolerancia de solicitud. |
| `especialidadId` | UUID | Sí | Identificador de la especialidad a la que pertenece esta prestación. Inmutable tras el alta. |
| `indicaciones` | Array (opcional) | No | Array anidado de indicaciones iniciales (se carga todo junto). Cada indicación valida su estructura. |

**Indicaciones anidadas en el request — `CreateIndicacionPrestacionAnidadaRequest`** (dentro del array `indicaciones`)

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `nombre` | String (máx. 150) | Sí | Nombre de la indicación (ej. "Ayuno 8 horas"). |
| `descripcion` | String (máx. 1000) | Sí | Descripción detallada de qué debe cumplir el paciente. |
| `requiereValidacion` | Boolean | Sí | Si es `true`, el turno nace en "Espera de Validación"; el admin debe confirmar luego que se cumplió. Si es `false`, el turno nace en "Pendiente". |
| `tipoIndicacionPrestacionId` | UUID | Sí | Clasificación de esta indicación (referencia a un tipo ya creado). |

**Response para el front — `CreatePrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la prestación. Para navegar al detalle, listar indicaciones, publicar. |
| `codigo` | String | Confirmación del código creado. |
| `nombre` | String | Confirmación del nombre. |
| `duracionMinimaMinutos` | Integer | Confirmación. |
| `duracionMaximaMinutos` | Integer | Confirmación. |
| `tiempoToleranciaSolicitudMinutos` | Integer | Confirmación de la tolerancia. |
| `tiempoToleranciaValidacionMinutos` | Integer | Confirmación. |
| `tiempoToleranciaReprogramacionMinutos` | Integer | Confirmación. |
| `tiempoToleranciaConfirmacionMinutos` | Integer | Confirmación. |
| `tiempoToleranciaCancelacionMinutos` | Integer | Confirmación. |
| `tiempoToleranciaAnuncioMinutos` | Integer | Confirmación. |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | Confirmación. |
| `especialidadId` | UUID | Identificador de la especialidad. |
| `especialidadNombre` | String | Nombre de la especialidad (para mostrar). |
| `estadoActual` | String (`NO_PUBLICADA`\|`PUBLICADA`\|`DESHABILITADA`) | Nace en `NO_PUBLICADA`. Para mostrar el estado y habilitar el botón "Publicar". |
| `indicaciones` | Array | Lista de indicaciones creadas (vacía si no se proporcionaron). |

**Errores posibles:**
- `PRESTACION_CODIGO_DUPLICADO` (409): ya existe una prestación no deshabilitada con ese código.
- `PRESTACION_NOMBRE_DUPLICADO` (409): ya existe una prestación no deshabilitada con ese nombre.
- Validación de reglas de tolerancia (422): véase "Reglas de tolerancia" abajo.
- `ESPECIALIDAD_NO_ENCONTRADA` (404): la especialidad no existe.
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): uno de los tipos de indicación no existe.

---

### Actualizar prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}`

Cubre nombre y las 9 duraciones/tolerancias. A diferencia de v2, **se puede modificar en
`NO_PUBLICADA` o `PUBLICADA`** — no hay bloqueo por publicación. `DESHABILITADA` es
terminal e irreversible, así que una prestación en ese estado no admite más cambios.

**Flujo simplificado:**
1. Valida que la prestación exista y no esté deshabilitada.
2. Si vino `nombre`, valida que sea único entre no deshabilitadas (excluyendo esta prestación).
3. Revalida las reglas de tolerancia con el resultado de aplicar los campos que vinieron
   sobre los valores actuales de la prestación.
4. Actualiza los campos que vinieron en el request.
5. Devuelve la prestación actualizada.

**Request para el front — `UpdatePrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `nombre` | String (máx. 150) | No | `null` o ausente = no lo toques. |
| `duracionMinimaMinutos` | Integer | No | `null` o ausente = no lo toques. |
| `duracionMaximaMinutos` | Integer | No | |
| `tiempoToleranciaSolicitudMinutos` | Integer | No | |
| `tiempoToleranciaValidacionMinutos` | Integer | No | |
| `tiempoToleranciaReprogramacionMinutos` | Integer | No | |
| `tiempoToleranciaConfirmacionMinutos` | Integer | No | |
| `tiempoToleranciaCancelacionMinutos` | Integer | No | |
| `tiempoToleranciaAnuncioMinutos` | Integer | No | |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | No | |

**Response para el front — `UpdatePrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación. |
| `codigo` | String | Solo para confirmar (inmutable). |
| `nombre` | String | Confirmación del nombre. |
| `duracionMinimaMinutos` | Integer | Confirmación de todos los campos actualizados. |
| `duracionMaximaMinutos` | Integer | |
| `tiempoToleranciaSolicitudMinutos` | Integer | |
| `tiempoToleranciaValidacionMinutos` | Integer | |
| `tiempoToleranciaReprogramacionMinutos` | Integer | |
| `tiempoToleranciaConfirmacionMinutos` | Integer | |
| `tiempoToleranciaCancelacionMinutos` | Integer | |
| `tiempoToleranciaAnuncioMinutos` | Integer | |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | |
| `especialidadId` | UUID | Confirmación de la especialidad. |
| `especialidadNombre` | String | Nombre de la especialidad. |
| `estadoActual` | String | Para saber en qué estado quedó la prestación. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe o está deshabilitada.
- `PRESTACION_NOMBRE_DUPLICADO` (409): otra prestación no deshabilitada ya tiene ese nombre.
- Validación de reglas de tolerancia (422): si la cadena de tolerancias falla.

---

### Publicar prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}/Publicar`

Transición **reversible** `NO_PUBLICADA → PUBLICADA`. No exige tener un médico asignado.

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Valida que esté en `NO_PUBLICADA` (si no, rechaza).
3. Cierra el tramo vigente del histórico y abre uno nuevo en `PUBLICADA` (el estado vigente se deriva del histórico, no se cachea).
4. Devuelve la prestación publicada.

**Este endpoint no lleva body en el request.**

**Response para el front — `CambioEstadoPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del id. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `estadoActual` | String | `PUBLICADA` siempre en este response. Para actualizar el estado en pantalla. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_NO_PUBLICABLE` (409): la prestación no está en `NO_PUBLICADA` (ya está publicada o está deshabilitada).

---

### Despublicar prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}/Despublicar`

Transición **reversible** `PUBLICADA → NO_PUBLICADA`.

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Valida que esté en `PUBLICADA` (si no, rechaza).
3. Cierra el tramo vigente del histórico y abre uno nuevo en `NO_PUBLICADA` (el estado vigente se deriva del histórico, no se cachea).
4. Devuelve la prestación despublicada.

**Este endpoint no lleva body en el request.**

**Response para el front — `CambioEstadoPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del id. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre. |
| `estadoActual` | String | `NO_PUBLICADA` siempre en este response. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_NO_DESPUBLICABLE` (409): la prestación no está en `PUBLICADA`.

---

### Deshabilitar prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}/Deshabilitar`

Transición **terminal e irreversible** — es la baja del eje "estados". No hay vuelta atrás:
"revivir" una prestación deshabilitada significa crearla de nuevo (el `codigo` queda
libre porque la unicidad rige entre no deshabilitadas). Es **restrictiva**: rechaza si la
prestación está en uso vigente.

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Valida que no esté ya deshabilitada.
3. **Precondición restrictiva real**: rechaza si hay algún `Turno` de la prestación con
   estado no final, o algún `AgendaHorarios` futuro ocupado de la prestación.
4. Cierra el tramo vigente del histórico y abre uno nuevo en `DESHABILITADA` (con el
   `motivo`, si vino); el estado vigente se deriva del histórico, no se cachea.
5. Devuelve la prestación deshabilitada.

**Pendiente (TODO)**: cerrar `MedicoPrestacion` vigentes, bajar `AgendaHorarios` libres,
cerrar `IndicacionPrestacion` vigentes y bajar `ObraSocialPlanPrestacion` asociadas — se
implementa cuando esos módulos existan.

**Request para el front — `DeshabilitarPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `motivo` | String (máx. 500) | No | Motivo de la deshabilitación, se guarda en el histórico. |

**Response para el front — `CambioEstadoPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del id. |
| `codigo` | String | Confirmación del código (a partir de acá, reutilizable en una prestación nueva). |
| `nombre` | String | Confirmación del nombre. |
| `estadoActual` | String | `DESHABILITADA` siempre en este response. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_YA_DESHABILITADA` (409): la prestación ya está deshabilitada.
- `PRESTACION_CON_TURNOS_VIVOS` (409): hay turnos con estado no final de esta prestación. El mensaje incluye la cantidad y la fecha más lejana.
- `PRESTACION_CON_AGENDA_OCUPADA` (409): hay horarios de agenda futuros ocupados de esta prestación.

---

> **`Prestacion` todavía no tiene `/Buscar`** (a diferencia de las demás entidades de este
> módulo): por ahora no hay forma de traer una prestación puntual con su lista de
> indicaciones salvo filtrando el listado por `id.equals=<uuid>` y leyendo `content[0]` (sin
> indicaciones — ese response solo trae los campos de `ListPrestacionResponse`). Pendiente.

### Listar prestaciones — `GET /accesmed-api/Prestacion/Prestacion`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).

**Flujo simplificado:**
1. Recupera las prestaciones que cumplen el criteria (sin filtros = todas).
2. Devuelve una página de resultados.

**Query params** — `PrestacionCriteria` (`id`, `codigo`, `nombre`, `estadoActual`,
`especialidadId`, `createdDate`, `lastModifiedDate`) + paginación (`page`, `size`, `sort`).

**Response para el front — `PageResponse<ListPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar a detalle o edición. |
| `content[].codigo` | String | Código de la prestación. |
| `content[].nombre` | String | Nombre. |
| `content[].especialidadId` | UUID | Para mostrar relaciones. |
| `content[].especialidadNombre` | String | Nombre de la especialidad (para mostrar). |
| `content[].estadoActual` | String | Para marcar visualmente o filtrar (colores, estados, etc.). |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

### Crear indicaciones de prestación — `POST /accesmed-api/IndicacionPrestacion/IndicacionPrestacion`

Crea **varias indicaciones juntas, en una sola operación**, todas asociadas a la misma
prestación (a diferencia del soft delete, que siempre es de a una).

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Para cada indicación de la lista: valida que su tipo de indicación exista.
3. Crea todas las indicaciones y las asocia a la prestación.
4. Devuelve las indicaciones creadas.

**Request para el front — `CreateIndicacionesPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `prestacionId` | UUID | Sí | Identificador de la prestación a la que pertenecen todas las indicaciones. |
| `indicaciones` | Array | Sí, al menos 1 | Cada item: `nombre` (máx. 150), `descripcion` (máx. 1000), `requiereValidacion` (Boolean), `tipoIndicacionPrestacionId` (UUID). |

**Response para el front — `CreateIndicacionesPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `indicaciones` | Array | Una entrada por indicación creada: `id`, `nombre`, `descripcion`, `requiereValidacion`, `prestacionId`, `tipoIndicacionPrestacionId`, `tipoIndicacionPrestacionNombre`. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): algún tipo de indicación de la lista no existe.

---

### Actualizar indicación de prestación — `PUT /accesmed-api/IndicacionPrestacion/IndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Valida que la indicación exista.
2. Valida que los `id` coincidan.
3. Valida que la prestación de la indicación esté en **borrador** (si está habilitada, lanza error).
4. Valida que el tipo exista.
5. Actualiza y devuelve.

**Request para el front — `UpdateIndicacionPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `nombre` | String (máx. 150) | Sí | Editable solo en borrador. |
| `descripcion` | String (máx. 1000) | Sí | Editable solo en borrador. |
| `requiereValidacion` | Boolean | Sí | Editable solo en borrador. |
| `tipoIndicacionPrestacionId` | UUID | Sí | Editable solo en borrador. |

**Response para el front — `UpdateIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación. |
| `nombre` | String | Confirmación de todos los campos actualizados. |
| `descripcion` | String | |
| `requiereValidacion` | Boolean | |
| `prestacionId` | UUID | Confirmación de la prestación. |
| `tipoIndicacionPrestacionId` | UUID | Confirmación del tipo. |
| `tipoIndicacionPrestacionNombre` | String | Nombre del tipo. |

**Errores posibles:**
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404): la indicación no existe.
- `PRESTACION_INDICACION_HABILITADA` (422): la prestación está habilitada.
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): el tipo no existe.

---

### Dar de baja indicación de prestación — `DELETE /accesmed-api/IndicacionPrestacion/IndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Valida que la indicación exista.
2. Ejecuta la baja lógica (`deletedAt = ahora`).
3. Devuelve la confirmación de la baja.

**Notas**: La baja es libre, sin restricciones. Una indicación de una prestación habilitada sí puede darse de baja (lo que bloquea es la **modificación**, no la baja).

**Response para el front — `SoftDeleteIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación de la indicación dada de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404): la indicación no existe o ya está de baja.

---

### Buscar indicación de prestación — `GET /accesmed-api/IndicacionPrestacion/IndicacionPrestacion/Buscar`

Filtrado dinámico — reemplaza al clásico "obtener por id". Ver
[`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md) para el formato completo de filtros. Solo
matchea indicaciones **vigentes** al momento de la consulta (no hay baja lógica en esta
entidad: se retira cerrando `fechaFinVigencia`).

**Query params** — `IndicacionPrestacionCriteria`: `id`, `nombre`, `requiereValidacion`,
`prestacionId`, `tipoIndicacionPrestacionId`, `createdDate`, `lastModifiedDate`.

**Response para el front — `GetIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la indicación. |
| `nombre` | String | Nombre de la indicación. |
| `descripcion` | String | Descripción. |
| `requiereValidacion` | Boolean | Para mostrar si es obligatoria o informativa. |
| `prestacionId` | UUID | Confirmación de la prestación. |
| `tipoIndicacionPrestacionId` | UUID | Confirmación del tipo. |
| `tipoIndicacionPrestacionNombre` | String | Nombre del tipo (para mostrar). |

**Errores posibles:**
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404): ninguna indicación vigente cumple el criteria.

---

### Listar indicaciones de prestación — `GET /accesmed-api/IndicacionPrestacion/IndicacionPrestacion`

Filtrado dinámico + paginación. Ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md).

**Query params** — `IndicacionPrestacionCriteria` (`id`, `nombre`, `requiereValidacion`,
`prestacionId`, `tipoIndicacionPrestacionId`, `createdDate`, `lastModifiedDate`) +
paginación (`page`, `size`, `sort`).

**Response para el front — `PageResponse<ListIndicacionPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar a detalle o edición. |
| `content[].nombre` | String | Nombre de la indicación. |
| `content[].requiereValidacion` | Boolean | Para mostrar en listados. |
| `content[].prestacionId` | UUID | Para confirmar la relación. |
| `content[].tipoIndicacionPrestacionId` | UUID | Para confirmar el tipo. |
| `content[].tipoIndicacionPrestacionNombre` | String | Nombre del tipo. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

## Reglas de tolerancia

Esta es la restricción más importante del módulo. **Debe validarse en toda creación y actualización de prestación.**

Las cinco tolerancias que se restan hacia atrás desde el instante del turno forman una **cadena de orden decreciente**:

```
tiempoToleranciaSolicitud
  ≥ tiempoToleranciaValidacion
  ≥ tiempoToleranciaReprogramacion
  ≥ tiempoToleranciaConfirmacion
  ≥ tiempoToleranciaCancelacion
  ≥ 0
```

Además:

```
tiempoToleranciaConfirmacion < tiempoRecordatorioConfirmacion ≤ tiempoToleranciaSolicitud
tiempoToleranciaAnuncio ≥ 0  (sin relación de orden con las demás)
```

**Fundamento**: Si alguna tolerancia violara el orden, un turno reservado en el último momento podría nacer con una fecha límite **ya vencida**, impidiéndole completar una acción que debería ser posible. Por ejemplo, si `tiempoToleranciaValidacion > tiempoToleranciaSolicitud`, un turno que se reserva justo en el límite de solicitud nacería con la fecha de validación ya pasada, y el primer barrido del scheduler lo cancelaría sin dar oportunidad al usuario.

Las reglas de tolerancia se **validan al guardar la prestación** (no en el alta de turno), de modo que ninguna fecha límite nace vencida.

**Para el equipo de frontend**: el formulario de creación/edición de prestación debe guiar al usuario para respetar este orden, mostrando advertencias si ve que una tolerancia es menor que la anterior en la cadena.

---

## Flujo de uso — orden natural desde el panel administrativo

1. **Crear tipos de indicación** (base de datos): `POST /TipoIndicacionPrestacion`
   - Define qué clasificaciones de indicaciones existen (ayuno, estudio previo, etc.).

2. **Crear prestación** (con indicaciones anidadas): `POST /Prestacion`
   - El admin carga la prestación: código, nombre, duraciones, tolerancias e indicaciones en una sola operación.
   - La prestación nace en estado `NO_PUBLICADA`.

3. **Ajustar si es necesario** (en cualquier estado):
   - `PATCH /Prestacion/{id}` para cambiar nombre, duraciones o tolerancias.
   - `POST /IndicacionPrestacion` (o actualizar/eliminar con PUT y DELETE) para agregar/cambiar indicaciones.

4. **Publicar / despublicar** (reversible, cuantas veces haga falta):
   - `PATCH /Prestacion/{id}/Publicar` → `PUBLICADA`. No exige médico asignado.
   - `PATCH /Prestacion/{id}/Despublicar` → vuelve a `NO_PUBLICADA`.

5. **Listar y filtrar**: `GET /Prestacion` con filtrado dinámico (`especialidadId.equals`,
   `estadoActual.equals`, etc. — ver [`FILTRADO-DINAMICO.md`](../FILTRADO-DINAMICO.md))
   - Para separar catálogo publicado de no publicado en los listados del panel.

6. **Deshabilitar eventualmente** (terminal, restrictiva): `PATCH /Prestacion/{id}/Deshabilitar`
   - Rechaza si hay turnos vivos o agenda futura ocupada de la prestación.
   - Es irreversible: "revivir" significa crear la prestación de nuevo (el `codigo` queda libre).
   - **Pendiente**: cascada de escritura sobre `MedicoPrestacion`, `AgendaHorarios`, `IndicacionPrestacion` y `ObraSocialPlanPrestacion` (en implementación).

---

## Tratamiento de errores

Todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier falla — ver `docs/FRONTEND-GUIA.md §1` para el formato exacto y la estrategia de manejo de errores en el front.

Los códigos de error específicos de esta feature son:

- `PRESTACION_CODIGO_DUPLICADO` (409)
- `PRESTACION_NOMBRE_DUPLICADO` (409)
- `PRESTACION_NO_PUBLICABLE` (409, no está en `NO_PUBLICADA`)
- `PRESTACION_NO_DESPUBLICABLE` (409, no está en `PUBLICADA`)
- `PRESTACION_YA_DESHABILITADA` (409)
- `PRESTACION_CON_TURNOS_VIVOS` (409, precondición restrictiva de deshabilitar)
- `PRESTACION_CON_AGENDA_OCUPADA` (409, precondición restrictiva de deshabilitar)
- `PRESTACION_NO_ENCONTRADA` (404)
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404)
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404)
- `TIPO_INDICACION_PRESTACION_CODIGO_DUPLICADO`
- `TIPO_INDICACION_PRESTACION_NOMBRE_DUPLICADO`
- `TIPO_INDICACION_PRESTACION_EN_USO` (hay indicaciones activas que lo usan)
- `ESPECIALIDAD_NO_ENCONTRADA` (404, levantado por dependencia en `EspecialidadDomainService`)

Errores de validación (422) se generan por Bean Validation:
- Campos vacíos/en blanco.
- Exceso de longitud.
- Violación de las reglas de tolerancia.
- Desajuste de `id` entre request y ruta.
