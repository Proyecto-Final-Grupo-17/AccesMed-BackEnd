# Guía para el Frontend — API de AccesMed

Este documento es el contrato entre el backend y quien desarrolla el frontend (panel web)
y, en lo aplicable, quien arma las Custom Tools del agente. Objetivo: que del lado del
front haya **un solo modelo mental** para consumir la API y manejar errores.

---

## 1. Lo más importante: los errores llegan SIEMPRE con la misma forma

No importa si el error es de validación de campos, de regla de negocio, de autenticación o
un 500 inesperado: el backend responde con el **mismo objeto `AccesMedError`**. Programá el
manejo de errores una vez y sirve para todo.

```json
{
  "timestamp": "2026-07-22T10:15:30Z",
  "status": 422,
  "codigo": "VALIDACION",
  "mensaje": "La solicitud tiene errores de validación.",
  "errores": [
    "La fecha desde no puede ser posterior a la fecha hasta.",
    "El médico indicado no existe o está dado de baja."
  ],
  "path": "/accesmed-api/Turno/Turno"
}
```

| Campo | Tipo | Para qué sirve en el front |
|-------|------|----------------------------|
| `timestamp` | string ISO-8601 | log / soporte |
| `status` | number | el HTTP status (redundante, cómodo) |
| `codigo` | string | **clave estable** para lógica: podés ramificar por `codigo`, no por el texto |
| `mensaje` | string | resumen para mostrar como título del error |
| `errores` | string[] | **siempre lista**; mostrala como items. En errores simples trae un solo elemento |
| `path` | string | endpoint que falló |

**Regla de oro del front:** para mostrar al usuario, recorré `errores` (siempre es una
lista). Para lógica condicional, usá `codigo` (nunca compares el texto del mensaje).

### Tabla de status que vas a ver

| HTTP | Cuándo | Qué hacer en el front |
|------|--------|-----------------------|
| 200 / 201 | OK | seguir |
| 400 | JSON malformado / tipo inválido | error de programación del request |
| 401 | sin token o token vencido | redirigir a login / refrescar token |
| 403 | autenticado pero sin permiso | mostrar "no autorizado" |
| 404 | recurso no encontrado (`RecursoNoEncontrado`) | mensaje de "no existe" |
| 409 | conflicto de regla de negocio (`ReglaNegocio`) | mostrar `mensaje` |
| 422 | validación (campos o negocio, `VALIDACION`) | listar `errores` bajo el formulario |
| 500 | error inesperado | mensaje genérico; el detalle no se filtra al front |

Los mensajes vienen **en español y prolijos**: los errores de Bean Validation (que
normalmente salen feos y en inglés) los traduce el backend antes de responder.

---

## 2. Forma de las URLs y verbos HTTP

Todos los endpoints cuelgan del prefijo **`/accesmed-api`**, seguido de la **entidad** y
del **recurso** que toca la operación (ambos en PascalCase singular, como en el modelo):

```
/accesmed-api/<Entidad>/<Recurso>[/{id}]
```

```
POST   /accesmed-api/Prestacion/Prestacion               crear
GET    /accesmed-api/Prestacion/Prestacion                listar (paginado, con filtros)
PUT    /accesmed-api/Prestacion/Prestacion/{id}           actualizar
DELETE /accesmed-api/Prestacion/Prestacion/{id}           dar de baja (soft delete)
GET    /accesmed-api/Especialidad/Especialidad/Buscar     traer una única especialidad, por filtro
POST   /accesmed-api/AgendaMedico/Agenda                  crear la agenda de un médico
```

Reglas que importan del lado del front:

- **`PUT` y `PATCH` mandan el `id` dos veces**: en la URL y dentro del body. **Tienen que
  ser el mismo valor** — si no coinciden, el backend responde **422** sin tocar nada. Al
  armar el request, tomá el id de una sola fuente y usalo en los dos lugares.
- Hay **`PATCH` sin body**: para cambios de un campo puntual el endpoint recibe solo el id
  en la URL. Mandá el request sin cuerpo.
- **`DELETE` es la baja lógica** (soft delete), responde **204** y no borra el registro:
  deja de aparecer en los listados por defecto, pero el id siguió existiendo.
