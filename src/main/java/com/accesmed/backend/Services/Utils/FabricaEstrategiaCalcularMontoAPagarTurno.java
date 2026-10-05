package com.accesmed.backend.Services.Utils;

import com.accesmed.backend.Domain.ModalidadCobertura;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fábrica que elige la estrategia de cálculo del monto a pagar de un turno según
 * la modalidad de cobertura. Agrega todas las implementaciones de
 * {@link EstrategiaCalcularMontoAPagarTurno} inyectadas por Spring en un {@code Map}
 * indexado por modalidad, y las expone a través de un método getter.
 */
@Component
@RequiredArgsConstructor
public class FabricaEstrategiaCalcularMontoAPagarTurno {

    //region ========== Dependencias o inyecciones ==========

    private final List<EstrategiaCalcularMontoAPagarTurno> estrategias;

    //endregion

    //region ========== Atributos ==========

    private Map<ModalidadCobertura, EstrategiaCalcularMontoAPagarTurno> estrategiasPorModalidad;

    //endregion

    //region ========== Métodos ==========

    /**
     * Inicializa el mapa de estrategias indexando las implementaciones inyectadas por
     * su modalidad soportada.
     */
    @PostConstruct
    public void inicializarEstrategias() {
        estrategiasPorModalidad = new HashMap<>();
        for (EstrategiaCalcularMontoAPagarTurno estrategia : estrategias) {
            estrategiasPorModalidad.put(estrategia.getModalidadSoportada(), estrategia);
        }
    }

    /**
     * Retorna la estrategia de cálculo correspondiente a la modalidad de cobertura
     * indicada.
     *
     * @param modalidad {@code ModalidadCobertura} la modalidad para la que se busca la estrategia
     * @return {@code EstrategiaCalcularMontoAPagarTurno} la estrategia soportada por esa modalidad
     * @throws NullPointerException si no existe una estrategia registrada para esa modalidad
     */
    public EstrategiaCalcularMontoAPagarTurno obtenerEstrategia(ModalidadCobertura modalidad) {
        return Objects.requireNonNull(
                estrategiasPorModalidad.get(modalidad),
                "No existe estrategia registrada para la modalidad " + modalidad
        );
    }

    //endregion

}
