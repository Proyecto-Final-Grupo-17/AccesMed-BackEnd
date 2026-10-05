# AccesMed — Dominio, reglas de negocio y validaciones (v3)

Documento de referencia técnica. Describe **qué significa cada clase**, **qué reglas debe hacer cumplir el backend** y **cómo se valida cada campo**, en los dos lados a la vez: anotación Bean Validation y constraint de esquema.

Fuente de verdad estructural: `modelo_acces_med.json` y `modelo_dte_turno.json`. Si algo de este documento contradice esos archivos, mandan los JSON.

## Qué cambió respecto de la v2

1. **`Prestacion` dejó de ser «bajable».** Su ciclo de vida se gobierna por `HistoricoEstadoPrestacion`: *No Publicada*, *Publicada*, *Deshabilitada*. Desaparece `fechaHabilitacion` y el CU irreversible «Habilitar Prestación».
2. **`Plan` dejó de ser «bajable»**, con el mismo esquema: `HistoricoEstadoPlan`. `ObraSocial` conserva su baja lógica.
3. **`MedicoPrestacion` dejó de ser «bajable»**: pasa a vigencia (`fechaInicioVigencia`, `fechaFinVigencia`), como `AgendaMedico` y `UsuarioRol`.
4. **Deshabilitar dejó de ser una cascada y pasó a ser restrictivo.** Ni prestaciones ni planes cancelan turnos: si hay turnos vivos, la operación se rechaza. Se eliminó `MotivoCancelacion.BAJA_DE_PRESTACION`.
5. **`IndicacionPrestacion` pasó al eje de vigencia y se volvió modificable, pero con restricción.** No se puede modificar mientras tenga turnos en estado no final; para cambiarla se programa el relevo con `fechaInicioVigencia` / `fechaFinVigencia`. **`IndicacionPrestacionTurno` sigue leyendo el texto por navegabilidad**, sin copiarlo: la combinación de vigencia y restricción lo vuelve seguro.
6. **La modificación de `Prestacion` no se restringe por turnos**, pero al cambiar las duraciones o `tiempoToleranciaSolicitud` revalida y recalcula los `AgendaHorariosDia` futuros libres.
7. **`Especialidad` tiene baja restrictiva.**
8. **La baja de `Medico` y la baja de `Usuario` quedan desacopladas**: ninguna arrastra a la otra. La asociación `Usuario`–`Medico`/`Admin` es **unidireccional** (la FK vive en `usuario`; `Medico` y `Admin` no la navegan de vuelta). `Usuario` se crea con dos caminos según el rol: rol médico crea también la instancia de `Medico` con los datos recibidos, rol admin crea la instancia de `Admin`.
9. **Clase nueva `Archivo`**: N→1 `Paciente` (obligatoria) y N→1 `Turno` (opcional).
10. **El estado `En Transcurso` pasó a llamarse `En Curso`.**

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
| «vigencia» | `fechaInicioVigencia`, `fechaFinVigencia`. Vigente = `fechaInicioVigencia ≤ ahora` y (`fechaFinVigencia` vacía o `ahora < fechaFinVigencia`). Lo usan `AgendaMedico`, **`MedicoPrestacion`**, **`IndicacionPrestacion`** y `UsuarioRol`. |
| «con estados» | La clase no tiene baja lógica ni vigencia: su ciclo de vida es un histórico de tramos. El vigente es el que tiene `fechaHoraFin` vacía. Lo usan `Turno`, **`Prestacion`** y **`Plan`**. |

**Tres ejes distintos de "ya no se usa", y ninguna clase usa más de uno.** Es la regla que más ordena el modelo v3: si una clase es «bajable», no tiene estados ni vigencia; si tiene estados, no tiene `deletedAt`. Cuando un CU pregunta "¿está disponible?", el predicado depende del eje de esa clase y de ninguno más.

| Clase | Eje | "Disponible" significa |
|---|---|---|
| `Especialidad`, `Medico`, `ObraSocial`, `Paciente`, `Archivo`, `TipoIndicacionPrestacion`, `Usuario`, `Admin`, `Rol`, `ObraSocialPlanPrestacion`, `ObraSocialPaciente`, `AgendaHorariosDia` | baja lógica | `deletedAt` vacío |
| `AgendaMedico`, `MedicoPrestacion`, `IndicacionPrestacion`, `UsuarioRol` | vigencia | `fechaInicioVigencia ≤ ahora <` `fechaFinVigencia` |
| `Prestacion`, `Plan`, `Turno` | estados | el tramo abierto no apunta a un estado terminal |

**El eje vigencia es el único que se puede programar.** `fechaInicioVigencia` y `fechaFinVigencia` admiten fechas futuras, así que un alta o una baja se pueden dejar agendadas. `deletedAt` no: distinto de vacío significa siempre "ya está de baja". Las transiciones de estado tampoco: se registran en el instante en que ocurren. Es la razón por la que `IndicacionPrestacion` pasó a este eje.
| Snapshot | `Turno` congela monto y fechas límite al crearse. Un cambio posterior en el catálogo no los altera. **Las indicaciones quedan fuera del snapshot**: se leen por navegabilidad. |
| En vivo | La autorización se evalúa en cada request. Revocar un permiso surte efecto inmediato. |

**Fuera de alcance de este documento**: el subsistema del agente conversacional (`ProcesoAgente`, `MensajeClave`, `TipoMensajeClave`).

---

## 2. Glosario del dominio

### Catálogo clínico

- **Clinica** — instancia única. Guarda datos de contacto y dos parámetros que acotan al resto del sistema: el horario de atención (`horarioInicioAtencion`, `horarioFinAtencion`), que limita los slots de agenda, y `diasMaximosAnticipacionReserva`, el horizonte hasta el que se puede pedir un turno.
- **Especialidad** — agrupa médicos y prestaciones. Un médico tiene **una sola**.
- **Medico** — profesional. Su `email` es dato de contacto, no credencial.
- **Prestacion** — el servicio que se presta. Es la clase que concentra toda la configuración temporal del ciclo de vida del turno: seis tolerancias, el tiempo de recordatorio y el rango de duración admitido. **No tiene baja lógica**: se gobierna por estados.
- **EstadoPrestacion** — **enum** Java (no entidad/tabla), tres valores: `NO_PUBLICADA`, `PUBLICADA`, `DESHABILITADA`. El estado vigente **no se materializa** en `Prestacion`: se deriva siempre del tramo de `HistoricoEstadoPrestacion` con `fecha_hora_fin` vacío (relación unidireccional; se consulta desde el histórico).
- **HistoricoEstadoPrestacion** — tramo de permanencia de una prestación en un estado (columna `estado` enum, no FK a catálogo), con un `motivo` opcional de auditoría.
- **MedicoPrestacion** — clase asociativa que dice qué prestaciones atiende cada médico, **durante qué período** y a qué precio particular. Es la **única** fuente del precio: `Prestacion` no tiene precio propio. **No tiene baja lógica**: se desasigna cerrando `fechaFinVigencia`, y reasignar es crear una instancia nueva.
- **TipoIndicacionPrestacion** — clasificación de las indicaciones (ayuno, estudio previo, etc.).
- **IndicacionPrestacion** — requisito previo de una prestación, **acotado por vigencia**. No tiene baja lógica: se retira cerrando `fechaFinVigencia`, que puede ser futura. Modificable, pero solo mientras no tenga turnos vivos. Nunca se comparte entre prestaciones ni existe suelta.

### Agenda

