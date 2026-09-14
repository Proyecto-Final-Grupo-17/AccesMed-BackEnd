# Feature: Usuarios, Roles y Permisos

## Contexto

- **Para qué es**: quién puede entrar al sistema (`Usuario`, la credencial) y qué puede
  hacer una vez adentro (`Rol` → conjunto de `Permiso`). Cada endpoint exige un `Permiso`
  puntual (`@PreAuthorize("hasAuthority('...')")`); un `Usuario` tiene uno o más `Rol`
  vigentes, y cada `Rol` agrupa un conjunto de `Permiso`. Las authorities de un usuario se
  recalculan **en cada request** desde la base (nunca desde el JWT, que es liviano — ver
  `Docs/Features/Autenticacion.md`), así que un cambio de rol, de permisos de un rol, o una
  baja de usuario tienen efecto inmediato — no hay que esperar a que expire ningún token.

- **Para qué sirve**: `Usuario` es la credencial de acceso de un `Medico` o de un `Admin`
  (nunca de los dos, nunca de ninguno — ver §Usuario). Hay tres roles de sistema fijos
  (`Medico`, `Admin`, `SuperAdmin`) que cubren el día a día de la clínica, y por encima de
  ellos el `SuperAdmin` puede crear **roles dinámicos** con un subconjunto de permisos a
  medida (ej. un rol "Facturación" que solo consulta turnos y obras sociales, sin tocar
  nada más).

- **Quiénes la usan**: el `SuperAdmin` gestiona usuarios, roles y asignaciones desde el
  panel; el resto de los roles simplemente heredan lo que su rol les da.

- **Cómo funciona el mecanismo por debajo** (JWT, filtros, `UserDetails`, etc.) está
  documentado aparte, pensado para aprender Spring Security a través de este proyecto: ver
  `Docs/Security.md`. Este documento es el contrato funcional (qué endpoints hay, qué
  reciben, qué reglas de negocio aplican); `Security.md` es el mecanismo.

---

## Usuario

`Usuario` es **la credencial de acceso**, no una persona: no tiene nombre, apellido ni
ningún dato personal propio — solo `mail` (el que se usa para loguearse) y
`passwordHash` (nunca texto plano). Apunta a **uno** de estos dos, nunca a los dos ni a
ninguno (invariante reforzado con un `CHECK` en el esquema, `ck_usuario_medico_xor_admin`):

- Un `Medico` (`Usuario.medicoId`) — el usuario del panel para un médico.
- Un `Admin` (`Usuario.adminId`) — el usuario del panel para personal administrativo,
  incluidos los `SuperAdmin` (la diferencia entre `Admin` y `SuperAdmin` está en el `Rol`
  asignado, no en la entidad `Admin`/`Usuario`).

**No hay endpoint de lectura para `Usuario`** (no existe `GetUsuarioResponse` ni
`UsuarioQueryService`) — a propósito: nadie necesita "listar usuarios" como tal, se opera
sobre el `Medico`/`Admin` dueño, o sobre el `Rol` que tiene asignado (`GET /Rol/{rolId}`
no lista usuarios tampoco; hoy no hay una pantalla de "todos los usuarios del sistema").

### Ciclo de vida

Un `Usuario` nunca se crea suelto — siempre nace atado a un `Medico` o a un `Admin` ya
existente, con la contraseña puesta en un hash **no utilizable** (un UUID aleatorio
hasheado) hasta que la persona la define de verdad activando la cuenta por mail:

