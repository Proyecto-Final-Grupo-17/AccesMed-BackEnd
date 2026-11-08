# Feature: Turno

## Contexto

- **Para qué es**: el `Turno` es el corazón del sistema — la reserva de un paciente con un
  médico para una prestación, en un horario concreto de la agenda. Esta feature cubre todo
  su ciclo de vida manual: desde que se solicita hasta que se finaliza, pasando por
  validación, confirmación, llegada a la clínica y atención, más los dos desvíos posibles
  (reprogramar y cancelar).

- **Para qué sirve**: le da al paciente (vía chatbot) y al personal de la clínica (vía panel
  web) las acciones para hacer avanzar un turno por sus estados. Cada endpoint es **una
  transición atómica** del diagrama de estados (DTE): una acción = una operación =
  una transacción. El estado vigente nunca se cachea en una columna; se deriva del tramo
  abierto del `HistoricoEstadoTurno` y se expone al front como `estadoActual`.

- **Quiénes la usan**: ambos frentes. El paciente (a través del agente de WhatsApp/Flowise)
  solicita, reprograma, cancela y confirma. El personal de la clínica (administrador y
  médico) desde el panel web valida indicaciones, registra la llegada a sala de espera,
  inicia la atención y la finaliza. Cada endpoint está protegido por un permiso propio
  (`TURN_*`), y las acciones del médico se restringen a sus propios turnos.

> **Transiciones automáticas**: las que dispara el paso del tiempo (recordatorio,
> confirmación por vencimiento, vencimiento de validación, marcar ausente) **no** son
> endpoints de esta feature — las ejecutan los schedulers. Ver
> [`SchedulersNotificacionesTurno.md`](SchedulersNotificacionesTurno.md).

---

## Estados del turno (DTE)

Nueve estados. Flujo principal y desvíos:

```
ESPERA_VALIDACION ─┐
                   ├─► PENDIENTE ─► CONFIRMADO ─► EN_SALA_DE_ESPERA ─► EN_CURSO ─► FINALIZADO
(sin validación) ──┘

Desvíos (finales, alcanzables desde distintos puntos): CANCELADO · REPROGRAMADO · AUSENTE
```

- Un turno nace en `ESPERA_VALIDACION` si alguna indicación de la prestación requiere
  validación; si no, nace en `PENDIENTE`.
- Estados **finales** (no admiten más transiciones): `CANCELADO`, `REPROGRAMADO`, `AUSENTE`,
  `FINALIZADO`.
- El `estadoActual` que devuelve cada response es siempre el tramo vigente del histórico.

---

## Forma compartida del response de las transiciones

Todas las acciones de transición (crear, validar, confirmar, sala de espera, atención,
finalizar, cancelar, reprogramar) devuelven **el mismo bloque base de campos**. Se
documenta una vez acá y cada función solo aclara su `estadoActual` y los campos extra que
agrega.

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador del turno, para navegar al detalle y disparar las siguientes acciones. |
| `codigo` | String | Código legible del turno (ej. `TURNO_A1B2C3D4`), para mostrar al paciente y en listados. |
| `pacienteId` | UUID | Para enlazar con la ficha del paciente. |
| `medicoId` | UUID | Para enlazar con la ficha del médico. |
| `prestacionId` | UUID | Para enlazar con la prestación reservada. |
| `fechaHoraInicio` | ZonedDateTime | Fecha y hora del turno, para mostrar y ordenar. |
| `montoAPagar` | BigDecimal | Importe a abonar ya calculado (según cobertura), para mostrar al paciente. |
| `tipoCobertura` | String (`PARTICULAR`\|`OBRA_SOCIAL`) | Para mostrar si el turno es particular o por obra social. |
| `estadoActual` | String (`EstadoTurno`) | Estado vigente tras la acción, para refrescar la UI y habilitar/deshabilitar los botones de la siguiente transición. |

---

## Funciones

### Solicitar turno — `POST /accesmed-api/Turno/Turno`

Permiso: `TURN_REGISTRAR`. Crea el turno y reserva el slot. Dispara la notificación
NOTIF-1 (Turno Registrado).

