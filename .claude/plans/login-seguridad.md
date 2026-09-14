# Plan — Login / Seguridad (JWT, roles dinámicos, permisos) — AccesMed

## Contexto

El backend tiene el dominio de seguridad modelado y migrado en BD (`Usuario`, `Rol`,
`Permiso`, `UsuarioRol`, `Admin`) pero cero capa de aplicación: sin JWT, sin
`PasswordEncoder`, sin autorización real (`/accesmed-api/**` está `permitAll()` hoy). Esta
conversación (larga, con muchas decisiones de diseño ya cerradas y confirmadas por el
usuario) define el sistema completo de autenticación y autorización, con roles dinámicos
(`Medico`/`Admin`/`SuperAdmin` de sistema + roles custom delegables) y permisos granulares.
Este plan lo baja a una secuencia de implementación concreta.

**Ejecución**: cada fase se delega a subagentes en modelo **Haiku** (trabajo mecánico,
sigue patrones ya establecidos en el código existente — `PrestacionController`/`App`/
`DomainService` como plantilla de referencia). Cada fase es idealmente un commit propio.
Dado el tamaño, esto abarca varias sesiones, no una sola pasada.

> ⚠️ **REGLA DE LIQUIBASE — LEER ANTES DE TOCAR CUALQUIER CHANGELOG (aplica a TODAS las
> fases que editan migraciones, especialmente la Fase 0)**
>
> Este proyecto está en ambiente **`dev`**. En `dev`, los changesets que **ya se
> ejecutaron** se pueden **editar directamente in situ** — se modifica el contenido
> XML/SQL que ya está dentro del `<changeSet>` existente (mismo `id`, mismo archivo).
> **NO es la regla estándar de Liquibase** (que diría "nunca toques un changeset ya
> corrido, agregá uno nuevo") — acá se pisa esa regla a propósito porque la base de dev
> se recrea entera después de cada edición, así que no hay riesgo de desincronizar el
> tracking.
>
> Concretamente para `20260731215500-Rol.xml`: **NO agregar `<changeSet>` nuevos** con
> id `2026073121550X` para ajustar el CHECK o los INSERT de permisos — hay que **editar
> el contenido de los 3 changesets que ya están** (`20260731215500-added-table-Rol`,
> `20260731215501-added-table-RolPermiso`, `20260731215502-inserted-data-RolSistema`),
> dejando los mismos 3 `id` de siempre. Un changeset nuevo es correcto **solo** cuando la
> tabla/entidad es genuinamente nueva (`RefreshToken`, `PasswordResetToken` — esas sí van
> en archivos y changesets propios, eso no cambia).
>
> Recreación de la base después de cualquier edición: `docker compose -f
> docker/dev/docker-compose.yml down -v && docker compose -f docker/dev/docker-compose.yml up -d`.

---

## Decisiones de diseño (ya cerradas con el usuario — no reabrir sin motivo)

**Arquitectura de carpetas**: `Security/` se aísla SOLO como "mecanismo reemplazable"
(Jwt, Config, Auth, y los tokens `RefreshToken`/`PasswordResetToken`, que son artefactos
de sesión, no datos de negocio). `Usuario`, `Rol`, `UsuarioRol`, `Admin` (el "quién existe
y qué puede hacer") quedan en el núcleo, junto a `Medico`/`Turno`/etc., con sus
Controller/App/DomainService/Repository de siempre. El cruce núcleo→Security se hace vía
un puerto (`GestionUsuarioPort`, interfaz en el núcleo, implementada en Security) — ningún
`App` del núcleo importa nada de `Security` directamente.

**JWT**: liviano (`sub`=usuarioId, sin roles/permisos embebidos) — las authorities se
recalculan en cada request desde BD. Access token 30 min, refresh token 7 días sin
rotación, persistido hasheado (`RefreshToken`). Logout = revocar el refresh token.

**Autorización, tres mecanismos**:
1. `@PreAuthorize("hasAuthority(...)")` — gate de entrada declarativo, un permiso fijo.
2. `AlcanceMedicoService` (`resolveMedicoId`, `validateMedicoPropietario`) — scope sobre
   recursos propios del médico (Turno, Agenda, Paciente). **Vive en `Security/Services/Utils/`**,
   no en el núcleo: es parte del mecanismo de autorización (interpreta lo que calculó
   `UsuarioDetailsService`), misma familia que `@PreAuthorize`/el filtro JWT. Es agnóstico
   del dominio (no conoce Turno/Paciente/Agenda) — la aplicación específica de cada
   dominio la hace el `QueryService`/`App` que lo llama, en el núcleo.
3. `AutorizacionService.requireAuthority(...)` — permiso extra condicional dentro de un
   método (ej. crear médico + usuario en el mismo request). Mismo lugar que el anterior:
   `Security/Services/Utils/`.

**Roles de sistema**: `Medico`, `Admin`, `SuperAdmin` — `esSistema=true`, únicos, no
editables/no bajables. `SuperAdmin` reemplaza a `Admin` (mutuamente excluyentes) y
**nunca** se asigna/revoca vía la app (guarda incondicional) — solo el seed hardcodeado.
Asignar rol `"Medico"` exige `usuario.medico != null`; cualquier otro rol exige
`usuario.admin != null`.

**Catálogo de permisos** (ver tabla completa más abajo, Fase 0) — categorías de
sensibilidad: identidad/accesos (`USER_*`, `AUTZ_ROL_*`) y identidad de médico
(`MED_ALTA/MODIFICAR/BAJA`) son SuperAdmin por defecto y delegables vía rol dinámico;
datos maestros (`PREST/OS/ESP` escritura, `CONFIG_MODIFICAR`) ídem, con lectura abierta a
Admin; auditoría (`AUDITORIA_CONSULTAR`) exclusiva SuperAdmin; el resto es operación
diaria (Admin + SuperAdmin, Médico recortado a lo propio).

**Alta/gestión de personas**: `Medico` se crea solo (ahora `MED_ALTA` es SuperAdmin
delegable), con un campo opcional para crear `Usuario` en el mismo request (exige además
`USER_ALTA`, mail = `Medico.email`). `Admin` se crea atómico con `Usuario` siempre (mail =
`Admin.email`). `POST /Usuario/AsignarMedico/{id}` y `POST /Usuario/AsignarAdmin/{id}`
asignan/reasignan usuario (alta inicial + reemplazo tras cuenta comprometida). Baja de
`Medico`/`Admin` → cascada de baja de su `Usuario` (vía `GestionUsuarioPort`). Baja de
`Usuario` no cascadea al revés.

**Mail**: reutiliza `Services/Utils/MailService.java` (ya existe, usado hoy para turnos —
ver `Notifications/Canales/CanalNotificacionTurnoMail.java` como plantilla de estilo).
`PasswordResetToken` sirve tanto para activación de cuenta nueva como recuperación de
contraseña. `POST /Auth/OlvideContrasena` siempre responde 200 (no revela si el mail
existe). `POST /Auth/RestablecerContrasena` consume el token y revoca los refresh tokens
vigentes del usuario.

**Auditoría**: `AUDITORIA_CONSULTAR` (SuperAdmin exclusivo). Campo `auditoria` (nullable)
embebido en cada Response de lectura existente, vía un `AuditoriaResponse` compartido,
poblado por el `QueryService` según permiso. Toca los ~15 controllers de lectura
existentes.

---

## Fase 0 — Migraciones Liquibase y enum `Permiso`

Editar **en el lugar** (convención `dev`: se edita el changeset ya ejecutado, se recrea la
base — confirmado por el usuario; **no se agregan `<changeSet>` nuevos** a este archivo)
`src/main/resources/liquibase-db-changelogs/changelogs/20260731215500-Rol.xml`:

1. Ampliar el `CHECK ck_rol_permiso_permiso` y el enum `Domain/Permiso.java` con los
   permisos nuevos: `ESP_ALTA`, `ESP_MODIFICAR`, `ESP_BAJA`, `ESP_CONSULTAR`,
   `CONFIG_CONSULTAR`, `AUDITORIA_CONSULTAR`.
2. Agregar el `INSERT` del rol de sistema `SuperAdmin` (`es_sistema=true`), con **todo**
   el catálogo de permisos.
3. Redistribuir el `INSERT` de permisos de `Medico`: agregar `PACIENTE_CONSULTAR`,
   `AGEN_CONFIGURAR`, `TURN_REPROGRAMAR`, `TURN_CANCELAR`, `TURN_ANUNCIAR`; sacar
   `TURN_VALIDAR` (ya no le corresponde).
4. Redistribuir el `INSERT` de permisos de `Admin`: sacar `CONFIG_MODIFICAR`,
   `MED_ALTA`, `MED_MODIFICAR`, `MED_BAJA`, `PREST_ALTA`, `PREST_MODIFICAR`,
   `PREST_BAJA`, `OS_ALTA`, `OS_MODIFICAR`, `OS_BAJA`, `USER_*` (las 4), `AUTZ_ROL_*`
   (las 4); agregar `CONFIG_CONSULTAR`, `ESP_CONSULTAR`.

Tabla completa de referencia (para no perder ningún permiso al editar):

| Permiso | Medico | Admin | SuperAdmin |
|---|---|---|---|
| `CONFIG_CONSULTAR` (nuevo) | | ✔ | ✔ |
| `CONFIG_MODIFICAR` | | | ✔ |
| `MED_ALTA/MODIFICAR/BAJA` | | | ✔ |
| `MED_CONSULTAR`, `MED_ASIGNAR_PRESTACION` | ✔(consultar) | ✔ | ✔ |
| `PREST_ALTA/MODIFICAR/BAJA` | | | ✔ |
| `PREST_CONSULTAR` | ✔ | ✔ | ✔ |
| `ESP_ALTA/MODIFICAR/BAJA` (nuevo) | | | ✔ |
| `ESP_CONSULTAR` (nuevo) | | ✔ | ✔ |
| `AGEN_CONFIGURAR`, `AGEN_CONSULTAR` | ✔(scope propio) | ✔ | ✔ |
| `PACIENTE_ALTA/MODIFICAR/BAJA` | | ✔ | ✔ |
| `PACIENTE_CONSULTAR` | ✔(scope: con turno) | ✔ | ✔ |
| `OS_ALTA/MODIFICAR/BAJA` | | | ✔ |
| `OS_CONSULTAR` | | ✔ | ✔ |
| `TURN_REGISTRAR`, `TURN_VALIDAR`, `TURN_CONFIRMAR` | | ✔ | ✔ |
| `TURN_REPROGRAMAR/CANCELAR/ANUNCIAR/INICIAR/FINALIZAR/CONSULTAR` | ✔(scope propio) | ✔ | ✔ |
| `USER_*` (4) | | | ✔ |
| `AUTZ_ROL_*` (4) | | | ✔ |
| `AUDITORIA_CONSULTAR` (nuevo) | | | ✔ |

Crear también (changelog nuevo, entidades nuevas, siguiendo el patrón de
`20260731220300-Usuario.xml`): `YYYYMMDDHHMMSS-RefreshToken.xml` y
`YYYYMMDDHHMMSS-PasswordResetToken.xml`, ambas con FK a `usuario`, y su `<include>` en
`master.xml` después del último (`20260731220700-IndicacionPrestacionTurno.xml`).

---

## Fase 1 — `Security/Domain` + `Security/Repositories` + `Security/Services/DomainServices` (tokens)

Solo los artefactos de sesión, aislados en `Security/` como corresponde:

- `Security/Domain/RefreshToken.java`, `PasswordResetToken.java` — extends `Auditable`,
  `ManyToOne` a `Usuario` (del núcleo — Security sí puede importar del núcleo, es la
  dirección permitida), `tokenHash`, `expiresAt`, `usedAt`/`revokedAt`.
- `Security/Repositories/RefreshTokenRepository.java`, `PasswordResetTokenRepository.java`
  — mismo patrón que `Repositories/MedicoRepository.java` (`findByTokenHashAndRevokedAtIsNull`, etc.).
- `Security/Services/DomainServices/RefreshTokenDomainService.java`,
  `PasswordResetTokenDomainService.java` — generar (hash + expiración), validar, revocar/marcar usado.

---

## Fase 2 — Núcleo: `Usuario`/`Rol`/`UsuarioRol`/`Admin` (Repository → DomainService → Mapper)

Las entidades ya existen (`Domain/Usuario.java`, `Rol.java`, `UsuarioRol.java`,
`Admin.java`) — falta toda la capa de aplicación, en el núcleo, patrón idéntico al de
`MedicoRepository`/`MedicoDomainService`/`MedicoMapper`:

- `Repositories/UsuarioRepository.java` (`findByMailAndDeletedAtIsNull`,
  `findByIdAndDeletedAtIsNull`, `findByMedicoIdAndDeletedAtIsNull`,
  `findByAdminIdAndDeletedAtIsNull`), `RolRepository.java`, `UsuarioRolRepository.java`
  (con la query JPQL de "roles vigentes por usuario" ya diseñada), `AdminRepository.java`.
- `Services/DomainServices/UsuarioDomainService.java`, `RolDomainService.java` (con la
  guarda `esSistema` bloqueando update/delete), `UsuarioRolDomainService.java` (con las
  dos guardas: vínculo médico/admin según el rol, y bloqueo incondicional de
  asignar/revocar `SuperAdmin`), `AdminDomainService.java`.
- `Services/Mappers/UsuarioMapper.java`, `RolMapper.java`, `AdminMapper.java`.
- `Services/QueryServices/` solo donde haga falta listar/buscar (Rol, Admin, Usuario —
  para pantallas de gestión).

---

## Fase 3 — Servicios cross-cutting

- `Security/Services/Utils/AlcanceMedicoService.java` — `resolveMedicoId(UsuarioDetails, Long)`,
  `validateMedicoPropietario(UsuarioDetails, Long)`.
- `Security/Services/Utils/AutorizacionService.java` — `requireAuthority(UsuarioDetails, Permiso)`.
- `Application/Ports/GestionUsuarioPort.java` (interfaz, **en el núcleo**): `asignarUsuarioAMedico`,
  `asignarUsuarioAAdmin`, `desactivarUsuarioDeMedico`, `desactivarUsuarioDeAdmin`.

---

## Fase 4 — `Security/Jwt` + `Security/Config`

- `Security/Jwt/JwtService.java` — generar/validar/extraer claims (`jjwt` ya en el
  `pom.xml`). Config vía `accesmed.jwt.secret`/`accesmed.jwt.expiration-minutes` en
  `application.yml`, siguiendo el patrón de `${VAR}` ya usado para `MAIL_USERNAME` (secreto
  obligatorio, sin default) y `accesmed.scheduler.*` (con default) para la expiración.
- `Security/Jwt/UsuarioDetails.java` (adapter `UserDetails`), `UsuarioDetailsService.java`
  (`UserDetailsService`, recorre `UsuarioRol` vigentes → `Rol.permisos`).
- `Security/Jwt/JwtAuthenticationFilter.java` (`OncePerRequestFilter`).
- `Security/Config/SecurityFilterChainConfig.java` — reemplaza el `Config/SecurityConfig.java`
  actual (placeholder): `anyRequest().authenticated()`, `STATELESS`, filtro JWT
  registrado, `@EnableMethodSecurity`, `PasswordEncoder` (BCrypt) y `AuthenticationManager`
  como beans. **Borrar** `Config/SecurityConfig.java` viejo.

---

## Fase 5 — Auth flow completo

- `Security/Application/AuthApp.java` — `login`, `refresh`, `logout`,
  `olvideContrasena` (siempre 200), `restablecerContrasena` (revoca refresh tokens).
- `Security/Application/GestionUsuarioAdapter.java` (implementa `GestionUsuarioPort`,
  delega en `UsuarioApp` del núcleo — ver Fase 6).
- `Security/Controllers/AuthController.java` — `POST /Auth/Login`, `/Auth/Refresh`,
  `/Auth/Logout`, `/Auth/OlvideContrasena`, `/Auth/RestablecerContrasena`. Estas 5 rutas
  van en `permitAll()` en el filter chain (excepto Logout, que requiere estar autenticado).
- `Security/Records/Auth/` — records de cada request/response.
- Mails de activación/baja/recuperación reutilizando `MailService`, mismo estilo que
  `CanalNotificacionTurnoMail.java` (texto plano con `String.format`, o HTML si se prefiere
  algo más prolijo para un link — a criterio de quien implemente, `MailService` soporta ambos).

---

## Fase 6 — `UsuarioApp`/`Controller`, `RolApp`/`Controller`, `AdminApp`/`Controller` (núcleo)

- `Application/UsuarioApp.java` — `asignarUsuario(medicoId|adminId, mail)` (da de baja el
  anterior si existe + revoca sus refresh tokens, crea el nuevo pendiente de activación,
  asigna el rol de sistema correspondiente, dispara mail), `desactivarUsuario(...)`.
- `Controllers/UsuarioController.java` — `POST /Usuario/AsignarMedico/{id}`,
  `POST /Usuario/AsignarAdmin/{id}`, ambos `@PreAuthorize("hasAuthority('USER_ALTA')")`.
- `Application/RolApp.java` + `Controllers/RolController.java` — CRUD de roles dinámicos +
  asignar rol a usuario, todo `AUTZ_ROL_*`.
- `Application/AdminApp.java` — `createAdmin` (atómico: crea `Admin` + llama
  `gestionUsuarioPort.asignarUsuarioAAdmin(...)`, mail = `Admin.email`) +
  `deleteAdmin` (cascada vía el puerto). `Controllers/AdminController.java` (nuevo,
  `/accesmed-api/Admin`) — todo `@PreAuthorize` con permisos `USER_*`/`MED_ALTA`-equivalente
  para Admin (ver tabla de Fase 0).

---

## Fase 7 — Retrofit de `Medico`

- `CreateMedicoRequest`: agregar campo opcional `CrearUsuarioRequest usuario` (sin mail,
  se copia de `Medico.email`).
- `MedicoApp.createMedico`: si `usuario != null`, `autorizacionService.requireAuthority(usuarioDetails, USER_ALTA)`
  y `gestionUsuarioPort.asignarUsuarioAMedico(...)`.
- `MedicoApp.deleteMedico`/`softDeleteMedico`: agregar `gestionUsuarioPort.desactivarUsuarioDeMedico(id)`.
- `MedicoController`: agregar `@PreAuthorize` (`MED_ALTA`/`MED_MODIFICAR`/`MED_BAJA` en
  los de escritura — ahora SuperAdmin delegable; `MED_CONSULTAR` en los de lectura —
  Admin+SuperAdmin+Medico).

---

## Fase 8 — `@PreAuthorize` + scoping en los 15 controllers existentes

Aplicar el catálogo de la Fase 0 a cada controller ya existente (Clinica, Especialidad,
MedicoPrestacion, ObraSocial, Plan, ObraSocialPaciente, ObraSocialPrestacion, Paciente,
Prestacion, TipoIndicacionPrestacion, IndicacionPrestacion, AgendaMedico, Turno):
`@PreAuthorize` según el permiso que le toque a cada endpoint (tabla de Fase 0), y
`AlcanceMedicoService` en los `QueryService`/`App` de `Turno`, `Paciente`, `AgendaMedico`
para el scope propio del médico. Patrón único, un subagente Haiku por entidad (o por
grupo chico), con la tabla de permisos como especificación exacta — no requiere criterio,
es mecánico.

---

## Fase 9 — Retrofit de auditoría

- `Records/Auditoria/AuditoriaResponse.java` (compartido): `createdAt`, `createdBy`,
  `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`, `deletedReason` — todos nullable.
- Por cada una de las ~15 entidades de lectura: agregar campo `auditoria` (nullable) a su
  Response principal, un método `toAuditoria(entidad)` en su `Mapper`, y en su
  `QueryService` poblarlo solo si `AUDITORIA_CONSULTAR` está entre las authorities.
  Filtros de auditoría (`createdByEquals`, `deletedByEquals`, rangos) agregados a los
  `Filtro`/`Criteria` existentes de cada entidad, honrados solo con ese permiso.
  Mismo patrón mecánico que Fase 8: un subagente Haiku por entidad, sin ambigüedad de diseño.

---

## Fase 10 — Documentación

- **`Docs/ARQUITECTURA.md`**: actualizar el árbol de `## 4` (mover `Usuario`/`Rol`/
  `UsuarioRol`/`Admin`/`Permiso` de `Security/Domain/` al `Domain/` del núcleo; agregar
  `Application/Ports/`; agregar `Security/Services/Utils/AlcanceMedicoService.java` y
  `AutorizacionService.java`; `Security/Domain/` ahora solo `RefreshToken`/
  `PasswordResetToken`). Actualizar la fila de la tabla de decisiones (línea ~50, "CRUD de
  usuarios/seguridad en su propio slice Security/") y la prosa de "`Security` como slice
  vertical" (línea ~562) para reflejar la división real (mecanismo aislado vs. datos en
  el núcleo) y agregar 2-3 filas nuevas a la tabla de decisiones documentando el patrón de
  puerto (`GestionUsuarioPort`) y las tres capas de autorización.
- **`Docs/Features/`** (vía convención de `feature-documenter`): `Autenticacion.md` (login,
  refresh, logout, activación, recuperación) y `RolesYPermisos.md` (catálogo, roles de
  sistema, delegación) — **cada uno con un ejemplo concreto de uso por rol**: qué ve/hace
  un Médico, qué ve/hace un Admin, qué ve/hace un SuperAdmin, sobre el mismo flujo (ej.
  "un Médico se loguea y solo puede confirmar sus propios turnos; un Admin ve y gestiona
  todos los turnos y pacientes pero no puede editar el catálogo de Prestaciones; un
  SuperAdmin además puede editar ese catálogo y crear el rol 'Facturación' con permisos
  acotados"). Actualizar `Docs/Features/Medico.md` con el campo opcional de usuario y la
  cascada de baja.
- **`Docs/FRONTEND-GUIA.md`** (ya existe, se extiende — no se crea un archivo nuevo):
  agregar una sección de Autenticación y sesión: cómo loguearse (`POST /Auth/Login`),
  dónde va el token (`Authorization: Bearer`), cuándo refrescar (401 con `codigo`
  específico vs. 401 por token inválido), logout, activación de cuenta nueva (link del
  mail), recuperación de contraseña. Reutiliza el formato de tablas ya establecido en el
  documento (ver filas 41-52 ya existentes sobre status HTTP).

---

## Fase 11 — Tests

Unit de `AuthApp`, `UsuarioApp` (mock de repos/services), `AlcanceMedicoService`,
`AutorizacionService`; test de integración de `AuthController` (MockMvc) cubriendo login
válido/inválido, refresh, y al menos un endpoint protegido con 401/403. Espejo en
`src/test/java/...`, patrón ya usado en el proyecto (ver `CanalNotificacionTurnoMailTest.java`
como referencia de estilo de test existente).

---

## Verificación end-to-end

1. `./mvnw compile` tras cada fase.
2. Recrear la base de dev (`docker compose -f docker/dev/docker-compose.yml down -v && up -d`)
   después de la Fase 0, para que Liquibase corra limpio con el changelog editado.
3. Swagger (`/swagger-ui.html`) debe mostrar los endpoints nuevos de `Auth`/`Usuario`/`Rol`/`Admin`.
4. Probar manualmente con los 3 roles de sistema ya sembrados (`Medico`, `Admin`,
   `SuperAdmin` — necesita al menos un `Usuario` de cada uno, sembrado a mano en dev para
   poder loguearse la primera vez): login, un endpoint 200 con permiso, un endpoint 403 sin
   permiso, un endpoint scoped (médico viendo solo sus turnos).
5. Confirmar que `/accesmed-api/**` ya NO es `permitAll()` salvo las rutas de `Auth`
   explícitamente listadas.
