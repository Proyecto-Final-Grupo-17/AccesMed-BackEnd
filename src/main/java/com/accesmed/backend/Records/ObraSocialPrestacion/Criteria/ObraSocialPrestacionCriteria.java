package com.accesmed.backend.Records.ObraSocialPrestacion.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.ModalidadCoberturaFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

/**
 * Filtros disponibles para el listado dinámico de {@code ObraSocialPlanPrestacion} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional. {@code obraSocialId} filtra por transitividad
 * (vía {@code plan.obraSocial}).
 */
@Getter
@Setter
@ToString
@ParameterObject
public class ObraSocialPrestacionCriteria {

    private UUIDFilter id;
    private UUIDFilter planId;
    private UUIDFilter prestacionId;
    private UUIDFilter obraSocialId;
    private ModalidadCoberturaFilter modalidadCobertura;

}
