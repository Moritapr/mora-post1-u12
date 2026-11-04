package com.empresa.reembolsos.procesamiento.calculo;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.math.BigDecimal;

/**
 * Estrategia de calculo del monto aprobado para una categoria de gasto (patron Strategy, RF2).
 *
 * <p>Cada implementacion encapsula la formula de una sola categoria. Para soportar una
 * categoria nueva (p. ej. "bienestar") basta con crear otra implementacion anotada con
 * {@code @Component}: {@link SelectorCalculadora} la registra automaticamente y las
 * calculadoras existentes no se modifican.</p>
 */
public interface CalculadoraReembolso {

    /**
     * @return la categoria cuya formula implementa esta calculadora (clave unica en el selector)
     */
    CategoriaGasto getCategoria();

    /**
     * Calcula el monto aprobado; nunca supera el monto solicitado.
     *
     * @param solicitud solicitud ya validada de la categoria {@link #getCategoria()}
     * @return monto aprobado
     */
    BigDecimal calcular(SolicitudReembolso solicitud);
}
