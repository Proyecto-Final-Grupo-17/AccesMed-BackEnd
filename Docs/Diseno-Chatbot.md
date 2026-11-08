# Diseño Chatbot

> **Estado:** etapa 1 (Análisis) redactada el 2026-11-07. La etapa 2 (Diseño: prompt, tools,
> estructura del flow) queda pendiente y se apoya en las decisiones abiertas de §1.9.
>
> **Para qué sirve este documento.** Traducir los casos de uso del actor Paciente en los
> **pasos de una conversación de WhatsApp**: qué escribe el paciente, qué tiene que razonar el
> agente (LLM), qué le pide al backend y qué espera recibir, y cómo puede fallar.
>
> **Fuentes** (de mayor a menor autoridad cuando se contradicen):
>
> 1. Código del back (implementado y testeado): `Application/AgenteApp` y DomainServices;
>    contrato en `Docs/Features/Chatbot.md`.
> 2. `Docs/Dominio/modelo_dte_turno.json`: diagrama de transición de estados del Turno.
> 3. `Docs/Dominio/dominio-reglas-validaciones.md` (v3): reglas de negocio.
> 4. `Docs/Dominio/Turnos Paciente.docx`: Especificaciones de CU (ECU-TURN-1..6 y 23). **Son
>    previas a la v3 de las reglas**: donde contradicen a 1, 2 o 3, se marcan como desactualizadas.
>
> Única excepción a esa jerarquía: la reprogramación, donde el código se alineó al DTE
> (2026-11-07: solo se reprograma un turno Pendiente).
>
> Los CU de Paciente (Registrarse, Modificar datos, Darse de baja) **no tienen
> especificación escrita**: acá se interpretan a partir de las reglas de negocio y del código.

---

## 1. Análisis

### 1.1 Criterios de lectura

Las especificaciones relatan un diálogo **actor ↔ sistema** con subpasos internos. Para el
chatbot se conserva solo lo que ocurre en la conversación o cruza una frontera:

| Se conserva | Se descarta |
|-------------|-------------|
| Lo que escribe o elige el paciente | "Buscar en la BD…", "Leer…", "Crear instancia…", "Guardar cambios" |
| Lo que el agente tiene que **razonar** (interpretar, mapear texto a una clave, decidir) | El detalle de cómo el back valida (vive en los DomainServices) |
| Cada **consulta al back** (Custom Tool) y la **salida esperada** (éxito o `codigo` de error) | Cálculos internos (límites, monto, estado inicial) |
| Lo que el agente le muestra al paciente | |

**Quién ejecuta cada paso:**

| Etiqueta | Significado |
|----------|-------------|
| **Paciente** | Mensaje del paciente por WhatsApp. |
| **Agente** | Paso de razonamiento o de conversación del LLM: interpretar, mapear a una clave de negocio, elegir qué responder, pedir un dato. **No toca datos de negocio.** |
| **Back** | Custom Tool → endpoint `/accesmed-api/Agente/*`. Se indica la tool y lo que devuelve. |
| **Canal** | Lo hace el pasamanos (Chatwoot ↔ back ↔ Flowise) antes o después del LLM, sin intervención del agente. |

**Procedencia de cada flujo alterno:** **Spec** (está en la especificación), **Regla** (sale
de las reglas de negocio o del DTE y la spec no lo tiene), **Código** (lo valida el back y no
figura en ninguna de las dos), **Canal** (lo resuelve el pasamanos).

### 1.2 Identificadores

Se reemplazan los ID de las especificaciones por ID nemotécnicos con prefijo `CB-` (Chatbot).
Las excepciones se numeran dentro de cada CU (`CB-SOL.E3`), y las transversales con prefijo
`EX-`. Los bloques que se repiten en varios CU se extraen como **fragmentos** (`F-`).

| ID nuevo | Caso de uso | ID original |
|----------|-------------|-------------|
| `CB-INF` | Consultar información general | ECU-TURN-1 |
| `CB-DISP` | Consultar turnos disponibles | ECU-TURN-2 |
| `CB-SOL` | Solicitar turno | ECU-TURN-3 |
| `CB-CAN` | Cancelar turno | ECU-TURN-4 |
| `CB-REP` | Reprogramar turno | ECU-TURN-5 |
| `CB-CONF` | Confirmar turno | ECU-TURN-6 |
| `CB-OPE` | Solicitar operador humano | ECU-TURN-23 |
| `CB-REG` | Registrarse | ECU-PAC-1 (sin spec) |
| `CB-MOD` | Modificar mis datos | ECU-PAC-2 (sin spec) |
| `CB-BAJA` | Darme de baja | ECU-PACIENTE-3 (sin spec) |

| Fragmento | Qué resuelve | Aparece en |
|-----------|--------------|------------|
| `F-ID` | Identificar al paciente por su teléfono | Paso 3 de todas las specs salvo TURN-1 y TURN-23 |
| `F-OFERTA` | Explorar especialidades → prestaciones y médicos | CB-INF, CB-DISP, CB-SOL |
| `F-HORARIOS` | Buscar horarios disponibles de un médico y una prestación | CB-DISP, CB-SOL, CB-REP |
| `F-MISTURNOS` | Listar los turnos propios que admiten una acción y elegir uno | CB-CAN, CB-REP, CB-CONF |

### 1.3 Catálogo de casos de uso

| ID | Objetivo del paciente | ¿Requiere estar registrado? | ¿Modifica el dominio? | Tools (endpoints) |
|----|-----------------------|-----------------------------|-----------------------|-------------------|
| `CB-INF` | Conocer la clínica y su oferta | **No** | No | `getClinica`, `listEspecialidades`, `getOfertaEspecialidad` |
| `CB-DISP` | Ver qué horarios hay | **Sí, según la spec** (el endpoint no lo exige, ver §1.9 D2) | No | `F-OFERTA` + `listHorariosDisponibles` |
| `CB-SOL` | Reservar un turno | Sí | Sí: crea Turno | `F-HORARIOS` + `createTurno` |
| `CB-CAN` | Liberar un turno propio | Sí | Sí: Turno → Cancelado | `listTurnos(CANCELABLE)` + `cancelTurno` |
| `CB-REP` | Mover un turno propio a otro horario | Sí | Sí: turno nuevo + viejo → Reprogramado | `listTurnos(REPROGRAMABLE)` + `F-HORARIOS` + `reprogramTurno` |
| `CB-CONF` | Asegurar la asistencia | Sí | Sí: Turno → Confirmado | `listTurnos(CONFIRMABLE)` + `confirmTurno` |
| `CB-OPE` | Hablar con una persona | **No** | No (solo el estado de la conversación en Chatwoot) | ninguna: token `—HANDOFF—` |
| `CB-REG` | Darse de alta | **No** (exige justamente **no** estarlo) | Sí: crea Paciente | `createPaciente` |
| `CB-MOD` | Corregir sus datos o su cobertura | Sí | Sí: modifica Paciente / coberturas | `getPaciente` + `updatePaciente` |
| `CB-BAJA` | Dejar de ser paciente | Sí | Sí: baja lógica del Paciente | `softDeletePaciente` |

