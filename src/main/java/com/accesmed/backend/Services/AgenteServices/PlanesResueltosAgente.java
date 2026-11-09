package com.accesmed.backend.Services.AgenteServices;

import com.accesmed.backend.Domain.Plan;

import java.util.List;

/**
 * Planes a los que se refiere una consulta de cobertura del canal chatbot, resueltos por nombre (o los
 * del paciente registrado). Si la obra social o el plan no existen, {@code planes} viene vacío y trae el
 * motivo, para contestarle al paciente sin tratarlo como un error.
 *
 * @param planes {@code List<Plan>} planes encontrados (vacía si no existen)
 * @param obraSocialDicha {@code String} obra social tal como la nombró el paciente, o {@code null}
 * @param planDicho {@code String} plan tal como lo nombró el paciente, o {@code null}
 * @param codigoMotivo {@code String} por qué no hay planes ({@code OBRA_SOCIAL_NO_ENCONTRADA}, {@code PLAN_NO_ENCONTRADO}), o {@code null}
 * @param motivo {@code String} el motivo en español para el paciente, o {@code null}
 */
public record PlanesResueltosAgente(
        List<Plan> planes,
        String obraSocialDicha,
        String planDicho,
        String codigoMotivo,
        String motivo
) {
}
