package com.accesmed.backend.Records.AgendaMedico.Request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Actualización del período de vigencia de una agenda ({@code PATCH /Agenda/Vigencia/{id}}).
 * Cubre las tres direcciones de movimiento del período: mover el inicio (solo si la agenda
 * no arrancó), adelantar el fin (restrictivo contra slots ocupados) o atrasarlo. Al menos
 * uno de los dos campos debe venir con valor.
 *
 * @param id {@code UUID} identificador de la agenda (validado contra la ruta en el Controller)
 * @param fechaInicioVigencia {@code LocalDate} nuevo inicio de vigencia, o {@code null}
 *        para no tocarlo
 * @param fechaFinVigencia {@code LocalDate} nuevo fin de vigencia, o {@code null} para
 *        no tocarlo
 */
public record UpdateVigenciaAgendaMedicoRequest(
        @NotNull UUID id,
        LocalDate fechaInicioVigencia,
        LocalDate fechaFinVigencia
) {

    /**
     * Valida que al menos uno de los dos campos de fecha venga con valor.
     *
     * @return {@code boolean} {@code true} si al menos una de las dos fechas no es {@code null}
     */
    @AssertTrue(message = "Debe indicarse al menos una de fechaInicioVigencia o fechaFinVigencia.")
    public boolean isAlgunaFechaPresente() {

        return fechaInicioVigencia != null || fechaFinVigencia != null;

    }

}
