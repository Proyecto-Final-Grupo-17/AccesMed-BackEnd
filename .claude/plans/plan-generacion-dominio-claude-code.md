# Plan de generación del dominio — AccesMed

Plan para ejecutar con Claude Code usando la skill **`domain-schema-generator`**. Genera las 23 entidades JPA de `Domain/` y sus migraciones Liquibase, en un orden que respeta las dependencias de clave foránea.

## Entradas

| Archivo | Para qué |
|---|---|
| `modelo/modelo_acces_med.json` | Atributos, tipos, unicidades, invariantes y relaciones. **Fuente de verdad.** |
| `modelo/modelo_dte_turno.json` | Estados del turno, para la carga inicial de `EstadoTurno`. |
| `modelo/dominio-reglas-validaciones.md` | Semántica de cada regla y mapeo Bean Validation ↔ constraint. |
| `docs/ARQUITECTURA.md §2 y §6` | Capas de defensa y nomenclatura de objetos de esquema. |

## Reglas de ejecución

1. **La skill `domain-schema-generator` manda.** Entidad JPA y changelog Liquibase se generan **en el mismo paso**, nunca uno como consecuencia tardía del otro.
2. **El modelo ya está confirmado.** La Fase 1 de la skill (preguntar atributos, tipos, relaciones y restricciones) se resuelve leyendo `modelo_acces_med.json`. No hace falta volver a preguntarle al usuario salvo que aparezca una ambigüedad que el JSON no cubra.
3. **Una entidad por vez, en el orden de este plan.** Cada entidad se cierra completa antes de pasar a la siguiente.
4. **Un archivo de changelog por entidad**, `YYYYMMDDHHMMSS-<Entidad>.xml`, con el timestamp del momento de creación, fijo para siempre.
5. **`master.xml` se toca una sola vez por entidad**, al crear su archivo.
6. Al terminar cada lote, correr el contexto de Spring con perfil `dev` y verificar que Liquibase arranca limpio antes de seguir.

---

## Fase 0 — Fundaciones

Antes de la primera entidad.

### 0.1 `Auditable`

`MappedSuperclass` con `createdAt`, `updatedAt`, `createdBy`, `updatedBy`. Todas las entidades del modelo la extienden.

**No crear una superclase `Bajable`.** Los tres campos de baja (`deletedAt`, `deletedBy`, `deletedReason`) se declaran **en cada clase** que los usa. Es una decisión de modelo, no un olvido.

Clases **sin** campos de baja: `Clinica`, `AgendaMedico`, `Turno`, `HistoricoEstadoTurno`, `UsuarioRol`. Las 18 restantes los llevan.

### 0.2 Enums

```
TipoCobertura      : PARTICULAR, OBRA_SOCIAL
MotivoCancelacion  : SOLICITUD_DEL_PACIENTE, VALIDACION_RECHAZADA, VALIDACION_VENCIDA,
                     BAJA_DE_MEDICO, BAJA_DE_PRESTACION, DECISION_ADMINISTRATIVA
ModalidadCobertura : TOTAL, CARGO_FIJO, PORCENTUAL
Permiso            : catálogo <MODULO>_<ACCION>, uno por CU interno
```

Todos con `@Enumerated(EnumType.STRING)`. En el esquema, `varchar` con `CHECK (col IN (...))`, nunca `smallint`.

### 0.3 Convención de tipos

| Concepto | Java | Postgres |
|---|---|---|
| Identificador | `UUID` | `uuid` |
| Instante absoluto | `ZonedDateTime` | `timestamptz` |
| Fecha de calendario | `LocalDate` | `date` |
| Hora del día | `LocalTime` | `time` |
| Tolerancia / duración | `Duration` | `interval` |
| Monto | `BigDecimal` | `numeric(12,2)` |
| Porcentaje | `BigDecimal` | `numeric(5,2)` |

`Duration` requiere un `AttributeConverter` o el mapeo nativo de Hibernate a `interval`. Definirlo en la Fase 0 y usarlo en las siete tolerancias de `Prestacion`.

---

## Fase 1 — Catálogo base (sin dependencias)

