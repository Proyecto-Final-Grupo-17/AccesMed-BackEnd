package com.accesmed.backend.Domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Instancia única con los datos y parámetros de configuración de la clínica. No tiene
 * alta ni baja: solo modificación. La unicidad de la fila (una sola instancia) se
 * garantiza a nivel de esquema con la columna de guardia {@code singleton_guard}, no
 * mapeada en esta entidad.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "clinica")
public class Clinica extends Auditable {

    //region ========== Atributos ==========

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "descripcion", nullable = false, length = 1000)
    private String descripcion;

    @NotBlank
    @Size(max = 250)
    @Column(name = "ubicacion", nullable = false, length = 250)
    private String ubicacion;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @NotBlank
    @Size(max = 30)
    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;

    @NotNull
    @Column(name = "horario_inicio_atencion", nullable = false)
    private LocalTime horarioInicioAtencion;

    @NotNull
    @Column(name = "horario_fin_atencion", nullable = false)
    private LocalTime horarioFinAtencion;

    @NotNull
    @Min(1)
    @Column(name = "dias_maximos_anticipacion_reserva", nullable = false)
    private Integer diasMaximosAnticipacionReserva;

    //endregion

}
