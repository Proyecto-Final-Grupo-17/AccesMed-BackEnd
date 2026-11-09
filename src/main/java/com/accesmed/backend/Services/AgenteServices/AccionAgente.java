package com.accesmed.backend.Services.AgenteServices;

/**
 * Acciones de escritura del canal chatbot que el agente pide durante una tanda
 * ({@code TandaAgenteService}): cada una tiene su aviso de espera. Las consultas no avisan: el
 * "Un momento…" genérico sale al despachar la tanda (Diseno-Chatbot §4.7).
 */
public enum AccionAgente {

    RESERVA_TURNO,
    CANCELACION_TURNO,
    REPROGRAMACION_TURNO,
    CONFIRMACION_TURNO,
    REGISTRO_PACIENTE,
    MODIFICACION_PACIENTE,
    BAJA_PACIENTE

}
