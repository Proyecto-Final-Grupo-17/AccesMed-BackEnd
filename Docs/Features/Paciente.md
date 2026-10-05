# Feature: Paciente

## Contexto

- **Para qué es**: el catálogo de pacientes son las personas que piden turnos. Cada
  paciente puede declarar, opcionalmente, una o más coberturas de obra social (un plan
  concreto de una obra social, con su número de socio).

- **Para qué sirve**: el paciente se identifica por `numeroTelefono` en el canal chatbot.
  Se puede dar de alta junto con sus coberturas desde el arranque, y después sumar o
  quitar coberturas puntuales sin tocar el resto del paciente. Cada cobertura queda fija
  una vez creada (no se edita, solo se da de baja): el plan tiene que pertenecer
  efectivamente a la obra social indicada.

- **Quiénes la usan**: el personal de la clínica (rol administrador) desde el panel web
  interno, y el chatbot de WhatsApp para dar de alta pacientes nuevos o consultar sus
  coberturas durante la conversación.

---

## Funciones

### Crear paciente — `POST /accesmed-api/Paciente/Paciente`

**Flujo simplificado:**
1. Valida que el DNI, el número de teléfono y el email sean únicos entre pacientes activos.
2. Crea el paciente.
3. Por cada cobertura del array `obrasSociales`, busca la obra social (debe existir y
   estar activa) y el plan (debe existir y no estar deshabilitado), valida que el plan
   pertenezca efectivamente a esa obra social, y crea la cobertura.
4. Devuelve el paciente creado, con la lista de coberturas que quedó asignada.

**Request para el front — `CreatePacienteRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `dni` | String (máx. 15) | Sí | Único entre pacientes activos. |
| `nombre` | String (máx. 100) | Sí | |
| `apellido` | String (máx. 100) | Sí | |
| `fechaNacimiento` | LocalDate | Sí | Debe ser una fecha pasada. |
| `email` | String (máx. 150, formato email) | Sí | Único entre pacientes activos. |
| `numeroTelefono` | String (máx. 30) | Sí | Único entre pacientes activos. Identifica al paciente en el canal chatbot. |
| `obrasSociales` | Array de objetos | No | Puede venir vacío. Ver tabla siguiente. |

**Cobertura anidada — `AsignarObraSocialAnidadaRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `obraSocialId` | UUID | Sí | Debe ser una obra social activa existente. |
| `planId` | UUID | Sí | Debe ser un plan existente, no deshabilitado, y que pertenezca a `obraSocialId`. |
| `nroSocio` | String (máx. 50) | Sí | Número de socio del paciente en ese plan. |

**Response para el front — `CreatePacienteResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador único del paciente, necesario para editar, dar de baja o asignarle más coberturas. |
| `dni`, `nombre`, `apellido`, `email`, `numeroTelefono` | String | Para mostrar en listados y fichas. |
| `fechaNacimiento` | LocalDate | Para mostrar o calcular la edad. |
| `obrasSociales` | Array de `GetObraSocialAnidadaResponse` | Para mostrar de entrada las coberturas del paciente. Cada ítem trae `id` (de la cobertura, usado para desasignar), `obraSocialId`, `obraSocialNombre`, `planId`, `planNombre`, `nroSocio`. |

---

### Actualizar paciente — `PATCH /accesmed-api/Paciente/Paciente/{id}`

**Flujo simplificado:**
1. Verifica que el `id` de la ruta coincida con el del body.
2. Busca el paciente activo y valida unicidad solo de los campos que vinieron con valor.
3. Aplica los cambios (un campo en `null` deja ese dato sin tocar).
4. Devuelve el paciente actualizado, con sus coberturas actuales (no se tocan acá).

**Request para el front — `UpdatePacienteRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Debe ser el mismo que el `{id}` de la URL; si difieren, el backend responde 422. |
| `dni` | String (máx. 15) | No | `null` deja el DNI sin tocar. |
| `nombre` | String (máx. 100) | No | `null` deja el nombre sin tocar. |
| `apellido` | String (máx. 100) | No | `null` deja el apellido sin tocar. |
| `fechaNacimiento` | LocalDate | No | `null` deja la fecha de nacimiento sin tocar. |
| `email` | String (máx. 150, formato email) | No | `null` deja el email sin tocar. |
| `numeroTelefono` | String (máx. 30) | No | `null` deja el número de teléfono sin tocar. |

