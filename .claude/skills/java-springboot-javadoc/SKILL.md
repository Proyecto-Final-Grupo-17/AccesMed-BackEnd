---
name: java-springboot-javadoc
description: >
  Estilo y formato de Javadoc para el backend Java + Spring Boot de AccesMed. Úsala
  SIEMPRE que documentes métodos o clases Java: al crear código nuevo, al completar
  documentación faltante o al revisar Javadoc. Define qué documentar (nombre y qué hace el
  método, @param con tipo, @return con tipo, @throws con tipo y motivo), en qué orden, y
  cómo recuadrar los tipos con la etiqueta inline {@code}. Javadoc en español.
---

# Estilo de Javadoc — Java + Spring Boot (AccesMed)

> **Preferencias en evolución**: este formato refleja el gusto personal de Franco y puede
> cambiar con el tiempo. Si pide un ajuste, actualizá esta skill (no solo el código nuevo).

Documentá en **español**. Todo método público (y los privados no triviales) lleva Javadoc
con esta estructura y en este orden.

## Estructura obligatoria

1. **Primera línea**: qué hace el método, breve, empezando por un verbo en tercera persona
   ("Crea...", "Valida...", "Busca..."). Cuando ayude, mencioná el nombre del método.
2. `@param` — uno por parámetro, con **su tipo recuadrado** con `{@code}` y una breve descripción.
3. `@return` — qué devuelve, con **el tipo recuadrado** con `{@code}`. Omitir si es `void`.
4. `@throws` — una por excepción, con **el tipo recuadrado** con `{@code}` y el motivo, breve.

Los tipos se "recuadran" (se muestran en monoespaciado) con la etiqueta inline
`{@code TipoDeDato}`. Es la forma correcta en Javadoc; en el HTML generado se ve como código.

## Plantilla

```java
/**
 * Crea una prestación nueva validando que su código no esté repetido.
 *
 * @param crearPrestacionRequest {@code CrearPrestacionRequest} datos de la prestación a crear
 * @return {@code CrearPrestacionResponse} la prestación creada, con su id asignado
 * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
 *         activa con el mismo código
 * @throws ValidacionException {@code ValidacionException} si el request tiene errores de
 *         negocio acumulados
 */
public CrearPrestacionResponse createPrestacion(CrearPrestacionRequest crearPrestacionRequest) {
    ...
}
```

## Ejemplo con validación (void)

```java
/**
 * Valida que el código de prestación sea único entre las prestaciones activas.
 *
 * @param codigo {@code String} código a verificar
 * @throws ReglaNegocioException {@code ReglaNegocioException} si ya existe una prestación
 *         activa con ese código
 */
public void validateCodigoPrestacionIsUnique(String codigo) {
    ...
}
```

## Ejemplo de consulta

```java
/**
 * Busca un turno por su identificador. No devuelve turnos dados de baja.
 *
 * @param id {@code Long} identificador del turno
 * @return {@code Turno} el turno activo correspondiente al id
 * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si no existe un
 *         turno activo con ese id
 */
public Turno findTurnoById(Long id) {
    ...
}
```

## Javadoc de clase

Breve: qué representa/hace la clase y su rol en la arquitectura.

```java
/**
 * Caso de uso de Prestación. Orquesta el flujo completo de los endpoints de prestaciones
 * (creación, actualización, baja) validando reglas de negocio y coordinando los services.
 */
@Service
@RequiredArgsConstructor
public class PrestacionApp { ... }
```

## Reglas

- **Idioma**: español.
- **Tipos siempre recuadrados** con `{@code ...}` en `@param`, `@return` y `@throws`.
- **Orden fijo**: descripción → `@param` (en el orden de la firma) → `@return` → `@throws`.
- Un `@param` por parámetro; un `@throws` por cada excepción que el método puede lanzar
  (incluidas las no chequeadas relevantes, como las subclases de `AccesMedException`).
- Breve y útil: no repitas el nombre del tipo en prosa si ya está en `{@code}`; no
  documentes lo obvio con paja.
- Getters/setters triviales y accesores de records no necesitan Javadoc.
- Frases con verbo en tercera persona ("Crea", "Valida", "Busca", "Actualiza").

## Checklist

- [ ] Primera línea = qué hace, verbo en 3ª persona, en español.
- [ ] `@param` por cada parámetro, con `{@code Tipo}` + descripción.
- [ ] `@return` con `{@code Tipo}` (o ausente si es `void`).
- [ ] `@throws` por cada excepción, con `{@code Tipo}` + motivo.
- [ ] Orden: descripción → param → return → throws.
- [ ] Javadoc de clase presente en controllers, apps y services.