- **AgendaMedico** — período de vigencia de la agenda de un médico. **No tiene baja lógica**: se gestiona adelantando `fechaFinVigencia`. **Su duración es libre**: puede ser de un día, para un suplente, o de un semestre.
- **AgendaHorariosDia** — el slot reservable. Pertenece directamente a una `AgendaMedico` y a una `Prestacion`, y lleva su propia `fecha`: no hay un nivel intermedio de "día" como entidad separada. Excluir una fecha entera (feriado, licencia) es dar de baja todos sus `AgendaHorariosDia` activos. **No hace falta cubrir el período completo**: las fechas sin ningún `AgendaHorariosDia` simplemente no tienen atención. Su duración planificada se **deriva** de `horaHasta - horaDesde`; no se persiste.

### Pacientes y financiadores

- **Paciente** — se identifica por `numeroTelefono` en el canal chatbot.
- **Archivo** — documento adjunto. Pertenece **siempre** a un `Paciente` y **opcionalmente** a un `Turno`. El binario no vive en la base: la tabla guarda la clave del objeto en el almacenamiento.
- **ObraSocial** → **Plan** (1 a 1..N) → **ObraSocialPlanPrestacion** (cobertura sobre una prestación). `ObraSocial` conserva baja lógica; `Plan` **no**: se gobierna por estados.
- **EstadoPlan** / **HistoricoEstadoPlan** — el mismo mecanismo que en `Prestacion` (enum + histórico, sin columna `estado_actual`; el estado vigente se deriva del tramo con `fecha_hora_fin` vacío), con los valores `NO_PUBLICADO`, `PUBLICADO`, `DESHABILITADO`.
- **ObraSocialPaciente** — cobertura declarada por un paciente sobre un plan. Inmutable: alta y baja. La obra social se alcanza navegando por `Plan`.

### Turnos

- **Turno** — snapshot transaccional. **No tiene baja lógica**: su ciclo de vida se gobierna por estados.
- **EstadoTurno** — **enum** Java (no entidad/tabla), nueve valores (ver `modelo_dte_turno.json`). Los finales (`CANCELADO`, `REPROGRAMADO`, `AUSENTE`, `FINALIZADO`) son una **constante de código** (`EstadoTurno.FINALES`/`esFinal()`), no un dato en base. El estado vigente **no se materializa** en `Turno`: se deriva del tramo de `HistoricoEstadoTurno` con `fecha_hora_fin` vacío (las consultas de "turnos vivos" se escriben desde el histórico).
- **HistoricoEstadoTurno** — tramo de permanencia en un estado (columna `estado` enum, no FK a catálogo). El vigente es el que tiene `fechaHoraFin` vacío.
- **IndicacionPrestacionTurno** — registro por turno del cumplimiento de una indicación. **No duplica texto**: `nombre`, `descripcion` y `requiereValidacion` se leen por navegabilidad hacia `IndicacionPrestacion`. Solo aporta `fechaHoraValidacion` y `validadoPor`.

### Seguridad

- **Usuario** — credencial pura. Apunta a un `Medico` **o** a un `Admin`, nunca a los dos ni a ninguno. La asociación es **unidireccional**: la FK vive en `usuario`; `Medico` y `Admin` no la navegan de vuelta. Se crea con dos caminos según el rol: rol médico crea también la instancia de `Medico` con los datos recibidos, rol admin crea la instancia de `Admin`.
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
Archivo        : nombre, tipoContenido, ubicacion
Turno          : codigo
IndicacionPrestacionTurno : nombre, descripcion
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
TipoIndicacionPrestacion : codigo · nombre
Paciente                 : dni · numeroTelefono
ObraSocial               : codigo · nombre
ObraSocialPlanPrestacion : (plan, prestacion)
ObraSocialPaciente       : (paciente, plan)
IndicacionPrestacionTurno: (turno, indicacionPrestacion)
Usuario                  : mail
Rol                      : nombre
```

#### Unicidad entre no deshabilitadas

`Prestacion` y `Plan` ya no tienen `deletedAt`, así que su unicidad **no puede** apoyarse en el índice parcial `WHERE deleted_at IS NULL`. El predicado equivalente es el estado vigente:

```
Prestacion : codigo · nombre                          entre las no Deshabilitadas
Plan       : (obraSocial, codigo) · (obraSocial, nombre)  entre los no Deshabilitados
```

El problema de implementación es que el estado vive en el histórico, no en la fila. La
solución adoptada: **calcular siempre el estado vigente desde el histórico** (el tramo con
`fecha_hora_fin` vacío), sin materializarlo en la fila. La unicidad "entre no
deshabilitadas" se valida **en la capa de aplicación**, con una consulta al histórico
vigente (`existsBy...EstadoVigenteNot`), y se acepta la pérdida de la red de seguridad que
daba el índice único parcial de BD (que dependía de `estado_actual`, ahora eliminada).
Antes existía una columna derivada `estado_actual` que cacheaba el tramo vigente; se
eliminó para no tener dos fuentes de verdad ni la doble escritura (histórico + caché) en
cada transición. El histórico queda como **única fuente** del estado, además de auditoría
del ciclo de vida.

#### Las excepciones

Hay cuatro casos que **no** siguen la regla de "único entre activos", y conviene tenerlos presentes porque son justamente los que se prestan a error:

1. **`Turno.codigo` es único globalmente.** `Turno` no tiene baja lógica, así que no existe la noción de "turno activo" a los efectos de la unicidad: un turno cancelado sigue siendo un registro histórico con su código. El código no se reutiliza nunca. Se implementa con un `UNIQUE (codigo)` plano.

2. **`Usuario.medico` y `Usuario.admin` son únicos globalmente.** Un médico no puede tener dos usuarios ni siquiera contando los dados de baja: al reactivar habría ambigüedad sobre cuál es la credencial. `UNIQUE` plano sobre cada FK.

3. **Las tres clases con vigencia no usan unicidad sino no solapamiento.** En `AgendaMedico`, el período de un médico no se cruza con otro período del mismo médico. En `MedicoPrestacion`, los períodos del mismo par `(medico, prestacion)` no se cruzan — un médico puede tener la misma prestación asignada varias veces a lo largo del tiempo, pero nunca dos veces a la vez. En `IndicacionPrestacion`, los períodos del mismo par `(prestacion, nombre)` no se cruzan, que es lo que permite programar el relevo: la vieja termina el día 1 y la nueva arranca el día 1 sin solaparse. Es una validación de rango, no de igualdad, y va en el `DomainService`. En el esquema puede reforzarse con `EXCLUDE USING gist` sobre `(medico_id WITH =, tsrange(...) WITH &&)`, `(medico_id WITH =, prestacion_id WITH =, tsrange(...) WITH &&)` y `(prestacion_id WITH =, nombre WITH =, tsrange(...) WITH &&)` respectivamente.

4. **`UsuarioRol` no usa unicidad sino vigencia.** Un usuario no puede tener dos asignaciones **vigentes** del mismo rol, pero sí puede tener varias históricas ya cerradas. Se valida buscando `UsuarioRol` del par `(usuario, rol)` con `fechaFinVigencia` vacía o futura.

### 3.3 Numéricos y monetarios

| Campo | Regla | Bean Validation | Esquema |
|---|---|---|---|
| `MedicoPrestacion.precioParticular` | mayor a cero, **aun con `atiendeParticular` en falso** | `@NotNull @Positive` | `numeric(12,2) NOT NULL` + `CHECK (precio_particular > 0)` |
| `ObraSocialPlanPrestacion.porcentajeCobertura` | entre 0 y 100 | `@NotNull @DecimalMin("0") @DecimalMax("100")` | `numeric(5,2)` + `CHECK` |
| `ObraSocialPlanPrestacion.coseguro` | mayor o igual a cero | `@NotNull @PositiveOrZero` | `numeric(12,2)` + `CHECK (coseguro >= 0)` |
| `Turno.montoAPagar` | mayor o igual a cero | `@NotNull @PositiveOrZero` | `numeric(12,2)` + `CHECK` |
| `Clinica.diasMaximosAnticipacionReserva` | mayor o igual a 1 | `@NotNull @Min(1)` | `CHECK (dias_maximos_anticipacion_reserva >= 1)` |

`precioParticular` se pide incluso cuando el médico no atiende particular porque es la **base de cálculo de las coberturas porcentuales**: sin él, un plan con cobertura del 70% no tiene sobre qué aplicarse.

Todos los montos son `BigDecimal` con escala 2. Nunca `double`.

### 3.4 Fechas

| Campo | Regla |
|---|---|
| `Paciente.fechaNacimiento` | anterior a hoy (`@Past`) |
| `AgendaMedico.fechaInicioVigencia` | igual o posterior a hoy (zona horaria de la clínica) al darla de alta |
| `AgendaMedico.fechaFinVigencia` | igual o posterior a `fechaInicioVigencia` (ambos extremos inclusivos) |
| `AgendaHorariosDia.fecha` | comprendida en el período de vigencia de su `AgendaMedico` |
| `AgendaHorariosDia.horaDesde` / `horaHasta` | `horaDesde < horaHasta`, ambas dentro del horario de atención de la clínica |
| `HistoricoEstadoTurno.fechaHoraFin` | vacío, o posterior a `fechaHoraInicio` |
| `HistoricoEstadoPrestacion.fechaHoraFin` | vacío, o posterior a `fechaHoraInicio` |
| `HistoricoEstadoPlan.fechaHoraFin` | vacío, o posterior a `fechaHoraInicio` |
| `UsuarioRol.fechaFinVigencia` | vacío, o posterior a `fechaInicioVigencia` |
| `MedicoPrestacion.fechaFinVigencia` | vacío, o posterior a `fechaInicioVigencia`, **y no anterior al `fechaHoraInicio` del último turno vivo de ese par** |
| `IndicacionPrestacion.fechaInicioVigencia` | admite fecha futura: es cómo se programa el alta |
| `IndicacionPrestacion.fechaFinVigencia` | vacío, o posterior a `fechaInicioVigencia`. Admite fecha futura: es cómo se programa la baja |

**Tipos.** `ZonedDateTime` para instantes absolutos (todo lo que se compara contra "ahora"), `LocalDate` para fechas de calendario (`AgendaHorariosDia.fecha`, `Paciente.fechaNacimiento`), `LocalTime` para horas del día que se repiten (`AgendaHorariosDia.horaDesde`, el horario de atención). Las tolerancias son `Duration`.

La distinción importa: `AgendaHorariosDia` guarda `horaDesde` como `LocalTime` porque el patrón se repite día a día, pero `fechaLimiteReserva` es `ZonedDateTime` porque es un instante único que se compara con el reloj.

### 3.5 Enums y relaciones

- Enums: `@NotNull`, más `CHECK (col IN (...))` en el esquema. Se persisten como texto (`@Enumerated(EnumType.STRING)`), nunca como ordinal: agregar un valor al medio no puede corromper los datos existentes.
- Relaciones obligatorias: `@NotNull` en el campo, `NOT NULL` en la FK, y `fk_<tabla_origen>_<tabla_destino>` con nombre explícito.
- Relaciones opcionales (`Turno.obraSocialPaciente`, `IndicacionPrestacionTurno.validadoPor`, `Usuario.medico`, `Usuario.admin`): FK nullable, sin `@NotNull`.
- Toda relación a una entidad «bajable» exige validar en el `DomainService` que el destino esté **activo** al momento de la operación. El esquema no lo puede garantizar.

### 3.6 Validaciones cruzadas entre campos

Las que no se expresan con una anotación de campo van con `@AssertTrue` en el record de request, o a mano en el `DomainService`, y su equivalente en esquema es un `CHECK` de tabla:

```
Prestacion               : duracionMinima <= duracionMaxima
Prestacion               : cadena de las reglas de tolerancia (sección 4)
AgendaHorariosDia        : horaDesde < horaHasta
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
| `tiempoToleranciaSolicitud` | Antelación mínima para **reservar**. Define `AgendaHorariosDia.fechaLimiteReserva`. |
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

