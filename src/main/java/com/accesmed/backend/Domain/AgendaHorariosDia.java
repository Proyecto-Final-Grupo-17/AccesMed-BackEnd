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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Slot reservable de un día concreto de una {@link AgendaMedico}, para una
 * {@link Prestacion} determinada. La duración planificada se deriva de
 * {@code horaHasta - horaDesde}; no se persiste como columna.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agenda_horarios_dia")
public class AgendaHorariosDia extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @NotNull
    @Column(name = "hora_desde", nullable = false)
    private LocalTime horaDesde;

    @NotNull
    @Column(name = "hora_hasta", nullable = false)
    private LocalTime horaHasta;

    @NotNull
    @Column(name = "fecha_limite_reserva", nullable = false)
    private ZonedDateTime fechaLimiteReserva;

    @NotNull
    @Column(name = "esta_ocupada", nullable = false)
    private Boolean estaOcupada = false;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_medico_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_agenda_horarios_dia_agenda_medico"))
    private AgendaMedico agendaMedico;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_agenda_horarios_dia_prestacion"))
    private Prestacion prestacion;

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
