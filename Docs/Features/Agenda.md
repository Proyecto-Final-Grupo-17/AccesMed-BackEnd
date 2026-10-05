# Feature: Agenda

## Contexto

- **Para qué es**: sin agenda no hay turnos. `AgendaHorariosDia` es el único objeto reservable
  del sistema — todo lo que el paciente puede pedir por WhatsApp o el personal puede asignar
  desde el panel sale de acá.
- **Para qué sirve**: le permite al personal de la clínica publicar la disponibilidad real
  de un médico (qué días, en qué horarios, para qué prestación) y le da al chatbot una única
  fuente confiable de "qué horarios se pueden ofrecer ahora mismo".
- **Quiénes la usan**: personal de clínica desde el panel (administrador, y el médico sobre
  su propia agenda). El chatbot **solo** consume `listHorariosDisponibles` — ningún otro
  endpoint de esta feature es parte del flujo del agente.

`AgendaHorariosDia` no tiene controller propio ni vida independiente del período: siempre
se crea, edita y consulta a través de `AgendaMedico`.

## Cómo se arma un período de agenda

`createAgendaMedico` no persiste un patrón: lo **expande** a días y horarios concretos y
descarta el patrón. Dos formas de describirlo, mutuamente excluyentes:

- **Patrón semanal**: "todos los lunes y miércoles, de 9 a 12, turnos de 30 minutos" — se
  repite en cada fecha del período de vigencia que caiga en ese día de la semana.
- **Días sueltos**: fechas concretas, para agendas que no siguen un patrón regular.

Cada slot generado tiene que cumplir, todo junto:

- Caer dentro del horario de atención general de la clínica.
- La duración del turno tiene que dividir exactamente al bloque (no puede sobrar tiempo).
- La duración del turno tiene que estar entre la duración mínima y máxima de la prestación.
- La prestación tiene que estar **Publicada**.
- El médico tiene que tener esa prestación **vigente en la fecha del slot** (no alcanza con
  que la tenga vigente "ahora": si la asignación arranca o corta en el medio del período de
  la agenda, los días fuera de ese rango se rechazan).
- No puede superponerse con otro bloque del mismo día, ni con uno que ya esté en base.

Si algo de esto falla, el backend junta **todos** los errores del lote y los devuelve
juntos en un solo `AccesMedError` (422) — no hace falta corregir de a uno. Además, hay un
tope operativo de 500 horarios por confirmación (`AGENDA_LIMITE_GENERACION_EXCEDIDO`, 409):
es un guardarraíl de performance, no una regla de negocio; si se supera, hay que acotar el
período o el patrón y reintentar.

## Por qué excluir es restrictivo (no cancela turnos)

`updateAgendaMedico` y `updateVigenciaAgendaMedico` pueden dar de baja días y horarios. Si
alguno de los horarios que la operación arrastraría ya tiene un turno asociado
(`estaOcupada = true`), la operación se **rechaza entera**, antes de escribir nada
(`AGENDA_HORARIOS_CON_OCUPADOS`, 409), informando cuántos slots ocupados hay y hasta qué
fecha. Esto es así porque el módulo Turno todavía no tiene stack de escritura: cuando lo
tenga, acá va a entrar la cascada de cancelación real. Hasta entonces, el personal tiene que
resolver manualmente los turnos ocupados antes de poder liberar esos horarios.

## Funciones

### Crear agenda — `POST /accesmed-api/AgendaMedico/Agenda`

**Flujo simplificado:**
1. Valida que el médico exista y esté activo.
2. Valida que el período (`fechaInicioVigencia`/`fechaFinVigencia`) no empiece
   antes de hoy (según la zona horaria de la clínica) y no se solape con otro período de agenda del mismo médico.
3. Expande el patrón semanal o los días sueltos a una lista de bloques con fecha concreta.
4. Valida, para cada bloque: prestación publicada, `MedicoPrestacion` vigente en esa fecha,
   y las reglas de forma del slot (horario de clínica, divisibilidad, duración, superposición).
5. Genera los slots (cada uno con su propia fecha) y guarda todo.
6. Devuelve la agenda con sus días y horarios ya expandidos.

**Request para el front — `CreateAgendaMedicoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `medicoId` | UUID | Sí | Médico dueño de la agenda. |
| `fechaInicioVigencia` | Fecha (`AAAA-MM-DD`) | Sí | Primer día de la agenda. No puede ser anterior a hoy. |
| `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Sí | Último día de la agenda (inclusive). No puede ser anterior al inicio. |
| `patronSemanal` | lista de `DiaPatronRequest` (`diaSemana` + `bloques`) | Uno de los dos | Excluyente con `diasSueltos`. |
| `diasSueltos` | lista de `DiaSueltoRequest` (`fecha` + `bloques`) | Uno de los dos | Excluyente con `patronSemanal`. |

Cada bloque (`BloqueHorarioRequest`) lleva `horaDesde`, `horaHasta`, `prestacionId` y
`duracionTurno` (formato `Duration` ISO, ej. `PT30M`).