### 1.4 Agrupación, dependencias y disparadores

**Grupos por requisito de identidad:**

| Grupo | CU | Qué necesita saber el agente antes de empezar |
|-------|----|-----------------------------------------------|
| **A · Abiertos** | `CB-INF`, `CB-OPE` | Nada. Se atienden aunque el teléfono no esté registrado. |
| **B · Alta** | `CB-REG` | Que el teléfono **no** esté registrado. |
| **C · Paciente identificado** | `CB-DISP`, `CB-SOL`, `CB-CAN`, `CB-REP`, `CB-CONF`, `CB-MOD`, `CB-BAJA` | Que exista un paciente activo con ese teléfono (`F-ID` con éxito). Si no, se deriva a `CB-REG`. |

**Relaciones entre CU** (*incluye* = lo usa siempre; *puede disparar* = el paciente puede
seguir hacia ese CU desde un punto del flujo; *extensión* = se activa ante una condición):

| CU | Incluye | Puede disparar | Lo disparan |
|----|---------|----------------|-------------|
| `CB-INF` | `F-OFERTA` | `CB-DISP`, `CB-SOL` (el paciente ya vio la oferta y quiere un turno) | inicio de conversación |
| `CB-DISP` | `F-ID`, `F-OFERTA`, `F-HORARIOS` | `CB-SOL` (le gustó un horario) | `CB-INF` |
| `CB-SOL` | `F-ID`, `F-OFERTA` (opcional), `F-HORARIOS` | `CB-CONF` en otra conversación (el turno nace Pendiente) | `CB-INF`, `CB-DISP` |
| `CB-CAN` | `F-ID`, `F-MISTURNOS(CANCELABLE)` | `CB-SOL` (cancelar y sacar otro, p. ej. si el turno está Confirmado y no se puede reprogramar) | `CB-REP`, `CB-BAJA` |
| `CB-REP` | `F-ID`, `F-MISTURNOS(REPROGRAMABLE)`, `F-HORARIOS` | `CB-CAN` (fuera de plazo o sin horarios) | — |
| `CB-CONF` | `F-ID`, `F-MISTURNOS(CONFIRMABLE)` | — | notificación de recordatorio (Fase 2) |
| `CB-REG` | — | cualquiera del grupo C (el objetivo que tenía el paciente) | `EX-NOREG` desde cualquier CU del grupo C |
| `CB-MOD` | `F-ID` | — | — |
| `CB-BAJA` | `F-ID` | `CB-CAN` (si tiene turnos vivos, ver CB-BAJA.E2) | — |
| `CB-OPE` | — | — (la conversación pasa a una persona) | **extensión de todos** (`EX-OPE`) |

```mermaid
flowchart LR
  subgraph A[Abiertos]
    INF[CB-INF]
    OPE[CB-OPE]
  end
  REG[CB-REG]
  subgraph C[Paciente identificado]
    DISP[CB-DISP] --> SOL[CB-SOL]
    REP[CB-REP] -.fuera de plazo.-> CAN[CB-CAN]
    CAN -.sacar otro.-> SOL
    CONF[CB-CONF]
    MOD[CB-MOD]
    BAJA[CB-BAJA] -.turnos vivos.-> CAN
  end
  INF --> DISP
  INF --> SOL
  C -.EX-NOREG.-> REG
  REG -.retoma el objetivo.-> C
  A & C -.EX-OPE.-> OPE
```

> **La spec no retoma el objetivo después de registrarse:** `EX-NOREG` hace "Ir a CU
> Registrarse → Fin CU". En una conversación es natural volver a lo que el paciente quería
> (decisión de diseño, §1.9 D3).

### 1.5 Excepciones transversales

Aplican en cualquier paso donde el paciente escribe. Las cuatro primeras son de la
especificación; las demás las resuelve el canal antes de llegar al agente.

| ID | Disparador | Respuesta | Retorno | Procedencia |
|----|------------|-----------|---------|-------------|
| `EX-COH` | El mensaje no se entiende o no tiene relación con el paso | El agente pide reformular | Al mismo paso | Spec (Excepción 1 de todos) |
| `EX-OPE` | El paciente pide una persona o dice que no entiende | Ir a `CB-OPE` | Fin del bot en esa conversación | Spec (Excepción 2 de todos) |
| `EX-NOREG` | `F-ID` devuelve `404 PACIENTE_NO_ENCONTRADO` en un CU del grupo C | Presentación de la clínica + invitación a registrarse → `CB-REG` | Fin del CU (spec); ver §1.9 D3 | Spec (Excepción 3 de TURN-2..6) |
| `EX-BACK` | Una tool falla de forma no prevista (`5xx`, timeout, `CLINICA_NO_ENCONTRADA`) | Disculpa + `CB-OPE` | — | Código |
| `EX-MEDIA` | Audio, imagen, sticker | "Por ahora solo puedo leer mensajes de texto" | El paciente reescribe | Canal (no llega al LLM) |
| `EX-TEC` | Flowise falla o responde vacío | "Estamos con problemas técnicos…" + handoff | — | Canal |
| `EX-SINTEL` | El mensaje no trae teléfono | Mensaje de error + handoff | — | Canal |

### 1.6 Fragmentos reutilizables

#### `F-ID` · Identificar al paciente

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Canal | Inyecta `numeroTelefono` (verificado por WhatsApp) y `nombreContacto` en la sesión | — |
| 2 | Back | Busca el paciente activo con ese teléfono | `getPaciente` → `{ nombre, apellido, dni, email, fechaNacimiento, coberturas[] }` · `404 PACIENTE_NO_ENCONTRADO` |
| 3a | Agente | Si existe: saludo personalizado ("Hola Carlos…") | — |
| 3b | Agente | Si no existe: presentación de la clínica (`getClinica` → nombre, ubicación, horario de atención) → `EX-NOREG` | `getClinica` |

