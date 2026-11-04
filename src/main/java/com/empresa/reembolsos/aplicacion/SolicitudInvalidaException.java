package com.empresa.reembolsos.aplicacion;

/**
 * La solicitud no se puede registrar; el mensaje es el motivo legible para el empleado
 * (por ejemplo, el motivo de rechazo del primer validador que fallo).
 */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String motivo) {
        super(motivo);
    }
}
