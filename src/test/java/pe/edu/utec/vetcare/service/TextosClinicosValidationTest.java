package pe.edu.utec.vetcare.service;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pe.edu.utec.vetcare.dto.request.CreateAtencionRequest;
import pe.edu.utec.vetcare.dto.request.UpdateAtencionRequest;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.*;

class TextosClinicosValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void cerrar() { FACTORY.close(); }

    static Stream<Arguments> limites() {
        return Stream.of(CreateAtencionRequest.class, UpdateAtencionRequest.class)
                .flatMap(tipo -> Stream.of("sintoma", "diagnostico", "tratamiento", "observacion")
                        .flatMap(campo -> Stream.of(255, 256, 500, 501)
                                .map(longitud -> Arguments.of(tipo, campo, longitud))));
    }

    @ParameterizedTest
    @MethodSource("limites")
    void aceptaHasta500YRechaza501(Class<?> tipo, String campo, int longitud) throws Exception {
        Object request = tipo.getDeclaredConstructor().newInstance();
        String setter = "set" + Character.toUpperCase(campo.charAt(0)) + campo.substring(1);
        tipo.getMethod(setter, String.class).invoke(request, "á".repeat(longitud));
        var errores = FACTORY.getValidator().validateProperty(request, campo);
        assertEquals(longitud > 500, !errores.isEmpty(), tipo.getSimpleName() + "." + campo);
    }
}
