package com.accesmed.backend.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.util.UUID;

/**
 * Servicio que la clínica ofrece. Concentra toda la configuración temporal del ciclo de
 * vida del turno: siete tolerancias/duraciones expresadas como {@link Duration} y
 * persistidas directamente como {@code interval} de PostgreSQL. Hibernate maneja
 * automáticamente la conversión entre {@code java.time.Duration} e {@code interval}.
 *
 * <p>{@code codigo} es inmutable después del alta (genera el {@code Turno.codigo}). La
 * inmutabilidad la garantiza {@code updatable = false}: Hibernate nunca incluye la columna
 * en un {@code UPDATE}, así que el valor solo puede fijarse al mapear el alta. La
 * {@code especialidad} también es inmutable tras el alta.</p>
 *
 * <p>El ciclo de vida es por estados ({@link EstadoPrestacion}), no baja lógica:
 * {@code No Publicada ⇄ Publicada → Deshabilitada}. {@code estadoActual} es la
 * materialización del tramo vigente de {@link HistoricoEstadoPrestacion}, mantenida por
 * el {@code DomainService} en la misma transacción que abre el tramo. Deshabilitar es
 * terminal e irreversible: a partir de ahí no hay transición de vuelta, y el
 * {@code codigo}/{@code nombre} quedan libres para reutilizarse porque la unicidad es
 * entre no-deshabilitadas.</p>
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
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "duracion_minima", nullable = false, columnDefinition = "interval")
    private Duration duracionMinima;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "duracion_maxima", nullable = false, columnDefinition = "interval")
    private Duration duracionMaxima;

    //Tiempos de tolerancia: ORDENADOS DE MAYOR A MENOR

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_solicitud", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaSolicitud;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_validacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaValidacion;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_reprogramacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaReprogramacion;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_confirmacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaConfirmacion;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_cancelacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaCancelacion;

    //========================================================================================================

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_tolerancia_anuncio", nullable = false, columnDefinition = "interval")
    private Duration tiempoToleranciaAnuncio; //No depende de las demás

    @NotNull
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "tiempo_recordatorio_confirmacion", nullable = false, columnDefinition = "interval")
    private Duration tiempoRecordatorioConfirmacion; //No puede superar a tiempoToleranciaSolicitud

    /**
     * Estado actual del ciclo de vida del catálogo, mantenido por el
     * {@code DomainService} junto con el tramo de {@link HistoricoEstadoPrestacion}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actual", nullable = false, length = 20)
    private EstadoPrestacion estadoActual;

    //endregion

    //region ========== Relaciones ==========

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidad_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_prestacion_especialidad"))
    private Especialidad especialidad;

    //endregion

}
