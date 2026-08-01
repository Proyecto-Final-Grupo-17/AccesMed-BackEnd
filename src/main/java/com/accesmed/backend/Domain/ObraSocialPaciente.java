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
 * Clase asociativa: cobertura declarada por un {@link Paciente} sobre un {@link Plan}.
 * Inmutable: solo alta y baja lógica. La {@link ObraSocial} se alcanza navegando por
 * {@code plan.obraSocial}, sin FK redundante.
 */
@Getter
@Setter(AccessLevel.NONE)
@NoArgsConstructor
@Entity
@Table(name = "obra_social_paciente")
public class ObraSocialPaciente extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "nro_socio", nullable = false, updatable = false, length = 50)
    private String nroSocio;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_obra_social_paciente_paciente"))
    private Paciente paciente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_obra_social_paciente_plan"))
    private Plan plan;

    //endregion

    //region ========== Baja ==========

    @Setter
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Setter
    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Setter
    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

    //endregion

}
