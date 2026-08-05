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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Tramo de permanencia de una {@link Prestacion} en un {@link EstadoPrestacion}. El
 * vigente es el que tiene {@code fechaHoraFin} vacío. Es la auditoría del ciclo de vida
 * del catálogo, no la fuente de la consulta caliente (eso lo resuelve
 * {@code Prestacion.estadoActual}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "historico_estado_prestacion")
public class HistoricoEstadoPrestacion extends Auditable {

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
    private EstadoPrestacion estado;

    @Size(max = 500)
    @Column(name = "motivo", length = 500)
    private String motivo;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_historico_estado_prestacion_prestacion"))
    private Prestacion prestacion;

    //endregion

}