**Flujo simplificado:**
1. Valida que existan y estén activos el médico, la prestación (y que esté **publicada**),
   el slot (disponible y dentro de plazo de reserva) y el paciente.
2. Confirma que el médico atienda esa prestación en la fecha del slot (de ahí sale el precio).
3. Calcula el `montoAPagar`: si vino `obraSocialPacienteId`, aplica la estrategia de
   cobertura del plan; si no, es el precio particular.
4. Ocupa el slot, congela en el turno todas las fechas límite (validación, reprogramación,
   confirmación, cancelación, anuncio, recordatorio) a partir de las tolerancias de la prestación.
5. Copia las indicaciones vigentes de la prestación al turno y abre el estado inicial:
   `ESPERA_VALIDACION` si alguna indicación requiere validación, si no `PENDIENTE`.
6. Devuelve el turno creado (201).

**Request para el front — `CreateTurnoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `pacienteId` | UUID | Sí | Paciente para el que se reserva. |
| `medicoId` | UUID | Sí | Médico del turno. |
| `prestacionId` | UUID | Sí | Prestación a reservar. Debe estar **publicada**. |
| `slotId` | UUID | Sí | Slot disponible de la agenda (`AgendaHorariosDia`). Debe estar libre y dentro del plazo de reserva. |
| `obraSocialPacienteId` | UUID | No | Cobertura del paciente. Si viene, el turno es `OBRA_SOCIAL` y el monto se calcula según el plan; si se omite, es `PARTICULAR`. |

**Response para el front — `CreateTurnoResponse`**

Bloque base compartido. `estadoActual` = `ESPERA_VALIDACION` o `PENDIENTE` según la prestación.

**Errores posibles:**
- `PRESTACION_NO_PUBLICADA` (409): la prestación no está disponible para reservar.
- `AGENDA_HORARIO_FUERA_DE_PLAZO` (409): venció el plazo para reservar ese slot.
- `*_NO_ENCONTRADO/A` (404): paciente, médico, prestación, slot, cobertura o
  `MedicoPrestacion` vigente no existen o no están disponibles.
- Validación Bean (422): falta algún id obligatorio.

---

### Reprogramar turno — `PATCH /accesmed-api/Turno/Turno/{id}/Reprogramacion`

Permiso: `TURN_REPROGRAMAR`. No modifica el turno original: **crea uno nuevo** en otro slot,
enlazado al viejo por `turnoOrigen`, y cierra el viejo en `REPROGRAMADO`. Dispara NOTIF-4.

**Flujo simplificado:**
1. Busca el turno original y valida que pertenezca al médico autenticado (si quien llama es médico).
2. Valida que no haya vencido el plazo de reprogramación del turno.
3. Valida el nuevo slot con los mismos criterios que al solicitar (disponible, dentro de
   plazo, médico sigue atendiendo esa prestación, prestación sigue publicada).
4. Recalcula monto e indicaciones (por si cambiaron), ocupa el slot nuevo y libera el viejo.
5. Crea el turno nuevo (enlazado por `turnoOrigen`), cierra el viejo en `REPROGRAMADO` y
   abre el estado inicial del nuevo (`ESPERA_VALIDACION`/`PENDIENTE`).
6. Devuelve el turno nuevo (200).

**Request para el front — `ReprogramTurnoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Turno a reprogramar. Debe coincidir con el `id` de la URL (422 si difieren). |
| `slotId` | UUID | Sí | Nuevo slot de agenda. Mismo médico y prestación que el turno original. |

**Response para el front — `ReprogramTurnoResponse`**

Bloque base compartido (del turno **nuevo**) + un campo extra. `estadoActual` =
`ESPERA_VALIDACION` o `PENDIENTE`.

| Campo extra | Tipo | Para qué lo usa el front |
|-------------|------|--------------------------|
| `turnoOrigenId` | UUID | Id del turno original (ahora `REPROGRAMADO`), para enlazar el historial de reprogramaciones. |