#### `F-OFERTA` · Explorar la oferta

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Back | Lista las especialidades | `listEspecialidades` → `[{ nombre }]` |
| 2 | Agente | Las muestra y pregunta cuál le interesa | — |
| 3 | Paciente | Nombra una especialidad (con sus palabras: "el del corazón", "cardio") | — |
| 4 | Agente | **Mapea** el texto a un `nombre` de la lista mostrada | — |
| 5 | Back | Trae prestaciones y médicos de esa especialidad | `getOfertaEspecialidad(nombre)` → `{ prestaciones[{nombre}], medicos[{nombre, apellido, matricula}] }` · `404 ESPECIALIDAD_NO_ENCONTRADA` |
| 6 | Agente | Muestra prestaciones y médicos y ofrece seguir (otra especialidad, ver horarios, reservar) | — |

**Alterno:** `ESPECIALIDAD_NO_ENCONTRADA` → "No encontramos esa especialidad", vuelve al paso 3 (Spec).

#### `F-HORARIOS` · Buscar horarios disponibles

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Agente | Pide prestación, médico y, opcionalmente, franja (mañana, tarde, noche) | — |
| 2 | Paciente | Los indica con sus palabras ("una consulta con la doctora Gómez a la tarde") | — |
| 3 | Agente | **Mapea**: prestación → `nombrePrestacion`; médico → `matricula` (la spec pide matrícula, pero el paciente va a dar el nombre: el agente la resuelve con la oferta ya mostrada y pregunta si hay homónimos); franja → `MANANA` / `TARDE` / `NOCHE` | — (o `getOfertaEspecialidad` si todavía no la tiene) |
| 4 | Back | Busca los horarios libres | `listHorariosDisponibles(nombrePrestacion, matricula, franja?)` → `[{ slotId, fechaHora, franja }]` |
| 5 | Agente | Muestra fechas y horas legibles. **Retiene el `slotId` de cada opción sin mostrarlo** | — |

| Alterno | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------------|----------|---------------------|-------------|
| F-HORARIOS.E1 | Prestación inexistente | `PRESTACION_NO_ENCONTRADA` | "No encontramos esa prestación" → paso 2 | Spec |
| F-HORARIOS.E2 | Matrícula inexistente | `MEDICO_NO_ENCONTRADO` | "No encontramos un médico con esa matrícula" → paso 2 | Spec |
| F-HORARIOS.E3 | El médico no hace esa prestación | `MEDICO_NO_ATIENDE_PRESTACION` | "Ese médico no realiza la prestación" → paso 2 | Spec |
| F-HORARIOS.E4 | Lista vacía | — (200 `[]`) | "No hay turnos con esos criterios": ofrecer otra franja u otro médico → paso 2 | Spec |

#### `F-MISTURNOS(accion)` · Listar mis turnos y elegir uno

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Back | Lista los turnos del paciente según la acción | `listTurnos(accion)` → `[{ codigo, fechaHoraInicio, nombrePrestacion, nombreMedico, apellidoMedico, matricula, estadoActual }]` |
| 2 | Agente | Muestra la lista (fecha, hora, prestación, médico, estado) | — |
| 3 | Paciente | Elige uno ("el del martes", "el de la doctora") | — |
| 4 | Agente | **Mapea** la elección a un `codigo` de la lista; si es ambigua, repregunta | — |

| `accion` | Estados que lista (código) | Spec | DTE / reglas |
|----------|---------------------------|------|--------------|
| `CANCELABLE` | Pendiente, Confirmado, Espera de Validación | igual | igual (el DTE suma En Sala de Espera, pero solo para el personal interno) |
| `REPROGRAMABLE` | Pendiente | Pendiente o Espera de Validación (spec desactualizada) | solo Pendiente (código = DTE, ver §1.8 #16) |
| `CONFIRMABLE` | Pendiente | igual | igual |

**Alterno:** lista vacía → "No tenés turnos que se puedan {cancelar / reprogramar /
confirmar}" → **fin del CU** (Spec, Excepción 4 de TURN-4..6).

> **Plazo:** el listado filtra por estado **y** por plazo vigente de la acción
> (`fechaLimiteCancelacion`, `fechaLimiteReprogramacion` o `fechaLimiteConfirmacion` según
> corresponda), así que el paciente no elige un turno vencido. El 409 `*_FUERA_DE_PLAZO_*`
> queda como defensa ante un vencimiento entre el listado y la acción. Ver §1.9 D5.

---

### 1.7 Análisis por caso de uso

#### `CB-INF` · Consultar información general (ECU-TURN-1)

**Descripción (spec):** El Paciente consulta información general de la clínica a través del
chatbot para conocer su oferta de atención: el sistema le muestra los datos de la clínica y le
permite ver las especialidades y, por especialidad, sus prestaciones y médicos. No requiere
identificación ni modifica el estado del sistema.

**Grupo:** A (abierto) · **Modifica:** no.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Saluda o pregunta por la clínica | — |
| 2 | Agente | Interpreta la intención (`EX-COH`, `EX-OPE`) | — |
| 3 | Back | Trae los datos de la clínica | `getClinica` → `{ nombre, ubicacion, horarioInicioAtencion, horarioFinAtencion }` |
| 4 | Agente | Da la bienvenida con esos datos y ofrece ver especialidades | — |
| 5 | Paciente | Acepta | — |
| 6 | — | `F-OFERTA` | — |
| 7 | Agente | Ofrece consultar otra especialidad (vuelve a 6) o seguir hacia `CB-DISP` / `CB-SOL` | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-INF.E1 | 6 | Especialidad inexistente | `ESPECIALIDAD_NO_ENCONTRADA` | ver `F-OFERTA` | Spec (Exc. 3) |
| CB-INF.E2 | 3 | Clínica sin cargar | `CLINICA_NO_ENCONTRADA` | `EX-BACK` | Código |

**Diferencias:** la spec dice "Prestaciones **activas**"; el back lista las **publicadas**,
que es lo correcto según las reglas (una prestación No Publicada no genera horarios). La spec
está desactualizada.

---

#### `CB-DISP` · Consultar turnos disponibles (ECU-TURN-2)

