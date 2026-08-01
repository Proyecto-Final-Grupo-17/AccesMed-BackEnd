# Feature: Prestaciones e Indicaciones

## Contexto

- **Para qué es**: el catálogo de prestaciones es la configuración raíz del sistema de turnos. Define qué servicios ofrece la clínica, cuánto dura cada uno y, a través de siete tolerancias configurables, establece todo el calendario de vencimientos que después se congela en cada turno (fechas límite de validación, reprogramación, confirmación, cancelación, anuncio y recordatorio).

- **Para qué sirve**: el administrador arma el catálogo, le carga a cada prestación sus indicaciones previas (ayuno, estudios previos, etc., clasificadas por tipo), y lo publica. Solo las prestaciones habilitadas se ofrecen para dar y pedir turnos. Recién ahí médicos y pacientes pueden usarlas. Una vez habilitada, una prestación **queda congelada** en lo que respecta a su nombre e indicaciones editables (para no reescribir retroactivamente lo que ve un paciente en un turno vivo), pero sus duraciones y tolerancias siguen siendo ajustables.

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

### Obtener tipo de indicación — `GET /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Busca el tipo activo por `id`.
2. Devuelve sus datos.

**Response para el front — `GetTipoIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador del tipo. |
| `codigo` | String | Código para mostrar en detalle. |
| `nombre` | String | Nombre para mostrar en detalle. |

**Errores posibles:**
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): no existe o está de baja.

---

### Listar tipos de indicación — `GET /accesmed-api/TipoIndicacionPrestacion/TipoIndicacionPrestacion`

**Flujo simplificado:**
1. Recupera todos los tipos activos.
2. Devuelve la lista.

**Response para el front — `List<ListTipoIndicacionPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador para seleccionar en dropdowns al crear indicaciones. |
| `codigo` | String | Código para mostrar en listados. |
| `nombre` | String | Nombre descriptivo para mostrar al usuario. |

---

### Crear prestación — `POST /accesmed-api/Prestacion/Prestacion`

**Flujo simplificado:**
1. Valida que el `codigo` y `nombre` sean únicos entre prestaciones activas.
2. Valida que la especialidad exista.
3. **Valida las reglas de tolerancia** (ver sección "Reglas de tolerancia" abajo).
4. Crea la prestación en estado **borrador** (`fechaHabilitacion = null`).
5. Si hay indicaciones anidadas en el request, las crea y las asocia en la misma transacción.
6. Devuelve la prestación creada con sus indicaciones.

**Request para el front — `CreatePrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `codigo` | String (máx. 20) | Sí | Código único e inmutable. Se congela en creación (baja + alta = código nuevo). |
| `nombre` | String (máx. 150) | Sí | Nombre descriptivo. Se congela una vez que se habilita la prestación. |
| `duracionMinimaMinutos` | Integer | Sí | Minutos. Mayor a cero. Debe ser ≤ `duracionMaximaMinutos`. |
| `duracionMaximaMinutos` | Integer | Sí | Minutos. Mayor a cero. Debe ser ≥ `duracionMinimaMinutos`. |
| `tiempoToleranciaSolicitudMinutos` | Integer | Sí | Minutos (≥ 0). Antelación mínima para reservar un turno. **Techo de la cadena de tolerancias.** |
| `tiempoToleranciaValidacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo se pueden validar indicaciones. Debe ser ≤ tolerancia de solicitud. |
| `tiempoToleranciaReprogramacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo se puede reprogramar. Debe ser ≤ tolerancia de validación. |
| `tiempoToleranciaConfirmacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo el paciente confirma por su cuenta. Debe ser ≤ tolerancia de reprogramación. |
| `tiempoToleranciaCancelacionMinutos` | Integer | Sí | Minutos (≥ 0). Hasta cuándo el paciente cancela. Debe ser ≤ tolerancia de confirmación. |
| `tiempoToleranciaAnuncioMinutos` | Integer | Sí | Minutos (≥ 0). Semiancho de la ventana de anuncio en recepción. **No sigue el orden de la cadena.** |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | Sí | Minutos (≥ 0). Cuánto antes se envía el recordatorio. Debe ser > tolerancia de confirmación y ≤ tolerancia de solicitud. |
| `especialidadId` | UUID | Sí | Identificador de la especialidad a la que pertenece esta prestación. |
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
| `id` | UUID | Identificador de la prestación. Para navegar al detalle, listar indicaciones, habilitar. |
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
| `fechaHabilitacion` | ZonedDateTime | Nulo porque la prestación nace en borrador. |
| `habilitada` | Boolean | `false` (en borrador). |
| `indicaciones` | Array | Lista de indicaciones creadas (vacía si no se proporcionaron). |

