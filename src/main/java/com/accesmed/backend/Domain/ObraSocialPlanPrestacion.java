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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Clase asociativa: cobertura que un {@link Plan} da sobre una {@link Prestacion}. Define
 * cómo se calcula el monto a pagar del {@link Turno}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "obra_social_plan_prestacion")
public class ObraSocialPlanPrestacion extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_cobertura", nullable = false, length = 20)
    private ModalidadCobertura modalidadCobertura;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(name = "porcentaje_cobertura", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeCobertura;

    @NotNull
    @PositiveOrZero
    @Column(name = "coseguro", nullable = false, precision = 12, scale = 2)
    private BigDecimal coseguro;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_obra_social_plan_prestacion_plan"))
    private Plan plan;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_obra_social_plan_prestacion_prestacion"))
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