**Errores posibles:**
- `TURNO_FUERA_DE_PLAZO_REPROGRAMACION` (409): venció el plazo para reprogramar.
- `AGENDA_HORARIO_FUERA_DE_PLAZO` (409): venció el plazo para reservar el nuevo slot.
- `PRESTACION_NO_PUBLICADA` (409): la prestación dejó de estar publicada.
- `*_NO_ENCONTRADO/A` (404): turno original o nuevo slot no existen.
- `RECURSO_AJENO` (409): el médico intenta reprogramar un turno que no es suyo.
- Validación (422): `id` de ruta distinto al del body, o falta algún id obligatorio.

---

### Cancelar turno — `DELETE /accesmed-api/Turno/Turno/{id}`

Permiso: `TURN_CANCELAR`. Usa `DELETE` pero **no borra**: transiciona el turno a `CANCELADO`
(baja lógica de estado) y libera el slot. Dispara NOTIF-3 (o NOTIF-5 si el motivo es
`VALIDACION_VENCIDA`).

**Flujo simplificado:**
1. Busca el turno y valida que pertenezca al médico autenticado (si quien llama es médico).
2. Toma el motivo (por este endpoint siempre `SOLICITUD_DEL_PACIENTE`, el valor por defecto).
3. Si el motivo es `SOLICITUD_DEL_PACIENTE`, valida que no haya vencido el plazo de cancelación.
4. Libera el slot, guarda el motivo y transiciona a `CANCELADO` (rechaza si ya está en estado final).
5. Responde 204 sin cuerpo.

**Este endpoint no lleva body.** El motivo no se recibe por esta ruta: el DELETE siempre
cancela a pedido del paciente. Los otros motivos (`VALIDACION_RECHAZADA`, `VALIDACION_VENCIDA`,
`BAJA_DE_MEDICO`, `DECISION_ADMINISTRATIVA`) los setea internamente el propio sistema
(validación, schedulers, otros casos de uso).

**Response para el front:** 204 No Content (sin cuerpo).

**Errores posibles:**
- `TURNO_FUERA_DE_PLAZO_CANCELACION` (409): venció el plazo para que el paciente cancele.
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- `RECURSO_AJENO` (409): el médico intenta cancelar un turno que no es suyo.
- Regla de negocio (409): el turno ya está en un estado final.

---

### Validar turno — `PATCH /accesmed-api/Turno/Turno/{id}/Validacion`

Permiso: `TURN_VALIDAR`. Aprueba o rechaza un turno en `ESPERA_VALIDACION`. Lo usa el
admin tras comprobar que el paciente cumplió las indicaciones previas que lo requerían.

**Flujo simplificado:**
1. Busca el turno y al admin que valida.
2. Si `aprobado = true`: transiciona `ESPERA_VALIDACION → PENDIENTE` y marca las indicaciones
   como validadas. No notifica (el paciente todavía tiene que confirmar).
3. Si `aprobado = false`: libera el slot, setea motivo `VALIDACION_RECHAZADA`, transiciona a
   `CANCELADO`, marca las indicaciones como validadas y dispara NOTIF-5 (No Validado).
4. Devuelve el turno con su nuevo estado (200).

