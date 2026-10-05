package com.accesmed.backend.Records.Medico.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.BooleanFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.InstantFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

import java.time.LocalDate;

/**
 * Filtros disponibles para el listado dinámico de {@code Medico} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Excepción a
 * "{@code <Accion><Entidad>Request}": no es un endpoint de escritura sino un objeto de
 * filtro para un {@code GET}, así que es una clase mutable (no {@code record}) sin Bean
 * Validation — todo campo es opcional. No incluye {@code deletedAt}: el
 * {@code QueryService} excluye las bajas lógicas siempre, sin exponerlo como filtro.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class MedicoCriteria {

    private UUIDFilter id;
    private StringFilter matricula;
    private StringFilter dni;
    private StringFilter nombre;
    private StringFilter apellido;
    private StringFilter email;
    private UUIDFilter especialidadId;
    private InstantFilter createdDate;
    private InstantFilter lastModifiedDate;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code MedicoQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

    /**
     * Filtro derivado (§5 AGEN): resuelve con un {@code EXISTS}/{@code NOT EXISTS} contra
     * {@code agenda_medico} si el médico tiene una agenda vigente en {@link #agendaVigenteAl}.
     * Reemplaza al endpoint "médicos sin agenda vigente" (no se construye aparte).
     */
    private BooleanFilter tieneAgendaVigente;

    /**
     * Fecha de referencia para {@link #tieneAgendaVigente}. Si no viene con valor y
     * {@code tieneAgendaVigente} sí, el {@code QueryService} usa la fecha de hoy (zona horaria de la clínica) por defecto (a
     * diferencia de {@code AgendaMedicoCriteria.vigenteAl}, que no tiene default: acá el
     * default habilita además el caso "por vencer" combinando
     * {@code tieneAgendaVigente.equals=false&agendaVigenteAl=<hoy+30>}).
     */
    private LocalDate agendaVigenteAl;

}
