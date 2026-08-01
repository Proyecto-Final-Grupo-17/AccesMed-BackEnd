package com.accesmed.backend.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Copia por {@link Turno} de una {@link IndicacionPrestacion} de la prestación. No
 * duplica texto: {@code nombre}, {@code descripcion} y {@code requiereValidacion} se leen
 * por navegabilidad hacia {@link IndicacionPrestacion}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "indicacion_prestacion_turno")
public class IndicacionPrestacionTurno extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "fecha_hora_validacion")
    private ZonedDateTime fechaHoraValidacion;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_indicacion_prestacion_turno_turno"))
    private Turno turno;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicacion_prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_indicacion_prestacion_turno_indicacion_prestacion"))
    private IndicacionPrestacion indicacionPrestacion;

    /**
     * Rol {@code validadoPor}: quién validó la indicación. Nulo mientras esté pendiente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validado_por_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_indicacion_prestacion_turno_validado_por"))
    private Usuario validadoPor;

    //endregion

    //region ========== Baja ==========

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

    //endregion

}
