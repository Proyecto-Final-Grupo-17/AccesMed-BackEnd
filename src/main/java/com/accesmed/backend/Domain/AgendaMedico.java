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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Período de vigencia de la agenda de un {@link Medico}. No tiene baja lógica: se
 * gestiona por vigencia, adelantando {@code fechaFinVigencia}.
 *
 * <p>El no solapamiento de períodos del mismo médico se refuerza en el esquema con una
 * constraint {@code EXCLUDE USING gist} (ver changelog), además de validarse en el
 * {@code DomainService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agenda_medico")
public class AgendaMedico extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private LocalDate fechaInicioVigencia;

    @NotNull
    @Column(name = "fecha_fin_vigencia", nullable = false)
    private LocalDate fechaFinVigencia;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_agenda_medico_medico"))
    private Medico medico;

    //endregion

}