**Errores posibles:**
- `PRESTACION_CODIGO_DUPLICADO` (422): ya existe una prestación activa con ese código.
- `PRESTACION_NOMBRE_DUPLICADO` (422): ya existe una prestación activa con ese nombre.
- Validación de reglas de tolerancia (422): véase "Reglas de tolerancia" abajo.
- `ESPECIALIDAD_NO_ENCONTRADA` (404): la especialidad no existe.
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): uno de los tipos de indicación no existe.

---

### Actualizar datos generales de prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}`

Cubre el grupo de campos que solo pueden tocarse **mientras la prestación está en
borrador**: nombre y especialidad. Una vez habilitada, el endpoint entero queda
bloqueado (no hay forma de cambiar el nombre después de publicar).

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Valida que la prestación esté en **borrador**; si ya está habilitada, rechaza el
   request completo (`PRESTACION_YA_HABILITADA`).
3. Si vino `nombre`, valida que sea único (excluyendo esta prestación) y lo actualiza.
4. Si vino `especialidadId`, valida que la especialidad exista y la actualiza.
5. Devuelve la prestación actualizada.

**Request para el front — `UpdatePrestacionNoHabilitadaRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `nombre` | String (máx. 150) | No | `null` o ausente = no lo toques. |
| `especialidadId` | UUID | No | `null` o ausente = no la toques. |

**Response para el front — `UpdatePrestacionNoHabilitadaResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación. |
| `codigo` | String | Inmutable, solo para confirmar. |
| `nombre` | String | Confirmación del nombre. |
| `especialidadId` | UUID | Confirmación de la especialidad. |
| `especialidadNombre` | String | Nombre de la especialidad. |
| `habilitada` | Boolean | Siempre `false` en este response (el endpoint exige borrador). |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_YA_HABILITADA` (422): la prestación ya está habilitada, no admite esta operación.
- `PRESTACION_NOMBRE_DUPLICADO` (422): otra prestación activa ya tiene ese nombre.
- `ESPECIALIDAD_NO_ENCONTRADA` (404): la especialidad no existe.

---

### Actualizar tolerancias de prestación — `PATCH /accesmed-api/Prestacion/Prestacion/Tolerancias/{id}`

Cubre las 9 duraciones/tolerancias. Es lo **único editable una vez habilitada**; mientras
está en borrador también se edita por acá (separado del endpoint anterior).

**Flujo simplificado:**
1. Valida que la prestación exista (sin importar si está en borrador o habilitada).
2. Revalida las reglas de tolerancia con el resultado de aplicar los campos que vinieron
   sobre los valores actuales de la prestación.
3. Actualiza los campos que vinieron en el request.
4. Devuelve la prestación actualizada.

**Request para el front — `UpdateToleranciasPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Identificador. Debe coincidir con el `id` de la ruta (422 si difieren). |
| `duracionMinimaMinutos` | Integer | No | `null` o ausente = no lo toques. |
| `duracionMaximaMinutos` | Integer | No | |
| `tiempoToleranciaSolicitudMinutos` | Integer | No | |
| `tiempoToleranciaValidacionMinutos` | Integer | No | |
| `tiempoToleranciaReprogramacionMinutos` | Integer | No | |
| `tiempoToleranciaConfirmacionMinutos` | Integer | No | |
| `tiempoToleranciaCancelacionMinutos` | Integer | No | |
| `tiempoToleranciaAnuncioMinutos` | Integer | No | |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | No | |

