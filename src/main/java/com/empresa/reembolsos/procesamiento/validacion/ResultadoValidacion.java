package com.empresa.reembolsos.procesamiento.validacion;

/**
 * Resultado de recorrer la cadena de validacion: valida, o rechazada con el motivo
 * del primer eslabon que la rechazo.
 *
 * @param valida        {@code true} si la solicitud supero la verificacion
 * @param motivoRechazo motivo legible del rechazo; {@code null} cuando es valida
 */
public record ResultadoValidacion(boolean valida, String motivoRechazo) {
    public static ResultadoValidacion ok() {
        return new ResultadoValidacion(true, null);
    }

    public static ResultadoValidacion rechazar(String motivo) {
        return new ResultadoValidacion(false, motivo);
    }
}