**Descripción (spec):** El Paciente consulta los turnos disponibles a través del chatbot para
conocer la oferta de atención: el sistema lo identifica por su teléfono de WhatsApp, le permite
ver las especialidades y, por especialidad, sus prestaciones y médicos, y finalmente, a partir
de un médico y una prestación, le muestra la lista de horarios disponibles. No modifica el
estado del sistema.

**Grupo:** C (según la spec) · **Modifica:** no.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Saluda o pide ver turnos | — |
| 2 | — | `F-ID` (si no está registrado → `EX-NOREG`) | `getPaciente` |
| 3 | Agente | Ofrece consultar especialidades o ir directo a los horarios | — |
| 4 | — | (opcional) `F-OFERTA` | — |
| 5 | — | `F-HORARIOS` | `listHorariosDisponibles` |
| 6 | Agente | Ofrece reservar alguno → `CB-SOL` | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-DISP.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Spec (Exc. 3) |
| CB-DISP.E2 | 4 | Especialidad inexistente | `ESPECIALIDAD_NO_ENCONTRADA` | ver `F-OFERTA` | Spec (Exc. 4) |
| CB-DISP.E3..E6 | 5 | Prestación / médico / vínculo / sin turnos | ver `F-HORARIOS` | ver `F-HORARIOS` | Spec (Exc. 5..8) |

**Diferencias:**
- La spec exige registro, pero `GET /Agente/Turno/Disponibilidad` **no** pide
  `X-Paciente-Telefono`: técnicamente es una consulta abierta. Decisión en §1.9 D2.
- En 11.6 la spec no excluye los slots con turno "En Sala de Espera" (TURN-3 y TURN-5 sí). El
  back usa `estaOcupada`, que cubre todos los casos: la inconsistencia es solo de la spec.
- La spec no menciona el **horizonte de reserva** (`diasMaximosAnticipacionReserva`); el back
  ya lo aplica al listar (Regla).

---

#### `CB-SOL` · Solicitar turno (ECU-TURN-3)

**Descripción (spec):** El Paciente solicita un turno a través del chatbot para reservar un
horario de atención: el sistema lo identifica por su teléfono de WhatsApp, le permite consultar
especialidades, prestaciones y médicos, le muestra los turnos disponibles del médico y la
prestación elegidos y registra el Turno sobre el horario que selecciona, de modo que queda
creado en estado "Pendiente" o "Espera de Validación" y se dispara la notificación
correspondiente al paciente.

**Grupo:** C · **Modifica:** crea un Turno.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide un turno | — |
| 2 | — | `F-ID` | `getPaciente` |
| 3 | Agente | Ofrece consultar especialidades o reservar directo | — |
| 4 | — | (opcional) `F-OFERTA` | — |
| 5 | — | `F-HORARIOS` (si viene de `CB-DISP`, ya está hecho) | `listHorariosDisponibles` |
| 6 | Paciente | Elige un horario ("el jueves a las 10") | — |
| 7 | Agente | **Mapea** la elección al `slotId` retenido; si es ambigua, repregunta | — |
| 8 | Back | Registra el turno | `createTurno(slotId)` → `{ codigo, fechaHoraInicio, montoAPagar, tipoCobertura, estadoActual }` |
| 9 | Agente | Informa código, fecha y hora, médico, monto, cobertura y estado. Si nació en **Espera de Validación**, explica que hay indicaciones por validar | — |
| 10 | Canal | (Fase 2) Notificación de turno registrado | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-SOL.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Spec (Exc. 3) |
| CB-SOL.E2 | 4–5 | Especialidad / prestación / médico / vínculo / sin turnos | ver `F-OFERTA`, `F-HORARIOS` | idem | Spec (Exc. 4..8) |
| CB-SOL.E3 | 8 | Otro paciente tomó el horario | `AGENDA_HORARIO_NO_DISPONIBLE` (404) | "Ese horario ya no está disponible, elegí otro" → volver a `F-HORARIOS` paso 4 | Spec (Exc. 9) |
| CB-SOL.E4 | 8 | Venció el plazo de reserva del slot | `AGENDA_HORARIO_FUERA_DE_PLAZO` | idem E3 | Spec (Exc. 9) |
| CB-SOL.E5 | 8 | La prestación dejó de estar Publicada | `PRESTACION_NO_PUBLICADA` | Informar; ofrecer otra prestación o `CB-OPE` | Regla |
| CB-SOL.E6 | 8 | El médico no tiene la prestación **vigente en la fecha del slot** | `MEDICO_PRESTACION_NO_ENCONTRADA` | Informar; volver a `F-HORARIOS` | Regla |
| CB-SOL.E7 | 8 | El `slotId` no existe (el agente lo inventó o lo perdió) | `404` del slot | Volver a `F-HORARIOS` paso 4 | Código |

**Diferencias:**
- La spec no habla de **cobertura ni de monto**. Las reglas (§8) y el código sí: se usa la
  primera cobertura del paciente cuyo plan está **Publicado** y cubre la prestación; si no hay
  ninguna, el turno queda `PARTICULAR` (corregido en el código el 2026-11-08). El paciente se entera del monto **después** de reservar (§1.9 D6).
- El borde superior de la ventana de reserva (horizonte) es Regla: el back lo aplica al
  **listar**, pero `createTurno` no lo vuelve a comprobar. Hueco menor, porque el `slotId`
  siempre sale del listado.
- La spec no tiene un paso "¿confirmás la reserva?" antes del paso 8 (§1.9 D4).

---

#### `CB-CAN` · Cancelar turno (ECU-TURN-4)

**Descripción (spec):** El Paciente cancela un turno propio a través del chatbot para liberar
el horario reservado: el sistema lo identifica, le muestra sus turnos cancelables y, si está
dentro del plazo, deja el turno elegido en estado "Cancelado" y dispara la notificación
correspondiente.

