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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Requisito previo de una {@link Prestacion}, acotado por vigencia. No tiene baja lógica:
 * se retira cerrando {@code fechaFinVigencia}, que admite fecha futura para programar el
 * retiro. Nunca existe suelta ni se comparte entre prestaciones.
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

    //region ========== Vigencia ==========

    @NotNull
    @Setter
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Setter
    @Column(name = "fecha_fin_vigencia")
    private LocalDate fechaFinVigencia;

    //endregion

}
