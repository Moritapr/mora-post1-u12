package com.empresa.reembolsos.procesamiento.validacion;

import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.util.List;

/**
 * Eslabon base de la cadena de validacion de solicitudes (patron Chain of Responsibility, RF1).
 *
 * <p>Cada subclase implementa una unica verificacion en {@link #validarPropio}. Si la
 * verificacion rechaza, la cadena se corta y se devuelve ese motivo; si aprueba, la
 * solicitud pasa al siguiente eslabon. Ningun eslabon sabe cuantos hay despues de el,
 * por lo que una verificacion nueva (p. ej. un limite anual acumulado) se agrega como
 * otra subclase sin modificar las existentes; el orden se arma en {@link CadenaValidadores}.</p>
 *
 * <p>Los eslabones no consultan la base de datos: reciben ya cargadas las solicitudes
 * pendientes del empleado, lo que mantiene este paquete libre de infraestructura.</p>
 */
public abstract class ValidadorSolicitud {
    private ValidadorSolicitud siguiente;

    /**
     * Enlaza el eslabon que se ejecuta despues de este.
     *
     * @param siguiente eslabon siguiente
     * @return el eslabon recibido, para encadenar llamadas ({@code a.siguiente(b).siguiente(c)})
     */
    public ValidadorSolicitud siguiente(ValidadorSolicitud siguiente) {
        this.siguiente = siguiente;
        return siguiente;
    }

    /**
     * Ejecuta la verificacion propia y, si aprueba, delega en el siguiente eslabon.
     *
     * @param solicitud             solicitud a validar
     * @param pendientesDelEmpleado solicitudes PENDIENTE del mismo empleado
     * @return el primer rechazo encontrado, o {@code ok} si todos los eslabones aprueban
     */
    public ResultadoValidacion validar(SolicitudReembolso solicitud, List<SolicitudReembolso> pendientesDelEmpleado) {
        ResultadoValidacion propio = validarPropio(solicitud, pendientesDelEmpleado);
        if (!propio.valida() || siguiente == null) {
            return propio;
        }
        return siguiente.validar(solicitud, pendientesDelEmpleado);
    }

    /**
     * Verificacion especifica de este eslabon, sin conocer a los demas.
     *
     * @param solicitud             solicitud a validar
     * @param pendientesDelEmpleado solicitudes PENDIENTE del mismo empleado
     * @return {@code ok} o un rechazo con motivo
     */
    protected abstract ResultadoValidacion validarPropio(
        SolicitudReembolso solicitud, List<SolicitudReembolso> pendientesDelEmpleado);
}