Orden: **`Clinica` → `Especialidad` → `TipoIndicacionPrestacion` → `Paciente` → `ObraSocial` → `Admin` → `EstadoTurno`**

Ninguna tiene FK. Se pueden generar en cualquier orden entre sí.

Puntos de atención:

- **`Clinica`** es la única sin baja lógica y sin unicidad. Necesita un changeset de carga inicial con la instancia única.
- **`EstadoTurno`** necesita un changeset de carga inicial con los nueve estados de `modelo_dte_turno.json`, en este orden: `Espera de Validación`, `Pendiente`, `Confirmado`, `En Sala de Espera`, `En Transcurso`, `Cancelado`, `Reprogramado`, `Ausente`, `Finalizado`. **Ojo con el nombre**: el estado se llama `En Transcurso`, no `Iniciado`.
- **`Paciente`**: unicidad entre activos de `dni` y de `numeroTelefono`.

---

## Fase 2 — Primer nivel de dependencias

Orden: **`Medico` → `Prestacion` → `Plan` → `Rol`**

- `Medico → Especialidad` (N a 1, obligatoria).
- `Prestacion → Especialidad` (N a 1, obligatoria). Es la entidad más cargada del modelo: 11 atributos, de los cuales siete son `Duration`.
- `Plan → ObraSocial` (N a 1, obligatoria e **inmutable**: sin `setter`, y el `Mapper` no la actualiza).
- `Rol` necesita además la tabla de la asociación N:N con el enum `Permiso`.

### 2.1 `Prestacion` — el `CHECK` del invariante

Es la constraint más importante del esquema. En el changeset:

```sql
CHECK (
  tiempo_tolerancia_solicitud      >= tiempo_tolerancia_validacion      AND
  tiempo_tolerancia_validacion     >= tiempo_tolerancia_reprogramacion  AND
  tiempo_tolerancia_reprogramacion >= tiempo_tolerancia_confirmacion    AND
  tiempo_tolerancia_confirmacion   >= tiempo_tolerancia_cancelacion     AND
  tiempo_tolerancia_cancelacion    >= interval '0'
)
```
nombre: `ck_prestacion_invariante_tolerancias`

Más:

```sql
CHECK (tiempo_tolerancia_anuncio >= interval '0')                       -- ck_prestacion_tolerancia_anuncio
CHECK (tiempo_recordatorio_confirmacion >  tiempo_tolerancia_confirmacion
   AND tiempo_recordatorio_confirmacion <= tiempo_tolerancia_solicitud) -- ck_prestacion_recordatorio
CHECK (duracion_minima > interval '0'
   AND duracion_minima <= duracion_maxima)                              -- ck_prestacion_duracion
```

Del lado de la entidad, el mismo invariante va como `@AssertTrue` en el record de request del ABM de Prestación. `codigo` es inmutable: sin `setter`.

### 2.2 `Rol` y `rol_permiso`

`Permiso` es un enum, pero la asociación es N:N. Se materializa con `@ElementCollection` + `@CollectionTable(name = "rol_permiso")`, columna `permiso varchar` con `CHECK` sobre el catálogo, y `UNIQUE (rol_id, permiso)`. La FK `fk_rol_permiso_rol` con `ON DELETE CASCADE`, porque es una colección propiedad del rol y no una entidad con vida propia.

Carga inicial: los roles de sistema `Medico` y `Admin` con `es_sistema = true`.

---

## Fase 3 — Clases asociativas y agenda

Orden: **`MedicoPrestacion` → `IndicacionPrestacion` → `AgendaMedico` → `AgendaDia` → `AgendaHorarios` → `ObraSocialPlanPrestacion` → `ObraSocialPaciente` → `Usuario` → `UsuarioRol`**

