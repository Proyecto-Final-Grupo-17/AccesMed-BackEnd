package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Actualización del período de vigencia de una agenda ({@code PATCH /Agenda/Vigencia/{id}}).
 * Cubre las tres direcciones de movimiento del período: mover el inicio (solo si la agenda
 * no arrancó), adelantar el fin (restrictivo contra slots ocupados) o atrasarlo. Al menos
 * uno de los dos campos debe venir con valor.
 *
 * @param id {@code UUID} identificador de la agenda (validado contra la ruta en el Controller)
 * @param fechaHoraInicioVigencia {@code ZonedDateTime} nuevo inicio de vigencia, o {@code null}
 *        para no tocarlo
 * @param fechaHoraFinVigencia {@code ZonedDateTime} nuevo fin de vigencia, o {@code null} para
 *        no tocarlo
 */
public record UpdateVigenciaAgendaMedicoRequest(
        @NotNull UUID id,
        ZonedDateTime fechaHoraInicioVigencia,
        ZonedDateTime fechaHoraFinVigencia
) {

    /**
     * Valida que al menos uno de los dos campos de fecha venga con valor.
     *
     * @return {@code boolean} {@code true} si al menos una de las dos fechas no es {@code null}
     */
    @AssertTrue(message = "Debe indicarse al menos una de fechaHoraInicioVigencia o fechaHoraFinVigencia.")
    public boolean isAlgunaFechaPresente() {

        return fechaHoraInicioVigencia != null || fechaHoraFinVigencia != null;

    }

}