**Response para el front — `UpdateToleranciasPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación. |
| `codigo` | String | Solo para confirmar. |
| `nombre` | String | Solo para confirmar. |
| `duracionMinimaMinutos` | Integer | Confirmación de todos los campos actualizados. |
| `duracionMaximaMinutos` | Integer | |
| `tiempoToleranciaSolicitudMinutos` | Integer | |
| `tiempoToleranciaValidacionMinutos` | Integer | |
| `tiempoToleranciaReprogramacionMinutos` | Integer | |
| `tiempoToleranciaConfirmacionMinutos` | Integer | |
| `tiempoToleranciaCancelacionMinutos` | Integer | |
| `tiempoToleranciaAnuncioMinutos` | Integer | |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | |
| `habilitada` | Boolean | Para saber si la prestación sigue en borrador o ya está publicada. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- Validación de reglas de tolerancia (422): si la cadena de tolerancias falla.

---

### Habilitar prestación — `PATCH /accesmed-api/Prestacion/Prestacion/{id}/Habilitacion`

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Valida que esté en borrador (`fechaHabilitacion` vacío).
3. Asigna `fechaHabilitacion = ahora` (irreversible).
4. Devuelve la prestación habilitada.

**Este endpoint no lleva body en el request.**

**Response para el front — `EnablePrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación del id. |
| `codigo` | String | Confirmación del código. |
| `nombre` | String | Confirmación del nombre (ahora congelado). |
| `fechaHabilitacion` | ZonedDateTime | Fecha/hora en que se habilitó (para mostrar el cambio de estado). |
| `habilitada` | Boolean | `true` siempre en este response. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_YA_HABILITADA` (422): la prestación ya está habilitada (el endpoint es idempotente en cierto sentido, pero lanza error).

---

### Dar de baja prestación — `DELETE /accesmed-api/Prestacion/Prestacion/{id}`

**Flujo simplificado:**
1. Valida que la prestación exista.
2. Ejecuta la baja lógica de todas sus indicaciones activas.
3. Ejecuta la baja lógica de la prestación (`deletedAt = ahora`).
4. **Pendiente (TODO)**: Debe cancelar todos los turnos futuros de esta prestación con motivo `BAJA_DE_PRESTACION`, dar de baja los `AgendaHorarios` futuros y las `MedicoPrestacion` asociadas. Por ahora solo hace baja de prestación e indicaciones (módulos de Agenda y MedicoPrestacion todavía no existen).
5. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeletePrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmación de la prestación dada de baja. |
| `deletedAt` | Instant | Momento de la baja. |
| `deletedReason` | String | Motivo de la baja. |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe o ya está de baja.

---

### Obtener prestación — `GET /accesmed-api/Prestacion/Prestacion/{id}`

**Flujo simplificado:**
1. Busca la prestación activa por `id`.
2. Carga todas sus indicaciones activas.
3. Devuelve los datos completos.

**Response para el front — `GetPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la prestación. |
| `codigo` | String | Código de la prestación. |
| `nombre` | String | Nombre. |
| `duracionMinimaMinutos` | Integer | Duraciones (para mostrar en detalle). |
| `duracionMaximaMinutos` | Integer | |
| `tiempoToleranciaSolicitudMinutos` | Integer | Todas las tolerancias (para editar o mostrar). |
| `tiempoToleranciaValidacionMinutos` | Integer | |
| `tiempoToleranciaReprogramacionMinutos` | Integer | |
| `tiempoToleranciaConfirmacionMinutos` | Integer | |
| `tiempoToleranciaCancelacionMinutos` | Integer | |
| `tiempoToleranciaAnuncioMinutos` | Integer | |
| `tiempoRecordatorioConfirmacionMinutos` | Integer | |
| `especialidadId` | UUID | Para confirmar la especialidad. |
| `especialidadNombre` | String | Nombre de la especialidad. |
| `fechaHabilitacion` | ZonedDateTime | Para mostrar si está habilitada o en borrador. |
| `habilitada` | Boolean | Para deshabilitar/habilitar botones de edición según estado. |
| `indicaciones` | Array | Lista completa de indicaciones activas (cada una con su tipo). |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): no existe o está de baja.

---

### Listar prestaciones — `GET /accesmed-api/Prestacion/Prestacion`

**Flujo simplificado:**
1. Recupera prestaciones activas, opcionalmente filtradas por especialidad y/o estado (borrador vs. habilitada).
2. Devuelve una lista compacta.

**Query parameters (opcionales)**

| Parámetro | Tipo | Significado |
|-----------|------|-------------|
| `especialidadId` | UUID | Filtrar por especialidad. |
| `habilitadas` | Boolean | `true` = solo habilitadas; `false` = solo borradores; sin parámetro = todas. |

**Response para el front — `List<ListPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Para navegar a detalle o edición. |
| `codigo` | String | Código de la prestación. |
| `nombre` | String | Nombre. |
| `especialidadId` | UUID | Para mostrar relaciones. |
| `especialidadNombre` | String | Nombre de la especialidad (para mostrar). |
| `fechaHabilitacion` | ZonedDateTime | Nulo si borrador; con fecha si habilitada. |
| `habilitada` | Boolean | Para marcar visualmente o filtrar (colores, estados, etc.). |

---

### Crear indicación de prestación — `POST /accesmed-api/IndicacionPrestacion/IndicacionPrestacion`

**Flujo simplificado:**
1. Valida que la prestación exista y esté **en borrador** (si está habilitada, lanza error).
2. Valida que el tipo de indicación exista.
3. Crea la indicación y la asocia a la prestación.
4. Devuelve la indicación creada.

**Request para el front — `CreateIndicacionPrestacionRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `prestacionId` | UUID | Sí | Identificador de la prestación a la que pertenece. Debe estar en borrador. |
| `nombre` | String (máx. 150) | Sí | Nombre de la indicación. |
| `descripcion` | String (máx. 1000) | Sí | Detalle de qué debe cumplir el paciente. |
| `requiereValidacion` | Boolean | Sí | Si es `true`, los turnos de esta prestación nacerán en "Espera de Validación". |
| `tipoIndicacionPrestacionId` | UUID | Sí | Clasificación (referencia a un tipo). |

**Response para el front — `CreateIndicacionPrestacionResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la indicación. |
| `nombre` | String | Confirmación del nombre. |
| `descripcion` | String | Confirmación de la descripción. |
| `requiereValidacion` | Boolean | Confirmación. |
| `prestacionId` | UUID | Confirmación de la prestación. |
| `tipoIndicacionPrestacionId` | UUID | Confirmación del tipo. |
| `tipoIndicacionPrestacionNombre` | String | Nombre del tipo (para mostrar). |

**Errores posibles:**
- `PRESTACION_NO_ENCONTRADA` (404): la prestación no existe.
- `PRESTACION_INDICACION_HABILITADA` (422): la prestación está habilitada (solo puede haber alta de indicaciones en borrador o dentro de POST /Prestacion).
- `TIPO_INDICACION_PRESTACION_NO_ENCONTRADO` (404): el tipo no existe.

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

### Obtener indicación de prestación — `GET /accesmed-api/IndicacionPrestacion/IndicacionPrestacion/{id}`

**Flujo simplificado:**
1. Busca la indicación activa por `id`.
2. Devuelve sus datos.

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
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404): no existe o está de baja.

---

### Listar indicaciones de prestación — `GET /accesmed-api/IndicacionPrestacion/IndicacionPrestacion`

**Flujo simplificado:**
1. Recupera indicaciones activas, opcionalmente filtradas por prestación.
2. Devuelve la lista.

**Query parameters (opcionales)**

| Parámetro | Tipo | Significado |
|-----------|------|-------------|
| `prestacionId` | UUID | Filtrar por prestación. |

**Response para el front — `List<ListIndicacionPrestacionResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Para navegar a detalle o edición. |
| `nombre` | String | Nombre de la indicación. |
| `requiereValidacion` | Boolean | Para mostrar en listados. |
| `prestacionId` | UUID | Para confirmar la relación. |
| `tipoIndicacionPrestacionId` | UUID | Para confirmar el tipo. |
| `tipoIndicacionPrestacionNombre` | String | Nombre del tipo. |

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

2. **Crear prestación en borrador** (con indicaciones anidadas): `POST /Prestacion`
   - El admin carga la prestación: código, nombre, duraciones, tolerancias e indicaciones en una sola operación.
   - La prestación nace con `fechaHabilitacion = null` (borrador).

3. **Ajustar si es necesario** (mientras esté en borrador):
   - `PATCH /Prestacion/{id}` para cambiar nombre o especialidad.
   - `PATCH /Prestacion/Tolerancias/{id}` para cambiar duraciones o tolerancias.
   - `POST /IndicacionPrestacion` (o actualizar/eliminar con PUT y DELETE) para agregar/cambiar indicaciones.

4. **Habilitar** (irreversible): `PATCH /Prestacion/{id}/Habilitacion`
   - Asigna `fechaHabilitacion = ahora`.
   - El nombre y la especialidad quedan congelados (`PATCH /Prestacion/{id}` deja de admitirse).
   - Las indicaciones pasan a ser de solo lectura (no se pueden actualizar, solo dar de baja).
   - Las duraciones y tolerancias siguen siendo editables vía `PATCH /Prestacion/Tolerancias/{id}`.

5. **Listar y filtrar**: `GET /Prestacion` con parámetros `especialidadId` y/o `habilitadas`
   - Para separar catálogo publicado (habilitadas) de borradores en trabajos.

6. **Baja eventual**: `DELETE /Prestacion/{id}` (solo si ya no se necesita)
   - Baja lógica de la prestación e indicaciones asociadas.
   - **Pendiente**: cancelación en cascada de turnos futuros (en implementación).

---

## Tratamiento de errores

Todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier falla — ver `docs/FRONTEND-GUIA.md §1` para el formato exacto y la estrategia de manejo de errores en el front.

Los códigos de error específicos de esta feature son:

- `PRESTACION_CODIGO_DUPLICADO`
- `PRESTACION_NOMBRE_DUPLICADO`
- `PRESTACION_YA_HABILITADA` (intento de habilitar dos veces, o de tocar datos generales de una prestación ya habilitada)
- `PRESTACION_NO_ENCONTRADA` (404)
- `INDICACION_PRESTACION_NO_ENCONTRADA` (404)
- `INDICACION_PRESTACION_HABILITADA` (intentó editar indicaciones de prestación habilitada)
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
