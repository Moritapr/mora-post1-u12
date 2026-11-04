package com.empresa.reembolsos.procesamiento;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fabrica de solicitudes para las pruebas unitarias de procesamiento.
 */
public final class SolicitudesDePrueba {

    private SolicitudesDePrueba() {
    }

    public static SolicitudReembolso solicitud(CategoriaGasto categoria, String monto, LocalDate fechaGasto) {
        var solicitud = new SolicitudReembolso();
        solicitud.setEmpleadoId("E-001");
        solicitud.setCategoria(categoria);
        solicitud.setMontoSolicitado(new BigDecimal(monto));
        solicitud.setFechaGasto(fechaGasto);
        solicitud.setDocumentosSoporte(true);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        return solicitud;
    }

    public static SolicitudReembolso solicitud(CategoriaGasto categoria, String monto) {
        return solicitud(categoria, monto, LocalDate.of(2026, 3, 15));
    }
}
