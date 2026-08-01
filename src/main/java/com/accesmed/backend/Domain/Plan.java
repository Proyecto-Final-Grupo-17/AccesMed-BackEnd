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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Plan de una {@link ObraSocial}. La relación con la obra social es inmutable después
 * del alta: no expone setter, y el {@code Mapper} no la actualiza.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan")
public class Plan extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "obra_social_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_plan_obra_social"))
    private ObraSocial obraSocial;

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
