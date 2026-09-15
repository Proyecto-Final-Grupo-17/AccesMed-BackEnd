# Feature: Autenticación

## Contexto

- **Para qué es**: todo el panel web interno (administrador, médico) requiere sesión.
  `/accesmed-api/**` ya **no** es de acceso libre: salvo las rutas de `Auth` explícitamente
  listadas, cualquier endpoint exige un access token JWT válido, y además el permiso que
  le corresponda a esa operación (ver `Docs/Features/UsuariosRolesYPermisos.md`).

- **Para qué sirve**: login con mail/contraseña, renovar la sesión sin volver a loguearse
  (refresh token), cerrar sesión, y el ciclo de vida de la contraseña — activar una cuenta
  nueva y recuperar una contraseña olvidada, ambos por el mismo mecanismo de token de un
  solo uso enviado por mail.

- **Quiénes la usan**: el panel web interno. El chatbot de WhatsApp (Flowise) no pasa por
  acá: el agente tiene su propia entrada (`Agente/`) y reutiliza los mismos `App`/`Service`
  del núcleo, sin sesión de usuario humano.

- **JWT liviano**: el token solo lleva `sub` (el id del usuario) — **no** lleva roles ni
  permisos embebidos. En cada request, el backend recalcula las authorities desde la base
  (`UsuarioDetailsService`, recorriendo los `UsuarioRol` vigentes del usuario). Esto
  significa que si a alguien le revocan un rol o le asignan uno nuevo, el efecto es
  **inmediato** en el próximo request — no hace falta esperar a que expire el token.

---

## Funciones

### Login — `POST /accesmed-api/Auth/Login`

**Flujo simplificado:**
1. Busca el usuario activo por `mail` y verifica la contraseña (BCrypt).
2. Si es válida, emite un access token (JWT, vence en 30 minutos por default) y un refresh
   token (vence en 7 días, se persiste hasheado en `RefreshToken`, sin rotación).
3. Devuelve ambos tokens.

**Request — `LoginRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `mail` | String (formato email) | Sí | El mail del usuario. |
| `password` | String | Sí | La contraseña en texto plano (va sobre HTTPS). |

**Response — `LoginResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `accessToken` | String (JWT) | Va en el header `Authorization: Bearer <accessToken>` de cada request siguiente. |
| `refreshToken` | String | Se guarda para pedir un access token nuevo cuando el actual vence (ver `Refresh`). |

**Errores posibles:**
- `CREDENCIALES_INVALIDAS` (401): el mail no existe, el usuario está dado de baja, o la contraseña no coincide. El mensaje es genérico a propósito (no distingue "mail no existe" de "contraseña incorrecta") para no filtrar qué mails están registrados.

### Quién soy — `GET /accesmed-api/Auth/Me`

Requiere estar autenticado. Devuelve el perfil del usuario del token (médico o admin
vinculado) junto con sus roles vigentes y los permisos de cada uno — pensado para que el
front sepa "quién es" y "qué puede hacer" sin depender de un 403 real ni de un selector
manual de rol.

**Flujo simplificado:**
1. Resuelve el `Usuario` a partir del `usuarioId` del token.
2. Busca sus `UsuarioRol` vigentes (mismo criterio de vigencia que usa
   `UsuarioDetailsService` para armar las authorities de cada request).
3. Arma la respuesta con los datos personales (de `Medico` o `Admin`, según a cuál esté
   vinculado el usuario) y la lista de roles con sus permisos.

**Request**: sin body, sin parámetros — el usuario sale del `Authorization`.

**Response — `MeResponse`**

| Campo | Tipo | Notas |
|-------|------|-------|
| `id` | UUID | Id del usuario. |
| `mail` | String | |
| `nombre` / `apellido` | String | De la persona vinculada (`Medico` o `Admin`). |
| `medicoId` | UUID o `null` | No nulo si el usuario es un médico. |
| `adminId` | UUID o `null` | No nulo si el usuario es personal administrativo. Mutuamente excluyente con `medicoId`. |
| `roles` | `List<MeRolResponse>` | Roles vigentes: cada uno con `id`, `nombre` y su `Set<Permiso>`. |

**Errores posibles**: los mismos 401 genéricos de cualquier endpoint autenticado (token
ausente/inválido/vencido) — ver `Docs/FRONTEND-GUIA.md §5`.

