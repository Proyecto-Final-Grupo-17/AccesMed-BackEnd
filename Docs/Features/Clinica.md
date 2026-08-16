# Feature: Clínica

## Contexto

- **Para qué es**: la clínica tiene una única fila de configuración con sus datos
  institucionales (nombre, descripción, ubicación, contacto) y los parámetros que rigen
  el resto del sistema de turnos: el horario de atención y la ventana de días mínimos y
  máximos de vigencia con la que se pueden generar agendas médicas.

- **Para qué sirve**: el administrador consulta y ajusta estos datos y parámetros desde
  un único lugar, sin alta ni baja — la fila existe desde la migración inicial y solo se
  actualiza. Los horarios y la ventana de vigencia de agenda son la referencia que usa el
  resto del dominio (ej. `AgendaMedico`) para validar que las agendas que se generan caigan
  dentro de lo permitido.

- **Quiénes la usan**: el personal de la clínica (rol administrador) desde el panel web
  interno, para configurar los parámetros. El chatbot de WhatsApp puede consultar los datos
  de contacto y el horario de atención para responderle al paciente.

---

## Funciones

### Buscar clínica — `GET /accesmed-api/Clinica/Clinica`

**Flujo simplificado:**
1. Busca la única fila de configuración de la clínica.
2. Devuelve sus datos y parámetros.

**Response para el front — `GetClinicaResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `nombre` | String | Para mostrar el nombre de la clínica. |
| `descripcion` | String | Para mostrar la descripción institucional. |
| `ubicacion` | String | Para mostrar o geolocalizar la dirección física. |
| `email` | String | Para mostrar el contacto o prellenar un enlace de mail. |
| `telefono` | String | Para mostrar el contacto o prellenar un enlace de llamada/WhatsApp. |
| `horarioInicioAtencion` | LocalTime | Para mostrar el horario de atención y validar en el front que un horario cargado caiga dentro de la ventana. |
| `horarioFinAtencion` | LocalTime | Ídem, límite de fin. |
| `zonaHoraria` | String | Identificador IANA (ej. `America/Argentina/Buenos_Aires`). Es la zona con la que el backend resuelve a instante absoluto las fechas y horas de calendario del dominio, sobre todo `AgendaHorariosDia.fechaLimiteReserva`. |
| `diasMaximosAnticipacionReserva` | Integer | Horizonte de reserva: hasta cuántos días en el futuro se puede pedir un turno. El front lo usa para acotar el rango de fechas que ofrece al elegir turno. |

**Errores posibles:**
- `CLINICA_NO_ENCONTRADA` (404): no existe la fila de configuración (no debería ocurrir: la migración la crea).

---

### Actualizar clínica — `PATCH /accesmed-api/Clinica/Clinica`

**Flujo simplificado:**
1. Busca la única fila de configuración de la clínica.
2. Resuelve los valores efectivos de horario de atención (el que vino en el request, o si
   no vino, el ya guardado) y valida que el horario de inicio sea anterior al de fin. Si el
   request trae `zonaHoraria`, valida que sea un identificador IANA reconocido
   (`CLINICA_ZONA_HORARIA_INVALIDA`).
3. Aplica los campos que vinieron en el request (un campo en `null` deja ese dato sin
   tocar) y guarda.
4. Devuelve la clínica actualizada.

**Request para el front — `UpdateClinicaRequest`**

No lleva `id`: al ser una instancia única, la ruta ya identifica el recurso.

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `nombre` | String (máx. 150) | No | `null` deja el nombre sin tocar. |
| `descripcion` | String (máx. 1000) | No | `null` deja la descripción sin tocar. |
| `ubicacion` | String (máx. 250) | No | `null` deja la ubicación sin tocar. |
| `email` | String (máx. 150, formato email) | No | `null` deja el email sin tocar. |
| `telefono` | String (máx. 30) | No | `null` deja el teléfono sin tocar. |
| `horarioInicioAtencion` | LocalTime | No | `null` deja el horario de inicio sin tocar. Debe quedar antes del horario de fin efectivo. |
| `horarioFinAtencion` | LocalTime | No | `null` deja el horario de fin sin tocar. Debe quedar después del horario de inicio efectivo. |
| `zonaHoraria` | String (máx. 64) | No | `null` deja la zona sin tocar. Debe ser un identificador IANA válido. |
| `diasMaximosAnticipacionReserva` | Integer (mín. 1) | No | `null` deja el valor sin tocar. |

**Response para el front — `GetClinicaResponse`**

Mismo contrato que en "Buscar clínica".

**Errores posibles:**
- `CLINICA_NO_ENCONTRADA` (404): no existe la fila de configuración.
- `CLINICA_HORARIO_ATENCION_INVALIDO` (422): el horario de inicio efectivo no es anterior al de fin efectivo.
- `CLINICA_DIAS_VIGENCIA_AGENDA_INVALIDO` (422): los días mínimos efectivos superan a los máximos efectivos.
- Validación Bean (422): campos que exceden longitud máxima, email con formato inválido, o días de vigencia menores a 1.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
