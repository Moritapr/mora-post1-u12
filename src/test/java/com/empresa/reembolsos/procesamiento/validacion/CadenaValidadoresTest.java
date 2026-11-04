package com.empresa.reembolsos.procesamiento.validacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.util.List;
import org.junit.jupiter.api.Test;

class CadenaValidadoresTest {

    private final CadenaValidadores cadena = new CadenaValidadores(
        new ValidadorDocumentosSoporte(), new ValidadorMontoMaximo(), new ValidadorDuplicados());

    @Test
    void solicitudQueCumpleLasTresVerificaciones_esValida() {
        var resultado = cadena.validar(solicitud(CategoriaGasto.REPRESENTACION, "500000"), List.of());

        assertThat(resultado.valida()).isTrue();
    }

    @Test
    void conVariosIncumplimientos_devuelveElMotivoDelPrimerEslabon() {
        var pendiente = solicitud(CategoriaGasto.VIAJE, "1000");
        var incumpleTodo = solicitud(CategoriaGasto.VIAJE, "999999");
        incumpleTodo.setDocumentosSoporte(false);

        var resultado = cadena.validar(incumpleTodo, List.of(pendiente));

        assertThat(resultado.motivoRechazo()).isEqualTo("Faltan documentos soporte");
    }

    @Test
    void conDocumentosPeroMontoExcedidoYDuplicado_cortaEnElMonto() {
        var pendiente = solicitud(CategoriaGasto.VIAJE, "1000");

        var resultado = cadena.validar(solicitud(CategoriaGasto.VIAJE, "999999"), List.of(pendiente));

        assertThat(resultado.motivoRechazo()).isEqualTo("Monto excede el tope de la categoria VIAJE");
    }

    @Test
    void soloDuplicado_llegaAlUltimoEslabon() {
        var pendiente = solicitud(CategoriaGasto.VIAJE, "1000");

        var resultado = cadena.validar(solicitud(CategoriaGasto.VIAJE, "2000"), List.of(pendiente));

        assertThat(resultado.motivoRechazo())
            .isEqualTo("Ya existe una solicitud pendiente de la misma categoria en el periodo");
    }
}