No incluye `obrasSociales`: se gestionan por los endpoints de Obra Social-Paciente (más abajo).

**Response para el front — `GetPacienteResponse`**

Mismo contrato que `CreatePacienteResponse`.

---

### Buscar paciente — `GET /accesmed-api/Paciente/Paciente/Buscar`

Recibe filtros de [filtrado dinámico](../FILTRADO-DINAMICO.md) (`id`, `dni`, `nombre`,
`apellido`, `email`, `numeroTelefono`, `createdDate`, `lastModifiedDate`) y devuelve el
primer paciente activo que los cumple, con el mismo contrato que `GetPacienteResponse`.
Pensado para criterios puntuales (ej. `numeroTelefono.equals`, para resolver al paciente
que escribe por WhatsApp).

### Listar pacientes — `GET /accesmed-api/Paciente/Paciente`

Recibe los mismos filtros dinámicos, paginados. Cada ítem usa `ListPacienteResponse`:
igual que `GetPacienteResponse` pero **sin** la lista de coberturas (evita N+1 en listados).

### Dar de baja paciente — `DELETE /accesmed-api/Paciente/Paciente/{id}`

**Flujo simplificado:**
1. Busca el paciente activo.
2. Valida que no tenga turnos vivos (estado actual no final). Si tiene, rechaza la baja.
3. Marca el paciente como dado de baja (`deletedAt`/`deletedReason`).
4. Devuelve la confirmación de la baja.

**Response para el front — `SoftDeletePacienteResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmar qué paciente se dio de baja. |
| `deletedAt` | Instant | Para mostrar cuándo se dio de baja. |
| `deletedReason` | String | Para mostrar el motivo. |

---

## Obra Social-Paciente (asignar/desasignar coberturas)

No es CRUD completo: solo dos acciones para modificar las coberturas de un paciente ya
creado, fuera del alta atómica. La cobertura es inmutable una vez creada: no tiene
actualización, solo alta y baja.

### Asignar cobertura a paciente — `POST /accesmed-api/ObraSocialPaciente/Asignar`

**Flujo simplificado:**
1. Busca el paciente, la obra social y el plan (todos deben existir y estar activos).
2. Valida que el plan pertenezca efectivamente a la obra social indicada.
3. Valida que no exista ya una cobertura activa entre ese paciente y ese plan.
4. Crea la cobertura y la devuelve.

**Request para el front — `AssignObraSocialPacienteRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `pacienteId` | UUID | Sí | Paciente activo existente. |
| `obraSocialId` | UUID | Sí | Obra social activa existente. |
| `planId` | UUID | Sí | Plan existente, no deshabilitado, que pertenezca a `obraSocialId`. |
| `nroSocio` | String (máx. 50) | Sí | |

**Response para el front — `GetObraSocialPacienteResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la cobertura, necesario para desasignarla después. |
| `pacienteId`, `obraSocialId`, `planId` | UUID | Para relacionar la cobertura con paciente, obra social y plan. |
| `obraSocialNombre`, `planNombre` | String | Para mostrar sin otra consulta. |
| `nroSocio` | String | Para mostrar el número de socio. |

**Errores posibles:**
- `OBRA_SOCIAL_PACIENTE_PLAN_NO_PERTENECE` (422): el plan indicado no pertenece a la obra social indicada.
- `OBRA_SOCIAL_PACIENTE_YA_ASIGNADA` (422): ya existe una cobertura activa entre ese paciente y ese plan.

### Desasignar cobertura de paciente — `DELETE /accesmed-api/ObraSocialPaciente/{id}`

Da de baja lógica la cobertura identificada por su propio `id` (el que devolvió el
`Asignar`, o el que trae cada ítem de `obrasSociales` en las respuestas de Paciente).
Responde con `SoftDeleteObraSocialPacienteResponse` (`id`, `deletedAt`, `deletedReason`).

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
