package com.empresa.reembolsos.procesamiento.validacion;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rechaza la solicitud si el monto solicitado excede el tope de su categoria.
 * Un monto igual al tope se acepta.
 */
@Component
public class ValidadorMontoMaximo extends ValidadorSolicitud {
    private static final Map<CategoriaGasto, BigDecimal> TOPES = Map.of(
        CategoriaGasto.VIAJE, new BigDecimal("150000"),
        CategoriaGasto.REPRESENTACION, new BigDecimal("2000000"),
        CategoriaGasto.CAPACITACION, new BigDecimal("3000000"));

    @Override
    protected ResultadoValidacion validarPropio(SolicitudReembolso solicitud, List<SolicitudReembolso> pendientes) {
        var tope = TOPES.get(solicitud.getCategoria());
        return solicitud.getMontoSolicitado().compareTo(tope) > 0
            ? ResultadoValidacion.rechazar("Monto excede el tope de la categoria " + solicitud.getCategoria())
            : ResultadoValidacion.ok();
    }
}
