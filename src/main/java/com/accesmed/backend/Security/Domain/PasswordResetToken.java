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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Token para activar una cuenta nueva o recuperar una contraseña olvidada — mismo
 * mecanismo para ambos casos. Vive hasheado, de un solo uso.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends Auditable {

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

    //endregion

    //region ========== Relaciones ==========

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_password_reset_token_usuario"))
    @NotNull
    private Usuario usuario;

    //endregion

}
