package com.empresa.reembolsos.procesamiento.calculo;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import org.junit.jupiter.api.Test;

class CalculadoraViajeTest {

    private final CalculadoraViaje calculadora = new CalculadoraViaje();

    @Test
    void declaraLaCategoriaViaje() {
        assertThat(calculadora.getCategoria()).isEqualTo(CategoriaGasto.VIAJE);
    }

    @Test
    void montoBajoElTopeDiarioPorDias_seApruebaCompleto() {
        var viaje = solicitud(CategoriaGasto.VIAJE, "250000");
        viaje.setDiasViaje(2); // tope 300000

        assertThat(calculadora.calcular(viaje)).isEqualByComparingTo("250000");
    }

    @Test
    void montoSobreElTopeDiarioPorDias_seRecortaAlTope() {
        var viaje = solicitud(CategoriaGasto.VIAJE, "500000");
        viaje.setDiasViaje(3); // tope 450000

        assertThat(calculadora.calcular(viaje)).isEqualByComparingTo("450000");
    }

    @Test
    void viajeDeCeroDias_apruebaCero() {
        var viaje = solicitud(CategoriaGasto.VIAJE, "100000");
        viaje.setDiasViaje(0);

        assertThat(calculadora.calcular(viaje)).isEqualByComparingTo("0");
    }
}