**Grupo:** C · **Modifica:** Turno → Cancelado; libera el slot.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide cancelar (o elige "cancelar" del menú de acciones sobre sus turnos) | — |
| 2 | — | `F-ID` | `getPaciente` |
| 3 | — | `F-MISTURNOS(CANCELABLE)` → `codigo` | `listTurnos(CANCELABLE)` |
| 4 | Back | Cancela | `cancelTurno(codigo)` → `204` |
| 5 | Agente | Confirma la cancelación; ofrece sacar otro turno (`CB-SOL`) | — |
| 6 | Canal | (Fase 2) Notificación de turno cancelado | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-CAN.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Spec (Exc. 3) |
| CB-CAN.E2 | 3 | Sin turnos cancelables | — (`[]`) | "No tenés turnos que se puedan cancelar" → fin | Spec (Exc. 4) |
| CB-CAN.E3 | 4 | Pasó la fecha límite de cancelación | `TURNO_FUERA_DE_PLAZO_CANCELACION` | Informar → volver a la lista (spec); ofrecer `CB-OPE` (el personal cancela sin límite) | Spec (Exc. 5) |
| CB-CAN.E4 | 4 | El turno cambió de estado entre el listado y la acción (el scheduler lo marcó, otro canal lo canceló) | `TURNO_TRANSICION_INVALIDA` | Informar el estado actual → volver a la lista | Código (la spec lo pide en los parámetros, pero no tiene excepción) |
| CB-CAN.E5 | 4 | `codigo` inexistente o de otro paciente | `TURNO_NO_ENCONTRADO` / `RECURSO_AJENO` | Volver a la lista | Código (defensivo) |

---

#### `CB-REP` · Reprogramar turno (ECU-TURN-5)

**Descripción (spec):** El Paciente reprograma un turno propio a través del chatbot para
trasladarlo a otro horario: el sistema lo identifica, le muestra sus turnos reprogramables, le
ofrece los horarios disponibles del mismo médico y prestación y, si está dentro del plazo,
reasigna el turno elegido al nuevo horario, manteniendo su estado, y dispara la notificación
correspondiente.

> **Desactualizada según reglas v3 / DTE:** la reprogramación **no reasigna** el turno: crea un
> **turno nuevo** (con monto, fechas límite, indicaciones y estado recalculados) y cierra el
> viejo como **Reprogramado** (`turnoOrigen` registra la cadena). Así lo hace el código.

**Grupo:** C · **Modifica:** crea Turno nuevo; viejo → Reprogramado; libera el slot viejo.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide reprogramar | — |
| 2 | — | `F-ID` | `getPaciente` |
| 3 | — | `F-MISTURNOS(REPROGRAMABLE)` → `codigo` | `listTurnos(REPROGRAMABLE)` |
| 4 | Agente | Pide, opcionalmente, la franja horaria | — |
| 5 | Agente | Toma `nombrePrestacion` y `matricula` **del turno elegido** (vienen en `listTurnos`) | — |
| 6 | Back | Horarios libres del mismo médico y prestación | `listHorariosDisponibles(nombrePrestacion, matricula, franja?)` |
| 7 | Agente | Muestra fechas y horas, retiene `slotId` | — |
| 8 | Paciente | Elige el nuevo horario | — |
| 9 | Agente | Mapea a `slotId` | — |
| 10 | Back | Reprograma | `reprogramTurno(codigo, slotId)` → `{ codigo (nuevo), fechaHoraInicio, montoAPagar, tipoCobertura, estadoActual }` |
| 11 | Agente | Informa el nuevo código, fecha, monto y estado | — |
| 12 | Canal | (Fase 2) Notificación de turno reprogramado | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-REP.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Spec (Exc. 3) |
| CB-REP.E2 | 3 | Sin turnos reprogramables | — (`[]`) | "No tenés turnos que se puedan reprogramar" → fin. Si tiene uno **Confirmado**, explicar que un turno confirmado no se reprograma: hay que cancelarlo y sacar otro (`CB-CAN` → `CB-SOL`) | Spec (Exc. 4) + Regla |
| CB-REP.E3 | 10 (la spec lo pone en el paso 4, antes de mostrar horarios) | Pasó la fecha límite de reprogramación | `TURNO_FUERA_DE_PLAZO_REPROGRAMACION` | Informar → volver a la lista; ofrecer `CB-CAN` | Spec (Exc. 5) |
| CB-REP.E4 | 6 | Sin horarios del mismo médico y prestación | — (`[]`) | "No hay otros horarios para ese médico y prestación": ofrecer otra franja → paso 4 | Spec (Exc. 6) |
| CB-REP.E5 | 10 | El nuevo horario ya fue tomado o venció | `AGENDA_HORARIO_NO_DISPONIBLE` / `AGENDA_HORARIO_FUERA_DE_PLAZO` | "Ese horario ya no está disponible" → paso 6 | Spec (Exc. 7) |
| CB-REP.E6 | 10 | La prestación ya no está Publicada | `PRESTACION_NO_PUBLICADA` | Informar; el turno sigue como estaba | Regla |
| CB-REP.E7 | 10 | La cobertura del plan para esa prestación se dio de baja | `OBRA_SOCIAL_PLAN_PRESTACION_NO_ENCONTRADA` | Informar; el turno sigue como estaba; ofrecer `CB-OPE` | Regla |
| CB-REP.E7b | 10 | El plan de la cobertura del turno dejó de estar Publicado | `PLAN_NO_PUBLICADO` | Informar; el turno sigue como estaba; ofrecer cancelar y sacar otro (sale particular) o `CB-OPE` | Regla |
| CB-REP.E8 | 10 | El turno no está en un estado reprogramable | `TURNO_TRANSICION_INVALIDA` | Informar → volver a la lista | Código (defensivo: el estado cambió entre el listado y la acción) |
| CB-REP.E9 | 10 | `codigo` inexistente o ajeno | `TURNO_NO_ENCONTRADO` / `RECURSO_AJENO` | Volver a la lista | Código |

---

#### `CB-CONF` · Confirmar turno (ECU-TURN-6)

**Descripción (spec):** El Paciente confirma un turno propio a través del chatbot para asegurar
su asistencia: el sistema lo identifica, le muestra sus turnos confirmables y, si está dentro
del plazo, deja el turno elegido en estado "Confirmado" y dispara la notificación
correspondiente.

