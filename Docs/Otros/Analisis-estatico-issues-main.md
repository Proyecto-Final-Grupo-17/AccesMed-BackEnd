# Análisis estático del código — Resumen de issues

**Proyecto:** AccesMed — Backend | **Rama:** main | **Herramienta:** SonarQube Cloud  
**Último análisis de la rama:** 15/09/2026, 13:29 (GitHub Actions) | **Informe generado:** 28/09/2026

> **Alcance:** los datos corresponden al último análisis ejecutado sobre main, del 15/09/2026. El trabajo posterior realizado en develop no está reflejado en estas métricas.

---

## 1. Estado general

La rama principal pasó de no tener ningún issue abierto a acumular cuatro, y el Quality Gate quedó en **rojo**. Los cuatro issues se originaron el 14/09/2026, dentro de la feature de Security/JWT. El esfuerzo total de corrección estimado por la herramienta es de **25 minutos**.

| Métrica | Corrida del 14/09 | Corrida del 15/09 |
|---|---|---|
| Quality Gate | OK | **ERROR** |
| Issues abiertos | 0 | **4** |
| Bugs | 0 | 2 |
| Vulnerabilities | 0 | 1 |
| Code Smells | 0 | 1 |
| Security Hotspots | 0 | 0 |
| Reliability Rating | A | **D** |
| Security Rating | A | **D** |
| Maintainability Rating | A | A |
| Cobertura de tests | 12,6 % | 15,3 % |
| Duplicación de código | 1,1 % | 1,2 % |
| Líneas de código | 11.545 | 14.108 |
| Clases analizadas | 301 | 355 |

El crecimiento del proyecto fue considerable: 2.563 líneas y 54 clases nuevas. Pese a ese volumen, la cobertura de tests subió de 12,6 % a 15,3 %, lo que indica que el código incorporado llegó acompañado de tests.

---

## 2. Por qué el Quality Gate está en rojo

El Quality Gate «Sonar way» evalúa condiciones sobre el código nuevo, tomando como referencia el período que arranca el 13/09/2026. Tres de las seis condiciones fallaron:

| Condición | Valor actual | Umbral | Estado |
|---|---|---|---|
| Reliability Rating sobre código nuevo | D | A | **ERROR** |
| Security Rating sobre código nuevo | D | A | **ERROR** |
| Cobertura sobre código nuevo | 38,0 % | ≥ 80 % | **ERROR** |
| Maintainability Rating sobre código nuevo | A | A | OK |
| Duplicación sobre código nuevo | 1,0 % | < 3 % | OK |
| Security Hotspots revisados | 100 % | 100 % | OK |

Los dos ratings en D se explican íntegramente por los issues detallados en la sección siguiente. La cobertura sobre código nuevo (38 %) es bastante mejor que la global (15,3 %), pero sigue por debajo del umbral exigido.

---

## 3. Detalle de los issues detectados

### 3.1. Test sin assertions

| Dato | Valor |
|---|---|
| Regla | `java:S2699` — Tests should include assertions |
| Tipo | Code Smell |
| Severidad | BLOCKER |
| Impacto | Mantenibilidad / BLOCKER |
| Archivo | `src/test/java/.../Security/Services/Utils/AutorizacionServiceTest.java` |
| Línea | 36 |
| Esfuerzo estimado | 10 minutos |

El método de test `requireAuthority_usuarioTienePermiso_noLanza` ejecuta el método bajo prueba pero no verifica nada después:

```java
@Test
void requireAuthority_usuarioTienePermiso_noLanza() {
    doReturn(List.of(new SimpleGrantedAuthority(Permiso.USER_ALTA.name())))
            .when(usuarioDetails).getAuthorities();

    autorizacionService.requireAuthority(usuarioDetails, Permiso.USER_ALTA);
}
```

**Valoración:** es un problema real. El test pasa siempre que el método no lance una excepción, pero también pasaría si el método cambiara de comportamiento de otras formas. Un test sin assertions da una falsa sensación de cobertura: aporta a la métrica pero no protege contra regresiones.

**Corrección sugerida:** envolver la llamada en `assertDoesNotThrow`, que hace explícita la intención que el nombre del test ya declara («noLanza»).

```java
assertDoesNotThrow(() ->
        autorizacionService.requireAuthority(usuarioDetails, Permiso.USER_ALTA));
```

---

### 3.2. Instancias de SecureRandom creadas en cada llamada (dos ocurrencias)

| Dato | Valor |
|---|---|
| Regla | `java:S2119` — "Random" objects should be reused |
| Tipo | Bug |
| Severidad | CRITICAL |
| Impacto | Fiabilidad / HIGH |
| Archivos | `PasswordResetTokenDomainService.java` (línea 55) · `RefreshTokenDomainService.java` (línea 55) |
| Esfuerzo estimado | 5 minutos cada uno |

