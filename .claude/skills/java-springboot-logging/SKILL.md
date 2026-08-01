---
name: java-springboot-logging
description: >
  Convenciones de logging para el backend Java 25 + Spring Boot 4 de AccesMed (y aplicable a
  cualquier proyecto Java/Spring Boot con la misma filosofía). Úsala SIEMPRE que escribas
  o revises código que loguee: controllers, casos de uso (App), DomainService/
  QueryService, o el GlobalExceptionHandler. Cubre qué librería usar (SLF4J vía Lombok
  `@Slf4j`, sin dependencias nuevas), qué nivel corresponde a cada situación (info/debug/
  warn/error), en qué capa se loguea cada cosa, el formato del mensaje (placeholders, no
  concatenación), y la regla de "un solo lugar por error" para no duplicar logs entre el
  Service que lanza una excepción y el GlobalExceptionHandler que la atrapa. Actívala
  antes de escribir código nuevo o al revisar logging existente.
---

# Logging — Java + Spring Boot (AccesMed)

> **Preferencias en evolución**: estas convenciones reflejan el gusto personal de Franco y
> pueden cambiar con el tiempo. Si pide un ajuste, actualizá esta skill (no solo el código nuevo).

Aplicá estas reglas en toda clase que loguee. Si generás código, ya tiene que salir con
este estilo — no lo dejes para un paso posterior.

## 1. Librería: SLF4J vía Lombok `@Slf4j` — no hace falta agregar nada nuevo