| Entidad | Puntos de atención |
|---|---|
| `MedicoPrestacion` | Ambas flechas salen de la asociativa. Unicidad `(medico, prestacion)` entre activos. `precioParticular > 0` incluso con `atiendeParticular` en falso. La regla "la especialidad de la prestación coincide con la del médico" **no es expresable en el esquema**: va al `DomainService` y se documenta. |
| `IndicacionPrestacion` | Inmutable: sin `setter` de negocio. Unicidad `(prestacion, nombre)` entre activos. |
| `AgendaMedico` | **Sin campos de baja.** No solapamiento de períodos del mismo médico: `EXCLUDE USING gist (medico_id WITH =, tstzrange(fecha_hora_inicio_vigencia, fecha_hora_fin_vigencia) WITH &&)`. Requiere la extensión `btree_gist`. |
| `AgendaDia` | Unicidad `(agendaMedico, fecha)` entre activos. |
| `AgendaHorarios` | **No persistir `duracionTurno`**: se deriva de `horaHasta - horaDesde`. `CHECK (hora_desde < hora_hasta)`. `estaOcupada` con `DEFAULT false NOT NULL`. Índice `idx_agenda_horarios_fecha_limite_reserva` para el barrido de disponibilidad. |
| `ObraSocialPlanPrestacion` | `CHECK` de coherencia de `modalidadCobertura` con `porcentajeCobertura` y `coseguro`. Unicidad `(plan, prestacion)` entre activos. |
| `ObraSocialPaciente` | Inmutable. Apunta a `Plan`, **no** a `ObraSocial`: la obra social se llega navegando. Sin FK redundante. |
| `Usuario` | FK nullable a `Medico` y a `Admin`, con `CHECK ((medico_id IS NULL) <> (admin_id IS NULL))`. `UNIQUE (medico_id)` y `UNIQUE (admin_id)` planos, **no** parciales. `mail` único entre activos. |
| `UsuarioRol` | **Sin campos de baja**, se gobierna por vigencia. `CHECK (fecha_fin_vigencia IS NULL OR fecha_fin_vigencia > fecha_inicio_vigencia)`. La regla "un usuario no tiene dos asignaciones vigentes del mismo rol" va al `DomainService`. |

---

## Fase 4 — Turno y su historia

Orden: **`Turno` → `HistoricoEstadoTurno` → `IndicacionPrestacionTurno`**

### `Turno`

La entidad con más relaciones del modelo. Seis FK más la auto-referencia:

```
paciente            NOT NULL
medico              NOT NULL
prestacion          NOT NULL
agenda_horarios     NOT NULL
obra_social_paciente NULL      -- vacío cuando tipoCobertura = PARTICULAR
turno_origen        NULL       -- auto-referencia, rol turnoOrigen
```

**No lleva FK a `MedicoPrestacion`.** Se eliminó a propósito: el par `(medico, prestacion)` se valida buscando una `MedicoPrestacion` activa en el `DomainService`.

Sin campos de baja: el ciclo de vida se gobierna por estados.

`UNIQUE (codigo)` **plano**, no parcial: es la excepción a la regla de unicidad entre activos, porque un turno cancelado sigue siendo histórico y su código no se reutiliza.

`CHECK` a incluir:

```sql
CHECK ((tipo_cobertura = 'OBRA_SOCIAL') = (obra_social_paciente_id IS NOT NULL))
  -- ck_turno_cobertura_coherente
CHECK (fecha_hora_recordatorio_confirmacion < fecha_limite_confirmacion)
  -- ck_turno_recordatorio_antes_de_limite
CHECK (fecha_limite_anuncio_temprano <= fecha_hora_inicio
   AND fecha_hora_inicio <= fecha_limite_anuncio_tardio)
  -- ck_turno_ventana_anuncio
CHECK (monto_a_pagar >= 0)
  -- ck_turno_monto
```

Índices para el scheduler: `idx_turno_fecha_limite_confirmacion`, `idx_turno_fecha_limite_validacion`, `idx_turno_fecha_limite_anuncio_tardio`.

### `HistoricoEstadoTurno`

Sin campos de baja. `fechaHoraFin` nullable.

Índice único parcial que garantiza el tramo vigente único:

```sql
CREATE UNIQUE INDEX uq_historico_estado_turno_vigente
  ON historico_estado_turno (turno_id) WHERE fecha_hora_fin IS NULL;
```

Es la constraint que hace imposible que un turno tenga dos estados vigentes a la vez, incluso con concurrencia entre el paciente y el scheduler. **No omitirla**: es la defensa real contra la condición de carrera descrita en la sección 7 del documento de reglas.

