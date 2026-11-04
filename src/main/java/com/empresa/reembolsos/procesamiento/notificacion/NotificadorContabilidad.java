package com.empresa.reembolsos.procesamiento.notificacion;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Avisa a Contabilidad para programar el pago; solo reacciona cuando la solicitud queda APROBADA.
 *
 * <p>Va al final ({@code @Order(3)}): es el observador que integrara la API externa de pagos de
 * Contabilidad, el mas propenso a fallar, y un fallo suyo no debe impedir que los demas reaccionen.</p>
 */
@Component
@Order(3)
public class NotificadorContabilidad implements ObservadorCambioEstado {
    @Override
    public void onCambioEstado(SolicitudReembolso solicitud, EstadoSolicitud estadoAnterior) {
        if (solicitud.getEstado() == EstadoSolicitud.APROBADA) {
            System.out.println("Contabilidad: programar pago de " + solicitud.getMontoAprobado()
                + " para " + solicitud.getEmpleadoId());
        }
    }
}
