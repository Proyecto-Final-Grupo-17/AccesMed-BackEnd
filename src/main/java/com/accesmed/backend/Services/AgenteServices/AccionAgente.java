package com.accesmed.backend.Services.AgenteServices;

/**
 * Acciones del canal chatbot que se registran en la tanda en curso ({@code TandaAgenteService}):
 * sirven para mandar el aviso de espera y para verificar que el agente no afirme un resultado que
 * no se ejecutó. Las de escritura son las que el agente puede anunciar como hechas.
 */
public enum AccionAgente {

    CONSULTA_CATALOGO("listEspecialidades / getOfertaEspecialidad / searchPrestacion / searchMedico", false),
    CONSULTA_HORARIOS("listHorariosDisponibles", false),
    CONSULTA_TURNOS("listTurnos", false),
    RESERVA_TURNO("createTurno", true),
    CANCELACION_TURNO("cancelTurno", true),
    REPROGRAMACION_TURNO("reprogramTurno", true),
    CONFIRMACION_TURNO("confirmTurno", true),
    REGISTRO_PACIENTE("createPaciente", true),
    MODIFICACION_PACIENTE("updatePaciente", true),
    BAJA_PACIENTE("softDeletePaciente", true);

    //region ========== Atributos ==========

    /**
     * Nombre de la herramienta de Flowise que ejecuta la acción (para la instrucción de corrección).
     */
    private final String herramienta;

    /**
     * {@code true} si la acción modifica datos.
     */
    private final boolean escritura;

    //endregion

    AccionAgente(String herramienta, boolean escritura) {
        this.herramienta = herramienta;
        this.escritura = escritura;
    }

    //region ========== Métodos ==========

    /**
     * Obtiene el nombre de la herramienta de Flowise que ejecuta la acción.
     *
     * @return {@code String} nombre de la herramienta
     */
    public String getHerramienta() {
        return herramienta;
    }

    /**
     * Indica si la acción modifica datos.
     *
     * @return {@code boolean} {@code true} si es una escritura
     */
    public boolean isEscritura() {
        return escritura;
    }

    //endregion

}