- **Ya no hay un `GET /{id}` de toda la vida** en las entidades de catálogo (`Especialidad`,
  `ObraSocial`, `Plan`, `TipoIndicacionPrestacion`, `IndicacionPrestacion`): "obtener por id"
  y "listar" comparten el mismo lenguaje de filtros dinámicos. Ver
  [`FILTRADO-DINAMICO.md`](FILTRADO-DINAMICO.md) — es obligatorio leerlo antes de armar
  cualquier pantalla de listado o de detalle de estas entidades.

La lista exacta de rutas está siempre en Swagger (§4).

## 3. Un DTO por endpoint (request y response)

El backend expone **un request y un response propios por cada endpoint**, no la entidad
completa. Consecuencias para el front:

- En un **update**, los campos inmutables **no existen** en el request. No los mandes: no
  se ignoran silenciosamente, directamente no forman parte del contrato. Ej: el `codigo`
  de una prestación puede no ser editable → no aparece en `UpdatePrestacionRequest`.
- El **response** trae solo lo que ese endpoint devuelve. No asumas que un `create` y un
  `getById` devuelven la misma forma: mirá el response de cada uno en Swagger.
- Nombres del contrato: `<Accion><Entidad>Request` / `<Accion><Entidad>Response`
  (ej. `CreateTurnoRequest`, `ConfirmTurnoResponse`). Te sirven como referencia al leer Swagger.

---

## 4. Documentación viva: Swagger

La fuente de verdad de qué endpoints hay, qué reciben y qué devuelven es **Swagger UI**:

```
http://localhost:8080/swagger-ui.html
```

Ahí ves cada endpoint, su request/response y podés probarlo. Cualquier duda de forma, se
mira ahí antes de preguntar.

---

## 5. Autenticación y sesión

Detalle completo de cada endpoint en [`Docs/Features/Autenticacion.md`](Features/Autenticacion.md)
y del catálogo de permisos en [`Docs/Features/UsuariosRolesYPermisos.md`](Features/UsuariosRolesYPermisos.md).
Acá va el contrato mínimo que necesita el front para manejar la sesión.

### Login

`POST /accesmed-api/Auth/Login` con `{ mail, password }` devuelve
`{ accessToken, refreshToken }`. Guardá los dos. El `accessToken` va en el header de **cada**
request siguiente:

```
Authorization: Bearer <accessToken>
```

### Cuándo refrescar (401 vs 403 — no son lo mismo)

| HTTP | Código en `AccesMedError` | Significa | Qué hacer en el front |
|------|---------------------------|-----------|------------------------|
| 401 en `/Auth/Login` | `CREDENCIALES_INVALIDAS` | mail o contraseña incorrectos | mostrar el error en el form de login, no reintentar solo |
| 401 en cualquier otro endpoint | `NO_AUTENTICADO` | no hay `Authorization`, el token es inválido, o **venció** | intentar `POST /Auth/Refresh` con el `refreshToken` guardado; si el refresh también falla (401/404), limpiar la sesión y mandar a login |
| 403 en cualquier endpoint | `ACCESO_DENEGADO` | el usuario está autenticado (el token es válido) pero **no tiene el permiso** que ese endpoint exige | no reintentar ni refrescar — mostrar "no autorizado". Reintentar el refresh acá no cambia nada: el problema no es el token |

**Regla práctica para el interceptor HTTP**: un 401 dispara el flujo de refresh-y-reintento
una vez; un 403 no. Distinguilos por el `status`, no adivines por el `codigo` (que puede
crecer).

El access token vence a los 30 minutos (default); el refresh token a los 7 días y **no
rota** — sigue sirviendo hasta que venza o se revoque con `Logout`. No hace falta pedir uno
nuevo en cada refresh de access token.

### Logout

`POST /accesmed-api/Auth/Logout` con `{ refreshToken }` (requiere estar autenticado, o sea
mandar también el `Authorization` vigente). Responde `204`. Revoca el refresh token: después
de esto, ni él ni ningún access token que se haya emitido con él sirven. Limpiá ambos
tokens del storage del front en este paso, no esperes la respuesta del backend para eso.

### Activación de cuenta nueva y recuperación de contraseña

Mismo mecanismo, mismo endpoint final — la diferencia es solo cómo se llega al link:

- **Cuenta nueva** (un médico o admin recién tiene usuario asignado): le llega un mail con
  un link de activación. El front resuelve una pantalla de "poner tu contraseña" a partir
  del `token` que viene como query param en ese link.