**Response para el front — `CreateAgendaMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Identificador de la agenda recién creada, para navegar al detalle. |
| `medicoId` | UUID | Confirmar contra qué médico se creó. |
| `fechaInicioVigencia` / `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Mostrar el período vigente. |
| `dias` | lista de `DiaAgendaResponse` (fecha + `horarios`) | Pintar el calendario recién generado en el preview del wizard. |
| `cantidadHorariosGenerados` | int | Mostrar el conteo total en la confirmación ("se generaron 48 turnos"). |

### Actualizar agenda (delta de composición) — `PATCH /accesmed-api/AgendaMedico/Agenda/{id}`

**Flujo simplificado:**
1. Valida que el `id` de la ruta coincida con el del body.
2. Resuelve todo lo que el request daría de baja: los `AgendaHorariosDia` activos en las
   `fechasAExcluir` (atajo del feriado) más los `AgendaHorariosDia` sueltos de
   `horariosAExcluir` (por id).
3. Si esa unión incluye algún slot ocupado, rechaza la operación completa sin escribir nada.
4. Aplica las bajas.
5. Aplica `horariosAAgregar`: cada horario nuevo lleva su propia `fecha`, no hace falta
   crear ni resolver ninguna entidad de día.

**Request para el front — `UpdateAgendaMedicoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Tiene que ser el mismo que el de la URL; si difieren, 422. |
| `horariosAAgregar` | lista de `HorarioAAgregarRequest` (fecha + bloque) | No | `null`/ausente = no agregar nada. |
| `horariosAExcluir` | lista de UUID | No | Ids de `AgendaHorariosDia` a dar de baja. |
| `fechasAExcluir` | lista de `LocalDate` (ISO, ej. `"2026-12-25"`) | No | Fechas a dar de baja completas: se excluyen todos los `AgendaHorariosDia` activos de esas fechas (atajo del feriado). |

No se puede excluir una fecha y agregar un horario en esa misma fecha en el mismo request —
el backend lo rechaza como contradictorio (422) antes de tocar nada.

**Response para el front — `UpdateAgendaMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmar sobre qué agenda se aplicó el delta. |
| `cantidadHorariosAgregados` | int | Mostrar cuántos slots nuevos se generaron. |
| `cantidadHorariosExcluidos` | int | Mostrar cuántos slots se dieron de baja (directos + arrastrados). |
| `cantidadFechasExcluidas` | int | Cuántas fechas se dieron de baja explícitamente por `fechasAExcluir`. |

### Actualizar vigencia de agenda — `PATCH /accesmed-api/AgendaMedico/Agenda/Vigencia/{id}`

**Flujo simplificado:**
1. Valida que el `id` de la ruta coincida con el del body.
2. Según qué campo venga, mueve el inicio (solo si la agenda todavía no arrancó) y/o el fin.
3. Si se adelanta el fin, calcula qué `AgendaHorariosDia` quedan con fecha posterior al
   nuevo período; si alguno está ocupado, rechaza toda la operación.
4. Si pasó la validación, da de baja esos horarios posteriores y guarda el nuevo período.

**Request para el front — `UpdateVigenciaAgendaMedicoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `id` | UUID | Sí | Tiene que ser el mismo que el de la URL. |
| `fechaInicioVigencia` | Fecha (`AAAA-MM-DD`) | Al menos uno de los dos | `null`/ausente = no tocar. Solo se puede mover si la agenda no arrancó. |
| `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Al menos uno de los dos | `null`/ausente = no tocar. Adelantarlo es restrictivo (ver arriba). |

**Response para el front — `UpdateVigenciaAgendaMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Confirmar sobre qué agenda se aplicó el cambio. |
| `fechaInicioVigencia` / `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Refrescar el período mostrado. |
| `cantidadHorariosDadosDeBaja` | int | Avisar cuántos horarios se perdieron al adelantar el fin (0 si no se tocó el fin). |

### Listar agendas — `GET /accesmed-api/AgendaMedico/Agenda`

Alimenta el selector de agendas del front. Filtrado dinámico estándar (ver
`Docs/FILTRADO-DINAMICO.md`) sobre `id`, `medicoId`, `especialidadId`,
`fechaInicioVigencia`, `fechaFinVigencia` y el derivado `vigenteAl`
(`inicio <= vigenteAl <= fin`, ambos extremos inclusivos; `vigenteAl` es una fecha, sin valor por defecto: hay que pedirlo explícitamente). El
listado **no** filtra por vigencia automáticamente: incluye tanto los períodos vencidos
como los programados a futuro.

**Response por fila — `ListAgendaMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Navegar al detalle de la agenda. |
| `medicoId`, `medicoNombre`, `medicoApellido` | UUID/String | Mostrar de quién es la agenda sin otra consulta. |
| `fechaInicioVigencia` / `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Mostrar el período. |
| `cantidadDias` | long | Mostrar cuántos días tiene generados, sin expandir el detalle. |
| `cantidadHorarios` | long | Mostrar cuántos slots tiene generados, sin expandir el detalle. |

