package com.empresa.reembolsos.procesamiento.validacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidadorDocumentosSoporteTest {

    private final ValidadorDocumentosSoporte validador = new ValidadorDocumentosSoporte();

    @Test
    void conDocumentosSoporte_esValida() {
        var resultado = validador.validar(solicitud(CategoriaGasto.VIAJE, "100000"), List.of());

        assertThat(resultado.valida()).isTrue();
        assertThat(resultado.motivoRechazo()).isNull();
    }

    @Test
    void sinDocumentosSoporte_seRechazaConMotivo() {
        var sinDocumentos = solicitud(CategoriaGasto.VIAJE, "100000");
        sinDocumentos.setDocumentosSoporte(false);

        var resultado = validador.validar(sinDocumentos, List.of());

        assertThat(resultado.valida()).isFalse();
        assertThat(resultado.motivoRechazo()).isEqualTo("Faltan documentos soporte");
    }
}