`fechaLimiteReserva` **no vive en el turno**: vive en `AgendaHorariosDia`, se calcula al generar la agenda y vale `inicio del slot − tiempoToleranciaSolicitud`.

### 4.3 Las reglas de tolerancia

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
| Reservar un slot — borde inferior | `ahora < AgendaHorariosDia.fechaLimiteReserva` | Alta y reprogramación de turno |
| Reservar un slot — borde superior | `fechaHoraInicio ≤ ahora + Clinica.diasMaximosAnticipacionReserva` | Alta y reprogramación de turno |
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

Un turno reservado para hoy o mañana en una prestación con `tiempoToleranciaConfirmacion` alta nace con `fechaLimiteConfirmacion` ya vencida, y el primer barrido lo confirma automáticamente en minutos. **Es correcto y no requiere una rama nueva en el DTE.** Las reglas de tolerancia impiden que nazca vencida una fecha *anterior* a la de solicitud, pero no impiden que la de confirmación quede vencida en un turno reservado con muy poca antelación: eso es exactamente lo que se quiere, porque no tiene sentido pedirle al paciente que confirme un turno que es dentro de dos horas.

---

## 5. Reglas por módulo

### CONFIG — Clínica

- Instancia única. No hay alta ni baja, solo modificación.
- `horarioInicioAtencion < horarioFinAtencion`.
- `diasMaximosAnticipacionReserva ≥ 1`.
- Bajar `horarioFinAtencion` o subir `horarioInicioAtencion` **no** recorta las agendas ya generadas: la validación se aplica al crear o modificar slots. Lo mismo vale para bajar el horizonte de reserva: no da de baja ningún slot, solo deja de admitir reservas más allá del valor nuevo.

#### El horizonte de reserva reemplazó al rango de vigencia de agenda

**Se derogaron `diasMinimosVigenciaAgenda` y `diasMaximosVigenciaAgenda`.** En su lugar hay un solo parámetro, `diasMaximosAnticipacionReserva`, y la guarda se mudó del alta de agenda al alta de turno.

El mínimo se eliminó sin reemplazo: exigir que una agenda durara al menos N días no defendía ninguna invariante, y bloqueaba el caso legítimo del médico suplente que viene tres días.

El máximo no se eliminó, se **reinterpretó**. Estaba acotando el largo del período de agenda, pero lo que en realidad protegía era otra cosa: que nadie reservara un turno a un futuro demasiado lejano. Lo hacía por carambola — como no hay slots fuera del período, nadie podía reservar más allá — y de paso prohibía cargar agenda por adelantado, que es una operación sana y distinta.

| | Antes | Ahora |
|---|---|---|
| Parámetros | `diasMinimos` / `diasMaximosVigenciaAgenda` | solo `diasMaximosAnticipacionReserva` |
| Qué acotan | el largo del período de `AgendaMedico` | hasta cuándo se puede reservar |
| Dónde se evalúa | al configurar la agenda | **al crear el turno** |

**Queda simétrico con lo que ya existía.** La ventana de reserva tiene dos bordes y ahora los dos se comprueban en el mismo lugar:

```
AgendaHorariosDia.fechaLimiteReserva  ≤  ahora  ...  fechaHoraInicio ≤ ahora + diasMaximosAnticipacionReserva
        borde inferior                                        borde superior
   (no reservar demasiado cerca)                      (no reservar demasiado lejos)
```

**Y habilita generar agenda más allá del horizonte.** Si el patrón del médico es estable, se carga el semestre entero de una vez y los slots lejanos **se vuelven reservables solos** a medida que el horizonte móvil los alcanza. Antes había que regenerar la agenda cada tanto para abrir fechas nuevas.

