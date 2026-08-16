package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.Clinica;
import com.accesmed.backend.Repositories.ClinicaRepository;
import com.accesmed.backend.Services.Errors.RecursoNoEncontradoException;
import com.accesmed.backend.Services.Errors.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * Lógica de dominio y persistencia para la entidad {@code Clinica}. Al ser una instancia
 * única (sin alta ni baja), no expone búsqueda por id: siempre resuelve la única fila
 * existente, garantizada por la constraint de esquema {@code singleton_guard}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicaDomainService {

    //region ========== Dependencias o inyecciones ==========

    private final ClinicaRepository clinicaRepository;

    //endregion

    //region ========== Métodos ==========

    /**
     * Guarda la clínica en la base de datos.
     *
     * @param clinica {@code Clinica} entidad a persistir
     * @return {@code Clinica} la clínica guardada
     */
    public Clinica saveClinica(Clinica clinica) {

        log.debug("Guardando clínica: nombre={}", clinica.getNombre());

        return clinicaRepository.save(clinica);

    }

    /**
     * Busca la única fila de la clínica. No recibe id: hay una única instancia
     * garantizada por el esquema.
     *
     * @return {@code Clinica} la instancia única de la clínica
     * @throws RecursoNoEncontradoException {@code RecursoNoEncontradoException} si la
     *         fila de configuración no existe (no debería ocurrir: la migración la crea)
     */
    public Clinica findClinica() {

        log.debug("Buscando la instancia única de clínica");

        List<Clinica> clinicas = clinicaRepository.findAll();

        if (clinicas.isEmpty()) {
            log.warn("No se encontró la fila de configuración de la clínica");
            throw new RecursoNoEncontradoException(getClass(), "CLINICA_NO_ENCONTRADA",
                    "No existe la fila de configuración de la clínica.");
        }

        return clinicas.get(0);

    }

    /**
     * Valida que el horario de inicio de atención sea anterior al horario de fin.
     *
     * @param horarioInicioAtencion {@code LocalTime} horario de inicio efectivo
     * @param horarioFinAtencion {@code LocalTime} horario de fin efectivo
     * @throws ReglaNegocioException {@code ReglaNegocioException} si el horario de inicio
     *         no es anterior al de fin
     */
    public void validateHorarioAtencion(LocalTime horarioInicioAtencion, LocalTime horarioFinAtencion) {

        if (!horarioInicioAtencion.isBefore(horarioFinAtencion)) {
            log.warn("No se pudo actualizar la clínica: horario de inicio {} no es anterior al de fin {}",
                    horarioInicioAtencion, horarioFinAtencion);
            throw new ReglaNegocioException(getClass(), "CLINICA_HORARIO_ATENCION_INVALIDO",
                    "El horario de inicio de atención debe ser anterior al horario de fin.");
        }

    }

    //endregion

}
