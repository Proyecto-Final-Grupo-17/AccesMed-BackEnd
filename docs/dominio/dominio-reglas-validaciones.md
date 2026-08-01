# AccesMed — Dominio, reglas de negocio y validaciones

Documento de referencia técnica. Describe **qué significa cada clase**, **qué reglas debe hacer cumplir el backend** y **cómo se valida cada campo**, en los dos lados a la vez: anotación Bean Validation y constraint de esquema.

Fuente de verdad estructural: `modelo_acces_med.json` y `modelo_dte_turno.json`. Si algo de este documento contradice esos archivos, mandan los JSON.

---

## 1. Alcance y convenciones

**Sistema**: gestión de turnos médicos. Dos canales: chatbot de WhatsApp para el paciente y panel web interno para el personal de la clínica.

**Convenciones que valen para todo el modelo:**

| Concepto | Definición |
|---|---|
| `id` | Toda clase persistente tiene `id: UUID`. Nunca se expone al paciente; al paciente se le muestra `Turno.codigo`. |
| «auditable» | `createdAt`, `updatedAt`, `createdBy`, `updatedBy`. En implementación es la `MappedSuperclass Auditable`. |
| «bajable» | `deletedAt`, `deletedBy`, `deletedReason`, **declarados en cada clase**. No existe una superclase `Bajable`. |
| Activo | `deletedAt` vacío. Es el filtro por defecto de toda consulta de catálogo. |
| Vigente | Concepto distinto de "activo". Se usa en `AgendaMedico`, `UsuarioRol` y `HistoricoEstadoTurno`, que no tienen baja lógica y se gobiernan por fechas. |
| Snapshot | `Turno` congela monto, fechas límite e indicaciones al crearse. Un cambio posterior en el catálogo no lo altera. |
| En vivo | La autorización se evalúa en cada request. Revocar un permiso surte efecto inmediato. |

**Fuera de alcance de este documento**: el subsistema del agente conversacional (`ProcesoAgente`, `MensajeClave`, `TipoMensajeClave`).

---

## 2. Glosario del dominio

### Catálogo clínico

- **Clinica** — instancia única. Guarda datos de contacto y dos pares de parámetros que acotan al resto del sistema: el horario de atención (`horarioInicioAtencion`, `horarioFinAtencion`), que limita los slots de agenda, y el rango de vigencia de agenda (`diasMinimosVigenciaAgenda`, `diasMaximosVigenciaAgenda`).
- **Especialidad** — agrupa médicos y prestaciones. Un médico tiene **una sola**.
- **Medico** — profesional. Su `email` es dato de contacto, no credencial.
- **Prestacion** — el servicio que se presta. Es la clase que concentra toda la configuración temporal del ciclo de vida del turno: seis tolerancias, el tiempo de recordatorio y el rango de duración admitido.
- **MedicoPrestacion** — clase asociativa que dice qué prestaciones atiende cada médico y a qué precio particular. Es la **única** fuente del precio: `Prestacion` no tiene precio propio.
- **TipoIndicacionPrestacion** — clasificación de las indicaciones (ayuno, estudio previo, etc.).
- **IndicacionPrestacion** — requisito previo de una prestación. Inmutable: solo alta y baja. Nunca se comparte entre prestaciones ni existe suelta.

### Agenda

- **AgendaMedico** — período de vigencia de la agenda de un médico. **No tiene baja lógica**: se gestiona adelantando `fechaHoraFinVigencia`.
- **AgendaDia** — un día concreto dentro de ese período. Excluir un día (feriado, licencia) es darlo de baja.
- **AgendaHorarios** — el slot reservable. Pertenece a un día y a una prestación. Su duración planificada se **deriva** de `horaHasta - horaDesde`; no se persiste.

### Pacientes y financiadores

- **Paciente** — se identifica por `numeroTelefono` en el canal chatbot.
- **ObraSocial** → **Plan** (1 a 1..N) → **ObraSocialPlanPrestacion** (cobertura sobre una prestación).
- **ObraSocialPaciente** — cobertura declarada por un paciente sobre un plan. Inmutable: alta y baja. La obra social se alcanza navegando por `Plan`.

### Turnos

- **Turno** — snapshot transaccional. **No tiene baja lógica**: su ciclo de vida se gobierna por estados.
- **EstadoTurno** — catálogo de estados. Es una clase, no un enum, porque los CU la tratan como instancia buscable. **No tiene un atributo `esFinal`**: un estado es final porque su tramo de histórico queda con `fechaHoraFin` vacío para siempre.
- **HistoricoEstadoTurno** — tramo de permanencia en un estado. El vigente es el que tiene `fechaHoraFin` vacío.
- **IndicacionPrestacionTurno** — copia por turno de una indicación de la prestación. No duplica texto: `nombre`, `descripcion` y `requiereValidacion` se leen por navegabilidad hacia `IndicacionPrestacion`.

