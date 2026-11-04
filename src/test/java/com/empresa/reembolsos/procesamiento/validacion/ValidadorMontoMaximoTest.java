package com.empresa.reembolsos.procesamiento.validacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidadorMontoMaximoTest {

    private final ValidadorMontoMaximo validador = new ValidadorMontoMaximo();

    @ParameterizedTest
    @CsvSource({"VIAJE, 150000", "REPRESENTACION, 2000000", "CAPACITACION, 3000000"})
    void montoIgualAlTope_esValido(CategoriaGasto categoria, String tope) {
        var resultado = validador.validar(solicitud(categoria, tope), List.of());

        assertThat(resultado.valida()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"VIAJE, 150000.01", "REPRESENTACION, 2000001", "CAPACITACION, 3000001"})
    void montoSobreElTope_seRechazaNombrandoLaCategoria(CategoriaGasto categoria, String monto) {
        var resultado = validador.validar(solicitud(categoria, monto), List.of());

        assertThat(resultado.valida()).isFalse();
        assertThat(resultado.motivoRechazo()).isEqualTo("Monto excede el tope de la categoria " + categoria);
    }
}