> **Importante:** no hay un problema de seguridad. Ambos servicios usan `SecureRandom` (no `java.util.Random`), generan 32 bytes aleatorios, los codifican en Base64-URL y almacenan únicamente el hash del token. La generación de tokens es criptográficamente correcta.

Lo que señala la regla es que la instancia se crea dentro del método, en cada invocación:

```java
byte[] bytesAleatorios = new byte[32];
new SecureRandom().nextBytes(bytesAleatorios);
String tokenPlano = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(bytesAleatorios);
```

**Valoración:** es un tema de eficiencia, no de seguridad. Cada instanciación de `SecureRandom` puede re-sembrarse desde la fuente de entropía del sistema operativo, lo que tiene un costo. El rating de fiabilidad HIGH está sobredimensionado en este caso: la regla apunta principalmente a `java.util.Random`, donde crear instancias repetidas puede producir secuencias predecibles por compartir semilla con el reloj del sistema. Con `SecureRandom` ese riesgo no existe.

**Corrección sugerida:** `SecureRandom` es thread-safe, así que puede declararse como constante de clase:

```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

// ...dentro del método:
SECURE_RANDOM.nextBytes(bytesAleatorios);
```

---

### 3.3. Protección CSRF deshabilitada

| Dato | Valor |
|---|---|
| Regla | `java:S4502` — CSRF protections should not be disabled |
| Tipo | Vulnerability |
| Severidad | CRITICAL |
| Impacto | Seguridad / HIGH |
| Archivo | `src/main/java/.../Security/Config/SecurityFilterChainConfig.java` |
| Línea | 53 |
| Esfuerzo estimado | 5 minutos |

Este issue **no es una regresión**: es el mismo que ya se había analizado y aceptado. La verificación del código confirma que la política de sesión sin estado sigue presente en la cadena de filtros, de modo que la justificación documentada en su momento continúa siendo válida.

```java
.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.cors(Customizer.withDefaults())
.csrf(AbstractHttpConfigurer::disable)
```

**Por qué reapareció:** el archivo se movió de `Config/SecurityConfig.java` a `Security/Config/SecurityFilterChainConfig.java`. SonarQube hace seguimiento de los issues por archivo; al desaparecer el original, cerró aquel issue como FIXED y levantó uno nuevo sobre el archivo destino. La resolución «Accepted» no se transfiere entre archivos distintos.

**Corrección sugerida:** no requiere tocar código. Basta con volver a marcarlo como **Accepted** en SonarQube, reutilizando la justificación: al no crearse sesiones HTTP, no existe cookie de sesión que un sitio externo pueda reutilizar para falsificar un request, que es precisamente el vector que la protección CSRF previene.

> **Nota:** esta situación se repetirá cada vez que el archivo cambie de nombre o de ubicación.

---

## 4. Mejoras observadas respecto del análisis anterior

- **La API dejó de estar abierta.** En el análisis anterior, la cadena de filtros aplicaba `permitAll()` sobre `/accesmed-api/**`, es decir, la totalidad de la API quedaba accesible sin autenticación. Con la feature de JWT incorporada, el `permitAll()` quedó acotado a los cinco endpoints de Auth (login, refresh, recuperación y confirmación de credenciales) y el resto exige autenticación.
- **El falso positivo previo se mantiene resuelto.** El issue `java:S8696` sobre `AgendaMedicoApp` sigue marcado como False Positive pese a que su línea se desplazó de la 315 a la 327. A diferencia del caso de CSRF, esa resolución sobrevivió porque el archivo conservó su nombre y ubicación.
- **La cobertura creció.** Pasó de 12,6 % a 15,3 % global, y el código nuevo alcanza 38 %, muy por encima del promedio histórico del proyecto.

---

## 5. Plan de corrección

| Orden | Issue | Acción | Efecto esperado |
|---|---|---|---|
| 1 | Test sin assertions (S2699) | Envolver la llamada en `assertDoesNotThrow` | Elimina el único defecto genuino; cierra el issue BLOCKER |
| 2 | SecureRandom ×2 (S2119) | Declarar la instancia como constante estática de clase | Reliability Rating vuelve de D a A |
| 3 | CSRF deshabilitado (S4502) | Marcar como Accepted con la justificación existente | Security Rating vuelve de D a A |

Completados los tres puntos, los tres ratings del proyecto regresan a A y quedan **cero issues abiertos**. El Quality Gate, sin embargo, **seguirá en rojo** por la condición de cobertura sobre código nuevo (38 % frente al 80 % exigido), que solo puede resolverse ampliando la batería de tests.
