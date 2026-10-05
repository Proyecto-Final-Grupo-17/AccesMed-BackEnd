package com.accesmed.backend.Security.Domain;

import com.accesmed.backend.Domain.Auditable;
import com.accesmed.backend.Domain.Usuario;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Token para confirmar un cambio de mail (propio o disparado por el SuperAdmin) — el
 * mail solo se aplica sobre {@link Usuario} cuando se confirma contra el mail nuevo.
 * Vive hasheado, de un solo uso.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cambio_mail_token")
public class CambioMailToken extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(name = "mail_nuevo", nullable = false, length = 150)
    private String mailNuevo;

    //endregion

    //region ========== Relaciones ==========

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_cambio_mail_token_usuario"))
    @NotNull
    private Usuario usuario;

    //endregion

}
