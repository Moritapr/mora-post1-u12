package com.empresa.reembolsos.adaptadores.rest;

import com.empresa.reembolsos.aplicacion.SolicitudInvalidaException;
import com.empresa.reembolsos.aplicacion.SolicitudNoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de aplicacion a respuestas HTTP con el motivo en {@code detail}
 * (formato RFC 9457 de {@link ProblemDetail}).
 */
@RestControllerAdvice
public class ManejadorErroresRest {

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail solicitudInvalida(SolicitudInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud invalida", e.getMessage());
    }

    @ExceptionHandler(SolicitudNoEncontradaException.class)
    public ProblemDetail solicitudNoEncontrada(SolicitudNoEncontradaException e) {
        return problema(HttpStatus.NOT_FOUND, "Solicitud no encontrada", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la peticion invalido",
            "El cuerpo JSON no se pudo leer (formato o valores de enumeracion invalidos)");
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
