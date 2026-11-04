package com.empresa.reembolsos.procesamiento.calculo;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import org.junit.jupiter.api.Test;

class CalculadoraCapacitacionTest {

    private final CalculadoraCapacitacion calculadora = new CalculadoraCapacitacion();

    @Test
    void declaraLaCategoriaCapacitacion() {
        assertThat(calculadora.getCategoria()).isEqualTo(CategoriaGasto.CAPACITACION);
    }

    @Test
    void reembolsaElOchentaPorCiento() {
        assertThat(calculadora.calcular(solicitud(CategoriaGasto.CAPACITACION, "1000000")))
            .isEqualByComparingTo("800000");
    }

    @Test
    void ochentaPorCientoSobreElTopeAnual_seRecortaAlTope() {
        // 80% de 5000000 = 4000000 > tope anual 3000000
        assertThat(calculadora.calcular(solicitud(CategoriaGasto.CAPACITACION, "5000000")))
            .isEqualByComparingTo("3000000");
    }
}
