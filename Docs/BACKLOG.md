# Backlog — AccesMed Backend

> Pendientes futuros, no prioritarios. A diferencia de `CLAUDE.md` (convenciones ya
> vigentes) o `Docs/Planes/` (planes de implementación en curso), esto es una lista de
> ideas/mejoras para retomar más adelante, sin fecha asignada.

## Logging

- **Correlation/request id (MDC)**: hoy cada línea de log (`Controller` → `App` →
  `DomainService`) es independiente. Sin un id común, con tráfico concurrente en
  `staging`/`prod` va a ser difícil reconstruir la secuencia completa de una request
  específica entre capas.

  Implementación prevista: un `Filter`/`HandlerInterceptor` que haga
  `MDC.put("requestId", UUID...)` al entrar la request y `MDC.clear()` al salir, más
  agregar `%X{requestId}` al pattern de Logback. No requiere tocar las líneas de
  `log.info/warn/debug` existentes.

  Retomar cuando se acerque `staging` o haya tráfico concurrente real.