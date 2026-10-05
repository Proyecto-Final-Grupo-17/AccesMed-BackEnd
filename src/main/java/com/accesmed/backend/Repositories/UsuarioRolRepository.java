package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code UsuarioRol}.
 * Encapsula la consulta de asignaciones de rol vigentes, validadas por fecha de vigencia.
 */
@Repository
public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UUID> {

    /**
     * Busca las asignaciones de rol vigentes de un usuario (vigencia iniciada y no
     * finalizada, o sin fecha de fin).
     *
     * @param usuarioId {@code UUID} identificador del usuario
     * @param ahora {@code ZonedDateTime} instante de referencia para evaluar la vigencia
     * @return {@code List<UsuarioRol>} las asignaciones vigentes
     */
    @Query("""
        SELECT ur FROM UsuarioRol ur
        WHERE ur.usuario.id = :usuarioId
          AND ur.fechaInicioVigencia <= :ahora
          AND (ur.fechaFinVigencia IS NULL OR ur.fechaFinVigencia > :ahora)
        """)
    List<UsuarioRol> findVigentesByUsuarioId(@Param("usuarioId") UUID usuarioId, @Param("ahora") ZonedDateTime ahora);

}
