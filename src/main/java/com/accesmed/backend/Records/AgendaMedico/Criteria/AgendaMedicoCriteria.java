package com.accesmed.backend.Records.AgendaMedico.Criteria;

import com.accesmed.backend.Services.QueryServices.Filtering.StringFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.UUIDFilter;
import com.accesmed.backend.Services.QueryServices.Filtering.ZonedDateTimeFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springdoc.core.annotations.ParameterObject;

import java.time.ZonedDateTime;

/**
 * Filtros disponibles para el listado dinámico de {@code AgendaMedico} (ver
 * {@code Docs/ARQUITECTURA.md §7 Filtrado dinámico}). Sin guarda fija de vigencia: el
 * selector del front tiene que poder mostrar también los períodos vencidos y los
 * programados a futuro, igual que {@code PrestacionCriteria} no filtra por estado
 * implícitamente.
 */
@Getter
@Setter
@ToString
@ParameterObject
public class AgendaMedicoCriteria {

    private UUIDFilter id;
    private UUIDFilter medicoId;
    private UUIDFilter especialidadId;
    private ZonedDateTimeFilter fechaHoraInicioVigencia;
    private ZonedDateTimeFilter fechaHoraFinVigencia;

    /**
     * Filtro derivado: si viene con valor, exige {@code inicio <= vigenteAl < fin}. No hay
     * valor por defecto aplicado por el servidor (a diferencia de otros derivados como
     * {@code MedicoCriteria.agendaVigenteAl}): el front decide explícitamente si quiere
     * "vigente a tal fecha" enviando el parámetro, típicamente con el valor de "ahora".
     */
    private ZonedDateTime vigenteAl;

    /**
     * Filtro de auditoría — solo se honra si quien consulta tiene {@code AUDITORIA_CONSULTAR}
     * (ver {@code AgendaMedicoQueryService}); se ignora en caso contrario.
     */
    private StringFilter createdBy;

}
