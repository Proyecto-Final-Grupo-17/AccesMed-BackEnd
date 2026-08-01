---
name: feature-documenter
description: >
  Genera o actualiza la documentación funcional de una feature del backend AccesMed en
  `docs/feature/<Entidad-o-Funcionalidad>.md`: contexto de negocio (para qué es, para qué
  sirve, quiénes la usan), las funciones que ofrece por endpoint, un flujo simplificado de
  cada una, y una guía de cómo armar el request y leer el response desde el front. Úsala al
  terminar de generar una feature (invocada por `springboot-feature-generator`) o cuando el
  usuario pida "documentá la feature de X" / "actualizá la doc de X". Una "feature" puede
  agrupar varias funcionalidades relacionadas (ej. todo el ciclo de vida de Turno).
---

# Documentador de features — backend AccesMed

Genera o actualiza el documento funcional de una feature en `../../../Docs/Features/`. Es el
complemento de Swagger: Swagger documenta la forma (request/response exactos), este
documento documenta el **propósito de negocio** y da una guía de integración legible para
quien arma el front o las Custom Tools del agente, sin tener que leer el código.

## Fase 1 — Reunir el material

Si la skill fue invocada por `springboot-feature-generator`, ya viene con el contexto de
negocio y el detalle técnico recopilados en su Fase 1 — no volver a preguntar, solo pedir
confirmación de lo que falte. Si se invoca de forma independiente, preguntar:

1. **Nombre de la feature o funcionalidad** a documentar (ej. `Turno`, `GestionTurnos`).
   Define el nombre del archivo: `../../../Docs/Features/<Nombre>.md`.
2. **Contexto de negocio**:
   - Para qué es: qué problema de negocio resuelve.
   - Para qué sirve: qué logra el usuario final al usarla.
   - Quiénes la usan: paciente (agente/WhatsApp), personal de clínica (admin, médico), o
     ambos, y desde qué frente.
3. **Funciones que la componen**: si el código ya existe, leer directo de
   `Controllers/<Entidad>Controller.java`, `Records/<Entidad>/Request/` y
   `Records/<Entidad>/Response/` en vez de volver a preguntar (ruta completa —base de clase
   + recurso del método—, verbo HTTP, campos de cada record, reglas de negocio visibles en
   el `App`/`DomainService`). Si no existe todavía, preguntar por cada función: ruta, verbo,
   campos de entrada/salida y qué reglas de negocio aplica.

## Fase 2 — Generar o actualizar el documento

Archivo: `../../../Docs/Features/<Nombre>.md`. Estructura fija:

```markdown
# Feature: <Nombre>

## Contexto
- Para qué es: ...
- Para qué sirve: ...
- Quiénes la usan: ...

## Funciones

### <Verbo + Entidad, ej. "Crear turno"> — `POST /accesmed-api/Turno/Turno`

**Flujo simplificado:**
1. ...
2. ...

**Request para el front — `CreateTurnoRequest`**

| Campo | Tipo | Obligatorio | Notas |
|-------|------|-------------|-------|
| ... | ... | ... | ... |

**Response para el front — `CreateTurnoResponse`**

| Campo | Tipo | Para qué lo usa el front |
|-------|------|--------------------------|
| ... | ... | ... |

(repetir el bloque "Función" por cada endpoint de la feature)

---

> Errores: todos los endpoints devuelven el mismo contrato `AccesMedError` ante cualquier
> falla — ver `docs/FRONTEND-GUIA.md §1`.
```

Reglas de contenido:

- **Flujo simplificado**: 3 a 6 pasos en lenguaje de negocio (qué valida, qué persiste, qué
  devuelve) — no repetir el código línea a línea ni nombrar variables internas.
- **Tablas de request/response**: la columna clave es "para qué lo usa el front" (no una
  descripción genérica del campo) — ej. "para navegar al detalle del turno", "para
  deshabilitar el botón de confirmar mientras está `Pendiente`". Mismo estilo que
  `../../../Docs/FRONTEND-GUIA.md`.
- **Ruta del endpoint**: siempre la ruta completa (`/accesmed-api/<Entidad>/<Recurso>[/{id}]`),
  tal como la ve el front. En `PUT`/`PATCH`, aclarar en las notas del campo `id` que tiene
  que ser **el mismo** que el de la URL (si difieren, el backend responde 422). Si un `PATCH`
  no lleva body, decirlo explícitamente en vez de poner una tabla de request vacía.
- **Errores**: nunca reexplicar `AccesMedError` acá — solo referenciar
  `docs/FRONTEND-GUIA.md §1`.
- **Si el archivo ya existe**: actualizar, no duplicar. Agregar funciones nuevas al final de
  `## Funciones`, actualizar las que cambiaron (flujo, request o response), dejar intacto el
  resto del documento (incluido el `## Contexto`, salvo que el propio contexto de negocio
  haya cambiado).
- **Idioma**: español, igual que el resto de la documentación del proyecto.

## Fase 3 — Verificar

- [ ] `../../../Docs/Features/<Nombre>.md` existe con `## Contexto` y una sección `### <Función>` por
      cada endpoint de la feature.
- [ ] Cada función documentada corresponde a un endpoint real: el nombre del record en la
      tabla (`CreateTurnoRequest`, etc.) coincide con la clase en `Records/<Entidad>/Request`
      o `Records/<Entidad>/Response`, y la ruta documentada con la del controller
      (`@RequestMapping` de la clase + ruta del método).
- [ ] No se duplicó el contrato de errores — se referencia `FRONTEND-GUIA.md §1`.
- [ ] Si el archivo ya existía, se actualizó sin perder contenido de funciones no tocadas.
- [ ] El documento no reexplica la arquitectura interna (capas, DomainService, etc.) — eso
      vive en `../../../Docs/ARQUITECTURA.md`; acá solo negocio + contrato para el front.