SLF4J ya viene transitivamente con cualquier `spring-boot-starter-*` (Logback es la
implementación por defecto). Como el proyecto ya tiene Lombok, usá la anotación
`@Slf4j` en cada clase que necesite loguear — genera el campo `log` automáticamente, sin
declarar `Logger` a mano:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PrestacionDomainService {
    // ya tenés disponible: log.info(...), log.warn(...), etc.
}
```

No agregues `LoggerFactory.getLogger(X.class)` manualmente — es exactamente lo que
`@Slf4j` reemplaza. Para `staging`/`prod`, si más adelante hace falta logging
estructurado en JSON (para herramientas de observabilidad), se puede sumar
`logstash-logback-encoder` — no es necesario ahora.

## 2. Niveles: qué va en cada uno

| Nivel | Cuándo | Ejemplo |
|-------|--------|---------|
| `info` | Hitos normales del flujo: una request llegó, un caso de uso arrancó | "Solicitud recibida: crear prestación" |
| `debug` | Detalle interno de diagnóstico (valores intermedios, decisiones no críticas). Apagado por defecto en producción | "Slot calculado: horaDesde=09:00, n=3 → 09:45" |
| `warn` | Dato mal enviado por el cliente o regla de negocio incumplida — **no es una falla del sistema** | "Código de prestación duplicado: CARD01" |
| `error` | Falla real e inesperada del sistema | Excepción genérica no controlada (500) |

Regla general: si el error es "el cliente mandó algo que no corresponde" o "una regla de
negocio no se cumplió", es `warn`. `error` se reserva para lo que de verdad indica que
algo se rompió (y ahí sí querés que alguien lo note).

## 3. Dónde loguear en cada capa

- **Controller**: `log.info` al recibir la request, con el endpoint y los datos
  identificatorios (ids). **No loguear el objeto completo si contiene datos sensibles**
  (ver punto 5).
- **App (`Application`)**: `log.info` al iniciar la orquestación del caso de uso. Es
  el punto más valioso para el nivel `info`: 1 App = 1 transacción, así que esta línea
  marca de forma clara "esto se intentó hacer" sin duplicar ruido en cada capa inferior.
- **DomainService / QueryService**: `log.debug` para el detalle interno. **No** uses
  `info` acá — si cada método interno logueara en `info`, terminás con 3-4 líneas por
  request y se vuelve ruido en vez de señal.
- **Errores de negocio** (`AccesMedException` y subclases): `log.warn` **en el Service,
  justo antes del `throw`** — nunca en el `GlobalExceptionHandler`. Ver punto 4.
- **`GlobalExceptionHandler`**: loguea solo lo que él mismo atrapa y que no viene de un
  Service — Bean Validation (`log.warn`, con el detalle de qué campo falló) y la
  excepción genérica (`log.error`, 500).

## 4. Regla de oro: un error se loguea en un solo lugar

Si logueás un error de negocio tanto en el Service (donde se lanza) como en el
`GlobalExceptionHandler` (donde se atrapa), termina duplicado en el log — la misma falla
aparece dos veces. Decisión de este proyecto: **loguear en el Service, no en el handler**,
porque el Service ya tiene todo el contexto de negocio en ese momento.

```java
// Services/DomainServices/PrestacionDomainService.java
public void validateCodigoPrestacionIsUnique(String codigo) {
    if (prestacionRepository.existsByCodigoAndFechaHoraBajaIsNull(codigo)) {
        log.warn("No se pudo crear la prestación: código {} ya existe", codigo);
        throw new ReglaNegocioException(getClass(), "PRESTACION_CODIGO_DUPLICADO",
                "Ya existe una prestación activa con el código " + codigo);
    }
}
```

El `GlobalExceptionHandler`, al atrapar `AccesMedException`, **no vuelve a loguear** —
solo arma el `AccesMedError` con `codigo` y `httpStatus`. La única excepción a esta regla
es Bean Validation / `ConstraintViolationException`: como no vienen de un Service (las
lanza el propio framework en el borde HTTP), no hay "origen" que las loguee antes, así que
el handler las atrapa **y** las loguea en el mismo lugar:

```java
// Controllers/Errors/GlobalExceptionHandler.java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<AccesMedError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<String> errores = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    log.warn("Validación fallida en {}: {}", request.getRequestURI(), errores);
    return ResponseEntity.status(422).body(AccesMedError.of(422, "VALIDACION",
            "La solicitud tiene errores de validación.", errores, request.getRequestURI()));
}
```

## 5. Cuidado con datos sensibles

El dominio maneja datos de salud (`Paciente`, `ObraSocial`, `Turno`). **No loguear objetos
completos** que puedan traer DNI, teléfono, dirección o datos de obra social. Loguear ids
y campos no sensibles en `info`; si hace falta más detalle para diagnóstico, usar `debug`
(apagado por defecto en producción) en vez de subir el nivel de detalle en `info`.

```java
log.info("Solicitud recibida: crear turno para paciente id={}", createTurnoRequest.pacienteId());  // ✅
log.info("Solicitud recibida: {}", createTurnoRequest);  // ❌ si el record trae datos sensibles del paciente
```

## 6. Formato del mensaje: placeholders, no concatenación

Usá los placeholders `{}` de SLF4J, no concatenación de strings — si el nivel está
deshabilitado (ej. `debug` en producción), SLF4J no arma el mensaje, más performante.

```java
log.info("Creación de prestación iniciada: código={}", createPrestacionRequest.codigo());        // ✅
log.info("Creación de prestación iniciada: código=" + createPrestacionRequest.codigo());          // ❌
```

Para errores, seguí el patrón "qué se intentó hacer + por qué falló", igual que las reglas
de negocio del proyecto (ver ejemplo del usuario: "se quiso actualizar un médico con id
nulo"):

```java
log.warn("No se pudo actualizar el médico: id nulo en la request");
log.warn("No se pudo confirmar el turno {}: estado actual {} no permite confirmar", turnoId, estadoActual);
```

## Checklist

- [ ] `@Slf4j` (Lombok) en toda clase que loguea; sin `LoggerFactory` manual.
- [ ] `info` en Controller (recibida la request) y App (inicio de orquestación); `debug` en DomainService/QueryService.
- [ ] `warn` para errores de negocio y Bean Validation; `error` solo para el 500 genérico.
- [ ] Errores de negocio logueados **en el Service**, no en el `GlobalExceptionHandler` (evitar duplicar).
- [ ] Bean Validation / `ConstraintViolationException` logueados en el `GlobalExceptionHandler` (único caso donde el handler loguea).
- [ ] Sin datos sensibles de `Paciente`/`ObraSocial` en `info`; a `debug` si hace falta detalle.
- [ ] Placeholders `{}`, nunca concatenación de strings.
