package com.empresa.reembolsos.procesamiento.validacion;

import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rechaza la solicitud si el empleado no adjunto los documentos soporte.
 */
@Component
public class ValidadorDocumentosSoporte extends ValidadorSolicitud {
    @Override
    protected ResultadoValidacion validarPropio(SolicitudReembolso solicitud, List<SolicitudReembolso> pendientes) {
        return solicitud.isDocumentosSoporte()
            ? ResultadoValidacion.ok()
            : ResultadoValidacion.rechazar("Faltan documentos soporte");
    }
}