### Seguridad

- **Usuario** — credencial pura. Apunta a un `Medico` **o** a un `Admin`, nunca a los dos ni a ninguno.
- **Admin** — personal administrativo. Se mantiene como clase porque tiene datos propios.
- **Rol** — conjunto de permisos. Los roles de sistema (`esSistema` verdadero) no se editan ni se dan de baja.
- **Permiso** — enum, catálogo fijo en código. Se relaciona con `Rol` como asociación N:N, materializada en la tabla `rol_permiso`.
- **UsuarioRol** — asignación acotada por vigencia. **No tiene baja lógica**: se revoca cerrando `fechaFinVigencia`.

---

## 3. Validaciones transversales de campos

Toda restricción existe en **dos capas de defensa** que no se reemplazan entre sí: la anotación Bean Validation y la constraint del esquema.

### 3.1 Campos String

**Regla**: ningún String obligatorio puede quedar vacío ni lleno de espacios.

| Caso | Bean Validation | Esquema |
|---|---|---|
| String obligatorio | `@NotBlank` | `NOT NULL` + `CHECK (btrim(col) <> '')` |
| String opcional | sin `@NotBlank`; si viene, no puede ser blanco → `@Pattern(regexp = "^(?!\\s*$).+")` o normalizar a null | `CHECK (col IS NULL OR btrim(col) <> '')` |
| Longitud | `@Size(max = n)` | `varchar(n)` |
| Formato mail | `@Email` + `@NotBlank` | `CHECK` con regex simple; la validación fina queda en la aplicación |

**`@NotBlank` y no `@NotNull`.** `@NotNull` acepta `""` y acepta `"   "`. `@NotBlank` rechaza los tres casos: null, vacío y solo espacios. Para tipos que no son String (`Integer`, `BigDecimal`, `ZonedDateTime`, enums, relaciones) el obligatorio se expresa con `@NotNull`.

**Normalización antes de validar.** Todo String entrante se recorta (`trim`) en el mapeo del request. Sin eso, `" Cardiología "` y `"Cardiología"` serían dos códigos distintos y la validación de unicidad no los detectaría.

Campos String obligatorios del modelo:

```
Clinica        : nombre, descripcion, ubicacion, email, telefono
Especialidad   : codigo, nombre
Medico         : matricula, dni, nombre, apellido, email, numeroTelefono
Prestacion     : codigo, nombre
TipoIndicacionPrestacion : codigo, nombre
IndicacionPrestacion     : nombre, descripcion
Paciente       : dni, nombre, apellido, email, numeroTelefono
ObraSocial     : codigo, nombre, razonSocial
Plan           : codigo, nombre
ObraSocialPaciente : nroSocio
Turno          : codigo
EstadoTurno    : nombre
Admin          : nombre, apellido, dni, email
Usuario        : mail, passwordHash
Rol            : nombre
```

`deletedReason` es el único String **nullable y libre**: es metadato de auditoría puro, ninguna consulta ni guarda lo lee. Igual se le aplica la regla de "si viene, que no sea blanco".

### 3.2 Unicidad

**Regla general**: la unicidad se evalúa **solo entre registros activos**, para que dar de baja algo no bloquee su código para siempre.

Esto tiene una consecuencia directa de implementación: **no se resuelve con `UNIQUE` de columna**. Un `UNIQUE (codigo)` plano impediría reutilizar el código de una prestación dada de baja, que es justamente lo que se quiere permitir.

| Nivel | Cómo se implementa |
|---|---|
| Aplicación | Método del `DomainService`, por ejemplo `PrestacionDomainService.validateCodigoIsUnique(codigo, idExcluido)`, que busca activos con ese valor. |
| Esquema | Índice único parcial: `CREATE UNIQUE INDEX uq_prestacion_codigo ON prestacion (codigo) WHERE deleted_at IS NULL;` |

El índice parcial de Postgres da la garantía real a nivel base sin romper la reutilización. Si el motor no lo soportara, queda solo la validación de aplicación más un índice no único `idx_<tabla>_<columna>` para que la consulta de validación sea barata.

**En una modificación**, la búsqueda de duplicados debe excluir la propia instancia (`id <> :id`). Es el error clásico: modificar una prestación sin tocarle el código y que el sistema la reporte como duplicada consigo misma.

Unicidad entre activos, por clase:

```
Especialidad             : codigo · nombre
Medico                   : matricula · dni
Prestacion               : codigo · nombre
TipoIndicacionPrestacion : codigo · nombre
MedicoPrestacion         : (medico, prestacion)
IndicacionPrestacion     : (prestacion, nombre)
AgendaDia                : (agendaMedico, fecha)
Paciente                 : dni · numeroTelefono
ObraSocial               : codigo · nombre
Plan                     : (obraSocial, codigo) · (obraSocial, nombre)
ObraSocialPlanPrestacion : (plan, prestacion)
ObraSocialPaciente       : (paciente, plan)
IndicacionPrestacionTurno: (turno, indicacionPrestacion)
EstadoTurno              : nombre
Usuario                  : mail
Rol                      : nombre
```