**Grupo:** C · **Modifica:** Turno → Confirmado.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide confirmar (o responde al recordatorio, Fase 2) | — |
| 2 | — | `F-ID` | `getPaciente` |
| 3 | — | `F-MISTURNOS(CONFIRMABLE)` → `codigo` | `listTurnos(CONFIRMABLE)` |
| 4 | Back | Confirma | `confirmTurno(codigo)` → `{ codigo, fechaHoraInicio, …, estadoActual = CONFIRMADO }` |
| 5 | Agente | Confirma la asistencia | — |
| 6 | Canal | (Fase 2) Notificación de turno confirmado | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-CONF.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Spec (Exc. 3) |
| CB-CONF.E2 | 3 | Sin turnos confirmables | — (`[]`) | "No tenés turnos que se puedan confirmar" → fin. Si tiene turnos en Espera de Validación, explicar que primero la clínica tiene que validarlos | Spec (Exc. 4) + Regla |
| CB-CONF.E3 | 4 | Pasó la fecha límite de confirmación | `TURNO_FUERA_DE_PLAZO_CONFIRMACION` | Informar → volver a la lista. En la práctica el scheduler ya lo confirmó solo (reglas §4.5), así que el turno no debería aparecer | Spec (Exc. 5) |
| CB-CONF.E4 | 4 | El turno dejó de estar Pendiente (lo confirmó el scheduler, se canceló) | `TURNO_TRANSICION_INVALIDA` | Informar el estado actual | Código |
| CB-CONF.E5 | 4 | `codigo` inexistente o ajeno | `TURNO_NO_ENCONTRADO` / `RECURSO_AJENO` | Volver a la lista | Código |

---

#### `CB-OPE` · Solicitar operador humano (ECU-TURN-23)

**Descripción (spec):** El Paciente solicita ser atendido por un operador humano durante su
interacción con el chatbot, en cualquier otro caso de uso, cuando no entiende las opciones o
prefiere hablar con una persona, de modo que el sistema deriva la conversación a Chatwoot para
que sea atendida por un operador.

**Grupo:** A · **Modifica:** no toca el dominio; cambia la conversación de `pending` a `open`.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide una persona o expresa que no entiende | — |
| 2 | Agente | Decide derivar: responde que un operador se va a comunicar y agrega `—HANDOFF—` al final | — |
| 3 | Canal | Quita el token, envía el mensaje y cambia la conversación a `open` (Chatwoot `toggle_status`) | — |
| 4 | — | El bot deja de responder mientras la conversación esté `open` | — |

| Alterno | Paso | Disparador | Respuesta / retorno | Procedencia |
|---------|------|------------|---------------------|-------------|
| CB-OPE.E1 | 2 | El mensaje no se entiende | `EX-COH` | Spec (Exc. 1) |
| CB-OPE.E2 | 3 | Chatwoot no responde al derivar | Manda el código: solo `log.error`, el paciente no recibe nada | Código; la Excepción 2 de la spec (disculpa + "reintentá más tarde") queda desactualizada, §1.8 #27 |

> El agente también deriva **por su cuenta** (no solo a pedido): paciente trabado en un
> bucle de `EX-COH`, errores `EX-BACK`, o situaciones que solo resuelve el personal (cancelar
> fuera de plazo, cambiar el teléfono). Criterio a fijar en el diseño (§1.9 D7).

---

#### `CB-REG` · Registrarse (ECU-PAC-1) — *interpretado, sin spec*

**Descripción (interpretada):** El Paciente que escribe desde un teléfono no registrado se da
de alta a través del chatbot para poder gestionar turnos: el sistema toma su número de teléfono
del canal de WhatsApp (no se lo pide), le solicita sus datos personales y, opcionalmente, su
obra social y plan, y crea el Paciente activo. Si no declara cobertura, queda como particular.

**Grupo:** B · **Modifica:** crea Paciente (y su `ObraSocialPaciente` si declaró cobertura).

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Agente | Llega desde `EX-NOREG` o porque el paciente pide registrarse; explica qué datos va a pedir | — |
| 2 | Agente | Pide los datos de a uno: DNI, nombre, apellido, fecha de nacimiento, email | — |
| 3 | Paciente | Los responde (puede dar varios juntos) | — |
| 4 | Agente | **Normaliza**: fecha a `AAAA-MM-DD`, DNI sin puntos; puede proponer el nombre de `nombreContacto` | — |
| 5 | Agente | Pregunta si tiene obra social; si sí: obra social, plan y nro. de socio | — |
| 6 | Agente | Resume los datos y pide confirmación | — |
| 7 | Paciente | Confirma o corrige (vuelve a 2) | — |
| 8 | Back | Crea el paciente | `createPaciente({ dni, nombre, apellido, fechaNacimiento, email, obraSocial? })` → `{ nombre, apellido }` |
| 9 | Agente | Da la bienvenida y retoma el objetivo original (§1.9 D3) | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-REG.E1 | 8 | Ya existe un paciente activo con ese teléfono | `PACIENTE_NUMERO_TELEFONO_DUPLICADO` | "Ya estás registrado": saludar y seguir | Regla |
| CB-REG.E2 | 8 | DNI ya registrado (otro teléfono) | `PACIENTE_DNI_DUPLICADO` | Informar; el cambio de teléfono lo hace el administrador → `CB-OPE` | Regla |
| CB-REG.E3 | 8 | Email ya registrado | `PACIENTE_EMAIL_DUPLICADO` | Pedir otro email → paso 2 | Código |
| CB-REG.E4 | 8 | Falta un dato o el formato es inválido (fecha futura, email mal formado) | `422 VALIDACION` (`errores[]` indica el campo) | Pedir de nuevo ese dato → paso 2 | Código |
| CB-REG.E5 | 8 | Obra social inexistente | `OBRA_SOCIAL_NO_ENCONTRADA` | Repreguntar o seguir como particular → paso 5 | Regla |
| CB-REG.E6 | 8 | Plan inexistente en esa obra social | `PLAN_NO_ENCONTRADO` | idem E5 | Regla |

> **Observación:** el agente no tiene una tool para listar obras sociales ni planes, así que
> el paciente tiene que acertar el nombre exacto (sin distinguir mayúsculas). Ver §1.9 D8.

---

#### `CB-MOD` · Modificar mis datos (ECU-PAC-2) — *interpretado, sin spec*

**Descripción (interpretada):** El Paciente registrado modifica sus datos personales o su
cobertura a través del chatbot: el sistema lo identifica por su teléfono, le muestra sus datos
actuales y actualiza los que indique. El DNI y el número de teléfono no se pueden modificar por
este canal: el teléfono lo cambia el administrador. Declarar una obra social reemplaza la
cobertura vigente (cambiar de plan es baja más alta).