| Acción | Endpoint | Permiso | Qué pasa |
|---|---|---|---|
| Alta con médico nuevo | `POST /accesmed-api/Medico/Medico` con `crearUsuario: true` | `MED_ALTA` + `USER_ALTA` | Crea el `Medico` y, en la misma transacción, su `Usuario` pendiente de activación — ver `Docs/Features/Medico.md`. |
| Alta con admin nuevo | `POST /accesmed-api/Admin/Admin` | `USER_ALTA` | El `Admin` **siempre** se crea con usuario — no existe alta de Admin sin credencial. |
| Asignar/reemplazar usuario de un médico | `POST /accesmed-api/Usuario/AsignarMedico/{id}` | `USER_ALTA` | Alta inicial (si el médico todavía no tenía usuario) o reemplazo (cuenta comprometida: da de baja el anterior y crea uno nuevo). |
| Asignar/reemplazar usuario de un admin | `POST /accesmed-api/Usuario/AsignarAdmin/{id}` | `USER_ALTA` | Mismo mecanismo que arriba, para un `Admin`. |
| Baja (siempre en cascada, nunca directa) | `DELETE /Medico/Medico/{id}` o `DELETE /Admin/Admin/{id}` | `MED_BAJA` / `USER_BAJA` | Dar de baja al `Medico`/`Admin` desactiva automáticamente su `Usuario` — no hay un endpoint para dar de baja solo el `Usuario`. La baja de `Usuario` **no** cascadea al revés. |

**Request para asignar/reemplazar — `AsignarUsuarioRequest`** (el id del médico/admin
viaja en la ruta, no en el body):

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `mail` | String (formato email) | Sí | El mail de login del usuario nuevo. |

