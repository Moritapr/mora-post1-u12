package com.empresa.reembolsos.procesamiento.notificacion;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Registra en el log de auditoria interna todo cambio de estado, sin filtrar, para el
 * reporte trimestral.
 *
 * <p>Va primero ({@code @Order(1)}): RF3 exige que auditoria registre todo cambio sin excepcion,
 * asi que no puede depender de que los observadores anteriores terminen bien.</p>
 */
@Component
@Order(1)
public class NotificadorAuditoria implements ObservadorCambioEstado {
    private static final Logger log = LoggerFactory.getLogger(NotificadorAuditoria.class);

    @Override
    public void onCambioEstado(SolicitudReembolso solicitud, EstadoSolicitud estadoAnterior) {
        log.info("Auditoria: solicitud {} cambio de {} a {}", solicitud.getId(), estadoAnterior, solicitud.getEstado());
    }
}
