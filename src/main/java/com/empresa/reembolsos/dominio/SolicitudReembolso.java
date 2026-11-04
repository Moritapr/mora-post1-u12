package com.empresa.reembolsos.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Solicitud de reembolso de un gasto corporativo registrada por un empleado.
 *
 * <p>Clase de dominio pura: no importa nada de Spring ni de JPA. Su persistencia se
 * declara fuera del dominio, en {@code src/main/resources/META-INF/orm.xml}.</p>
 */
public class SolicitudReembolso {
    private Long id;
    private String empleadoId;
    private CategoriaGasto categoria;
    private BigDecimal montoSolicitado;
    private BigDecimal montoAprobado;
    private LocalDate fechaGasto;
    private int diasViaje;
    private boolean documentosSoporte;
    private EstadoSolicitud estado;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpleadoId() {
        return empleadoId;
    }

    public void setEmpleadoId(String empleadoId) {
        this.empleadoId = empleadoId;
    }

    public CategoriaGasto getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaGasto categoria) {
        this.categoria = categoria;
    }

    public BigDecimal getMontoSolicitado() {
        return montoSolicitado;
    }

    public void setMontoSolicitado(BigDecimal montoSolicitado) {
        this.montoSolicitado = montoSolicitado;
    }

    public BigDecimal getMontoAprobado() {
        return montoAprobado;
    }

    public void setMontoAprobado(BigDecimal montoAprobado) {
        this.montoAprobado = montoAprobado;
    }

    public LocalDate getFechaGasto() {
        return fechaGasto;
    }

    public void setFechaGasto(LocalDate fechaGasto) {
        this.fechaGasto = fechaGasto;
    }

    public int getDiasViaje() {
        return diasViaje;
    }

    public void setDiasViaje(int diasViaje) {
        this.diasViaje = diasViaje;
    }

    public boolean isDocumentosSoporte() {
        return documentosSoporte;
    }

    public void setDocumentosSoporte(boolean documentosSoporte) {
        this.documentosSoporte = documentosSoporte;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitud estado) {
        this.estado = estado;
    }
}
