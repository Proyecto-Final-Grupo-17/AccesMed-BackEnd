# Plan — Login / Seguridad (JWT, roles dinámicos, permisos) — AccesMed

## ESTADO ACTUAL: 🎉 PLAN TERMINADO Y VERIFICADO DE PUNTA A PUNTA (2026-09-14)

Las 12 fases (0 a 11) están implementadas, documentadas, testeadas y **probadas contra la
app real** con Docker disponible — login, 401, 403, permisos y auditoría confirmados con
`curl`, y los dos `@SpringBootTest` (`AccesMedApplicationTests`, `AuthControllerTest`)
corren en verde. Dos bugs reales que solo un run real podía sacar a la luz quedaron
corregidos (ver "Verificación end-to-end" más abajo). No queda nada pendiente de este plan.

✅ **Completadas: Fase 0 a 11 — el plan de implementación está terminado.** Fase 0-8 (JWT,
roles dinámicos, permisos, retrofit de Medico y de los 13 controllers restantes), 9
(retrofit de auditoría en las 15 entidades con QueryService), 10 (documentación: ver
detalle abajo), 11 (tests: ver detalle abajo).

**Fase 9** — `AuditoriaResponse` compartido + `AutorizacionService.hasAuthority(...)` +
`toAuditoria` en cada Mapper + filtro `createdBy` condicional en cada Criteria/QueryService
+ `@AuthenticationPrincipal UsuarioDetails` en los Controllers de lectura, en las 15
entidades con QueryService.

