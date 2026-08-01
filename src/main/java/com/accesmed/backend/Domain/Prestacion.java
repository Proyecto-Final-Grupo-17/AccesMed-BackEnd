package com.accesmed.backend.Domain;

import com.accesmed.backend.Domain.Converters.DurationIntervalConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Servicio que la clínica ofrece. Concentra toda la configuración temporal del ciclo de
 * vida del turno: siete tolerancias/duraciones expresadas como {@link Duration} y
 * persistidas como {@code interval} vía {@link DurationIntervalConverter}.
 *
 * <p>{@code codigo} es inmutable después del alta (genera el {@code Turno.codigo}). La
 * inmutabilidad la garantiza {@code updatable = false}: Hibernate nunca incluye la columna
 * en un {@code UPDATE}, así que el valor solo puede fijarse al mapear el alta.</p>
 *
 * <p>El ciclo de vida del catálogo es borrador → habilitada. Con {@code fechaHabilitacion}
 * nula la prestación está en borrador y se puede editar libremente (nombre, tolerancias e
 * indicaciones). Habilitarla es irreversible: a partir de ahí el nombre queda congelado y
 * sus {@link IndicacionPrestacion} dejan de ser editables, porque
 * {@code IndicacionPrestacionTurno} lee su texto por navegabilidad y editarlo reescribiría
 * retroactivamente lo que ve un paciente en un turno vivo. Solo las prestaciones habilitadas
 * se ofrecen para dar y pedir turnos.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "prestacion")
public class Prestacion extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "codigo", nullable = false, updatable = false, length = 20)
    private String codigo;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "duracion_minima", nullable = false, columnDefinition = "interval")
    private Duration duracionMinima;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "duracion_maxima", nullable = false, columnDefinition = "interval")
    private Duration duracionMaxima;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_solicitud", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaSolicitud;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_validacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaValidacion;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_reprogramacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaReprogramacion;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_confirmacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaConfirmacion;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_cancelacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaCancelacion;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_tolerancia_anuncio", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaAnuncio;

    @NotNull
    @Convert(converter = DurationIntervalConverter.class)
    @Column(name = "tiempo_recordatorio_confirmacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoRecordatorioConfirmacion;

    /**
     * Nula mientras la prestación está en borrador. La sella el {@code DomainService} al
     * habilitarla y nunca vuelve a nulo: la irreversibilidad es una regla de negocio, no
     * una restricción de la entidad.
     */
    @Column(name = "fecha_habilitacion")
    private ZonedDateTime fechaHabilitacion;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidad_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_prestacion_especialidad"))
    private Especialidad especialidad;

    //endregion

    //region ========== Baja ==========

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

    //endregion

}
