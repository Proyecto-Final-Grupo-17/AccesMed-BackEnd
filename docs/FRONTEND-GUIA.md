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
  "path": "/api/turnos"
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

## 2. Un DTO por endpoint (request y response)

El backend expone **un request y un response propios por cada endpoint**, no la entidad
completa. Consecuencias para el front:

- En un **update**, los campos inmutables **no existen** en el request. No los mandes: no
  se ignoran silenciosamente, directamente no forman parte del contrato. Ej: el `codigo`
  de una prestación puede no ser editable → no aparece en `ActualizarPrestacionRequest`.
- El **response** trae solo lo que ese endpoint devuelve. No asumas que un `create` y un
  `getById` devuelven la misma forma: mirá el response de cada uno en Swagger.
- Nombres del contrato: `<Accion><Entidad>Request` / `<Accion><Entidad>Response`
  (ej. `CrearTurnoRequest`, `ConfirmarTurnoResponse`). Te sirven como referencia al leer Swagger.

---

## 3. Documentación viva: Swagger

La fuente de verdad de qué endpoints hay, qué reciben y qué devuelven es **Swagger UI**:

```
http://localhost:8080/swagger-ui.html
```

Ahí ves cada endpoint, su request/response y podés probarlo. Cualquier duda de forma, se
mira ahí antes de preguntar.

---

## 4. Autenticación

- La API usa **JWT**. Se obtiene en el endpoint de login y se manda en cada request en el
  header `Authorization: Bearer <token>`.
- Si el token vence, la API responde **401** (con el `AccesMedError` de siempre). El front
  refresca el token o manda a login.
- Los permisos son por rol; un usuario autenticado pero sin permiso recibe **403**.

---

## 5. Convenciones de datos

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

## 6. Checklist rápido para arrancar del lado del front

- [ ] Implementar un interceptor HTTP que parsee `AccesMedError` de forma uniforme.
- [ ] Mostrar `errores[]` como lista en formularios; usar `codigo` para lógica.
- [ ] Guardar el JWT y mandarlo en `Authorization`; manejar 401 (refresh/login).
- [ ] Leer los request/response de cada endpoint en Swagger, no asumir la forma.
- [ ] Manejar fechas en UTC; convertir solo para mostrar.
- [ ] Disparar transiciones de Turno por sus endpoints de acción, no por un campo estado.