#### Las excepciones

Hay cuatro casos que **no** siguen la regla de "único entre activos", y conviene tenerlos presentes porque son justamente los que se prestan a error:

1. **`Turno.codigo` es único globalmente.** `Turno` no tiene baja lógica, así que no existe la noción de "turno activo" a los efectos de la unicidad: un turno cancelado sigue siendo un registro histórico con su código. El código no se reutiliza nunca. Se implementa con un `UNIQUE (codigo)` plano.

2. **`Usuario.medico` y `Usuario.admin` son únicos globalmente.** Un médico no puede tener dos usuarios ni siquiera contando los dados de baja: al reactivar habría ambigüedad sobre cuál es la credencial. `UNIQUE` plano sobre cada FK.

3. **`AgendaMedico` no usa unicidad sino no solapamiento.** La regla es que el período `[fechaHoraInicioVigencia, fechaHoraFinVigencia]` de un médico no se cruce con otro período del mismo médico. Es una validación de rango, no de igualdad, y va en el `DomainService`. En el esquema puede reforzarse con una constraint `EXCLUDE USING gist` sobre `(medico_id WITH =, tsrange(...) WITH &&)`.

4. **`UsuarioRol` no usa unicidad sino vigencia.** Un usuario no puede tener dos asignaciones **vigentes** del mismo rol, pero sí puede tener varias históricas ya cerradas. Se valida buscando `UsuarioRol` del par `(usuario, rol)` con `fechaFinVigencia` vacía o futura.

### 3.3 Numéricos y monetarios

| Campo | Regla | Bean Validation | Esquema |
|---|---|---|---|
| `MedicoPrestacion.precioParticular` | mayor a cero, **aun con `atiendeParticular` en falso** | `@NotNull @Positive` | `numeric(12,2) NOT NULL` + `CHECK (precio_particular > 0)` |
| `ObraSocialPlanPrestacion.porcentajeCobertura` | entre 0 y 100 | `@NotNull @DecimalMin("0") @DecimalMax("100")` | `numeric(5,2)` + `CHECK` |
| `ObraSocialPlanPrestacion.coseguro` | mayor o igual a cero | `@NotNull @PositiveOrZero` | `numeric(12,2)` + `CHECK (coseguro >= 0)` |
| `Turno.montoAPagar` | mayor o igual a cero | `@NotNull @PositiveOrZero` | `numeric(12,2)` + `CHECK` |
| `Clinica.diasMinimosVigenciaAgenda` | mayor o igual a 1 | `@NotNull @Min(1)` | `CHECK (dias_minimos_vigencia_agenda >= 1)` |

`precioParticular` se pide incluso cuando el médico no atiende particular porque es la **base de cálculo de las coberturas porcentuales**: sin él, un plan con cobertura del 70% no tiene sobre qué aplicarse.

Todos los montos son `BigDecimal` con escala 2. Nunca `double`.

### 3.4 Fechas

| Campo | Regla |
|---|---|
| `Paciente.fechaNacimiento` | anterior a hoy (`@Past`) |
| `AgendaMedico.fechaHoraInicioVigencia` | igual o posterior a ahora al darla de alta |
| `AgendaMedico.fechaHoraFinVigencia` | posterior a `fechaHoraInicioVigencia` |
| `AgendaDia.fecha` | comprendida en el período de vigencia de su `AgendaMedico` |
| `AgendaHorarios.horaDesde` / `horaHasta` | `horaDesde < horaHasta`, ambas dentro del horario de atención de la clínica |
| `HistoricoEstadoTurno.fechaHoraFin` | vacío, o posterior a `fechaHoraInicio` |
| `UsuarioRol.fechaFinVigencia` | vacío, o posterior a `fechaInicioVigencia` |

**Tipos.** `ZonedDateTime` para instantes absolutos (todo lo que se compara contra "ahora"), `LocalDate` para fechas de calendario (`AgendaDia.fecha`, `Paciente.fechaNacimiento`), `LocalTime` para horas del día que se repiten (`AgendaHorarios.horaDesde`, el horario de atención). Las tolerancias son `Duration`.

La distinción importa: `AgendaHorarios` guarda `horaDesde` como `LocalTime` porque el patrón se repite día a día, pero `fechaLimiteReserva` es `ZonedDateTime` porque es un instante único que se compara con el reloj.

### 3.5 Enums y relaciones

