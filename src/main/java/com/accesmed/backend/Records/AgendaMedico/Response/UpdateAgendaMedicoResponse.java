package com.accesmed.backend.Records.AgendaMedico.Response;

import java.util.UUID;

/**
 * Respuesta del delta de composición de {@code updateAgendaMedico}, con los conteos de
 * cada efecto aplicado por la transacción (ver el orden de pasos en
 * {@code Docs/Planes/auditoria-v3-y-feature-agenda.md}).
 *
 * @param id {@code UUID} identificador de la agenda actualizada
 * @param cantidadHorariosAgregados {@code int} cantidad de slots nuevos generados por {@code horariosAAgregar}
 * @param cantidadHorariosExcluidos {@code int} cantidad de {@code AgendaHorarios} dados de baja
 *        (por {@code horariosAExcluir} más los arrastrados por {@code diasAExcluir})
 * @param cantidadDiasExcluidos {@code int} cantidad de {@code AgendaDia} dados de baja explícitamente
 *        por {@code diasAExcluir}
 * @param cantidadDiasDadosDeBajaAutomaticamente {@code int} cantidad de {@code AgendaDia} dados de baja
 *        porque quedaron sin horarios activos tras aplicar las exclusiones
 */
public record UpdateAgendaMedicoResponse(
        UUID id,
        int cantidadHorariosAgregados,
        int cantidadHorariosExcluidos,
        int cantidadDiasExcluidos,
        int cantidadDiasDadosDeBajaAutomaticamente
) {

}
