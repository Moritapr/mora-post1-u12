package com.empresa.reembolsos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import com.empresa.reembolsos.infraestructura.RepositorioSolicitudesJpa;
import com.empresa.reembolsos.procesamiento.notificacion.NotificadorAuditoria;
import com.empresa.reembolsos.procesamiento.notificacion.NotificadorContabilidad;
import com.empresa.reembolsos.procesamiento.notificacion.NotificadorEmpleado;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Flujo completo por HTTP: crear (validar + calcular + guardar) y cambiar estado (notificar).
 * Los tres notificadores son espias: ejecutan su codigo real y ademas se verifica que se invocaron.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class FlujoSolicitudesIntegracionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RepositorioSolicitudesJpa repositorio;

    @MockitoSpyBean
    private NotificadorEmpleado notificadorEmpleado;
    @MockitoSpyBean
    private NotificadorContabilidad notificadorContabilidad;
    @MockitoSpyBean
    private NotificadorAuditoria notificadorAuditoria;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    private static String json(String empleado, String categoria, String monto, int dias, boolean documentos) {
        return """
            {"empleadoId": "%s", "categoria": "%s", "montoSolicitado": %s,
             "fechaGasto": "2026-03-15", "diasViaje": %d, "documentosSoporte": %b}
            """.formatted(empleado, categoria, monto, dias, documentos);
    }

    private SolicitudReembolso crear(String cuerpo) throws Exception {
        var respuesta = mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, SolicitudReembolso.class);
    }

    @ParameterizedTest(name = "{0}: solicitado {1}, {2} dias -> aprobado {3}")
    @CsvSource({
        "VIAJE,          120000,  2, 120000",
        "REPRESENTACION, 1500000, 0, 1500000",
        "CAPACITACION,   1000000, 0, 800000"})
    void post_creaLaSolicitudDeCadaCategoriaConSuMontoAprobado(
            CategoriaGasto categoria, String monto, int dias, String aprobado) throws Exception {
        var creada = crear(json("E-100", categoria.name(), monto, dias, true));

        assertThat(creada.getId()).isNotNull();
        assertThat(creada.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(creada.getMontoAprobado()).isEqualByComparingTo(aprobado);
        assertThat(repositorio.findById(creada.getId()).orElseThrow().getMontoAprobado())
            .isEqualByComparingTo(aprobado);
    }

    @Test
    void post_sinDocumentosSoporte_responde400ConElMotivoYNoGuarda() throws Exception {
        mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
                .content(json("E-200", "VIAJE", "100000", 1, false)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Faltan documentos soporte"));

        assertThat(repositorio.count()).isZero();
    }

    @Test
    void post_duplicadoDeUnaPendienteDelMismoPeriodo_responde400() throws Exception {
        crear(json("E-300", "REPRESENTACION", "100000", 0, true));

        mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
                .content(json("E-300", "REPRESENTACION", "50000", 0, true)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Ya existe una solicitud pendiente de la misma categoria en el periodo"));
    }

    @Test
    void post_conCategoriaInexistente_responde400() throws Exception {
        mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON)
                .content(json("E-400", "BIENESTAR", "1000", 0, true)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Cuerpo de la peticion invalido"));
    }

    @Test
    void put_aprobada_disparaLosTresObservadoresYContabilidadProgramaElPago(CapturedOutput salida) throws Exception {
        var creada = crear(json("E-500", "CAPACITACION", "1000000", 0, true));

        mockMvc.perform(put("/api/solicitudes/{id}/estado", creada.getId())
                .contentType(MediaType.APPLICATION_JSON).content("\"APROBADA\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("APROBADA"));

        verify(notificadorEmpleado, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        verify(notificadorContabilidad, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        verify(notificadorAuditoria, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        assertThat(salida.getAll())
            .contains("Notificando a E-500: su solicitud paso de PENDIENTE a APROBADA")
            .contains("Contabilidad: programar pago de 800000")
            .contains("Auditoria: solicitud " + creada.getId() + " cambio de PENDIENTE a APROBADA");
    }

    @Test
    void put_rechazada_disparaLosTresObservadoresPeroContabilidadNoActua(CapturedOutput salida) throws Exception {
        var creada = crear(json("E-600", "VIAJE", "90000", 1, true));

        mockMvc.perform(put("/api/solicitudes/{id}/estado", creada.getId())
                .contentType(MediaType.APPLICATION_JSON).content("\"RECHAZADA\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        verify(notificadorEmpleado, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        verify(notificadorContabilidad, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        verify(notificadorAuditoria, times(1)).onCambioEstado(any(), eq(EstadoSolicitud.PENDIENTE));
        assertThat(salida.getAll())
            .contains("Notificando a E-600: su solicitud paso de PENDIENTE a RECHAZADA")
            .contains("Auditoria: solicitud " + creada.getId() + " cambio de PENDIENTE a RECHAZADA")
            .doesNotContain("Contabilidad:");
    }

    @Test
    void put_deSolicitudInexistente_responde404() throws Exception {
        mockMvc.perform(put("/api/solicitudes/{id}/estado", 9999)
                .contentType(MediaType.APPLICATION_JSON).content("\"APROBADA\""))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Solicitud no encontrada: 9999"));
    }
}