**Lo que se pierde**: ya no hay un tope estructural a la cantidad de `AgendaHorariosDia` que un CU puede generar de una vez. Un error de carga puede crear decenas de miles de filas. Eso se cubre con un tope de generación en ECU-AGEN-1, pero como **guardarraíl operativo con advertencia**, no como regla de negocio.

**Si alguna vez hace falta granularidad**, el lugar natural es la prestación y no la clínica: un `tiempoMaximoAnticipacionSolicitud` simétrico al `tiempoToleranciaSolicitud` que ya existe, para que una consulta se agende a tres meses y un estudio a un año. No está en el modelo — `Prestacion` ya tiene siete `Duration` y no se justifica un octavo hasta que el caso aparezca.

### MED — Médicos y especialidades

- El alta de médico es atómica y crea **solo** el médico. La asignación de prestaciones es un CU aparte, invocado con `Ir a CU`.
- Un médico tiene una sola especialidad.
- **Solo se pueden asignar prestaciones cuya `especialidad` coincida con la del médico.**
- **La baja de `Especialidad` es restrictiva.** No se puede dar de baja una especialidad si existe alguna `Prestacion` **no deshabilitada** con esa especialidad, ni ningún `Medico` activo con esa especialidad. No hay cascada: el administrador tiene que deshabilitar o reasignar primero. Es la misma forma de la baja de `TipoIndicacionPrestacion`.
- **La baja de `Medico` es restrictiva por turnos vivos.** No se puede dar de baja un médico con algún `Turno` en estado vigente no final. Detalle y cómo se resuelve, en §6.

#### Asignación de prestaciones por vigencia

- `MedicoPrestacion` **no tiene baja lógica**. Se asigna creando una instancia con `fechaInicioVigencia`, y se desasigna cerrando `fechaFinVigencia`. Reasignar es siempre **crear una instancia nueva**, nunca reabrir la vieja.
- La regla ya no es unicidad sino **no solapamiento**: los períodos del mismo par `(medico, prestacion)` no se cruzan. Un médico puede haber atendido una prestación en 2025, haberla dejado, y volver a atenderla en 2026: son dos filas.
- Se puede asignar una prestación **No Publicada**; no se puede asignar una **Deshabilitada**. Así el médico queda preparado para atenderla desde el día en que se publica, sin depender del orden de carga.
- **Al desasignar**: los turnos ya reservados **se mantienen** y el médico los atiende, pero la fecha de corte tiene un piso duro — **`fechaFinVigencia` no puede ser anterior al `fechaHoraInicio` de ningún turno vivo** de ese médico para esa prestación. Si el administrador pide una fecha más temprana, el sistema la rechaza y le muestra la fecha del último turno comprometido. La salida es cancelar o reprogramar esos turnos primero.
- Se dan de baja los `AgendaHorariosDia` **libres** posteriores a `fechaFinVigencia`. Los ocupados no hace falta tocarlos: la regla anterior garantiza que no existan después del corte.
- `precioParticular` y `atiendeParticular` se modifican sobre la instancia **vigente**. El turno ya congeló su monto, así que el cambio no lo alcanza.

### PREST — Prestaciones e indicaciones

#### Estados

`Prestacion` **no tiene baja lógica**. Su ciclo de vida es un histórico de tramos sobre tres estados:

| Estado | Se ofrece para pedir turnos | Se puede asignar a un médico | Admite `AgendaHorariosDia` nuevos |
|---|---|---|---|
| **No Publicada** | no | sí | no |
| **Publicada** | sí | sí | sí |
| **Deshabilitada** | no | **no** | no |

- Toda prestación **nace No Publicada**. Es lo que antes era el borrador.
- *No Publicada* ⇄ *Publicada* es una transición **reversible y sin restricciones**: es la palanca de "por ahora no lo ofrecemos".
- *Deshabilitada* es **terminal e irreversible**. Es la baja: la prestación deja de mostrarse incluso para asignarla a los médicos.
- Cada transición cierra el tramo vigente con `fechaHoraFin = ahora` y abre uno nuevo, exactamente igual que `HistoricoEstadoTurno`. `motivo` es un texto libre de auditoría; ninguna guarda lo lee.
- **Despublicar no toca lo existente.** Los `AgendaHorariosDia` ya generados siguen ahí y los turnos ya reservados siguen vivos; lo único que cambia es que la prestación deja de listarse y no admite reservas nuevas. Es una pausa, no una cascada.

#### Deshabilitar es restrictivo, no cascada

Este es el cambio de fondo respecto de la v2, donde la baja de prestación cancelaba turnos en lote.

- **No se puede deshabilitar** una prestación si existe algún `Turno` suyo cuyo **estado vigente no sea final**.
- **No se puede deshabilitar** si existe algún `AgendaHorariosDia` futuro con `estaOcupada` en verdadero. Es redundante con la anterior, pero se comprueba igual: es la guarda barata.
- El camino correcto para retirar una prestación con turnos vivos es **despublicarla** primero — deja de entrar trabajo nuevo — y deshabilitarla cuando el último turno llegó a un estado final. Si hay urgencia, el administrador cancela los turnos uno por uno con `Cancelar Turno para Paciente` y `motivoCancelacion = DECISION_ADMINISTRATIVA`.
- **Se eliminó `MotivoCancelacion.BAJA_DE_PRESTACION`**: ya no hay ningún camino que lo produzca.

Una vez que la deshabilitación es admisible, arrastra: cierre de las `MedicoPrestacion` vigentes de esa prestación, baja de sus `AgendaHorariosDia` futuros libres, cierre de sus `IndicacionPrestacion` vigentes y baja de sus `ObraSocialPlanPrestacion`.

#### Modificación de la prestación

- **Se puede modificar siempre**, en cualquier estado y **con turnos vivos**: `nombre`, las duraciones y las siete tolerancias. La cadena de reglas de tolerancia se revalida en cada modificación. **Se derogó** la regla de la v2 que congelaba el `nombre` al habilitar.
- `codigo` es **inmutable** siempre (genera el `Turno.codigo`); cambiarlo es deshabilitar más dar de alta.

**Por qué no se restringe por turnos.** El `Turno` ya congela el monto y las siete fechas límite. Después de creado no vuelve a leer de la prestación ni las duraciones ni las tolerancias: las duraciones solo se usan al generar slots, y las tolerancias solo al calcular las fechas límite. Bloquear la edición mientras haya turnos vivos sería, en la práctica, "nunca editable" para las prestaciones con demanda, sin comprar ninguna garantía que el snapshot no dé ya.

**Pero hay dos campos cuyo cambio sí alcanza a algo existente, y no son los turnos: son los slots.** Ninguna de las dos guardas toca `AgendaHorariosDia` ocupados.

| Campo modificado | Efecto sobre `AgendaHorariosDia` futuros **libres** |
|---|---|
| `duracionMinima` / `duracionMaxima` | Se revalida que `horaHasta − horaDesde` siga cayendo en el rango nuevo. Los que queden fuera se informan y se dan de baja |
| `tiempoToleranciaSolicitud` | Se **recalcula** `fechaLimiteReserva = inicio del slot − tolerancia nueva`. Los que queden con el plazo de reserva ya vencido se dan de baja |

La segunda es la importante: `fechaLimiteReserva` se calcula al generar la agenda y se persiste en el slot. Sin el recálculo, subir la tolerancia de solicitud dejaría toda la agenda futura con un plazo desactualizado **en silencio**, y el sistema seguiría aceptando reservas que ya no deberían entrar. Es el mismo chequeo que ECU-AGEN-8 hace al cambiar la prestación de un slot.

En los dos casos el CU muestra la cantidad de slots afectados antes del `Confirmar`. Los ocupados no se tocan nunca: cada uno tiene un turno detrás que ya congeló sus propias fechas.

