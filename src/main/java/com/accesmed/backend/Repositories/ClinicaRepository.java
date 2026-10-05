package com.accesmed.backend.Repositories;

import com.accesmed.backend.Domain.Clinica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositorio de acceso a datos para la entidad {@code Clinica}. No filtra baja lógica:
 * {@code Clinica} no es bajable, es una instancia única de configuración garantizada por
 * la constraint de esquema {@code singleton_guard}.
 */
@Repository
public interface ClinicaRepository extends JpaRepository<Clinica, UUID> {
}
