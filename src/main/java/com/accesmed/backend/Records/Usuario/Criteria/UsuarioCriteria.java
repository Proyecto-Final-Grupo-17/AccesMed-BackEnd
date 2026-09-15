package com.accesmed.backend.Records.Usuario.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code Usuario} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class UsuarioCriteria {

    private UUIDFilter id;
    private StringFilter mail;
    private UUIDFilter medicoId;
    private UUIDFilter adminId;

    /**
     * Estado de baja lógica a incluir. {@code null} equivale a {@code ACTIVO} (mismo
     * comportamiento por defecto que el resto de los listados del sistema).
     */
    private EstadoUsuarioFiltro estado;

}
