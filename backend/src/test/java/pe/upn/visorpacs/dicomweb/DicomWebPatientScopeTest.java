package pe.upn.visorpacs.dicomweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DicomWebPatientScopeTest {

    @Test
    void reemplazaCualquierPatientIdDelClientePorElDelToken() {
        String q = DicomWebPatientScope.consultaConPacienteForzado(
                "limit=10&PatientID=OTRO&00100020=X&patientid=Y", "PRUEBA-0001");
        assertEquals("limit=10&PatientID=PRUEBA-0001", q);
    }

    @Test
    void sinConsultaSoloAgregaElPaciente() {
        assertEquals("PatientID=PRUEBA-0001", DicomWebPatientScope.consultaConPacienteForzado(null, "PRUEBA-0001"));
    }

    @Test
    void parametroMalCodificadoSeRechaza() {
        assertThrows(InvalidDicomWebPathException.class,
                () -> DicomWebPatientScope.consultaConPacienteForzado("%ZZ=1", "P"));
    }

    @Test
    void identificaListadoYEstudioDeLaRuta() {
        assertTrue(DicomWebPatientScope.esListadoDeEstudios("/studies"));
        assertFalse(DicomWebPatientScope.esListadoDeEstudios("/series"));
        assertEquals(Optional.of("1.2.3"), DicomWebPatientScope.estudioDeLaRuta("/studies/1.2.3/series/4"));
        assertTrue(DicomWebPatientScope.estudioDeLaRuta("/series/1.2").isEmpty());
        assertTrue(DicomWebPatientScope.estudioDeLaRuta("/instances/1.2").isEmpty());
    }
}
