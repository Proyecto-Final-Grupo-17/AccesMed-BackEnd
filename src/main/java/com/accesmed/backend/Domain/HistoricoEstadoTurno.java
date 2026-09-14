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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Tramo de permanencia de un {@link Turno} en un {@link EstadoTurno}. El vigente es el
 * que tiene {@code fechaHoraFin} vacío. El estado va como columna enum, no como FK a
 * catálogo.
 *
 * <p>El índice único parcial {@code uq_historico_estado_turno_vigente} (ver changelog) es
 * la defensa real, a nivel de esquema, contra la condición de carrera que permitiría dos
 * tramos vigentes simultáneos del mismo turno.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "historico_estado_turno")
public class HistoricoEstadoTurno extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "fecha_hora_inicio", nullable = false)
    private ZonedDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin")
    private ZonedDateTime fechaHoraFin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTurno estado;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_historico_estado_turno_turno"))
    private Turno turno;

    //endregion

}
