package com.accesmed.backend.Services.AgenteServices;

/**
 * Acciones del canal chatbot que el agente pide durante una tanda ({@code TandaAgenteService}): cada
 * una tiene su aviso de espera para el paciente. Hay tres grupos: las escrituras, las consultas que
 * tardan (catálogo, horarios, turnos y cobertura) y el reintento de la verificación. Las consultas
 * rápidas (datos del paciente, clínica y obras sociales) no avisan. El "Aguardá un momento…"
 * genérico sale aparte, al despachar la tanda (Diseno-Chatbot §3.4.6).
 */
public enum AccionAgente {

    //Escrituras
    RESERVA_TURNO,
    CANCELACION_TURNO,
    REPROGRAMACION_TURNO,
    CONFIRMACION_TURNO,
    REGISTRO_PACIENTE,
    MODIFICACION_PACIENTE,
    BAJA_PACIENTE,

    //Consultas que tardan
    CONSULTA_CATALOGO,
    CONSULTA_HORARIOS,
    CONSULTA_TURNOS,
    CONSULTA_COBERTURA,

    /**
     * Segunda verificación de la misma tanda: el agente está rehaciendo su respuesta. No cuenta como
     * aviso de herramienta.
     */
    REINTENTO_VERIFICACION

}