- **Olvidé mi contraseña**: `POST /accesmed-api/Auth/OlvideContrasena` con `{ mail }`.
  **Siempre responde 200**, exista o no el mail — mostrá siempre el mismo mensaje ("si el
  mail existe, te llegará un correo"), nunca reveles si el mail está registrado.

En ambos casos, la pantalla final es la misma: `POST /accesmed-api/Auth/RestablecerContrasena`
con `{ token, passwordNueva }` (8 a 100 caracteres). Responde `200` sin cuerpo, o `404` si
el token no existe/venció/ya se usó — en ese caso el front debe ofrecer pedir un link nuevo,
no reintentar con el mismo token. Restablecer la contraseña revoca todas las sesiones
abiertas de ese usuario (todos los refresh tokens vigentes quedan sin efecto).

### Cambiar la propia contraseña o el propio mail (autoservicio)

Cualquier usuario logueado puede cambiar su contraseña o su mail sin depender del
SuperAdmin — ambos requieren `Authorization` pero **ningún permiso especial**. Los dos
casos reutilizan el mismo mecanismo por mail que activación/recuperación (arriba): no
hay un formulario de "contraseña actual + nueva", el cambio siempre se confirma por
link.

- **Contraseña**: `POST /accesmed-api/Auth/CambiarContrasena` (sin body). Responde `204`
  y manda un mail al usuario con el mismo link de "restablecer contraseña" de arriba —
  el front no hace nada más acá, la confirmación pasa por la pantalla de
  `RestablecerContrasena` que ya existe.
- **Mail**: `POST /accesmed-api/Auth/CambiarMail` con `{ mailNuevo }`. Responde `204` y
  manda un mail **a la casilla nueva** (no a la vieja) con un link de confirmación. El
  mail actual sigue sirviendo para loguearse mientras la confirmación esté pendiente —
  avisale esto al usuario en la pantalla ("tu mail actual sigue funcionando hasta que
  confirmes el nuevo").
- **Confirmar el cambio de mail**: `POST /accesmed-api/Auth/ConfirmarCambioMail` con
  `{ token }` (público, no requiere `Authorization` — se llega desde el link del mail,
  igual que `RestablecerContrasena`). Responde `200` sin cuerpo, o `404` si el token no
  existe/venció/ya se usó, o `409` con código `MAIL_YA_REGISTRADO` si otro usuario tomó
  ese mail mientras la confirmación estaba pendiente. Después de confirmar, el mail
  viejo deja de servir para loguearse — el front debe avisar que hay que volver a
  loguearse con el mail nuevo.

### `GET /accesmed-api/Auth/Me`: quién es el usuario logueado

Los permisos son dinámicos por rol (ver `UsuariosRolesYPermisos.md`) y se recalculan en cada
request — no vienen en el JWT (el token solo lleva el id de usuario). Para saber quién es el
usuario logueado y qué puede hacer (por ejemplo, para elegir la pantalla inicial u ocultar un
botón sin esperar un 403), el front debe llamar a `GET /accesmed-api/Auth/Me` (requiere
`Authorization`) después del login. Devuelve:

```json
{
  "id": "uuid",
  "mail": "medico@clinica.com",
  "nombre": "...",
  "apellido": "...",
  "medicoId": "uuid o null",
  "adminId": "uuid o null",
  "roles": [
    { "id": "uuid", "nombre": "Medico", "permisos": ["TURN_CONSULTAR", "..."] }
  ]
}
```

`medicoId`/`adminId` son mutuamente excluyentes (exactamente uno de los dos es no nulo) y
sirven para saber si quien se logueó es un médico o personal administrativo. `roles` trae
solo las asignaciones vigentes, cada una con sus permisos.

Esto resuelve "quién es" y "qué rol/permisos tiene", no reemplaza la validación real: un
403 en una acción concreta sigue siendo la fuente de verdad final (los permisos pueden
cambiar entre que se llamó a `/Me` y que se intenta la acción). Diseñá el panel para tolerar
un 403 igual (mostrar el mensaje, no romper la pantalla).

---

## 6. Gestión de usuarios (SuperAdmin)

Pantalla de "usuarios del sistema" — exclusiva del SuperAdmin (permisos `USER_CONSULTAR`/
`USER_MODIFICAR`/`USER_BAJA`). Detalle funcional completo en
[`Docs/Features/UsuariosRolesYPermisos.md`](Features/UsuariosRolesYPermisos.md).

### Listar y ver detalle

`GET /accesmed-api/Usuario/Usuario` — paginado, con criteria de filtrado dinámico
(`mail`, `medicoId`, `adminId`, y `estado` con valores `ACTIVO`/`INACTIVO`/`TODOS`; sin
mandar `estado` trae solo activos). A diferencia del resto de los listados del sistema,
**este sí puede traer usuarios dados de baja** — el front necesita mostrar el filtro de
estado explícitamente, no asumir que todo lo que aparece está activo (usá el campo
`activo` de cada fila para pintar el estado).

`GET /accesmed-api/Usuario/Usuario/{id}` — detalle de un usuario puntual, esté activo o
no. Devuelve, entre otros campos, `nombre`/`apellido` de la persona vinculada (médico o
admin), `roles` (nombres de los roles vigentes) y, si está dado de baja, `deletedAt`/
`deletedReason`.

### Dar de baja un usuario

`DELETE /accesmed-api/Usuario/Usuario/{id}?motivo=...` — el query param `motivo` es
**opcional** (texto libre); si no se manda, el backend usa un motivo por default. Responde
`204`. Importante para la UX: **esto no da de baja al médico/admin dueño de la cuenta**,
solo revoca su acceso al sistema — la persona sigue existiendo en el padrón. Si el
objetivo es dar de baja a la persona (no solo su acceso), es el flujo de
`DELETE /Medico/Medico/{id}` o `DELETE /Admin/Admin/{id}` (que sí desactiva el usuario en
cascada), no este endpoint.

### Forzar un cambio de contraseña o de mail de otro usuario

Mismo mecanismo que el autoservicio (ver §5), pero disparado por el SuperAdmin sobre un
usuario cualquiera, indicando su `id` en la ruta:

- `POST /accesmed-api/Usuario/Usuario/{id}/RestablecerContrasena` (sin body) — manda el
  mail de restablecimiento al usuario. `204`.
- `POST /accesmed-api/Usuario/Usuario/{id}/CambiarMail` con `{ mailNuevo }` — manda la
  confirmación al mail nuevo (mismo endpoint público de confirmación,
  `POST /Auth/ConfirmarCambioMail`, que el caso de autoservicio). `204`, o `409` con
  código `MAIL_YA_REGISTRADO` si el mail ya está en uso o es igual al actual.

---

## 7. Convenciones de datos

- **Fechas y horas**: ISO-8601 en UTC (`2026-07-22T10:15:30Z`). Convertí a zona local solo para mostrar.
- **Bajas lógicas (soft delete)**: el backend no borra físico. Un recurso "dado de baja"
  puede no aparecer en los listados por defecto. No asumas que un id que existió sigue activo.
- **Estados del Turno**: el turno tiene una máquina de estados
  (`Pendiente → EsperaValidacion → Confirmado → EnSalaDeEspera → Iniciado/Ausente →
  Finalizado/Cancelado`). Cada transición es un endpoint propio (ej. `confirmarTurno`,
  `anunciarPaciente`), no un `PUT` de un campo `estado`. El front dispara la acción, no
  setea el estado a mano.
- **Operaciones atómicas vs. UX**: una pantalla tipo wizard puede orquestar varias
  operaciones atómicas del backend. Que en el back sean endpoints separados no obliga a
  que el usuario los perciba fragmentados: el front los encadena.

---

## 8. Checklist rápido para arrancar del lado del front

- [ ] Implementar un interceptor HTTP que parsee `AccesMedError` de forma uniforme.
- [ ] Mostrar `errores[]` como lista en formularios; usar `codigo` para lógica.
- [ ] Guardar el `accessToken`/`refreshToken` y mandar el primero en `Authorization`; en
      401 intentar `Refresh` una vez y si falla ir a login, en 403 no reintentar (ver §5).
- [ ] Leer los request/response de cada endpoint en Swagger, no asumir la forma.
- [ ] Manejar fechas en UTC; convertir solo para mostrar.
- [ ] En `PUT`/`PATCH`, mandar el mismo `id` en la URL y en el body (si difieren, 422).
- [ ] Disparar transiciones de Turno por sus endpoints de acción, no por un campo estado.
- [ ] Listados y "obtener por id" de `Especialidad`/`ObraSocial`/`Plan`/`TipoIndicacionPrestacion`/`IndicacionPrestacion` usan filtrado dinámico — ver [`FILTRADO-DINAMICO.md`](FILTRADO-DINAMICO.md).