- Enums: `@NotNull`, más `CHECK (col IN (...))` en el esquema. Se persisten como texto (`@Enumerated(EnumType.STRING)`), nunca como ordinal: agregar un valor al medio no puede corromper los datos existentes.
- Relaciones obligatorias: `@NotNull` en el campo, `NOT NULL` en la FK, y `fk_<tabla_origen>_<tabla_destino>` con nombre explícito.
- Relaciones opcionales (`Turno.obraSocialPaciente`, `IndicacionPrestacionTurno.validadoPor`, `Usuario.medico`, `Usuario.admin`): FK nullable, sin `@NotNull`.
- Toda relación a una entidad «bajable» exige validar en el `DomainService` que el destino esté **activo** al momento de la operación. El esquema no lo puede garantizar.

### 3.6 Validaciones cruzadas entre campos

Las que no se expresan con una anotación de campo van con `@AssertTrue` en el record de request, o a mano en el `DomainService`, y su equivalente en esquema es un `CHECK` de tabla:

```
Prestacion               : duracionMinima <= duracionMaxima
Prestacion               : cadena del invariante de tolerancias (sección 4)
AgendaHorarios           : horaDesde < horaHasta
ObraSocialPlanPrestacion : coherencia entre modalidadCobertura, porcentajeCobertura y coseguro
Usuario                  : exactamente uno de (medico, admin) presente
Turno                    : tipoCobertura = OBRA_SOCIAL  <->  obraSocialPaciente presente
IndicacionPrestacionTurno: fechaHoraValidacion presente  <->  validadoPor presente
HistoricoEstadoTurno     : fechaHoraFin vacío o posterior a fechaHoraInicio
```

---

## 4. Tolerancias, fechas límite y sus restricciones

Esta es la parte del dominio con más reglas cruzadas. Conviene leerla entera antes de tocar `Prestacion` o `Turno`.

### 4.1 Qué es una tolerancia

Una tolerancia es un `Duration` configurado en la **prestación** que responde a la pregunta *"¿hasta cuánto antes del turno se puede hacer esta acción?"*. Vive en el catálogo, no en el turno.

`Prestacion` tiene siete campos de este tipo: seis tolerancias más el tiempo de recordatorio.

| Campo | Qué acota |
|---|---|
| `tiempoToleranciaSolicitud` | Antelación mínima para **reservar**. Define `AgendaHorarios.fechaLimiteReserva`. |
| `tiempoToleranciaValidacion` | Hasta cuándo se pueden **validar** las indicaciones. |
| `tiempoToleranciaReprogramacion` | Hasta cuándo se puede **reprogramar**. |
| `tiempoToleranciaConfirmacion` | Hasta cuándo el paciente puede **confirmar** por su cuenta. Pasado ese punto, el scheduler confirma solo. |
| `tiempoToleranciaCancelacion` | Hasta cuándo el **paciente** puede cancelar. El personal interno cancela sin límite. |
| `tiempoToleranciaAnuncio` | Semiancho de la ventana de **anuncio** en recepción. |
| `tiempoRecordatorioConfirmacion` | Cuánto antes del turno se le manda al paciente el recordatorio de confirmación. |

### 4.2 Cómo se convierten en fechas límite

Al **crear** el turno se calculan las siete fechas de una sola vez y quedan congeladas. Ninguna se calcula en diferido: el turno es un snapshot.

```
fechaLimiteValidacion             = fechaHoraInicio − tiempoToleranciaValidacion
fechaLimiteReprogramacion         = fechaHoraInicio − tiempoToleranciaReprogramacion
fechaLimiteConfirmacion           = fechaHoraInicio − tiempoToleranciaConfirmacion
fechaLimiteCancelacion            = fechaHoraInicio − tiempoToleranciaCancelacion
fechaHoraRecordatorioConfirmacion = fechaHoraInicio − tiempoRecordatorioConfirmacion

fechaLimiteAnuncioTemprano        = fechaHoraInicio − tiempoToleranciaAnuncio
fechaLimiteAnuncioTardio          = fechaHoraInicio + tiempoToleranciaAnuncio
```

`fechaLimiteReserva` **no vive en el turno**: vive en `AgendaHorarios`, se calcula al generar la agenda y vale `inicio del slot − tiempoToleranciaSolicitud`.

### 4.3 El invariante de tolerancias

Las cinco tolerancias que se restan hacia atrás forman una cadena de orden. Es una regla que debe ser verdadera **siempre**, en cualquier instancia de `Prestacion`:

```
tiempoToleranciaSolicitud
  ≥ tiempoToleranciaValidacion
  ≥ tiempoToleranciaReprogramacion
  ≥ tiempoToleranciaConfirmacion
  ≥ tiempoToleranciaCancelacion
  ≥ 0
```