**Grupo:** C · **Modifica:** Paciente; `ObraSocialPaciente` (baja + alta).

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide cambiar algún dato | — |
| 2 | — | `F-ID` (ya trae los datos actuales y las coberturas) | `getPaciente` |
| 3 | Agente | Muestra los datos actuales y pregunta qué quiere cambiar | — |
| 4 | Paciente | Indica el o los cambios | — |
| 5 | Agente | Mapea a los campos (`nombre`, `apellido`, `fechaNacimiento`, `email`, `obraSocial`). Si pide cambiar DNI o teléfono, explica que no se puede por acá | — |
| 6 | Agente | Resume y pide confirmación; si cambia la cobertura, avisa que **reemplaza** la actual | — |
| 7 | Back | Actualiza | `updatePaciente({ campos cambiados })` → datos actualizados |
| 8 | Agente | Confirma los cambios | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-MOD.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | `EX-NOREG` | Regla |
| CB-MOD.E2 | 5 | Quiere cambiar teléfono o DNI | — (no llega al back) | Explicar que lo hace el personal → ofrecer `CB-OPE` | Regla |
| CB-MOD.E3 | 7 | Email de otro paciente | `PACIENTE_EMAIL_DUPLICADO` | Pedir otro → paso 4 | Código |
| CB-MOD.E4 | 7 | Obra social / plan inexistente | `OBRA_SOCIAL_NO_ENCONTRADA` / `PLAN_NO_ENCONTRADO` | Repreguntar → paso 4 | Regla |
| CB-MOD.E5 | 7 | Ya tiene ese mismo plan activo | `OBRA_SOCIAL_PACIENTE_YA_ASIGNADA` | Informar que no hay cambios | Código |
| CB-MOD.E6 | 7 | Formato inválido | `422 VALIDACION` | Pedir de nuevo el dato | Código |

---

#### `CB-BAJA` · Darme de baja (ECU-PACIENTE-3) — *interpretado, sin spec*

**Descripción (interpretada):** El Paciente registrado solicita darse de baja a través del
chatbot: el sistema lo identifica por su teléfono, le pide confirmación explícita y registra la
baja lógica del Paciente, que arrastra sus coberturas y archivos.

**Grupo:** C · **Modifica:** baja lógica del Paciente.

| # | Quién | Paso | Back (tool → salida) |
|---|-------|------|----------------------|
| 1 | Paciente | Pide darse de baja | — |
| 2 | — | `F-ID` | `getPaciente` |
| 3 | Agente | Explica las consecuencias y pide confirmación explícita | — |
| 4 | Paciente | Confirma | — |
| 5 | Back | Da de baja | `softDeletePaciente` → `204` |
| 6 | Agente | Despedida | — |

| Alterno | Paso | Disparador | `codigo` | Respuesta / retorno | Procedencia |
|---------|------|------------|----------|---------------------|-------------|
| CB-BAJA.E1 | 2 | No registrado | `PACIENTE_NO_ENCONTRADO` | "No encontramos un paciente con este número" → fin | Regla |
| CB-BAJA.E2 | 5 | Tiene turnos vivos | `PACIENTE_CON_TURNOS_VIVOS` (el mensaje trae cuántos y la fecha del último) | Informar; ofrecer cancelarlos (`CB-CAN`) y volver | Código (reglas §6 actualizadas) |
| CB-BAJA.E3 | 4 | El paciente no confirma | — | Fin sin cambios | — |

---

### 1.8 Matriz reglas de negocio ↔ flujos alternos

Compara cada validación de negocio con su excepción en la spec y su `codigo` en el back.

**Estado:** ✅ coincide · 🟡 falta en la spec (el back lo cubre) · 🟠 diferencia menor o a
verificar · 🔴 conflicto que rompe un flujo.

