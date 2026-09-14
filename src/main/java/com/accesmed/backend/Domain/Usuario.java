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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Credencial de acceso. Sin datos personales: apunta a un {@link Medico} o a un
 * {@link Admin}, nunca a los dos ni a ninguno (invariante reforzado con un {@code CHECK}
 * en el esquema, ver changelog).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(name = "mail", nullable = false, length = 150)
    private String mail;

    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    //endregion

    //region ========== Relaciones ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_usuario_medico"))
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_usuario_admin"))
    private Admin admin;

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