### Buscar agenda puntual — `GET /accesmed-api/AgendaMedico/Agenda/Buscar`

Reemplazo de `GET /{id}` según `Docs/FILTRADO-DINAMICO.md §3`: se arma con los mismos
filtros de `listAgendaMedico` (típicamente `id.equals=<uuid>`), pero devuelve un único
objeto en vez de una página, y 404 si no matchea. Trae la agenda con sus días y horarios
**activos** ya expandidos — pensado para el detalle/edición de una agenda puntual.

**Response — `GetAgendaMedicoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id`, `medicoId`, `medicoNombre`, `medicoApellido` | — | Encabezado del detalle. |
| `fechaInicioVigencia` / `fechaFinVigencia` | Fecha (`AAAA-MM-DD`) | Encabezado del detalle. |
| `dias` | lista de `DiaAgendaResponse` (`fecha`, `horarios`) | Pintar el calendario completo de la agenda. |

Cada horario anidado (`HorarioAgendaResponse`) trae `id`, `horaDesde`, `horaHasta`,
`prestacionId`, `prestacionNombre`, `fechaLimiteReserva` y `estaOcupada` — suficiente para
decidir si un slot se puede seguir editando o ya está tomado.

### Listar horarios de agenda (panel) — `GET /accesmed-api/AgendaMedico/Horarios`

Pinta el calendario del período de agenda elegido. Filtrado dinámico sobre `id`,
`agendaMedicoId`, `medicoId`, `prestacionId`, `fecha`, `horaDesde` y `estaOcupada`. Guarda
fija (no configurable por el front): solo horarios activos.

**Response por fila — `ListAgendaHorarioResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `id` | UUID | Referenciar el slot para excluirlo desde `updateAgendaMedico` (por id, o por fecha con `fechasAExcluir`). |
| `fecha`, `horaDesde`, `horaHasta` | LocalDate/LocalTime | Ubicar el slot en el calendario. |
| `medicoId`, `prestacionId`, `prestacionNombre` | — | Mostrar de qué prestación es cada celda. |
| `fechaLimiteReserva` | Fecha (`AAAA-MM-DD`) | Informativo: hasta cuándo se puede reservar ese slot. |
| `estaOcupada` | Boolean | Pintar el slot como libre u ocupado, y bloquear la exclusión si está ocupado. |

### Listar horarios disponibles (chatbot) — `GET /accesmed-api/AgendaMedico/HorariosDisponibles`

El único endpoint de esta feature que consume el agente de WhatsApp. Mismos filtros que
`listHorariosAgenda`, pero con tres guardas fijas adicionales que el front/agente **no
puede desactivar**: `estaOcupada = false`, `ahora < fechaLimiteReserva` (todavía se puede
pedir) y `fecha <= hoy + diasMaximosAnticipacionReserva` (dentro del horizonte de reserva
configurado en `Clinica`). Así, cualquier filtro que llegue del bot siempre devuelve slots
genuinamente reservables.

**Response por fila — `ListHorarioDisponibleResponse`**

| Campo | Tipo | Para qué lo usa el front/agente |
|-------|------|----------------------------------|
| `id` | UUID | Identificador del slot a reservar (input del futuro `Confirmar` de Turno). |
| `medicoId`, `medicoNombre`, `medicoApellido` | — | Mostrarle al paciente con qué médico sería el turno. |
| `prestacionId`, `prestacionNombre` | — | Mostrarle al paciente para qué prestación es el turno. |
| `fecha`, `horaDesde`, `horaHasta` | LocalDate/LocalTime | Mostrarle al paciente cuándo sería el turno. |
| `fechaLimiteReserva` | Fecha (`AAAA-MM-DD`) | Informativo: plazo límite para confirmar la reserva. |

### Consulta relacionada: médicos sin agenda vigente (no es un endpoint propio)

La función "médicos activos sin agenda vigente" no tiene endpoint dedicado: se resolvió
como dos filtros nuevos en `MedicoCriteria` (`GET /accesmed-api/Medico/Medico`):

- `tieneAgendaVigente` (booleano): `tieneAgendaVigente.equals=false` trae los médicos sin
  ninguna `AgendaMedico` vigente a la fecha de referencia.
- `agendaVigenteAl` (fecha, default "ahora" si no se envía): sirve además para el caso "por
  vencer", combinando `tieneAgendaVigente.equals=false&agendaVigenteAl.equals=<hoy+30>`.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`. Códigos propios de esta feature:
> `AGENDA_MEDICO_NO_ENCONTRADA` (404), `AGENDA_MEDICO_SOLAPADA` (409),
> `AGENDA_HORARIOS_CON_OCUPADOS` (409) y `AGENDA_LIMITE_GENERACION_EXCEDIDO` (409).
