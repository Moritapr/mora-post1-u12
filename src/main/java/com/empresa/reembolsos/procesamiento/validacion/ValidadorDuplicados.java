package com.empresa.reembolsos.procesamiento.validacion;

import com.empresa.reembolsos.dominio.SolicitudReembolso;
import com.empresa.reembolsos.infraestructura.RepositorioSolicitudesJpa;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rechaza la solicitud si el empleado ya tiene otra PENDIENTE de la misma categoria en el
 * mismo periodo. El periodo es el mes calendario de la fecha del gasto, incluido el anio
 * ({@link YearMonth}): enero de 2026 y enero de 2027 son periodos distintos.
 */
@Component
public class ValidadorDuplicados extends ValidadorSolicitud {
    // VIOLACION INTENCIONAL de la Regla 1 (se revierte en el commit siguiente)
    private RepositorioSolicitudesJpa repositorio;

    @Override
    protected ResultadoValidacion validarPropio(SolicitudReembolso solicitud, List<SolicitudReembolso> pendientes) {
        var periodo = YearMonth.from(solicitud.getFechaGasto());
        boolean existeDuplicado = pendientes.stream()
            .anyMatch(p -> p.getCategoria() == solicitud.getCategoria()
                && YearMonth.from(p.getFechaGasto()).equals(periodo));
        return existeDuplicado
            ? ResultadoValidacion.rechazar("Ya existe una solicitud pendiente de la misma categoria en el periodo")
            : ResultadoValidacion.ok();
    }
}
