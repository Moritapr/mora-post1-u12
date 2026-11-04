package com.empresa.reembolsos.procesamiento.notificacion;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;

/**
 * Interesado en los cambios de estado de una solicitud (patron Observer, RF3).
 *
 * <p>El servicio de aplicacion notifica a todas las implementaciones registradas como
 * {@code @Component} sin conocerlas; cada una decide si reacciona y como (por ejemplo,
 * Contabilidad solo ante APROBADA). Un interesado nuevo, como el panel de metricas, es
 * otra implementacion y no requiere cambiar el servicio ni a los demas observadores.</p>
 */
public interface ObservadorCambioEstado {

    /**
     * Se invoca despues de persistir el nuevo estado.
     *
     * @param solicitud      solicitud ya guardada, con el estado nuevo
     * @param estadoAnterior estado que tenia antes del cambio
     */
    void onCambioEstado(SolicitudReembolso solicitud, EstadoSolicitud estadoAnterior);
}
