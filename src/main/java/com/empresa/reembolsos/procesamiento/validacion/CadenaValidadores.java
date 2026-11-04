package com.empresa.reembolsos.procesamiento.validacion;

import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Arma la cadena de validacion de RF1 y es el unico lugar que conoce su orden:
 * documentos -> monto -> duplicados (de la verificacion mas barata a la mas cara;
 * ver Decision de diseno 1 en el README).
 */
@Component
public class CadenaValidadores {
    private final ValidadorSolicitud primero;

    public CadenaValidadores(ValidadorDocumentosSoporte documentos,
                             ValidadorMontoMaximo monto,
                             ValidadorDuplicados duplicados) {
        documentos.siguiente(monto).siguiente(duplicados);
        this.primero = documentos;
    }

    /**
     * Valida la solicitud contra todos los eslabones, cortando en el primer rechazo.
     *
     * @param solicitud  solicitud a validar
     * @param pendientes solicitudes PENDIENTE del mismo empleado
     * @return resultado de la cadena
     */
    public ResultadoValidacion validar(SolicitudReembolso solicitud, List<SolicitudReembolso> pendientes) {
        return primero.validar(solicitud, pendientes);
    }
}