### `IndicacionPrestacionTurno`

```
turno                 NOT NULL
indicacion_prestacion NOT NULL
validado_por          NULL      -- rol validadoPor, apunta a Usuario
```

`CHECK ((fecha_hora_validacion IS NULL) = (validado_por_id IS NULL))` → `ck_indicacion_prestacion_turno_validacion_coherente`.

Unicidad `(turno, indicacionPrestacion)` entre activos. Índice `idx_indicacion_prestacion_turno_pendiente ON (turno_id) WHERE fecha_hora_validacion IS NULL` para la consulta que decide si el turno sale de `Espera de Validación`.

---

## Fase 5 — Verificación

Por cada entidad:

- [ ] `Domain/<Entidad>.java` extiende `Auditable`.
- [ ] Los campos de baja están **declarados en la clase**, no heredados.
- [ ] Todo String obligatorio lleva `@NotBlank` (no `@NotNull`) y `@Size(max = n)` con el mismo `n` del `varchar(n)`.
- [ ] Cada unicidad del JSON tiene su índice único parcial `WHERE deleted_at IS NULL`, salvo las cuatro excepciones documentadas.
- [ ] Cada invariante del JSON tiene su `CHECK` con nombre `ck_<tabla>_<regla>`, o está documentado por qué solo vive en el `DomainService`.
- [ ] Todo objeto de esquema tiene nombre explícito según la convención de `ARQUITECTURA.md §6`; ninguno queda autogenerado por Liquibase.
- [ ] Las relaciones respetan la navegabilidad del diagrama: sin FK redundante.
- [ ] Los enums se persisten como texto.
- [ ] El `<include>` está en `master.xml` una sola vez.

Al cierre de cada fase:

- [ ] `./mvnw spring-boot:run` con perfil `dev` arranca y Liquibase corre limpio, sin error de sintaxis ni de checksum.
- [ ] Un test de contexto valida que el esquema generado por Hibernate en modo `validate` coincide con el creado por Liquibase.

Al cierre total:

- [ ] Las 23 entidades existen y compilan.
- [ ] La carga inicial de `EstadoTurno` tiene los nueve estados, con `En Transcurso` y sin `Iniciado`.
- [ ] Los dos roles de sistema existen con `es_sistema = true`.
- [ ] La instancia única de `Clinica` existe con sus cuatro parámetros cargados.
- [ ] Un test de integración crea un turno completo de punta a punta: paciente, médico, prestación con tolerancias, agenda, slot, turno, historial e indicaciones.

---

## Orden completo de un vistazo

```
Fase 0  Auditable · enums · converter de Duration
Fase 1  Clinica · Especialidad · TipoIndicacionPrestacion · Paciente · ObraSocial · Admin · EstadoTurno
Fase 2  Medico · Prestacion · Plan · Rol (+ rol_permiso)
Fase 3  MedicoPrestacion · IndicacionPrestacion · AgendaMedico · AgendaDia · AgendaHorarios ·
        ObraSocialPlanPrestacion · ObraSocialPaciente · Usuario · UsuarioRol
Fase 4  Turno · HistoricoEstadoTurno · IndicacionPrestacionTurno
Fase 5  Verificación
```

## Qué queda fuera de este plan

La skill `domain-schema-generator` cubre entidad y esquema. Lo demás (`Records`, `Mapper`, `DomainService`, `App`, `Controller`) lo genera `springboot-feature-generator` en una etapa posterior. Las reglas que este plan marca como "va al `DomainService`" son la entrada de esa etapa:

- Unicidad entre activos, con exclusión de la propia instancia en la modificación.
- Coincidencia de especialidad entre médico y prestación.
- No solapamiento de períodos de agenda.
- Un solo `UsuarioRol` vigente por par `(usuario, rol)`.
- Validación de que el destino de cada relación esté activo.
- Cálculo del monto a pagar.
- Cálculo de las siete fechas del turno al crearlo.
- Transiciones de estado con cierre y apertura de `HistoricoEstadoTurno`.