#### Indicaciones

- Una indicación siempre pertenece a una prestación. Dos prestaciones no comparten indicación: se carga una vez para cada una.
- **`IndicacionPrestacion` no tiene baja lógica: se gobierna por vigencia.** Alta con `fechaInicioVigencia`, retiro cerrando `fechaFinVigencia`. Las dos admiten fecha futura.
- **La modificación es restrictiva**: no se puede modificar una indicación mientras exista alguna `IndicacionPrestacionTurno` suya cuyo `Turno` tenga **estado vigente no final**.

El predicado se evalúa por `IndicacionPrestacionTurno`, no por "turnos de la prestación". Es más preciso y más permisivo a la vez: una indicación agregada la semana pasada no fue copiada por los turnos reservados el mes pasado, así que esos turnos no la bloquean.

**Por qué la restricción es la pieza central, y no un snapshot.** `IndicacionPrestacionTurno` **no copia** el texto: lo lee por navegabilidad. En la v2 eso obligaba a que la indicación fuera inmutable, porque editarla reescribía lo que ve un paciente en un turno vivo. Ahora hay dos cosas que lo vuelven seguro sin duplicar datos:

1. **La fila nunca desaparece.** Al pasar al eje de vigencia, retirar una indicación cierra `fechaFinVigencia`; no hay `deletedAt` ni borrado físico. La navegación desde un turno viejo siempre resuelve.
2. **No se puede modificar con turnos vivos.** Ningún turno en estado no final puede ver cambiar el texto bajo sus pies.

**Cómo se cambia entonces una indicación que sí tiene turnos vivos: se programa el relevo.** No se edita — se cierra la vieja y se abre la nueva en la misma fecha de corte.

```
IndicacionPrestacion A : "Ayuno de 8 horas"   [inicio: 01-01]  → fechaFinVigencia = 01-10
IndicacionPrestacion B : "Ayuno de 12 horas"  [inicio: 01-10]  → fechaFinVigencia vacía
```

Los períodos del par `(prestacion, nombre)` no se solapan, así que el relevo es limpio y las dos conviven en la tabla sin ambigüedad.

**La fecha de corte conviene ubicarla más allá del horizonte de reserva** (`Clinica.diasMaximosAnticipacionReserva`). Si no, quedan turnos ya creados con fecha posterior al corte que van a seguir mostrando la indicación vieja, porque nacieron antes de que el relevo se programara. Con la fecha de corte fuera del horizonte no puede existir ningún turno de ese lado.

- **Al crear un turno se le asocian las indicaciones vigentes en `Turno.fechaHoraInicio`**, no las vigentes en el instante de creación. Es la elección semánticamente correcta: la indicación es lo que el paciente tiene que cumplir para **su fecha de turno**, así que si el relevo ya está programado para antes de esa fecha, le corresponde la nueva.
- **Lo que se acepta al no copiar**: la restricción mira turnos en estado **no final**, así que una indicación con solo turnos finalizados o cancelados sí se puede modificar, y eso reescribe lo que muestra el registro histórico de esos turnos. Es un costo asumido a cambio de no duplicar texto y de que corregir un error se propague en vez de quedar congelado.
- No se pueden dar de alta ni modificar indicaciones de una prestación **Deshabilitada**. Deshabilitar la prestación **cierra la `fechaFinVigencia`** de sus indicaciones vigentes; ya no las da de baja.
- **`TipoIndicacionPrestacion` mantiene su baja restrictiva**: no se puede dar de baja si hay `IndicacionPrestacion` **vigentes** que lo referencian. Sus `codigo` y `nombre` son editables.

### AGEN — Agenda

- Una agenda está **vigente** cuando hoy cae entre `fechaInicioVigencia` y `fechaFinVigencia`. Queda derogada la regla anterior de "agenda activa = `fechaFinVigencia` vacía".
- **La duración del período es libre.** Un día para un suplente, un semestre para un médico de planta. La restricción entre `diasMinimosVigenciaAgenda` y `diasMaximosVigenciaAgenda` quedó derogada y esos dos parámetros ya no existen.
- Los períodos de un mismo médico no se solapan. Con la restricción de duración fuera, **esta es la única regla estructural que le queda al período**, así que pasa a ser la que más importa validar.
- **El período se puede extender más allá del horizonte de reserva.** Los slots existen desde que se generan, pero solo son reservables cuando el horizonte móvil los alcanza.
- El **patrón semanal es entrada del CU**: se usa para generar los `AgendaHorariosDia` y no se persiste. **Es una forma de cargar los días, no la única**: se pueden dar de alta días sueltos, y no hace falta que todos los días del período tengan `AgendaHorariosDia` generados.
- ECU-AGEN-1 lleva un **tope de generación** — cuántos `AgendaHorariosDia` produce una sola ejecución — como advertencia operativa antes del `Confirmar`. No es una regla de negocio: es el guardarraíl que reemplaza al tope estructural que daba `diasMaximosVigenciaAgenda`.
- Cálculo de slots: `slot(n) = horaDesde + n × duracionTurno`. La duración debe dividir exactamente al bloque.
- La duración de cada slot debe caer entre `duracionMinima` y `duracionMaxima` de su prestación.
- Cada slot exige una `MedicoPrestacion` **vigente en la fecha del slot** entre el médico de la agenda y la prestación del slot. No alcanza con que exista: hay que evaluar el período contra `AgendaHorariosDia.fecha`.
- Cada slot exige además que la prestación esté **Publicada**. Una prestación *No Publicada* se puede asignar a un médico pero no genera horarios.
- Los bloques de un mismo día no se superponen.
- **Un slot ocupado no se puede dar de baja**: primero hay que cancelar o reprogramar el turno.
- Al reemplazar un slot, la `fechaLimiteReserva` recalculada con la tolerancia de solicitud de la prestación nueva debe quedar posterior a ahora.
- Hace falta una consulta de **médicos activos sin agenda vigente**, como criterio de `Consultar Agendas`.

### PACIENTE

- `dni` y `numeroTelefono` únicos entre activos. El teléfono es la clave de identificación del canal de WhatsApp.
- En el canal chatbot el teléfono lo provee la API de WhatsApp; en el canal interno lo ingresa el administrador.
- **El paciente no puede cambiarse el `numeroTelefono` a sí mismo** desde el chatbot: ese cambio lo hace el administrador.
- Si no declara ninguna cobertura, se atiende como particular.
- `ObraSocialPaciente` es inmutable: alta y baja lógica. Cambiar de plan es baja más alta. **Solo se puede asociar un `Plan` Publicado**, y la cobertura se da de baja en cascada cuando su plan se deshabilita.

#### Archivos

- `Archivo` es N→1 `Paciente` (**obligatoria**) y N→1 `Turno` (**opcional**). Todo archivo pertenece siempre a un paciente; si además se cargó en el contexto de un turno concreto — un estudio previo, un resultado — queda asociado a él.
- **Regla cruzada**: si `turno` está presente, `turno.paciente` tiene que ser el mismo `paciente` del archivo. Es la validación que justifica la FK redundante a `Paciente`; sin ella un archivo podría colgar de un turno de otra persona.
- El binario **no vive en la base**: la tabla guarda `ubicacion`, la clave del objeto en el almacenamiento. `tipoContenido` se valida contra una lista blanca de MIME types y `tamanioBytes` contra un máximo configurado.
- Baja lógica. La baja del `Paciente` da de baja sus archivos en cascada. `Turno` no tiene baja, así que un turno cancelado conserva los suyos.

### OS — Obras sociales

