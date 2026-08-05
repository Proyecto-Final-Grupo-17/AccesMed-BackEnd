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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Plan de una {@link ObraSocial}. La relación con la obra social es inmutable después
 * del alta: no expone setter, y el {@code Mapper} no la actualiza.
 *
 * <p>El ciclo de vida es por estados ({@link EstadoPlan}), mismo esquema que
 * {@link Prestacion}: {@code No Publicado ⇄ Publicado → Deshabilitado}. Deshabilitar es
 * terminal e irreversible, y la unicidad de {@code codigo}/{@code nombre} (por obra
 * social) rige entre los no deshabilitados.</p>
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

    /**
     * Estado actual del ciclo de vida del catálogo, mantenido por el
     * {@code DomainService} junto con el tramo de {@link HistoricoEstadoPlan}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actual", nullable = false, length = 20)
    private EstadoPlan estadoActual;

    //endregion

    //region ========== Relaciones ==========

    /**
     * Se setea una única vez, al alta (constructor/mapper de creación): la columna es
     * {@code updatable = false} y el {@code Mapper} de actualización nunca la toca.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "obra_social_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_plan_obra_social"))
    private ObraSocial obraSocial;

    //endregion

}
