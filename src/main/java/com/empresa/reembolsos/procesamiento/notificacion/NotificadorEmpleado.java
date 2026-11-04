package com.empresa.reembolsos.procesamiento.notificacion;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import org.springframework.stereotype.Component;

/**
 * Informa al empleado el resultado de cualquier cambio de estado de su solicitud.
 */
@Component
public class NotificadorEmpleado implements ObservadorCambioEstado {
    @Override
    public void onCambioEstado(SolicitudReembolso solicitud, EstadoSolicitud estadoAnterior) {
        System.out.println("Notificando a " + solicitud.getEmpleadoId()
            + ": su solicitud paso de " + estadoAnterior + " a " + solicitud.getEstado());
    }
}
