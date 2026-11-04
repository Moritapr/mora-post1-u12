package com.empresa.reembolsos.aplicacion;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import com.empresa.reembolsos.infraestructura.RepositorioSolicitudesJpa;
import com.empresa.reembolsos.procesamiento.calculo.SelectorCalculadora;
import com.empresa.reembolsos.procesamiento.notificacion.ObservadorCambioEstado;
import com.empresa.reembolsos.procesamiento.validacion.CadenaValidadores;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de las solicitudes de reembolso. Es el unico punto que coordina los tres
 * patrones (cadena de validadores, estrategias de calculo y observadores) y el unico que
 * conoce el repositorio de persistencia.
 */
@Service
public class SolicitudReembolsoService {
    private final CadenaValidadores validadores;
    private final SelectorCalculadora calculadoras;
    private final List<ObservadorCambioEstado> observadores;
    private final RepositorioSolicitudesJpa repositorio;

    public SolicitudReembolsoService(CadenaValidadores validadores, SelectorCalculadora calculadoras,
                                     List<ObservadorCambioEstado> observadores, RepositorioSolicitudesJpa repositorio) {
        this.validadores = validadores;
        this.calculadoras = calculadoras;
        this.observadores = observadores;
        this.repositorio = repositorio;
    }

    /**
     * Valida la solicitud, calcula el monto aprobado segun su categoria y la registra como PENDIENTE.
     *
     * @param solicitud datos enviados por el empleado (se ignoran id, estado y monto aprobado)
     * @return la solicitud guardada
     * @throws SolicitudInvalidaException si faltan datos obligatorios o algun validador la rechaza
     */
    public SolicitudReembolso crear(SolicitudReembolso solicitud) {
        exigirDatosObligatorios(solicitud);
        var pendientes = repositorio.findByEmpleadoIdAndEstado(solicitud.getEmpleadoId(), EstadoSolicitud.PENDIENTE);
        var resultado = validadores.validar(solicitud, pendientes);
        if (!resultado.valida()) {
            throw new SolicitudInvalidaException(resultado.motivoRechazo());
        }
        solicitud.setId(null);
        solicitud.setMontoAprobado(calculadoras.obtener(solicitud.getCategoria()).calcular(solicitud));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        return repositorio.save(solicitud);
    }

    /**
     * Cambia el estado de una solicitud, lo persiste y notifica a todos los observadores.
     *
     * @param id          identificador de la solicitud
     * @param nuevoEstado estado destino
     * @return la solicitud guardada con el nuevo estado
     * @throws SolicitudInvalidaException     si el nuevo estado es nulo
     * @throws SolicitudNoEncontradaException si no existe la solicitud
     */
    public SolicitudReembolso cambiarEstado(Long id, EstadoSolicitud nuevoEstado) {
        if (nuevoEstado == null) {
            throw new SolicitudInvalidaException("El nuevo estado es obligatorio");
        }
        var solicitud = repositorio.findById(id)
            .orElseThrow(() -> new SolicitudNoEncontradaException(id));
        var estadoAnterior = solicitud.getEstado();
        solicitud.setEstado(nuevoEstado);
        var guardada = repositorio.save(solicitud);
        observadores.forEach(o -> o.onCambioEstado(guardada, estadoAnterior));
        return guardada;
    }

    private static void exigirDatosObligatorios(SolicitudReembolso solicitud) {
        var faltantes = new ArrayList<String>();
        if (solicitud.getEmpleadoId() == null || solicitud.getEmpleadoId().isBlank()) {
            faltantes.add("empleadoId");
        }
        if (solicitud.getCategoria() == null) {
            faltantes.add("categoria");
        }
        if (solicitud.getMontoSolicitado() == null) {
            faltantes.add("montoSolicitado");
        }
        if (solicitud.getFechaGasto() == null) {
            faltantes.add("fechaGasto");
        }
        if (!faltantes.isEmpty()) {
            throw new SolicitudInvalidaException("Faltan datos obligatorios: " + String.join(", ", faltantes));
        }
    }
}