> Este endpoint es una foto al momento de pedirla, no reemplaza la validación real de cada
> acción: los permisos pueden cambiar entre que se llamó a `/Me` y que se intenta una
> operación, así que un 403 puntual en otro endpoint sigue siendo la fuente de verdad final.

### Refresh — `POST /accesmed-api/Auth/Refresh`

Renueva el access token sin pedir contraseña de nuevo, mientras el refresh token siga
vigente y no haya sido revocado.

**Flujo simplificado:**
1. Busca el refresh token por su hash; valida que exista, no esté vencido y no esté revocado.
2. Emite un access token nuevo. El refresh token **no rota**: sigue siendo el mismo hasta que venza o se revoque (logout).

**Request — `RefreshRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `refreshToken` | String | Sí | El que devolvió el `Login`. |

**Response — `RefreshResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| `accessToken` | String (JWT) | Reemplaza al access token vencido. |

### Logout — `POST /accesmed-api/Auth/Logout`

Requiere estar autenticado (no está en la lista de rutas `permitAll()`). Revoca el refresh
token indicado — a partir de ahí, ni ese refresh token ni ningún access token emitido con
él (una vez que venza el access token en curso, en 30 minutos como máximo) sirven para
nada nuevo.

**Request — `RefreshRequest`** (mismo record que `Refresh`: el refresh token a revocar)

**Response**: `204 No Content`.

### Olvidé mi contraseña — `POST /accesmed-api/Auth/OlvideContrasena`

**Flujo simplificado:**
1. Busca el usuario por `mail`.
2. Si existe y está activo, genera un `PasswordResetToken` de un solo uso y manda un mail
   con el link de recuperación (reutiliza `MailService`).
3. **Siempre responde 200**, exista o no el mail — así el endpoint no revela qué mails
   están registrados en el sistema.

**Request — `OlvideContrasenaRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `mail` | String (formato email) | Sí | |

**Response**: `200 OK` sin cuerpo, siempre. El front debe mostrar el mismo mensaje
("si el mail existe, te llegará un correo") sin importar el resultado real.

### Restablecer contraseña — `POST /accesmed-api/Auth/RestablecerContrasena`

Mismo endpoint sirve para **dos casos**: activar una cuenta nueva (cuando un médico o admin
recién tiene usuario asignado) y recuperar una contraseña olvidada — en ambos casos el
usuario llega con un token de un solo uso desde el link del mail.

**Flujo simplificado:**
1. Busca el `PasswordResetToken` por su hash; valida que exista, no esté vencido y no haya
   sido usado.
2. Actualiza la contraseña (BCrypt) y marca el token como usado.
3. **Revoca todos los refresh tokens vigentes** del usuario — si alguien más tenía una
   sesión abierta con la contraseña vieja (ej. cuenta comprometida), queda deslogueado.

**Request — `RestablecerContrasenaRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| `token` | String | Sí | El token que vino en el link del mail (query param del link, no algo que el usuario tipea). |
| `passwordNueva` | String (8 a 100 caracteres) | Sí | |

**Response**: `200 OK` sin cuerpo.

**Errores posibles:**
- `RECURSO_NO_ENCONTRADO` (404): el token no existe, ya fue usado, o venció — el front debe ofrecer pedir un link nuevo (`OlvideContrasena`), no reintentar con el mismo token.

---

## Ejemplo por rol: un mismo flujo, distinto alcance

Tomemos el login de tres usuarios distintos contra el mismo endpoint:

- **Un Médico** hace `POST /Auth/Login` con su mail. El token que recibe, en cualquier
  endpoint de `Turno`/`AgendaMedico`/`Paciente`, queda automáticamente acotado a sus propios
  recursos vía `AlcanceMedicoService` — no es que el JWT lleve "soy el médico X", es que
  `UsuarioDetails` expone `medicoId` (resuelto de `Usuario.medico` en cada request) y el
  `QueryService`/`App` de esas entidades lo usa para filtrar.
- **Un Admin** hace login igual, pero su usuario no tiene `medicoId` — así que esos mismos
  endpoints le devuelven todo, sin scope.
- **Un SuperAdmin** hace login igual; la diferencia no está en el login, está en qué
  permisos resuelve `UsuarioDetailsService` al armar sus authorities (ver el detalle de
  permisos por rol en `Docs/Features/UsuariosRolesYPermisos.md`).

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `Docs/FRONTEND-GUIA.md §1` y su nueva sección §5 (autenticación y sesión).