**El histórico de estados va solo en `Plan`, no en `ObraSocial`.** `Plan` es lo que el paciente elige al declarar cobertura y lo que cuelga de `ObraSocialPlanPrestacion`; la obra social es un agrupador. Ponerle estados a las dos duplicaría la regla de derivación — habría que decidir qué pasa con un plan publicado de una obra social despublicada — sin ganar poder expresivo. `ObraSocial` conserva su baja lógica y opera sobre sus planes en cascada.

- Multiplicidad `ObraSocial 1 → 0..N Plan`. El alta de obra social puede crear, opcionalmente, sus planes iniciales en el mismo `Confirmar` (si no se envían, la obra social nace sin planes y se agregan después). Cada plan nace **No Publicado**.
- La FK `Plan → ObraSocial` es **inmutable** después del alta.
- `codigo` y `nombre` son editables en ambas.

#### Estados del plan

Simétrico al de prestación, con las instancias *No Publicado*, *Publicado*, *Deshabilitado*:

| Estado | Se ofrece para declarar cobertura | Cubre turnos nuevos | Admite `ObraSocialPlanPrestacion` |
|---|---|---|---|
| **No Publicado** | no | no | sí |
| **Publicado** | sí | sí | sí |
| **Deshabilitado** | no | no | no |

- *No Publicado* ⇄ *Publicado* es reversible. *Deshabilitado* es terminal.
- **No se puede deshabilitar un plan** si existe algún `Turno` cuya `obraSocialPaciente` apunte a ese plan y cuyo estado vigente no sea final. Misma lógica restrictiva que en prestación: **se derogó** la regla de la v2 de que "las bajas de obras sociales no cancelan turnos, solo bloquean el futuro" — ahora directamente no se dejan hacer mientras haya turnos vivos. **Esta es la única precondición**: se elimina la regla "no se puede deshabilitar el último plan no deshabilitado de una obra social activa" — una obra social puede terminar con todos sus planes deshabilitados sin que eso la afecte.
- Deshabilitar un plan da de baja sus `ObraSocialPlanPrestacion` y las `ObraSocialPaciente` que lo referencian.
- **La baja de `ObraSocial` es restrictiva por transitividad**: no se puede si alguno de sus planes no se puede deshabilitar. Si se puede, deshabilita en cascada todos sus planes no deshabilitados.

#### Coberturas

- `ObraSocialPlanPrestacion`: `porcentajeCobertura` y `coseguro` son editables. Coherencia según `modalidadCobertura`:
  - `TOTAL` → `porcentajeCobertura = 100` y `coseguro = 0`
  - `CARGO_FIJO` → `coseguro ≥ 0`, es el monto que paga el paciente
  - `PORCENTUAL` → `0 ≤ porcentajeCobertura ≤ 100`, el paciente paga la parte no cubierta
- Para dar de alta o modificar una cobertura, ni el `Plan` ni la `Prestacion` pueden estar deshabilitados.

### TURN — Turnos

- **La ventana de reserva tiene dos bordes y los dos se comprueban al crear el turno**: `ahora < AgendaHorariosDia.fechaLimiteReserva` por abajo, y `fechaHoraInicio ≤ ahora + Clinica.diasMaximosAnticipacionReserva` por arriba. El segundo es nuevo: reemplaza al tope que antes daba, indirectamente, el largo máximo del período de agenda.
- El par `(medico, prestacion)` se valida buscando una `MedicoPrestacion` **vigente en `fechaHoraInicio` del turno**, no simplemente activa. El esquema ya no lo garantiza, porque `Turno` no apunta a `MedicoPrestacion`.
- La `Prestacion` tiene que estar **Publicada**. Si la cobertura es por obra social, el `Plan` también tiene que estar **Publicado**; si no lo está, el turno se registra como `PARTICULAR`.
- Al crear el turno se genera una `IndicacionPrestacionTurno` por cada `IndicacionPrestacion` **vigente en `fechaHoraInicio`** de la prestación. **No se copia texto**: el vínculo alcanza, y `nombre`, `descripcion` y `requiereValidacion` se leen por navegabilidad cada vez que se muestran.
- El turno nace en `Espera de Validación` si alguna de esas indicaciones tiene `requiereValidacion` en verdadero; si no, en `Pendiente`.
- Sale de `Espera de Validación` cuando ninguna `IndicacionPrestacionTurno` **activa y obligatoria** queda con `fechaHoraValidacion` vacía.
- Un `Turno` puede tener `Archivo` asociados. No condicionan ninguna transición de estado.
- `AgendaHorariosDia.estaOcupada` pasa a verdadero al crear el turno y vuelve a falso al entrar en `Cancelado` o en `Reprogramado`.
- Un turno `Confirmado` **no se puede reprogramar**: hay que cancelarlo y dar de alta uno nuevo. Es una decisión de negocio deliberada.
- Al paciente se le expone el `codigo`, nunca el `id`.

### USER y AUTZ

- `Usuario.mail` es credencial y **no se copia** del email de contacto de la persona.
- Unicidad de `mail` entre activos; unicidad global de `medico` y de `admin`.
- **La asociación `Usuario`–`Medico`/`Admin` es unidireccional.** La FK vive en `usuario` — es el único lado que sabe a quién pertenece la credencial. `Medico` y `Admin` no navegan de vuelta hacia su `Usuario`; si algún CU necesita esa dirección, es una consulta de repositorio (`UsuarioRepository.findByMedicoId` / `findByAdminId`), no una relación de dominio.
- **Crear un `Usuario` crea también la instancia de la persona, en la misma operación, según el rol elegido**: rol médico da de alta un `Medico` con los datos recibidos; rol admin da de alta un `Admin`. No es un alta en dos pasos con dos aggregate roots separados — es un único caso de uso que, puertas adentro, escribe las dos tablas.
- **La baja se desentiende del modelo: `Usuario` y la persona (`Medico`/`Admin`) se dan de baja cada uno por su cuenta, sin cascada en ningún sentido.** Dar de baja el `Usuario` no toca a la persona, y dar de baja la persona no toca su `Usuario`. El `Usuario` se puede dar de baja en cualquier momento. Si un caso de uso necesita que las dos bajas ocurran juntas (p. ej. offboarding completo), lo orquesta explícitamente el `App` llamando a los dos `DomainService`, no es un comportamiento implícito de la relación.
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

## 6. Bajas, deshabilitaciones y cascadas

En la v2 la regla de fondo era "toda baja es lógica y casi todas cascadean". En la v3 el criterio se invierte para el catálogo que sostiene turnos: **si hay turnos vivos, la operación se rechaza**. La cascada queda reservada para lo que no compromete a un paciente.

| Clase | Eje | Forma de la baja |
|---|---|---|
| `Prestacion` | estados | **restrictiva** por turnos vivos, después cascada de catálogo |
| `Plan` | estados | **restrictiva** por turnos vivos |
| `ObraSocial` | baja lógica | **restrictiva** por transitividad, después cascada sobre sus planes |
| `Especialidad` | baja lógica | **restrictiva** por prestaciones y médicos |
| `TipoIndicacionPrestacion` | baja lógica | **restrictiva** por indicaciones activas |
| `Medico` | baja lógica | **restrictiva** por turnos vivos, después cascada de agenda y `MedicoPrestacion` |
| `Paciente` | baja lógica | cascada sobre coberturas y archivos |
| `Rol` | baja lógica | cascada sobre `UsuarioRol` vigentes |

### Prestación — deshabilitar

**Precondición restrictiva** — se comprueba antes de cualquier escritura:

1. Ningún `Turno` de la prestación con estado vigente no final.
2. Ningún `AgendaHorariosDia` futuro con `estaOcupada` en verdadero.

Si alguna falla, el CU termina por excepción y le muestra al administrador cuántos turnos lo impiden y hasta qué fecha llegan. **No hay confirmación que lo saltee**: la salida es despublicar y esperar, o cancelar los turnos a mano.

