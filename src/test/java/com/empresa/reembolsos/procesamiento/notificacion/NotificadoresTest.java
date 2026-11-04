package com.empresa.reembolsos.procesamiento.notificacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class NotificadoresTest {

    private static SolicitudReembolso solicitudEn(EstadoSolicitud estado) {
        var solicitud = solicitud(CategoriaGasto.REPRESENTACION, "500000");
        solicitud.setId(7L);
        solicitud.setMontoAprobado(new BigDecimal("500000"));
        solicitud.setEstado(estado);
        return solicitud;
    }

    @Test
    void empleado_recibeElEstadoAnteriorYElNuevo(CapturedOutput salida) {
        new NotificadorEmpleado().onCambioEstado(solicitudEn(EstadoSolicitud.RECHAZADA), EstadoSolicitud.PENDIENTE);

        assertThat(salida.getOut()).contains("Notificando a E-001: su solicitud paso de PENDIENTE a RECHAZADA");
    }

    @Test
    void contabilidad_actuaCuandoLaSolicitudQuedaAprobada(CapturedOutput salida) {
        new NotificadorContabilidad().onCambioEstado(solicitudEn(EstadoSolicitud.APROBADA), EstadoSolicitud.PENDIENTE);

        assertThat(salida.getOut()).contains("Contabilidad: programar pago de 500000 para E-001");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class, names = "APROBADA", mode = EnumSource.Mode.EXCLUDE)
    void contabilidad_ignoraLosDemasEstados(EstadoSolicitud estado, CapturedOutput salida) {
        new NotificadorContabilidad().onCambioEstado(solicitudEn(estado), EstadoSolicitud.PENDIENTE);

        assertThat(salida.getOut()).doesNotContain("Contabilidad");
    }

    @ParameterizedTest
    @EnumSource(EstadoSolicitud.class)
    void auditoria_registraTodoCambioDeEstado(EstadoSolicitud estado, CapturedOutput salida) {
        new NotificadorAuditoria().onCambioEstado(solicitudEn(estado), EstadoSolicitud.PENDIENTE);

        assertThat(salida.getAll()).contains("Auditoria: solicitud 7 cambio de PENDIENTE a " + estado);
    }
}
