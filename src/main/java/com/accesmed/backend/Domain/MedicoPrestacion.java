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
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Clase asociativa: asignación de una {@link Prestacion} a un {@link Medico}. Guarda las
 * condiciones de atención particular. No tiene baja lógica: se retira cerrando
 * {@code fechaFinVigencia}, que admite fecha futura para programar el corte. Reasignar es
 * crear una instancia nueva, no reabrir la vieja.
 *
 * <p>La regla "la especialidad de la prestación coincide con la del médico" no es
 * expresable en el esquema: se valida en el {@code DomainService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "medico_prestacion")
public class MedicoPrestacion extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "atiende_particular", nullable = false)
    private Boolean atiendeParticular;

    @NotNull
    @Positive
    @Column(name = "precio_particular", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioParticular;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_medico_prestacion_medico"))
    private Medico medico;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestacion_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_medico_prestacion_prestacion"))
    private Prestacion prestacion;

    //endregion

    //region ========== Vigencia ==========

    @NotNull
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia")
    private LocalDate fechaFinVigencia;

    //endregion

}
