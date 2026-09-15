package com.accesmed.backend.Security.Services.Utils;

import com.accesmed.backend.Security.Jwt.UsuarioDetails;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resuelve el alcance ("lo mío" vs. "todo") para los endpoints que un médico solo puede
 * usar sobre sus propios recursos (Turno, Agenda, Paciente). No conoce nada de esos
 * dominios: solo interpreta al usuario autenticado. La aplicación específica de cada
 * dominio (qué campo identifica "lo propio" en Turno, en Agenda, etc.) la hace el
 * {@code QueryService}/{@code App} que lo llama.
 */
@Slf4j
@Component
public class AlcanceMedicoService {

    //region ========== Métodos ==========

    /**
     * Para lecturas: si quien pregunta es médico, ignora el id solicitado y fuerza el
     * propio. Si no es médico (Admin/SuperAdmin), devuelve el id solicitado tal cual
     * (puede ser {@code null}, sin filtro).
     *
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @param medicoIdSolicitado {@code UUID} id de médico pedido en el filtro del request, puede ser {@code null}
     * @return {@code UUID} el id de médico efectivo a usar en la consulta
     */
    public UUID resolveMedicoId(UsuarioDetails usuarioDetails, UUID medicoIdSolicitado) {
        return usuarioDetails.getMedicoId() != null ? usuarioDetails.getMedicoId() : medicoIdSolicitado;
    }

    /**
     * Para escrituras: valida que el recurso pertenezca al médico que llama. No hace
     * nada si quien llama no es médico (Admin/SuperAdmin no tienen esta restricción).
     *
     * @param usuarioDetails {@code UsuarioDetails} identidad autenticada
     * @param medicoIdDelRecurso {@code UUID} id del médico dueño del recurso ya cargado
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el médico autenticado
     *         no es el dueño del recurso
     */
    public void validateMedicoPropietario(UsuarioDetails usuarioDetails, UUID medicoIdDelRecurso) {

        UUID medicoIdAutenticado = usuarioDetails.getMedicoId();

        if (medicoIdAutenticado != null && !medicoIdAutenticado.equals(medicoIdDelRecurso)) {
            log.warn("Médico intentó operar sobre un recurso ajeno: medicoIdAutenticado={}, medicoIdDelRecurso={}",
                    medicoIdAutenticado, medicoIdDelRecurso);
            throw new ReglaNegocioException(getClass(), "RECURSO_AJENO",
                    "No puede operar sobre un recurso que no le pertenece.");
        }

    }

    //endregion

}
