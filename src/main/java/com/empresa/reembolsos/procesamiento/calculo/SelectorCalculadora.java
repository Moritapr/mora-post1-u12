package com.empresa.reembolsos.procesamiento.calculo;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Elige la estrategia de calculo segun la categoria. El mapa se construye con todas las
 * {@link CalculadoraReembolso} que Spring inyecta, asi que una categoria nueva no requiere
 * modificar esta clase (ver Decision de diseno 2 en el README). Si dos calculadoras
 * declaran la misma categoria, {@code Collectors.toMap} falla al arrancar el contexto.
 */
@Component
public class SelectorCalculadora {
    private final Map<CategoriaGasto, CalculadoraReembolso> calculadoras;

    public SelectorCalculadora(List<CalculadoraReembolso> lista) {
        this.calculadoras = lista.stream()
            .collect(Collectors.toMap(CalculadoraReembolso::getCategoria, Function.identity()));
    }

    /**
     * @param categoria categoria de la solicitud
     * @return la calculadora registrada para esa categoria
     * @throws IllegalArgumentException si ninguna calculadora soporta la categoria
     */
    public CalculadoraReembolso obtener(CategoriaGasto categoria) {
        return Optional.ofNullable(calculadoras.get(categoria))
            .orElseThrow(() -> new IllegalArgumentException("Categoria no soportada: " + categoria));
    }
}
