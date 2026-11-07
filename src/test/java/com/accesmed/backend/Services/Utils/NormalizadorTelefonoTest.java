package com.accesmed.backend.Services.Utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios de {@link NormalizadorTelefono}: la comparación de teléfonos se hace sobre
 * dígitos, sin importar el formato con que esté guardado el número.
 */
class NormalizadorTelefonoTest {

    @Test
    void normalizarDigitos_quitaMasEspaciosGuionesYParentesis() {
        assertEquals("5492617742682", NormalizadorTelefono.normalizarDigitos("+54 9 (261) 774-2682"));
    }

    @Test
    void normalizarDigitos_formatoE164YFormatoGuardadoDanLoMismo() {
        assertEquals(NormalizadorTelefono.normalizarDigitos("+5492617742682"),
                NormalizadorTelefono.normalizarDigitos("+54-9-261-7742682"));
    }

    @Test
    void normalizarDigitos_nulo_devuelveVacio() {
        assertEquals("", NormalizadorTelefono.normalizarDigitos(null));
    }

    @Test
    void normalizarE164_anteponeMasALosDigitos() {
        assertEquals("+5492617742682", NormalizadorTelefono.normalizarE164("54 9 261 774 2682"));
    }

    @Test
    void esValido_exigeEntreSieteYQuinceDigitos() {
        assertTrue(NormalizadorTelefono.esValido("+5492617742682"));
        assertFalse(NormalizadorTelefono.esValido("12345"));
        assertFalse(NormalizadorTelefono.esValido("1234567890123456"));
        assertFalse(NormalizadorTelefono.esValido(null));
        assertFalse(NormalizadorTelefono.esValido("   "));
    }

}