| # | Regla / validación | CU | Spec | Back (`codigo`) | Estado |
|---|--------------------|----|------|-----------------|--------|
| 1 | Especialidad activa | INF, DISP, SOL | Exc. "Especialidad inexistente" | `ESPECIALIDAD_NO_ENCONTRADA` | ✅ |
| 2 | La oferta muestra solo prestaciones **Publicadas** | INF | dice "activas" | lista publicadas | 🟡 spec desactualizada |
| 3 | Prestación existente por nombre | DISP, SOL | Exc. 5 | `PRESTACION_NO_ENCONTRADA` | ✅ |
| 4 | Médico activo por matrícula | DISP, SOL | Exc. 6 | `MEDICO_NO_ENCONTRADO` | ✅ |
| 5 | El médico ofrece la prestación | DISP, SOL | Exc. 7 | `MEDICO_NO_ATIENDE_PRESTACION` | ✅ |
| 6 | `MedicoPrestacion` **vigente en la fecha del slot** | SOL, REP | — | `MEDICO_PRESTACION_NO_ENCONTRADA` | 🟡 |
| 7 | Sin horarios disponibles | DISP, SOL, REP | Exc. 8 / 6 | lista vacía | ✅ |
| 8 | Ventana de reserva, borde inferior (`fechaLimiteReserva`) | SOL, REP | Exc. 9 / 7 | `AGENDA_HORARIO_FUERA_DE_PLAZO` | ✅ |
| 9 | Slot ocupado entre el listado y la reserva | SOL, REP | Exc. 9 / 7 | `AGENDA_HORARIO_NO_DISPONIBLE` | ✅ |
| 10 | Ventana de reserva, borde superior (horizonte de la clínica) | DISP, SOL, REP | — | se aplica al listar; `createTurno` no lo revalida | 🟠 |
| 11 | La prestación tiene que estar **Publicada** al reservar | SOL, REP | — | `PRESTACION_NO_PUBLICADA` | 🟡 |
| 12 | Plan **Publicado** para cubrir; si no, `PARTICULAR` | SOL | — (la spec no habla de cobertura) | el alta elige la primera cobertura con plan Publicado que cubre la prestación | ✅ resuelto 2026-11-08 (antes no se revisaba el estado del plan) |
| 13 | El monto se informa al paciente | SOL, REP | — | viene en el response, **después** de reservar | 🟠 (reglas: "el monto nuevo se muestra antes del Confirmar" en la reprogramación) |
| 14 | Estado inicial Pendiente / Espera de Validación | SOL | ✅ | ✅ | ✅ |
| 15 | Cancelar: plazo `fechaLimiteCancelacion` (paciente) | CAN | Exc. 5 | `TURNO_FUERA_DE_PLAZO_CANCELACION` | ✅ |
| 16 | **Reprogramar: estados de origen** | REP | Pendiente **o** Espera de Validación | lista y transición: solo Pendiente | ✅ resuelto: transición y lista del agente solo Pendiente, como el DTE |
| 17 | Reprogramar crea turno nuevo y cierra el viejo como Reprogramado | REP | "reasigna el turno manteniendo su estado" | crea nuevo + Reprogramado | 🟡 spec desactualizada |
| 18 | Reprogramar: plazo `fechaLimiteReprogramacion` | REP | se valida **antes** de mostrar horarios (paso 8) | se valida recién al reprogramar | ✅ resuelto 2026-11-08 (el listado filtra por plazo: el paciente ya no elige un turno vencido; el 409 queda como defensa ante un vencimiento entre el listado y la acción) |
| 19 | Reprogramar: horarios del **mismo médico y prestación** | REP | lee la `MedicoPrestacion` del turno | `listTurnos` devuelve la `matricula` que exige `listHorariosDisponibles` | ✅ resuelto: `matricula` en `ListTurnoAgenteResponse` |
| 20 | Reprogramar con plan no Publicado o cobertura de baja → no se reprograma | REP | — | cobertura de baja → `OBRA_SOCIAL_PLAN_PRESTACION_NO_ENCONTRADA`; plan no Publicado → `PLAN_NO_PUBLICADO` | ✅ resuelto 2026-11-08 |
| 21 | Confirmar: plazo `fechaLimiteConfirmacion` | CONF | Exc. 5 | `TURNO_FUERA_DE_PLAZO_CONFIRMACION` | ✅ |
| 22 | El estado sigue siendo válido al ejecutar (cancelar, confirmar, reprogramar) | CAN, REP, CONF | en los parámetros, sin excepción | `TURNO_TRANSICION_INVALIDA` | 🟡 |
| 23 | Los listados filtran por plazo (precondición "dentro de la fecha límite") | CAN, REP, CONF | precondición sí, búsqueda no | filtran por estado y por plazo vigente de la acción | ✅ resuelto 2026-11-08 |
| 24 | Solo turnos propios; se expone el `codigo`, nunca el `id` | CAN, REP, CONF | implícito | `RECURSO_AJENO`, `TURNO_NO_ENCONTRADO` | 🟡 |
| 25 | **Baja de paciente con turnos vivos** | BAJA | sin spec | rechaza con `PACIENTE_CON_TURNOS_VIVOS` | ✅ manda el código; reglas §6 actualizadas |
| 26 | DNI y teléfono únicos entre activos; el teléfono no se cambia por el chatbot | REG, MOD | sin spec | `PACIENTE_DNI_DUPLICADO`, `PACIENTE_NUMERO_TELEFONO_DUPLICADO`; el request no admite teléfono | ✅ |
| 27 | Handoff con Chatwoot caído: disculpa al paciente | OPE | Exc. 2 | solo `log.error` | 🟡 manda el código; spec desactualizada |
| 28 | Solo se asocia un **Plan Publicado** como cobertura | REG, MOD | sin spec | acepta cualquier plan no deshabilitado (también No Publicado) | ✅ manda el código; regla actualizada |
| 29 | Email único entre pacientes | REG, MOD | sin spec | `PACIENTE_EMAIL_DUPLICADO` | ✅ regla actualizada |
| 30 | Fecha de nacimiento y email obligatorios | REG | sin spec | `@NotNull @Past`, `@NotBlank @Email` | ✅ `Chatbot.md` actualizado |

### 1.9 Insumos para el diseño (decisiones abiertas)

Preguntas que el análisis deja planteadas y se resuelven en la etapa 2:

| # | Decisión | Datos del análisis |
|---|----------|--------------------|
| D1 | **¿Identificar al primer mensaje o recién cuando hace falta?** | Las specs del grupo C arrancan siempre con `F-ID` (saludo "Hola Carlos…"). `CB-INF` y `CB-OPE` no lo necesitan. `getPaciente` es barato y responde 404 sin efectos: identificar al inicio permite saludar por el nombre y saber de antemano si un pedido del grupo C va a terminar en `CB-REG`. |
| D2 | **¿`CB-DISP` es abierto o exige registro?** | La spec lo pone en el grupo C; el endpoint no pide teléfono. Abrirlo permite consultar horarios sin registrarse y pedir el registro recién al reservar. |
| D3 | **¿Después de `CB-REG` se retoma el objetivo?** | La spec termina el CU. Conversacionalmente conviene retomar ("Listo, ya estás registrado. ¿Seguimos con el turno de cardiología?"). |
| D4 | **Confirmación explícita antes de cada operación que modifica** | Las specs pasan de "Seleccionar" a ejecutar. En lenguaje natural la elección puede ser ambigua: conviene un "¿Confirmás…?" antes de `createTurno`, `cancelTurno`, `reprogramTurno`, `confirmTurno`, `createPaciente`, `updatePaciente` y `softDeletePaciente`. |
| D5 | **Turnos fuera de plazo en los listados** | **Resuelta (2026-11-08):** el back filtra por plazo en el listado (#18 y #23); el 409 queda como defensa. |
| D6 | **Mostrar el monto antes de reservar o reprogramar** | Hoy solo se conoce después. Requeriría un endpoint de previsualización (#13). |
| D7 | **Criterio de handoff proactivo** | Cuántos `EX-COH` seguidos, qué errores derivan directo (`EX-BACK`, CB-CAN.E3, CB-MOD.E2, CB-REG.E2). |
| D8 | **Catálogo de obras sociales y planes** | Sin tool para listarlas, el paciente tiene que acertar el nombre. ¿Agregar `GET /Agente/ObraSocial`? |
| D9 | **Correcciones aplicadas (2026-11-07)** | Resueltas: #18 y #23 (el listado del agente filtra por plazo; D5 resuelta, 2026-11-08), #16 (reprogramar solo desde Pendiente en la transición y en `REPROGRAMABLE`, alineado al DTE), #19 (`matricula` en `ListTurnoAgenteResponse`), #25 (reglas §6: la baja con turnos vivos se rechaza), #27 (la Excepción 2 de la spec queda desactualizada; manda `log.error`), #28 (regla: se asocia cualquier plan no Deshabilitado), #29 y #30 (reglas y `Chatbot.md` actualizados). Siguen pendientes las decisiones D1 a D8. |