**Por qué.** `tiempoToleranciaSolicitud` es el techo porque define el instante más tardío en el que un turno puede llegar a existir. Si `tiempoToleranciaValidacion` fuera mayor que `tiempoToleranciaSolicitud`, un turno reservado justo en el límite nacería con `fechaLimiteValidacion` **ya vencida** y el primer barrido del scheduler lo cancelaría sin que nadie hubiera tenido la oportunidad de validarlo. El mismo razonamiento encadena las demás: no tiene sentido poder cancelar hasta más tarde de lo que se puede confirmar, ni confirmar hasta más tarde de lo que se puede reprogramar.

**Dónde se valida: en el ABM de Prestación, no en el alta de turno.** Poniendo la restricción arriba, ninguna fecha límite puede nacer vencida y el alta de turno queda libre de esa comprobación.

**Restricciones adicionales:**

```
tiempoToleranciaConfirmacion < tiempoRecordatorioConfirmacion ≤ tiempoToleranciaSolicitud
tiempoToleranciaAnuncio ≥ 0
duracionMinima > 0  y  duracionMinima ≤ duracionMaxima
```

- El **límite inferior** del recordatorio garantiza `fechaHoraRecordatorioConfirmacion < fechaLimiteConfirmacion`: el recordatorio tiene que llegar **antes** de que venza el plazo, si no no sirve de nada.
- El **límite superior** garantiza que el recordatorio no nazca vencido en un turno reservado en el último momento.

**`tiempoToleranciaAnuncio` queda fuera de la cadena a propósito.** No mide antelación desde la reserva sino una ventana simétrica alrededor del turno: con 15 minutos, un turno de las 10:00 admite anuncio entre 9:45 y 10:15, y a las 10:15 el scheduler lo marca `Ausente`. No hay ninguna relación de orden que deba cumplir contra las otras; le alcanza con ser mayor o igual a cero.

### 4.4 Qué compara cada guarda

| Momento | Comparación | Quién |
|---|---|---|
| Reservar un slot | `ahora < AgendaHorarios.fechaLimiteReserva` | Alta y reprogramación de turno |
| Validar indicaciones | `ahora < fechaLimiteValidacion` | Administrador y scheduler |
| Reprogramar | `ahora < fechaLimiteReprogramacion` | Paciente, médico y administrador |
| Confirmar (paciente) | `ahora < fechaLimiteConfirmacion` | Paciente |
| Confirmar (scheduler) | `ahora ≥ fechaLimiteConfirmacion` | Scheduler |
| Cancelar (paciente) | `ahora < fechaLimiteCancelacion` | Paciente |
| Cancelar (interno) | sin límite temporal | Médico y administrador |
| Anunciar | `fechaLimiteAnuncioTemprano ≤ ahora ≤ fechaLimiteAnuncioTardio` | Administrador |
| Marcar ausente | `ahora > fechaLimiteAnuncioTardio` | Scheduler |
| Recordar confirmación | `ahora ≥ fechaHoraRecordatorioConfirmacion` y estado `Pendiente` | Scheduler |

### 4.5 Caso límite documentado

Un turno reservado para hoy o mañana en una prestación con `tiempoToleranciaConfirmacion` alta nace con `fechaLimiteConfirmacion` ya vencida, y el primer barrido lo confirma automáticamente en minutos. **Es correcto y no requiere una rama nueva en el DTE.** El invariante impide que nazca vencida una fecha *anterior* a la de solicitud, pero no impide que la de confirmación quede vencida en un turno reservado con muy poca antelación: eso es exactamente lo que se quiere, porque no tiene sentido pedirle al paciente que confirme un turno que es dentro de dos horas.

---

## 5. Reglas por módulo

### CONFIG — Clínica

- Instancia única. No hay alta ni baja, solo modificación.
- `horarioInicioAtencion < horarioFinAtencion`.
- `1 ≤ diasMinimosVigenciaAgenda ≤ diasMaximosVigenciaAgenda`.
- Bajar `horarioFinAtencion` o subir `horarioInicioAtencion` **no** recorta las agendas ya generadas: la validación se aplica al crear o modificar slots.

### MED — Médicos y especialidades

- El alta de médico es atómica y crea **solo** el médico. La asignación de prestaciones es un CU aparte, invocado con `Ir a CU`.
- Un médico tiene una sola especialidad.
- **Solo se pueden asignar prestaciones cuya `especialidad` coincida con la del médico.**
- `MedicoPrestacion` es única por `(medico, prestacion)` entre activos. Reasignar una prestación dada de baja es un **alta nueva**, no revivir la fila vieja.
- Al desasignar una prestación: los turnos ya reservados **se mantienen** y el médico los atiende. Se dan de baja todos los `AgendaHorarios` futuros de ese médico para esa prestación, ocupados y libres. Antes de ejecutar hay que mostrar cuántos turnos futuros hay y pedir confirmación.

### PREST — Prestaciones e indicaciones