Al asignar/reemplazar: si el médico/admin ya tenía un usuario activo, se le da de baja
(`softDeleteUsuario`) antes de crear el nuevo — nunca conviven dos usuarios activos para
la misma persona. El usuario nuevo queda **pendiente de activación**: recibe un mail con
un link de un solo uso (`RestablecerContrasena`, mismo mecanismo que "olvidé mi
contraseña" — ver `Docs/Features/Autenticacion.md`) para poner su propia contraseña, y se
le asigna automáticamente el rol de sistema que corresponde (`Medico` o `Admin`).

### Reglas de negocio

- Asignar el rol **`Medico`** a un usuario exige que tenga `medicoId` (no `adminId`).
- Asignar **cualquier otro rol** (incluido `Admin`, `SuperAdmin`, o uno dinámico) exige que
  tenga `adminId` (no `medicoId`). Un usuario vinculado a un `Medico` nunca puede tener un
  rol que no sea `Medico`.
- Dar de baja un `Medico`/`Admin` revoca **todos** los refresh tokens vigentes del usuario
  y le manda un mail avisando la baja — efecto inmediato sobre cualquier sesión abierta,
  no solo sobre logins futuros (el detalle técnico de por qué es inmediato está en
  `Docs/Security.md §5.4`).

---

## Roles de sistema

| Rol | `esSistema` | Reglas |
|-----|-------------|--------|
| `Medico` | `true` | Se asigna solo si `Usuario.medico != null`. |
| `Admin` | `true` | Se asigna solo si `Usuario.admin != null`. Mutuamente excluyente con `SuperAdmin`. |
| `SuperAdmin` | `true` | Igual que `Admin` (requiere `Usuario.admin != null`), lo reemplaza. **Nunca** se asigna ni se revoca vía la aplicación — código `SUPERADMIN_NO_ASIGNABLE` (422) si se intenta por `POST /Rol/{rolId}/AsignarUsuario` o su `DELETE`. Solo existe por el seed inicial de la base. |

Los roles de sistema son **únicos y no editables/no bajables**: no se puede `PUT`/`DELETE`
sobre `Medico`, `Admin` o `SuperAdmin` — esas operaciones están reservadas a roles
dinámicos (`esSistema = false`).

## Catálogo de permisos y quién los tiene por default

Permisos con **✔** entre paréntesis indican alcance recortado (ver "Scopes" más abajo).

| Permiso | Medico | Admin | SuperAdmin | Categoría |
|---|:---:|:---:|:---:|---|
| `CONFIG_CONSULTAR` | | ✔ | ✔ | Datos maestros |
| `CONFIG_MODIFICAR` | | | ✔ | Identidad/accesos — delegable |
| `MED_ALTA` / `MED_MODIFICAR` / `MED_BAJA` | | | ✔ | Identidad de médico — delegable |
| `MED_CONSULTAR` | ✔ | ✔ | ✔ | Operación diaria |
| `MED_ASIGNAR_PRESTACION` | | ✔ | ✔ | Operación diaria |
| `PREST_ALTA` / `PREST_MODIFICAR` / `PREST_BAJA` | | | ✔ | Datos maestros — delegable |
| `PREST_CONSULTAR` | ✔ | ✔ | ✔ | Operación diaria |
| `ESP_ALTA` / `ESP_MODIFICAR` / `ESP_BAJA` | | | ✔ | Datos maestros — delegable |
| `ESP_CONSULTAR` | | ✔ | ✔ | Operación diaria |
| `AGEN_CONFIGURAR` / `AGEN_CONSULTAR` | ✔ (propia) | ✔ | ✔ | Operación diaria |
| `PACIENTE_ALTA` / `PACIENTE_MODIFICAR` / `PACIENTE_BAJA` | | ✔ | ✔ | Operación diaria |
| `PACIENTE_CONSULTAR` | ✔ (con turno) | ✔ | ✔ | Operación diaria |
| `OS_ALTA` / `OS_MODIFICAR` / `OS_BAJA` | | | ✔ | Datos maestros — delegable |
| `OS_CONSULTAR` | | ✔ | ✔ | Operación diaria |
| `TURN_REGISTRAR` / `TURN_VALIDAR` / `TURN_CONFIRMAR` | | ✔ | ✔ | Operación diaria |
| `TURN_REPROGRAMAR` / `TURN_CANCELAR` / `TURN_ANUNCIAR` / `TURN_INICIAR` / `TURN_FINALIZAR` / `TURN_CONSULTAR` | ✔ (propios) | ✔ | ✔ | Operación diaria |
| `USER_ALTA` / `USER_MODIFICAR` / `USER_BAJA` / `USER_CONSULTAR` | | | ✔ | Identidad/accesos — delegable |
| `AUTZ_ROL_ALTA` / `AUTZ_ROL_MODIFICAR` / `AUTZ_ROL_BAJA` / `AUTZ_ROL_ASIGNAR` / `AUTZ_ROL_CONSULTAR` | | | ✔ | Identidad/accesos — delegable |
| `AUDITORIA_CONSULTAR` | | | ✔ | Auditoría — exclusivo SuperAdmin |

**Delegable** quiere decir: aunque el seed inicial se lo da solo a `SuperAdmin`, un
`SuperAdmin` puede crear un rol dinámico que incluya ese permiso y asignárselo a un
usuario — por ejemplo, un rol "Editor de catálogo" con `PREST_ALTA`/`PREST_MODIFICAR`/
`ESP_ALTA`/`ESP_MODIFICAR` pero sin `USER_*` ni `AUDITORIA_CONSULTAR`. `AUDITORIA_CONSULTAR`
es la única categoría que el diseño marca como **exclusiva** de SuperAdmin por sensibilidad
del dato, aunque técnicamente nada en el código impide delegarla — es una convención de
uso, no una restricción dura.

## Scopes: cuándo "tener el permiso" no alcanza

Tres permisos (`AGEN_CONSULTAR`/`AGEN_CONFIGURAR`, `PACIENTE_CONSULTAR`, `TURN_*`) están
recortados para `Medico` con `AlcanceMedicoService`: el permiso da el gate de entrada, pero
además el `QueryService`/`App` filtra por el `medicoId` de quien hace el request — un
médico nunca ve turnos, agenda o pacientes que no sean los suyos, aunque tenga el permiso
`TURN_CONSULTAR`. Ver el detalle de este mecanismo en
`Docs/ARQUITECTURA.md` ("Autorización en tres capas").

## Roles dinámicos: CRUD

| Acción | Endpoint | Permiso |
|---|---|---|
| Crear rol | `POST /accesmed-api/Rol/Rol` | `AUTZ_ROL_ALTA` |
| Actualizar rol (nombre y/o permisos) | `PUT /accesmed-api/Rol/Rol/{id}` | `AUTZ_ROL_MODIFICAR` |
| Dar de baja rol | `DELETE /accesmed-api/Rol/Rol/{id}` | `AUTZ_ROL_BAJA` |
| Obtener rol por id | `GET /accesmed-api/Rol/Rol/{id}` | `AUTZ_ROL_CONSULTAR` |
| Listar roles activos | `GET /accesmed-api/Rol/Rol` | `AUTZ_ROL_CONSULTAR` |
| Asignar rol a un usuario | `POST /accesmed-api/Rol/Rol/{rolId}/AsignarUsuario` | `AUTZ_ROL_ASIGNAR` |
| Revocar rol de un usuario | `DELETE /accesmed-api/Rol/Rol/{rolId}/RevocarUsuario/{usuarioId}` | `AUTZ_ROL_ASIGNAR` |

**Request para crear/actualizar — `CreateRolRequest`/`UpdateRolRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `nombre` | String (máx. 60) | Sí | Único entre roles activos. |
| `permisos` | `Set<Permiso>` | Sí, no vacío | Cualquier valor del catálogo de arriba, con las mismas restricciones de asignación (`Medico` exige `Usuario.medico != null`, cualquier otro rol exige `Usuario.admin != null`). |

**Request para asignar rol — `AsignarRolRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `usuarioId` | UUID | Sí | El usuario al que se le asigna el rol (viaja el `rolId` en la ruta). |

**Errores posibles:**
- `SUPERADMIN_NO_ASIGNABLE` (422): se intentó asignar o revocar el rol `SuperAdmin` vía la aplicación — no es negociable, ni siquiera un `SuperAdmin` puede hacerlo por acá.
- `USUARIO_SIN_MEDICO_VINCULADO` (422): se intentó asignar el rol `Medico` a un usuario que no tiene `Medico` vinculado.
- `USUARIO_SIN_ADMIN_VINCULADO` (422): se intentó asignar cualquier rol que no sea `Medico` a un usuario sin `Admin` vinculado.

## Ejemplo por rol: mismo flujo de turnos, distinto alcance

Tomando el mismo flujo — "ver y actuar sobre turnos" — desde los tres roles:

- **Un Médico** se loguea y hace `GET /accesmed-api/Turno/Turno`. Tiene `TURN_CONSULTAR`,
  pero `AlcanceMedicoService` acota la consulta a los turnos donde él es el médico — nunca
  ve turnos de otros médicos, sin importar el filtro que mande. Puede confirmar, reprogramar,
  cancelar, anunciar, iniciar y finalizar **sus propios** turnos; no puede registrar un
  turno nuevo ni validarlo (`TURN_REGISTRAR`/`TURN_VALIDAR` son de `Admin`/`SuperAdmin`).

- **Un Admin** se loguea y hace el mismo `GET`. Tiene `TURN_CONSULTAR` sin scope: ve todos
  los turnos de la clínica, de cualquier médico. Además gestiona pacientes
  (`PACIENTE_ALTA/MODIFICAR/BAJA`) y obras sociales de solo lectura (`OS_CONSULTAR`), pero
  **no puede** editar el catálogo de Prestaciones (`PREST_ALTA`/`MODIFICAR`/`BAJA` son de
  SuperAdmin) ni dar de alta médicos (`MED_ALTA` es de SuperAdmin) — puede consultarlos,
  no crearlos.

- **Un SuperAdmin** ve y hace todo lo del Admin, más lo que el Admin no puede: editar el
  catálogo de Prestaciones/Especialidades/ObrasSociales, dar de alta médicos, y — el caso
  que distingue a este rol — crear un rol dinámico nuevo, por ejemplo **"Facturación"**,
  con solo `TURN_CONSULTAR` y `OS_CONSULTAR` (sin scope de médico, porque quien lo recibe
  tiene `Usuario.admin != null`), y asignárselo a un usuario que necesita ver turnos y
  cobertura para armar facturas, sin darle acceso a nada más del panel.

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `Docs/FRONTEND-GUIA.md §1`. Para entender el mecanismo de autenticación y
> autorización por debajo de este contrato (JWT, filtros, `UserDetails`, los tres
> mecanismos de autorización), ver `Docs/Security.md`.
