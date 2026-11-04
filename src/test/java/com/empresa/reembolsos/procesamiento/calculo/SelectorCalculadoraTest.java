package com.empresa.reembolsos.procesamiento.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectorCalculadoraTest {

    @Test
    void devuelveLaCalculadoraDeCadaCategoria() {
        var selector = new SelectorCalculadora(List.of(
            new CalculadoraViaje(), new CalculadoraRepresentacion(), new CalculadoraCapacitacion()));

        assertThat(selector.obtener(CategoriaGasto.VIAJE)).isInstanceOf(CalculadoraViaje.class);
        assertThat(selector.obtener(CategoriaGasto.REPRESENTACION)).isInstanceOf(CalculadoraRepresentacion.class);
        assertThat(selector.obtener(CategoriaGasto.CAPACITACION)).isInstanceOf(CalculadoraCapacitacion.class);
    }

    @Test
    void categoriaSinCalculadora_fallaAlConstruirNombrandoLasFaltantes() {
        assertThatThrownBy(() -> new SelectorCalculadora(List.of(new CalculadoraViaje())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Categorias sin calculadora: [REPRESENTACION, CAPACITACION]");
    }

    @Test
    void categoriaNula_lanzaExcepcion() {
        var selector = new SelectorCalculadora(List.of(
            new CalculadoraViaje(), new CalculadoraRepresentacion(), new CalculadoraCapacitacion()));

        assertThatThrownBy(() -> selector.obtener(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Categoria no soportada: null");
    }

    @Test
    void dosCalculadorasParaLaMismaCategoria_fallanAlConstruir() {
        assertThatThrownBy(() -> new SelectorCalculadora(List.of(new CalculadoraViaje(), new CalculadoraViaje())))
            .isInstanceOf(IllegalStateException.class);
    }
}