**Request para el front — `ValidateTurnoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Turno a validar. Debe coincidir con el `id` de la URL (422 si difieren). |
| `aprobado` | Boolean | Sí | `true` aprueba (→ `PENDIENTE`); `false` rechaza (→ `CANCELADO`). |

**Response para el front — `ValidateTurnoResponse`**

Bloque base compartido. `estadoActual` = `PENDIENTE` si se aprobó, `CANCELADO` si se rechazó.

**Errores posibles:**
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- Regla de negocio (409): el turno no está en `ESPERA_VALIDACION`.
- Validación (422): `id` de ruta distinto al del body, o falta `aprobado`.

---

### Confirmar turno — `PATCH /accesmed-api/Turno/Turno/{id}/Confirmacion`

Permiso: `TURN_CONFIRMAR`. Pasa un turno de `PENDIENTE` a `CONFIRMADO`. Lo dispara el
paciente (vía chatbot) o el personal. Dispara NOTIF-2 (Turno Confirmado).

**Flujo simplificado:**
1. Busca el turno.
2. Valida que no haya vencido el plazo de confirmación.
3. Transiciona `PENDIENTE → CONFIRMADO` y dispara la notificación.
4. Devuelve el turno confirmado (200).

**Este endpoint no lleva body** — solo el `id` en la ruta.

**Response para el front — `ConfirmTurnoResponse`**

Bloque base compartido. `estadoActual` = `CONFIRMADO`.

**Errores posibles:**
- `TURNO_FUERA_DE_PLAZO_CONFIRMACION` (409): venció el plazo para confirmar.
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- Regla de negocio (409): el turno no está en `PENDIENTE`.

---

### Registrar sala de espera — `PATCH /accesmed-api/Turno/Turno/{id}/SalaDeEspera`

Permiso: `TURN_ANUNCIAR`. Registra la llegada del paciente a la clínica: pasa de
`CONFIRMADO` a `EN_SALA_DE_ESPERA`. Lo usa recepción.

**Flujo simplificado:**
1. Busca el turno y valida que pertenezca al médico autenticado (si quien llama es médico).
2. Valida que la llegada esté dentro de la ventana de anuncio del turno (ni antes de
   `fechaLimiteAnuncioTemprano` ni después de `fechaLimiteAnuncioTardio`).
3. Transiciona `CONFIRMADO → EN_SALA_DE_ESPERA`.
4. Devuelve el turno en sala de espera (200).

**Este endpoint no lleva body** — solo el `id` en la ruta.

**Response para el front — `StartSalaDeEsperaTurnoResponse`**

Bloque base compartido. `estadoActual` = `EN_SALA_DE_ESPERA`.

**Errores posibles:**
- `TURNO_ANUNCIO_ANTICIPADO` (409): el paciente llegó antes de la ventana de anuncio.
- `TURNO_ANUNCIO_TARDIO` (409): venció el plazo para registrar la llegada.
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- `RECURSO_AJENO` (409): el médico intenta operar un turno que no es suyo.
- Regla de negocio (409): el turno no está en `CONFIRMADO`.

---

### Iniciar atención — `PATCH /accesmed-api/Turno/Turno/{id}/Atencion`

Permiso: `TURN_INICIAR`. Pasa de `EN_SALA_DE_ESPERA` a `EN_CURSO`. Lo usa el médico al
empezar a atender.

**Flujo simplificado:**
1. Busca el turno y valida que pertenezca al médico autenticado (si quien llama es médico).
2. Pre-verifica el estado: si no está en `EN_SALA_DE_ESPERA`, rechaza con prioridad sobre
   cualquier error temporal (el error de estado gana).
3. Valida que no se inicie la atención antes de la ventana del turno
   (`fechaLimiteAnuncioTemprano`).
4. Transiciona `EN_SALA_DE_ESPERA → EN_CURSO`.
5. Devuelve el turno en atención (200).

**Este endpoint no lleva body** — solo el `id` en la ruta.

**Response para el front — `StartAtencionTurnoResponse`**

Bloque base compartido. `estadoActual` = `EN_CURSO`.

**Errores posibles:**
- `TURNO_TRANSICION_INVALIDA` (409): el turno no está en `EN_SALA_DE_ESPERA` (prevalece
  sobre el error temporal).
- `TURNO_ATENCION_ANTICIPADA` (409): se intenta iniciar antes de la ventana del turno.
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- `RECURSO_AJENO` (409): el médico intenta operar un turno que no es suyo.

---

### Finalizar turno — `PATCH /accesmed-api/Turno/Turno/{id}/Finalizacion`

Permiso: `TURN_FINALIZAR`. Cierra el ciclo principal: pasa de `EN_CURSO` a `FINALIZADO`
(estado final). Lo usa el médico al terminar la atención.

**Flujo simplificado:**
1. Busca el turno y valida que pertenezca al médico autenticado (si quien llama es médico).
2. Transiciona `EN_CURSO → FINALIZADO`.
3. Devuelve el turno finalizado (200).

**Este endpoint no lleva body** — solo el `id` en la ruta.

**Response para el front — `FinishTurnoResponse`**

Bloque base compartido. `estadoActual` = `FINALIZADO`.

**Errores posibles:**
- `TURNO_NO_ENCONTRADO` (404): el turno no existe.
- `RECURSO_AJENO` (409): el médico intenta operar un turno que no es suyo.
- Regla de negocio (409): el turno no está en `EN_CURSO`.

---

### Listar turnos — `GET /accesmed-api/Turno/Turno`

Permiso: `TURN_CONSULTAR`. Filtrado dinámico + paginación. Ver
[`FILTRADO-DINAMICO.md`](../Otros/FILTRADO-DINAMICO.md) para el formato de filtros.

**Flujo simplificado:**
1. Recupera los turnos que cumplen el criteria (sin filtros = todos los visibles para quien consulta).
2. Devuelve una página de resultados.

**Query params** — `TurnoCriteria`: `pacienteId`, `medicoId`, `prestacionId`,
`estadoActual`, `fechaHoraInicio`, `createdDate`, `lastModifiedDate`, `createdBy` + paginación
(`page`, `size`, `sort`).

> El filtro `createdBy` solo se honra si quien consulta tiene el permiso
> `AUDITORIA_CONSULTAR`; en caso contrario se ignora. El bloque `auditoria` del response
> sigue la misma regla: solo viene poblado para quien tiene ese permiso.

**Response para el front — `PageResponse<ListTurnoResponse>`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `content[].id` | UUID | Para navegar al detalle del turno. |
| `content[].codigo` | String | Código del turno, para mostrar en el listado. |
| `content[].pacienteId` | UUID | Para enlazar/mostrar el paciente. |
| `content[].medicoId` | UUID | Para enlazar/mostrar el médico. |
| `content[].prestacionId` | UUID | Para enlazar/mostrar la prestación. |
| `content[].fechaHoraInicio` | ZonedDateTime | Para ordenar y mostrar la agenda. |
| `content[].montoAPagar` | BigDecimal | Importe, para mostrar en el listado. |
| `content[].tipoCobertura` | String | `PARTICULAR`\|`OBRA_SOCIAL`, para mostrar/filtrar. |
| `content[].estadoActual` | String | Para colorear/filtrar por estado y decidir qué acciones ofrecer. |
| `content[].auditoria` | objeto | Datos de auditoría; solo poblado con `AUDITORIA_CONSULTAR`. |
| `page`, `size`, `totalElements`, `totalPages` | number | Metadatos de paginación. |

---

> **Canal del agente (WhatsApp/Flowise)**: `TurnoAgenteController`
> (`/accesmed-api/Agente/Turno`) está declarado pero **todavía no expone endpoints**. Cuando
> se implementen los Custom Tools (Solicitar / Reprogramar / Cancelar / Confirmar),
> reutilizarán el mismo `TurnoApp`; lo único propio será la forma de request/response que le
> convenga a Flowise. Se documentarán acá al existir.

---

## Tratamiento de errores

Todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier falla — ver
`docs/FRONTEND-GUIA.md §1` para el formato exacto y la estrategia de manejo en el front.

Códigos de error específicos de esta feature:

- `PRESTACION_NO_PUBLICADA` (409)
- `AGENDA_HORARIO_FUERA_DE_PLAZO` (409)
- `TURNO_FUERA_DE_PLAZO_REPROGRAMACION` (409)
- `TURNO_FUERA_DE_PLAZO_CANCELACION` (409)
- `TURNO_FUERA_DE_PLAZO_CONFIRMACION` (409)
- `TURNO_ANUNCIO_ANTICIPADO` (409)
- `TURNO_ANUNCIO_TARDIO` (409)
- `TURNO_ATENCION_ANTICIPADA` (409)
- `TURNO_TRANSICION_INVALIDA` (409)
- Transiciones de estado inválidas en general (409): intentar una acción sobre un turno que
  no está en el estado de origen que esa acción exige.
- `TURNO_NO_ENCONTRADO` (404) y otros `*_NO_ENCONTRADO/A` (404) de los recursos relacionados
  (paciente, médico, prestación, slot, cobertura).
- `RECURSO_AJENO` (409): un médico opera un turno que no le pertenece.

Errores de validación (422) por Bean Validation: ids obligatorios faltantes, o `id` de ruta
distinto al del body en los `PATCH` con body (reprogramar, validar).
