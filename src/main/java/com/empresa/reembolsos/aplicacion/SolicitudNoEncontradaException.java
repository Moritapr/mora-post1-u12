package com.empresa.reembolsos.aplicacion;

/**
 * No existe una solicitud con el identificador indicado.
 */
public class SolicitudNoEncontradaException extends RuntimeException {
    public SolicitudNoEncontradaException(Long id) {
        super("Solicitud no encontrada: " + id);
    }
}
