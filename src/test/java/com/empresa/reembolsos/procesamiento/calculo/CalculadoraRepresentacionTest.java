package com.empresa.reembolsos.procesamiento.calculo;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import org.junit.jupiter.api.Test;

class CalculadoraRepresentacionTest {

    private final CalculadoraRepresentacion calculadora = new CalculadoraRepresentacion();

    @Test
    void declaraLaCategoriaRepresentacion() {
        assertThat(calculadora.getCategoria()).isEqualTo(CategoriaGasto.REPRESENTACION);
    }

    @Test
    void montoBajoElTopeMensual_seApruebaCompleto() {
        assertThat(calculadora.calcular(solicitud(CategoriaGasto.REPRESENTACION, "1200000")))
            .isEqualByComparingTo("1200000");
    }

    @Test
    void montoSobreElTopeMensual_seRecortaAlTope() {
        assertThat(calculadora.calcular(solicitud(CategoriaGasto.REPRESENTACION, "2500000")))
            .isEqualByComparingTo("2000000");
    }
}