Cumplidas las precondiciones, arrastra en este orden:

1. Cierre de `fechaFinVigencia = ahora` en las `MedicoPrestacion` vigentes de esa prestación.
2. Baja de los `AgendaHorariosDia` futuros libres de esa prestación.
3. Cierre de `fechaFinVigencia = ahora` en sus `IndicacionPrestacion` vigentes.
4. Baja de sus `ObraSocialPlanPrestacion`.
5. Cierre del tramo vigente de `HistoricoEstadoPrestacion` y apertura de uno nuevo en *Deshabilitada*.

### Plan — deshabilitar

**Precondición restrictiva** (única): ningún `Turno` con `obraSocialPaciente.plan` igual a
ese plan y estado vigente no final.

Cumplida, arrastra: baja de sus `ObraSocialPlanPrestacion`, baja de las `ObraSocialPaciente` que lo referencian, y transición a *Deshabilitado*.

### Obra social — baja

Restrictiva **por transitividad**: se evalúa la precondición de deshabilitación de cada uno de sus planes. Si alguno la incumple, la baja se rechaza.

Cumplidas, arrastra: deshabilitación en cascada de todos sus planes no deshabilitados — con sus propias cascadas — y `deletedAt` en la obra social.

### Especialidad — baja

Restrictiva. No se da de baja si existe alguna `Prestacion` no deshabilitada o algún `Medico` activo con esa especialidad. No arrastra nada: el administrador reasigna o deshabilita primero.

### Médico — baja planificada

No se opera sobre el médico: se opera sobre la agenda.

- Si la salida coincide con el fin de vigencia actual: no hacer nada, dejar vencer.
- Si se va antes: adelantar `fechaFinVigencia`, dar de baja los `AgendaHorariosDia` posteriores a la fecha de corte, y resolver los turnos que caigan después del nuevo corte.
- El día siguiente al último turno, ya sin turnos vivos pendientes, se ejecuta la baja del `Medico` (ver más abajo), que solo arrastra `MedicoPrestacion`.

**No existe fecha de baja futura como atributo.** `deletedAt` distinto de vacío significa siempre "ya está de baja".

### Médico — baja

**Restrictiva por turnos vivos**, igual que el resto del catálogo (`Prestacion`, `Plan`, `Especialidad`, `TipoIndicacionPrestacion`). Deja de ser la excepción de la v3 original que cancelaba turnos en lote: se alinea con el criterio de fondo de este apartado — si hay turnos vivos, la operación se rechaza.

**Precondición restrictiva** — se comprueba antes de cualquier escritura:

1. Ningún `Turno` del médico con estado vigente no final.

Si falla, el CU termina por excepción y le muestra al administrador cuántos turnos lo impiden. **No hay confirmación que lo saltee.**

Cumplida la precondición, baja atómica en una sola transacción: `Medico` + `AgendaMedico` vigente (con sus `AgendaHorariosDia` en cascada) + cierre de `fechaFinVigencia` de sus `MedicoPrestacion` vigentes.

**Cómo se llega a cumplir la precondición cuando hay turnos vivos: `Cancelar Turnos de Médico`, un CU aparte.** Cancela en lote todos los turnos no finales del médico (`motivoCancelacion = BAJA_DE_MEDICO`), sin dar de baja nada más. Es una operación independiente que el administrador invoca explícitamente antes de reintentar la baja — no un paso implícito de ella. (Pendiente de implementar; queda anotado como CU futuro.)

**El `Usuario` del médico queda fuera de todo esto.** La baja se desentiende del modelo: `Baja de Médico` no toca su `Usuario`, y `Baja de Usuario Interno` no toca al `Medico` — solo le quita el acceso al panel, dejándolo activo para seguir atendiendo. Ese usuario dado de baja no bloquea un alta futura, porque `Usuario.medico` es único **globalmente**: hay que reactivar el que existe, no crear otro. Si un offboarding real necesita dar de baja las dos cosas, son dos operaciones explícitas, no una cascada implícita de la relación.

### Paciente

Baja lógica. Arrastra sus `ObraSocialPaciente` y sus `Archivo`. No cancela turnos: los turnos vivos de un paciente dado de baja se resuelven a mano.

### Rol

Cierra la `fechaFinVigencia` de sus `UsuarioRol` vigentes. No se pueden dar de baja los roles de sistema.

---

## 7. Ciclo de vida del turno

El detalle está en `modelo_dte_turno_v3.json` y en `dte_turno.svg`. Lo que el backend tiene que garantizar:

- **Cada transición** cierra el `HistoricoEstadoTurno` vigente con `fechaHoraFin = ahora` y crea uno nuevo con `fechaHoraInicio = ahora`. Los tramos son contiguos y no se solapan.
- Un turno tiene **a lo sumo un** `HistoricoEstadoTurno` con `fechaHoraFin` vacío.
- Un estado es final porque ese tramo queda abierto para siempre. No hay atributo que lo declare.
- La duración **real** del turno es la duración del tramo `En Curso`. La **planificada** sale del `AgendaHorariosDia`.
- Las transiciones automáticas se registran con identidad de sistema en `Auditable.createdBy`, lo que permite distinguir en reportes la confirmación manual de la automática.

### Reprogramación

Un solo CU atómico: un `Confirmar`, una transacción. Orden de ejecución obligatorio:

1. Validar y **crear el turno nuevo**.
2. Copiar las validaciones ya cumplidas: para cada `IndicacionPrestacionTurno` cuya `IndicacionPrestacion` ya estaba validada en el turno origen, se copian `fechaHoraValidacion` y `validadoPor`. Las indicaciones nuevas quedan en vacío.
3. Recién después, cerrar el viejo como `Reprogramado` y liberar su slot.

Recalcula todo: monto, fechas límite e indicaciones. Misma prestación siempre; si cambia la prestación es un alta común. Si la prestación no está **Publicada**, el plan no está **Publicado**, la `ObraSocialPlanPrestacion` está de baja, o no hay slot disponible, no se reprograma y el turno queda en `Pendiente`. El monto nuevo se muestra antes del `Confirmar`. `turnoOrigen` registra la cadena.

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
base = MedicoPrestacion vigente en fechaHoraInicio de (medico, prestacion).precioParticular

si tipoCobertura = PARTICULAR:
    requiere MedicoPrestacion.atiendeParticular = verdadero
    montoAPagar = base

si tipoCobertura = OBRA_SOCIAL:
    requiere obraSocialPaciente.plan Publicado
    cobertura = ObraSocialPlanPrestacion activa de (obraSocialPaciente.plan, prestacion)
    si el plan no está Publicado o no existe cobertura activa -> el turno se registra como PARTICULAR
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
| **Unicidad entre no deshabilitadas** | regla del `DomainService`, resolviendo el estado vigente desde el histórico (`existsBy...EstadoVigenteNot`) | no se refuerza en BD: sin columna `estado_actual`, no hay índice único parcial; queda solo en la capa de aplicación |
| **Baja restrictiva** | regla del `DomainService`: contar dependientes antes de escribir | no expresable; el `ON DELETE RESTRICT` no aplica porque la baja es lógica |
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
- [ ] Las reglas de validación de la clase están como `CHECK` con nombre `ck_<tabla>_<regla>` y como validación de aplicación.
- [ ] Las relaciones respetan la navegabilidad del diagrama: sin FK redundante donde la asociación debe derivarse.
- [ ] Los campos «bajable» están declarados en la clase, sin heredar de ninguna superclase.
- [ ] La clase usa **un solo** eje de retiro: baja lógica, o vigencia, o estados. Nunca dos.
- [ ] Los enums se persisten como texto, no como ordinal.
- [ ] Las relaciones a entidades «bajable» validan que el destino esté activo; las relaciones a entidades «con estados» validan el estado vigente; las relaciones a entidades «con vigencia» validan el período contra la fecha relevante, no contra `ahora`.
- [ ] Si la baja es restrictiva, la consulta de dependientes corre **antes** de cualquier escritura y el CU tiene su excepción declarada.
- [ ] Toda constraint de Bean Validation tiene su equivalente en el changelog, o está documentado por qué no aplica.

