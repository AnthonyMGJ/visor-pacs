package pe.upn.visorpacs.dicomweb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DicomWebPathValidatorTest {

    @Test
    void permiteRutasDeLecturaDicomWeb() {
        assertTrue(DicomWebPathValidator.esPermitida("/studies"));
        assertTrue(DicomWebPathValidator.esPermitida("/studies/1.2.840.113619/metadata"));
        assertTrue(DicomWebPathValidator.esPermitida(
                "/studies/1.2.3/series/4.5/instances/6.7/frames/1"));
        assertTrue(DicomWebPathValidator.esPermitida("/series"));
    }

    @Test
    void rechazaRutasFueraDeDicomWeb() {
        assertFalse(DicomWebPathValidator.esPermitida(""));
        assertFalse(DicomWebPathValidator.esPermitida("/"));
        assertFalse(DicomWebPathValidator.esPermitida("/patients"));
        assertFalse(DicomWebPathValidator.esPermitida("/tools/reset"));
        assertFalse(DicomWebPathValidator.esPermitida(null));
    }

    @Test
    void rechazaIntentosDeSalirDeLaRuta() {
        assertFalse(DicomWebPathValidator.esPermitida("/studies/../system"));
        assertFalse(DicomWebPathValidator.esPermitida("/studies/./x"));
        assertFalse(DicomWebPathValidator.esPermitida("/studies//x"));
        assertFalse(DicomWebPathValidator.esPermitida("/studies/%2e%2e/x"));
    }
}