- `codigo` es **inmutable** después del alta porque genera el `Turno.codigo`. Cambiarlo es baja más alta.
- `nombre` y las siete tolerancias son editables. La cadena del invariante se revalida en cada modificación.
- `IndicacionPrestacion` es **inmutable**: alta y baja lógica únicamente, nunca `Modificar`. Si el texto está mal, se da de baja y se carga de nuevo.
- Una indicación siempre pertenece a una prestación. Dos prestaciones no comparten indicación: se carga una vez para cada una.
- La baja de una indicación es libre, sin restricciones.
- **`TipoIndicacionPrestacion` tiene la única baja restrictiva del sistema**: no se puede dar de baja si hay `IndicacionPrestacion` activas que lo referencian.

### AGEN — Agenda

- Una agenda está **vigente** cuando hoy cae entre `fechaHoraInicioVigencia` y `fechaHoraFinVigencia`. Queda derogada la regla anterior de "agenda activa = `fechaHoraFinVigencia` vacía".
- La duración del período debe caer entre `Clinica.diasMinimosVigenciaAgenda` y `Clinica.diasMaximosVigenciaAgenda`.
- Los períodos de un mismo médico no se solapan.
- El **patrón semanal es entrada del CU**: se usa para generar los `AgendaDia` y `AgendaHorarios` y no se persiste.
- Cálculo de slots: `slot(n) = horaDesde + n × duracionTurno`. La duración debe dividir exactamente al bloque.
- La duración de cada slot debe caer entre `duracionMinima` y `duracionMaxima` de su prestación.
- Cada slot exige una `MedicoPrestacion` **activa** entre el médico de la agenda y la prestación del slot.
- Los bloques de un mismo día no se superponen.
- **Un slot ocupado no se puede dar de baja**: primero hay que cancelar o reprogramar el turno.
- Al reemplazar un slot, la `fechaLimiteReserva` recalculada con la tolerancia de solicitud de la prestación nueva debe quedar posterior a ahora.
- Hace falta una consulta de **médicos activos sin agenda vigente**, como criterio de `Consultar Agendas`.

### PACIENTE

- `dni` y `numeroTelefono` únicos entre activos. El teléfono es la clave de identificación del canal de WhatsApp.
- En el canal chatbot el teléfono lo provee la API de WhatsApp; en el canal interno lo ingresa el administrador.
- **El paciente no puede cambiarse el `numeroTelefono` a sí mismo** desde el chatbot: ese cambio lo hace el administrador.
- Si no declara ninguna cobertura, se atiende como particular.
- `ObraSocialPaciente` es inmutable: alta y baja lógica. Cambiar de plan es baja más alta.

### OS — Obras sociales

- Multiplicidad `ObraSocial 1 → 1..N Plan`. El alta de obra social crea la obra social **y al menos un plan** en el mismo `Confirmar`.
- La FK `Plan → ObraSocial` es **inmutable** después del alta.
- **No se puede dar de baja el último plan activo** de una obra social activa.
- `codigo` y `nombre` son editables en ambas.
- `ObraSocialPlanPrestacion`: `porcentajeCobertura` y `coseguro` son editables. Coherencia según `modalidadCobertura`:
  - `TOTAL` → `porcentajeCobertura = 100` y `coseguro = 0`
  - `CARGO_FIJO` → `coseguro ≥ 0`, es el monto que paga el paciente
  - `PORCENTUAL` → `0 ≤ porcentajeCobertura ≤ 100`, el paciente paga la parte no cubierta
- **Las bajas de obras sociales no cancelan turnos.** Solo bloquean el futuro.

### TURN — Turnos

- El par `(medico, prestacion)` se valida buscando una `MedicoPrestacion` activa. El esquema ya no lo garantiza, porque `Turno` no apunta a `MedicoPrestacion`.
- Al crear el turno se copian las `IndicacionPrestacion` activas de la prestación como `IndicacionPrestacionTurno`.
- El turno nace en `Espera de Validación` si alguna de esas indicaciones tiene `requiereValidacion` en verdadero; si no, en `Pendiente`.
- Sale de `Espera de Validación` cuando ninguna `IndicacionPrestacionTurno` **activa y obligatoria** queda con `fechaHoraValidacion` vacía.
- `AgendaHorarios.estaOcupada` pasa a verdadero al crear el turno y vuelve a falso al entrar en `Cancelado` o en `Reprogramado`.
- Un turno `Confirmado` **no se puede reprogramar**: hay que cancelarlo y dar de alta uno nuevo. Es una decisión de negocio deliberada.
- Al paciente se le expone el `codigo`, nunca el `id`.

### USER y AUTZ

