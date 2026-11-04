package com.empresa.reembolsos.procesamiento.validacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidadorDuplicadosTest {

    private final ValidadorDuplicados validador = new ValidadorDuplicados();
    private final LocalDate marzo2026 = LocalDate.of(2026, 3, 15);

    @Test
    void sinPendientes_esValida() {
        var resultado = validador.validar(solicitud(CategoriaGasto.VIAJE, "1000", marzo2026), List.of());

        assertThat(resultado.valida()).isTrue();
    }

    @Test
    void pendienteDeLaMismaCategoriaYMes_seRechaza() {
        var pendiente = solicitud(CategoriaGasto.VIAJE, "5000", LocalDate.of(2026, 3, 2));

        var resultado = validador.validar(solicitud(CategoriaGasto.VIAJE, "1000", marzo2026), List.of(pendiente));

        assertThat(resultado.valida()).isFalse();
        assertThat(resultado.motivoRechazo())
            .isEqualTo("Ya existe una solicitud pendiente de la misma categoria en el periodo");
    }

    @Test
    void pendienteDeOtraCategoriaEnElMismoMes_esValida() {
        var pendiente = solicitud(CategoriaGasto.CAPACITACION, "5000", marzo2026);

        var resultado = validador.validar(solicitud(CategoriaGasto.VIAJE, "1000", marzo2026), List.of(pendiente));

        assertThat(resultado.valida()).isTrue();
    }

    @Test
    void pendienteDelMismoMesDeOtroAnio_noEsDuplicado() {
        // Desviacion del enunciado: getMonth() consideraba duplicado marzo 2025 vs marzo 2026
        var pendiente = solicitud(CategoriaGasto.VIAJE, "5000", LocalDate.of(2025, 3, 15));

        var resultado = validador.validar(solicitud(CategoriaGasto.VIAJE, "1000", marzo2026), List.of(pendiente));

        assertThat(resultado.valida()).isTrue();
    }
}
