package com.accesmed.backend.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Superclase mapeada con los campos de auditoría comunes a toda entidad del dominio, y
 * base del criterio de soft delete ({@code fechaHoraBaja}).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    //region ========== Atributos ==========

    @CreatedDate
    @Column(name = "fecha_hora_alta", nullable = false, updatable = false)
    private LocalDateTime fechaHoraAlta;

    @LastModifiedDate
    @Column(name = "fecha_hora_modificacion")
    private LocalDateTime fechaHoraModificacion;

    @CreatedBy
    @Column(name = "usuario_alta", updatable = false)
    private String usuarioAlta;

    @LastModifiedBy
    @Column(name = "usuario_modificacion")
    private String usuarioModificacion;

    @Column(name = "fecha_hora_baja")
    private LocalDateTime fechaHoraBaja;

    //endregion

}