- `Usuario.mail` es credencial y **no se copia** del email de contacto de la persona.
- Unicidad de `mail` entre activos; unicidad global de `medico` y de `admin`.
- La contraseña se almacena solo como `passwordHash`.
- `Rol`: unicidad de `nombre` entre activos, al menos un permiso, y el nombre no puede coincidir con el de un rol de sistema.
- Los roles de sistema (`Medico`, `Admin`) no se editan ni se dan de baja.
- Dar de baja un rol cierra la `fechaFinVigencia` de sus `UsuarioRol` vigentes.
- La autorización se evalúa **en vivo**: no hay snapshot de permisos en el token ni en el turno.
- Los artefactos de `UserDetails` de Spring Security no son parte del modelo conceptual: `isEnabled()` se deriva de `deletedAt` vacío.

### NOTIF

- Las notificaciones no persisten entidades propias en este modelo: cada CU de notificación recibe el turno del CU llamador y despacha el mensaje.
- `Recordar Confirmación` corre sobre turnos `Pendiente` con `fechaHoraRecordatorioConfirmacion` vencida y **no cambia el estado**.

---

## 6. Bajas y cascadas

Regla de fondo: **toda baja es lógica**, salvo la de `TipoIndicacionPrestacion`, que es restrictiva.

### Prestación

Baja siempre permitida. Arrastra, en este orden:

1. `Buscar Turno` por prestación, activos y futuros → mostrar la cantidad, pedir confirmación explícita, `Ir a CU Cancelar Turno` por cada uno con `motivoCancelacion = BAJA_DE_PRESTACION`.
2. Baja de los `AgendaHorarios` futuros de esa prestación.
3. Baja de las `MedicoPrestacion` de esa prestación.

### Médico — baja planificada

No se opera sobre el médico: se opera sobre la agenda.

- Si la salida coincide con el fin de vigencia actual: no hacer nada, dejar vencer.
- Si se va antes: adelantar `fechaHoraFinVigencia`, dar de baja los `AgendaDia` posteriores con sus `AgendaHorarios` en cascada, y resolver los turnos que caigan después del nuevo corte.
- El día siguiente al último turno se ejecuta la baja del `Medico`, que ya no arrastra nada salvo `MedicoPrestacion` y `Usuario`.

**No existe fecha de baja futura como atributo.** `deletedAt` distinto de vacío significa siempre "ya está de baja".

### Médico — baja abrupta

1. Mostrar la cantidad de turnos afectados y pedir confirmación.
2. Baja atómica: `Medico` + `AgendaMedico` vigente (con `AgendaDia` y `AgendaHorarios` en cascada) + `MedicoPrestacion` + `Usuario`.
3. Cancelación de cada turno activo futuro con `Ir a CU` y `motivoCancelacion = BAJA_DE_MEDICO`.

Ese orden es deliberado: si falla una cancelación se reintenta, mientras que al revés habrían quedado turnos cancelados de un médico activo. La reprogramación queda como acción posterior y opcional, no en lote.

### Obras sociales

- `ObraSocialPlanPrestacion`: no arrastra nada.
- `Plan`: baja de sus `ObraSocialPlanPrestacion` y de las `ObraSocialPaciente` que lo referencian.
- `ObraSocial`: baja de sus `Plan`, en cascada.

### Rol

Cierra la `fechaFinVigencia` de sus `UsuarioRol` vigentes. No se pueden dar de baja los roles de sistema.

---

## 7. Ciclo de vida del turno

El detalle está en `modelo_dte_turno.json` y en `dte_turno.svg`. Lo que el backend tiene que garantizar:

- **Cada transición** cierra el `HistoricoEstadoTurno` vigente con `fechaHoraFin = ahora` y crea uno nuevo con `fechaHoraInicio = ahora`. Los tramos son contiguos y no se solapan.
- Un turno tiene **a lo sumo un** `HistoricoEstadoTurno` con `fechaHoraFin` vacío.
- Un estado es final porque ese tramo queda abierto para siempre. No hay atributo que lo declare.
- La duración **real** del turno es la duración del tramo `En Transcurso`. La **planificada** sale del `AgendaHorarios`.
- Las transiciones automáticas se registran con identidad de sistema en `Auditable.createdBy`, lo que permite distinguir en reportes la confirmación manual de la automática.

### Reprogramación

Un solo CU atómico: un `Confirmar`, una transacción. Orden de ejecución obligatorio:

1. Validar y **crear el turno nuevo**.
2. Copiar las validaciones ya cumplidas: para cada `IndicacionPrestacionTurno` cuya `IndicacionPrestacion` ya estaba validada en el turno origen, se copian `fechaHoraValidacion` y `validadoPor`. Las indicaciones nuevas quedan en vacío.
3. Recién después, cerrar el viejo como `Reprogramado` y liberar su slot.

Recalcula todo: monto, fechas límite e indicaciones. Misma prestación siempre; si cambia la prestación es un alta común. Si la prestación, el plan o la `ObraSocialPlanPrestacion` están de baja, o no hay slot disponible, no se reprograma y el turno queda en `Pendiente`. El monto nuevo se muestra antes del `Confirmar`. `turnoOrigen` registra la cadena.

