package com.accesmed.backend.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Snapshot transaccional de una reserva. No se recalcula por cambios posteriores en el
 * catálogo. No tiene baja lógica: su ciclo de vida se gestiona por {@link EstadoTurno} vía
 * {@link HistoricoEstadoTurno}.
 *
 * <p>No lleva FK a {@link MedicoPrestacion}: el par {@code (medico, prestacion)} se valida
 * buscando una {@code MedicoPrestacion} activa en el {@code DomainService}, porque el
 * esquema ya no lo garantiza.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "turno")
public class Turno extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 40)
    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @NotNull
    @Column(name = "fecha_hora_inicio", nullable = false)
    private ZonedDateTime fechaHoraInicio;

    @NotNull
    @PositiveOrZero
    @Column(name = "monto_a_pagar", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoAPagar;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cobertura", nullable = false, length = 20)
    private TipoCobertura tipoCobertura;

    @NotNull
    @Column(name = "fecha_limite_validacion", nullable = false)
    private ZonedDateTime fechaLimiteValidacion;

    @NotNull
    @Column(name = "fecha_limite_reprogramacion", nullable = false)
    private ZonedDateTime fechaLimiteReprogramacion;

    @NotNull
    @Column(name = "fecha_limite_confirmacion", nullable = false)
    private ZonedDateTime fechaLimiteConfirmacion;

    @NotNull
    @Column(name = "fecha_limite_cancelacion", nullable = false)
    private ZonedDateTime fechaLimiteCancelacion;

    @NotNull
    @Column(name = "fecha_limite_anuncio_temprano", nullable = false)
    private ZonedDateTime fechaLimiteAnuncioTemprano;

    @NotNull
    @Column(name = "fecha_limite_anuncio_tardio", nullable = false)
    private ZonedDateTime fechaLimiteAnuncioTardio;

    @NotNull
    @Column(name = "fecha_hora_recordatorio_confirmacion", nullable = false)
    private ZonedDateTime fechaHoraRecordatorioConfirmacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelacion", length = 30)
    private MotivoCancelacion motivoCancelacion;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_paciente"))
    private Paciente paciente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_medico"))
    private Medico medico;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_prestacion"))
    private Prestacion prestacion;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_horarios_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_agenda_horarios"))
    private AgendaHorarios agendaHorarios;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obra_social_paciente_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_obra_social_paciente"))
    private ObraSocialPaciente obraSocialPaciente;

    /**
     * Auto-referencia: turno del que este proviene por una reprogramación. Cadena de
     * reprogramaciones.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_origen_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_turno_turno_origen"))
    private Turno turnoOrigen;

    //endregion

}
