package com.accesmed.backend.Domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Conjunto de {@link Permiso}. Los roles de sistema ({@code esSistema} verdadero) no se
 * editan ni se dan de baja (regla validada en el {@code DomainService}).
 *
 * <p>La asociación N:N con {@link Permiso} se materializa con {@code @ElementCollection},
 * porque {@code Permiso} es un enum y no una entidad con vida propia: la tabla
 * {@code rol_permiso} es una colección propiedad del rol.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rol")
public class Rol extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @NotNull
    @Column(name = "es_sistema", nullable = false)
    private Boolean esSistema = false;

    //endregion

    //region ========== Relaciones ==========

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_rol_permiso_rol"))
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permiso", nullable = false, length = 50)
    private Set<Permiso> permisos = new HashSet<>();

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