### Scheduler

| Proceso | Módulo | Efecto |
|---|---|---|
| Recordar Confirmación | NOTIF | `Pendiente` con recordatorio vencido. No cambia estado |
| Confirmar Turnos Automáticamente | TURN | `Pendiente → Confirmado` |
| Vencer Validación | TURN | `Espera de Validación → Cancelado` |
| Marcar Ausentes | TURN | `Confirmado → Ausente` |

Reglas:

- Cada turno es su propia transacción: un fallo no bloquea el lote.
- La guarda sobre el estado vigente se evalúa **dentro** de la transacción, por la concurrencia con el paciente confirmando por WhatsApp.
- Idempotencia por filtrado del `HistoricoEstadoTurno` vigente.
- El intervalo del barrido debe ser **más fino que la menor de las tolerancias** configuradas en las prestaciones.
- `Confirmar Turnos Automáticamente` corre **antes** que `Marcar Ausentes`.

---

## 8. Cálculo del monto a pagar

Se calcula al crear el turno y queda congelado.

```
base = MedicoPrestacion(medico, prestacion).precioParticular

si tipoCobertura = PARTICULAR:
    requiere MedicoPrestacion.atiendeParticular = verdadero
    montoAPagar = base

si tipoCobertura = OBRA_SOCIAL:
    cobertura = ObraSocialPlanPrestacion activa de (obraSocialPaciente.plan, prestacion)
    si no existe cobertura activa -> el turno se registra como PARTICULAR
    segun cobertura.modalidadCobertura:
        TOTAL      -> montoAPagar = 0
        CARGO_FIJO -> montoAPagar = cobertura.coseguro
        PORCENTUAL -> montoAPagar = base × (100 − cobertura.porcentajeCobertura) / 100
```

Redondeo a dos decimales, `RoundingMode.HALF_UP`.

---

## 9. Mapeo validación ↔ constraint de esquema

Tabla de referencia para generar entidad y changelog en el mismo paso.

| Restricción de negocio | Bean Validation | Constraint de esquema |
|---|---|---|
| String obligatorio | `@NotBlank` | `NOT NULL` + `CHECK (btrim(col) <> '')` |
| No-String obligatorio | `@NotNull` | `NOT NULL` |
| Longitud máxima | `@Size(max = n)` | `varchar(n)` |
| Formato | `@Pattern` / `@Email` | `CHECK` si Postgres lo valida razonablemente; si no, queda solo en la aplicación y se documenta |
| Unicidad simple global | `@Column(unique = true)` | `UNIQUE` → `uq_<tabla>_<columna>` |
| **Unicidad entre activos** | no se resuelve con `@Column(unique)`; es regla del `DomainService` | índice único parcial `uq_<tabla>_<columna>` con `WHERE deleted_at IS NULL` |
| Rango numérico | `@Positive` / `@Min` / `@DecimalMax` | `CHECK` → `ck_<tabla>_<regla>` |
| Regla cruzada entre columnas | `@AssertTrue` en el record | `CHECK` → `ck_<tabla>_<regla>` |
| No solapamiento de rangos | validación en el `DomainService` | `EXCLUDE USING gist` |
| Relación obligatoria | `@NotNull` | `NOT NULL` + `fk_<origen>_<destino>` |
| Destino debe estar activo | validación en el `DomainService` | no expresable en esquema; se documenta |

Nomenclatura de objetos de esquema, siempre `snake_case`:

```
PK      pk_<tabla>
FK      fk_<tabla_origen>_<tabla_destino>
UNIQUE  uq_<tabla>_<columna(s)>
CHECK   ck_<tabla>_<regla>
INDEX   idx_<tabla>_<columna(s)>
TRIGGER trg_<tabla>_<momento>_<evento>
```

---

## 10. Checklist antes de dar por cerrada una entidad

- [ ] Todo String obligatorio tiene `@NotBlank`, no `@NotNull`.
- [ ] Todo String tiene `@Size(max)` y su `varchar(n)` con el mismo número.
- [ ] Las unicidades declaradas en el JSON están implementadas como índice único parcial y como método del `DomainService`.
- [ ] La búsqueda de duplicados en la modificación excluye la propia instancia.
- [ ] Los invariantes de la clase están como `CHECK` con nombre `ck_<tabla>_<regla>` y como validación de aplicación.
- [ ] Las relaciones respetan la navegabilidad del diagrama: sin FK redundante donde la asociación debe derivarse.
- [ ] Los campos «bajable» están declarados en la clase, sin heredar de ninguna superclase.
- [ ] Los enums se persisten como texto, no como ordinal.
- [ ] Las relaciones a entidades «bajable» validan que el destino esté activo.
- [ ] Toda constraint de Bean Validation tiene su equivalente en el changelog, o está documentado por qué no aplica.
