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
 * Requisito previo de una {@link Prestacion}. Editable únicamente mientras su prestación
 * está en borrador ({@code fechaHabilitacion} nula); una vez habilitada la prestación
 * queda inmutable y solo admite alta y baja lógica. Nunca existe suelta ni se comparte
 * entre prestaciones.
 */
@Getter
@Setter(AccessLevel.NONE)
@NoArgsConstructor
@Entity
@Table(name = "indicacion_prestacion")
public class IndicacionPrestacion extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Setter
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @NotBlank
    @Size(max = 1000)
    @Setter
    @Column(name = "descripcion", nullable = false, length = 1000)
    private String descripcion;

    @NotNull
    @Setter
    @Column(name = "requiere_validacion", nullable = false)
    private Boolean requiereValidacion;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_indicacion_prestacion_prestacion"))
    private Prestacion prestacion;

    @NotNull
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_indicacion_prestacion_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_indicacion_prestacion_tipo_indicacion_prestacion"))
    private TipoIndicacionPrestacion tipoIndicacionPrestacion;

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
