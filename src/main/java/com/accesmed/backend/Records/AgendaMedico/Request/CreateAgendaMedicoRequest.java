package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Alta de un período de agenda de un médico. Lleva exactamente uno de dos modos de
 * expansión: {@code patronSemanal} (se repite por día de la semana) o {@code diasSueltos}
 * (fechas concretas). Ninguno de los dos se persiste tal cual: el {@code App} los expande
 * a {@code AgendaHorariosDia} y descarta el patrón.
 *
 * @param medicoId {@code UUID} identificador del médico dueño de la agenda
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} inicio del período de vigencia
 * @param fechaHoraFinVigencia {@code ZonedDateTime} fin del período de vigencia
 * @param patronSemanal {@code List<DiaPatronRequest>} patrón semanal a repetir, o {@code null}
 *        si se usa {@code diasSueltos}
 * @param diasSueltos {@code List<DiaSueltoRequest>} fechas concretas, o {@code null} si se
 *        usa {@code patronSemanal}
 */
public record CreateAgendaMedicoRequest(
        @NotNull UUID medicoId,
        @NotNull ZonedDateTime fechaHoraInicioVigencia,
        @NotNull ZonedDateTime fechaHoraFinVigencia,
        List<@Valid DiaPatronRequest> patronSemanal,
        List<@Valid DiaSueltoRequest> diasSueltos
) {

    /**
     * Valida que se haya enviado exactamente uno de los dos modos de expansión.
     *
     * @return {@code boolean} {@code true} si exactamente uno de {@code patronSemanal}/{@code diasSueltos}
     *         viene con contenido
     */
    @AssertTrue(message = "Debe indicarse exactamente uno de patronSemanal o diasSueltos.")
    public boolean isModoExpansionExclusivo() {

        boolean tienePatron = patronSemanal != null && !patronSemanal.isEmpty();
        boolean tieneDiasSueltos = diasSueltos != null && !diasSueltos.isEmpty();
        return tienePatron ^ tieneDiasSueltos;

    }

}
