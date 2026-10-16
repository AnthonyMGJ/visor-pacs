package pe.upn.visorpacs.dicomweb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PatientStudyAccessTest {

    private static final String UNO =
            "[{\"00100020\":{\"vr\":\"LO\",\"Value\":[\"PRUEBA-0001\"]}}]";

    @Test
    void aceptaSoloSiElPatientIdDevueltoCoincideExacto() {
        assertTrue(PatientStudyAccess.tieneResultados(UNO, "PRUEBA-0001"));
        assertFalse(PatientStudyAccess.tieneResultados(UNO, "prueba-0001"));
        assertFalse(PatientStudyAccess.tieneResultados(UNO, "PRUEBA-0002"));
    }

    @Test
    void sinResultadosOCuerpoInvalidoFallaCerrado() {
        assertFalse(PatientStudyAccess.tieneResultados("[]", "PRUEBA-0001"));
        assertFalse(PatientStudyAccess.tieneResultados(null, "PRUEBA-0001"));
        assertFalse(PatientStudyAccess.tieneResultados("no es json", "PRUEBA-0001"));
    }
}
