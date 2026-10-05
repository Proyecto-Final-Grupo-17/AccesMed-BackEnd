package com.accesmed.backend.Services.DomainServices;

import com.accesmed.backend.Domain.EstadoTurno;
import com.accesmed.backend.Domain.Turno;
import com.accesmed.backend.Repositories.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link TurnoDomainService}:
 * verifica la delegación correcta en el repositorio para búsquedas por estado vigente.
 */
@ExtendWith(MockitoExtension.class)
class TurnoDomainServiceTest {

    @Mock
    private TurnoRepository turnoRepository;

    @InjectMocks
    private TurnoDomainService turnoDomainService;

    private Turno turno1;
    private Turno turno2;

    @BeforeEach
    void setUp() {
        turno1 = new Turno();
        turno1.setId(UUID.randomUUID());

        turno2 = new Turno();
        turno2.setId(UUID.randomUUID());
    }

    @Test
    void findTurnosByEstadoVigente_delegaAlRepositorio_yDevuelveLaListaRetornada() {
        List<Turno> turnosEsperado = List.of(turno1, turno2);
        when(turnoRepository.findByEstadoVigente(EstadoTurno.PENDIENTE))
                .thenReturn(turnosEsperado);

        List<Turno> turnosObtenido = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.PENDIENTE);

        assertEquals(2, turnosObtenido.size());
        assertEquals(turno1.getId(), turnosObtenido.get(0).getId());
        assertEquals(turno2.getId(), turnosObtenido.get(1).getId());
        verify(turnoRepository).findByEstadoVigente(EstadoTurno.PENDIENTE);
    }

    @Test
    void findTurnosByEstadoVigente_repositorioRetornaListaVacia_devuelveListaVacia() {
        when(turnoRepository.findByEstadoVigente(EstadoTurno.CONFIRMADO))
                .thenReturn(List.of());

        List<Turno> turnosObtenido = turnoDomainService.findTurnosByEstadoVigente(EstadoTurno.CONFIRMADO);

        assertTrue(turnosObtenido.isEmpty());
        verify(turnoRepository).findByEstadoVigente(EstadoTurno.CONFIRMADO);
    }

}