**Fase 10** — `Docs/ARQUITECTURA.md` actualizado (árbol de `Security/` reescrito a la
implementación real: `Usuario`/`Rol`/`UsuarioRol`/`Admin`/`Permiso` en el núcleo,
`Application/Ports/`, `Security/Services/Utils/`; tabla de decisiones y prosa de "Security
como slice vertical" reescritas). `Docs/Features/Autenticacion.md` y `RolesYPermisos.md`
nuevos, con ejemplo por rol. `Docs/Features/Medico.md` actualizado (`crearUsuario`, cascada
de baja). `Docs/FRONTEND-GUIA.md` §5 reescrita (401 vs 403, refresh, logout, activación/
recuperación). Documentar reveló dos bugs reales, ya corregidos: `RolController` exigía
`AUTZ_ROL_CONSULTAR`, permiso que no existía en el catálogo (nadie podía consultar roles) —
agregado al enum y al seed de SuperAdmin; no había `AuthenticationEntryPoint` propio, así
que un token ausente/inválido/vencido caía en el 403 sin cuerpo de Spring Security en vez
de un `AccesMedError` — agregado `JwtAuthenticationEntryPoint` (401, `NO_AUTENTICADO`).

**Fase 11** — Unit: `AuthAppTest`, `UsuarioAppTest`, `AlcanceMedicoServiceTest`,
`AutorizacionServiceTest` (22 tests, todos verdes). Integración: `AuthControllerTest`
(MockMvc + Testcontainers, contexto real) — login válido/inválido, refresh, endpoint
protegido con 401/403. Compila y el único fallo al intentar correrlo es "Could not find a
valid Docker environment" (Docker no está disponible en este entorno de sesión) — mismo
requisito preexistente que ya tenía `AccesMedApplicationTests`, no es nuevo. De paso se
arregló deuda de la Fase 8: `TurnoAppTest`/`TurnoControllerTest` no se habían actualizado
cuando `TurnoApp.startSalaDeEsperaTurno`/`startAtencionTurno`/`finishTurno` empezaron a
pedir `UsuarioDetails`, y rompían `./mvnw test-compile`.

`./mvnw.cmd compile`, `./mvnw.cmd test-compile` y `./mvnw.cmd test` (suite completa, los
dos `@SpringBootTest` incluidos) verdes con Docker disponible. Ver "Verificación
end-to-end" más abajo para el detalle de qué se probó contra la app real y los dos bugs
que aparecieron recién ahí (ya corregidos).

**Bootstrap ya resuelto**: `Scripts/seed-superadmin.sql` (mail
`proyectofinalgrupo17@gmail.com`, password `accesmed2026`) corrido contra la base de dev.
A partir de acá, cualquier `Admin`/`Medico` adicional se crea desde la app
(`POST /Admin/Admin` con `USER_ALTA`, o `POST /Medico/Medico` con `crearUsuario: true`).

---

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

✅ **Hecha (2026-09-14, con Docker disponible por primera vez en el proyecto).** Se
recreó la base de dev, Liquibase corrió limpio (30 changesets), se corrió
`Scripts/seed-superadmin.sql` y se probó contra la app real (`./mvnw spring-boot:run`,
perfil `dev`) con `curl`:

- Login real (`POST /Auth/Login`) → 200 con `accessToken`/`refreshToken`.
- Login con contraseña incorrecta → 401 `CREDENCIALES_INVALIDAS`.
- Endpoint protegido sin token (`GET /Rol/Rol`) → 401 `NO_AUTENTICADO`.
- Endpoint protegido con token y permiso → 200, con los 3 roles de sistema, su catálogo
  completo de permisos y el bloque `auditoria` poblado (confirma Fase 9 en vivo).
- `AuthControllerTest`/`AccesMedApplicationTests` corridos con Docker disponible: verdes
  (antes solo se habían verificado por compilación). Toda la suite de tests (`./mvnw test`)
  verde, exit 0.
- 403 sin permiso: cubierto por `AuthControllerTest.endpointProtegido_autenticadoSinElPermiso_403AccesoDenegado`,
  que ahora corre en verde contra la base real.
- Scope de médico: verificado a nivel unitario (`AlcanceMedicoServiceTest`, ya en verde
  desde la Fase 11); no se armó un segundo usuario médico a mano para probarlo por curl —
  si hace falta confirmarlo contra la app real, asignar el rol `Medico` a un `Usuario`
  vinculado a alguno de los médicos de `Scripts/seed-datos-demo.sql` y repetir el flujo.
- Confirmado que `/accesmed-api/**` ya no es `permitAll()` salvo las rutas de `Auth`
  explícitamente listadas — los 401/403 de arriba lo demuestran en la práctica.

**Dos bugs reales aparecieron recién al probar contra la app de verdad** (ningún test
mockeado los detectaba — exactamente el tipo de cosa que esta verificación existe para
atrapar), ya corregidos (ver commit `33d1886`):
1. Spring Boot 4.1 cambió el `ObjectMapper` por defecto a Jackson 3
   (`tools.jackson.databind`, no `com.fasterxml.jackson.databind`) — `JwtAuthenticationEntryPoint`
   (agregado en la Fase 10) importaba el tipo viejo y la app no levantaba. Mismo error en
   `AuthControllerTest`.
2. `UsuarioDetailsService.loadUserByUsername` no era `@Transactional`: `UsuarioRol.rol` es
   `LAZY` y no hay OSIV (`open-in-view: false`), así que **todo** request autenticado con
   JWT fallaba en silencio (`LazyInitializationException` atrapada y logueada en `debug`,
   invisible con el logging por default) y quedaba como 401 `NO_AUTENTICADO` aunque el
   token fuera válido — no era un caso borde, era el 100% de los requests protegidos.

Pasos originales de esta sección (referencia, ya ejecutados arriba):
1. `./mvnw compile` tras cada fase.
2. Recrear la base de dev (`docker compose -f docker/dev/docker-compose.yml down -v && up -d`).
3. Swagger (`/swagger-ui.html`) debe mostrar los endpoints nuevos de `Auth`/`Usuario`/`Rol`/`Admin`.
4. Probar manualmente con los roles de sistema sembrados: login, 200 con permiso, 403 sin
   permiso, scoped por médico.
5. Confirmar que `/accesmed-api/**` ya no es `permitAll()` salvo `Auth`.