---

## 11. Anexo — Reglas de implementación

Secciones portadas del antiguo `REGLAS-NEGOCIO.md`, que quedó absorbido por este documento.
Actualizadas al modelo v3: donde la regla vieja decía «activa» y ahora corresponde «vigente» o
«Publicada», está corregido.

### 11.1 Resolución del médico del contexto

Método compartido por Configurar Agenda, Cancelar Turno, Reprogramar Turno, Consultar Turnos y todo
CU que opere sobre un médico:

```
if (usuarioAutenticado.medico != null) -> operar sobre ese medico, ignorar el parámetro
else                                   -> el medico viene por parámetro Y se exige el permiso
```

Un admin puede operar sobre el médico que seleccione, si tiene el permiso correspondiente.

### 11.2 `Buscar` vs. `Leer`

- `Leer` requiere navegabilidad en la dirección correcta (es un `getter` sobre una relación).
- `Buscar` es una consulta de repositorio, filtrable por cualquier atributo o relación sin importar
  la dirección.

Importa para `IndicacionPrestacionTurno`, que **lee** el texto de `IndicacionPrestacion` por
navegabilidad. La baja de `Medico` **no** necesita leer su `Usuario`: quedaron desacoplados y sin
cascada (ver §5 USER y AUTZ), así que ya no es un caso que dependa de navegabilidad bidireccional.

### 11.3 Funciones por módulo

**AGEN**

| Función | Efecto |
|---|---|
| `configurarAgendaMedico` | Crea `AgendaMedico` + N `AgendaHorariosDia`. Admite patrón semanal o días sueltos |
| `modificarAgendaVigente` | Da de baja `AgendaHorariosDia` libres y genera nuevos. Permite agregar fechas nuevas directamente, sin entidad de día intermedia |
| `excluirDiaAgenda` | Baja de los `AgendaHorariosDia` de la fecha + cancelación de turnos del día |
| `adelantarFinVigenciaAgenda` | Corta `fechaFinVigencia` + baja de días posteriores + cancelaciones |
| `consultarAgendaMedico` | Consulta de días, horarios y turnos en un rango |
| `consultarTurnosDisponibles` | Slots libres para prestación / médico / franja / rango, **dentro del horizonte de reserva** |
| `consultarMedicosSinAgendaVigente` | Médicos activos sin agenda o con agenda por vencer |

**PREST** — a las de ABM se suman `publicarPrestacion`, `despublicarPrestacion`,
`deshabilitarPrestacion` (restrictiva), `modificarIndicacionPrestacion` (restrictiva) y
`programarBajaIndicacionPrestacion`.

**OS** — ídem con `publicarPlan`, `despublicarPlan` y `deshabilitarPlan`.

### 11.4 Alta de turno — secuencia de efectos

Todo en una sola transacción:

1. Validar disponibilidad del `AgendaHorariosDia` (regla de disponibilidad de AGEN), incluidos **los dos
   bordes** de la ventana de reserva.
2. Validar que la `Prestacion` esté **Publicada**.
3. Validar el par (médico, prestación) → `MedicoPrestacion` **vigente en `fechaHoraInicio`**.
4. Resolver cobertura —`Plan` **Publicado**, si no el turno se registra como `PARTICULAR`— y calcular
   `montoAPagar`.
5. Calcular `fechaHoraInicio` y las siete fechas límite más el recordatorio.
6. Crear `Turno`.
7. Crear una `IndicacionPrestacionTurno` por cada `IndicacionPrestacion` **vigente en
   `Turno.fechaHoraInicio`**, con `fechaHoraValidacion = null`. **No se copia texto.**
8. `AgendaHorariosDia.estaOcupada = true`.
9. Crear `HistoricoEstadoTurno` en `Espera de Validación` o en `Pendiente`.
10. Notificar (NOTIF-1).

### 11.5 Mapa evento → notificación

Los IDs de CU son los de la v3 de las especificaciones.

| Evento | Notificación |
|---|---|
| Registrar turno (TURN-3, TURN-9) | NOTIF-1 Turno Registrado |
| Confirmar turno (TURN-4, TURN-10, TURN-20) | NOTIF-2 Turno Confirmado |
| Cancelar turno (TURN-5, TURN-11, TURN-15 y cascadas) | NOTIF-3 Turno Cancelado |
| Reprogramar turno (TURN-6, TURN-12, TURN-16) | NOTIF-4 Turno Reprogramado |
| Rechazar validación (TURN-13) / Vencer validación (TURN-21) | NOTIF-5 Turno No Validado |
| Recordatorio de confirmación vencido (NOTIF-6) | NOTIF-6 Recordar Confirmación |

**Ya no hay notificación disparada por baja de prestación**: deshabilitar es restrictivo y no cancela
turnos.

### 11.6 Spring Security

Los artefactos de `UserDetails` **no van al modelo conceptual**; se derivan en implementación:

```java
isEnabled()               -> deletedAt == null
getAuthorities()          -> permisos de los Rol de los UsuarioRol vigentes (en vivo, sin snapshot)
getUsername()             -> mail
getPassword()             -> passwordHash
isAccountNonExpired()     -> true
isAccountNonLocked()      -> true
isCredentialsNonExpired() -> true
```

Nunca almacenar ni loguear contraseñas en claro. En el login, no revelar cuál de los dos datos falló.

### 11.7 EXT — Integraciones externas

- `Invocar Servicio` se reserva **exclusivamente** para sistemas externos.
- El envío por WhatsApp valida el formato del número, elige plantilla preaprobada cuando el sistema
  inicia la conversación, reintenta con espera creciente ante indisponibilidad, y devuelve
  `identificadorEnvio` o la falla al llamador.
- La falla se registra en bitácora y **no revierte** la operación de negocio del CU llamador.

### 11.8 Cabos abiertos

Fuera de alcance en esta versión. No implementar sin decisión previa.

- **`Medico N→1 Especialidad`**: si aparecen médicos con dos especialidades, hay que cambiar la
  multiplicidad. Afecta también a la baja restrictiva de `Especialidad`.
- **Valores del enum `MotivoCancelacion`**: los cinco vigentes están a confirmar.
- **Unicidad entre no deshabilitadas**: decidido — se valida **solo en el `DomainService`** (consulta
  al histórico vigente), sin columna derivada ni índice único parcial de BD. Ver §3.2.
- **Publicar una prestación sin médicos asignados**: ¿excepción dura o advertencia?
- **Archivos por el canal del chatbot**: hoy solo los carga el personal interno.
- **Turnos vivos de un paciente dado de baja**: la baja de paciente no los cancela ni se bloquea por
  ellos. Con el criterio restrictivo del v3, convendría decidirlo.
- **`tiempoMaximoAnticipacionSolicitud` por prestación**: hoy el horizonte de reserva es global
  (`Clinica.diasMaximosAnticipacionReserva`). Si hiciera falta que una consulta se agende a tres
  meses y un estudio a un año, el lugar es `Prestacion`.

**Resueltos en el v3, ya no son cabos**: la suspensión reversible de `Prestacion` y `Plan` existe y
son los estados *Publicada* / *No Publicada*; la configuración versionada por prestación se resolvió
con `HistoricoEstadoPrestacion` y con la vigencia de `IndicacionPrestacion`.
