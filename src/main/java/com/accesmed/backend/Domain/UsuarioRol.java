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

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Clase asociativa: asignación de un {@link Rol} a un {@link Usuario}, acotada por
 * vigencia. No tiene baja lógica: se revoca cerrando {@code fechaFinVigencia}.
 *
 * <p>La regla "un usuario no tiene dos {@code UsuarioRol} vigentes del mismo rol" no es
 * expresable en el esquema: se valida en el {@code DomainService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario_rol")
public class UsuarioRol extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "fecha_inicio_vigencia", nullable = false)
    private ZonedDateTime fechaInicioVigencia;

    @Column(name = "fecha_fin_vigencia")
    private ZonedDateTime fechaFinVigencia;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_usuario_rol_usuario"))
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_usuario_rol_rol"))
    private Rol rol;

    //endregion

}
