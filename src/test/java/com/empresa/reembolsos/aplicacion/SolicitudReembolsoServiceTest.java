package com.empresa.reembolsos.aplicacion;

import static com.empresa.reembolsos.procesamiento.SolicitudesDePrueba.solicitud;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.empresa.reembolsos.dominio.CategoriaGasto;
import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import com.empresa.reembolsos.infraestructura.RepositorioSolicitudesJpa;
import com.empresa.reembolsos.procesamiento.calculo.CalculadoraCapacitacion;
import com.empresa.reembolsos.procesamiento.calculo.CalculadoraRepresentacion;
import com.empresa.reembolsos.procesamiento.calculo.CalculadoraViaje;
import com.empresa.reembolsos.procesamiento.calculo.SelectorCalculadora;
import com.empresa.reembolsos.procesamiento.notificacion.ObservadorCambioEstado;
import com.empresa.reembolsos.procesamiento.validacion.CadenaValidadores;
import com.empresa.reembolsos.procesamiento.validacion.ValidadorDocumentosSoporte;
import com.empresa.reembolsos.procesamiento.validacion.ValidadorDuplicados;
import com.empresa.reembolsos.procesamiento.validacion.ValidadorMontoMaximo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SolicitudReembolsoServiceTest {

    private RepositorioSolicitudesJpa repositorio;
    private ObservadorCambioEstado observadorA;
    private ObservadorCambioEstado observadorB;
    private SolicitudReembolsoService servicio;

    @BeforeEach
    void configurar() {
        repositorio = mock(RepositorioSolicitudesJpa.class);
        observadorA = mock(ObservadorCambioEstado.class);
        observadorB = mock(ObservadorCambioEstado.class);
        when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        servicio = new SolicitudReembolsoService(
            new CadenaValidadores(new ValidadorDocumentosSoporte(), new ValidadorMontoMaximo(), new ValidadorDuplicados()),
            new SelectorCalculadora(List.of(new CalculadoraViaje(), new CalculadoraRepresentacion(), new CalculadoraCapacitacion())),
            List.of(observadorA, observadorB),
            repositorio);
    }

    @Test
    void crear_validaCalculaYGuardaComoPendienteIgnorandoIdYEstadoDelCliente() {
        var nueva = solicitud(CategoriaGasto.CAPACITACION, "1000000");
        nueva.setId(99L);
        nueva.setEstado(EstadoSolicitud.APROBADA);

        var guardada = servicio.crear(nueva);

        assertThat(guardada.getId()).isNull();
        assertThat(guardada.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(guardada.getMontoAprobado()).isEqualByComparingTo("800000");
        verify(repositorio).save(nueva);
    }

    @Test
    void crear_rechazadaPorLaCadena_lanzaExcepcionConElMotivoYNoGuarda() {
        var sinDocumentos = solicitud(CategoriaGasto.VIAJE, "100000");
        sinDocumentos.setDocumentosSoporte(false);

        assertThatThrownBy(() -> servicio.crear(sinDocumentos))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessage("Faltan documentos soporte");
        verify(repositorio, never()).save(any());
    }

    @Test
    void crear_usaLasPendientesDelEmpleadoParaDetectarDuplicados() {
        when(repositorio.findByEmpleadoIdAndEstado("E-001", EstadoSolicitud.PENDIENTE))
            .thenReturn(List.of(solicitud(CategoriaGasto.VIAJE, "5000")));

        assertThatThrownBy(() -> servicio.crear(solicitud(CategoriaGasto.VIAJE, "1000")))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessage("Ya existe una solicitud pendiente de la misma categoria en el periodo");
    }

    @Test
    void crear_sinDatosObligatorios_lanzaExcepcionListandoLosCampos() {
        assertThatThrownBy(() -> servicio.crear(new SolicitudReembolso()))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessage("Faltan datos obligatorios: empleadoId, categoria, montoSolicitado, fechaGasto");
        verifyNoInteractions(repositorio);
    }

    @Test
    void cambiarEstado_guardaYNotificaATodosLosObservadoresConElEstadoAnterior() {
        var existente = solicitud(CategoriaGasto.VIAJE, "1000");
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));

        var guardada = servicio.cambiarEstado(1L, EstadoSolicitud.RECHAZADA);

        assertThat(guardada.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        verify(observadorA).onCambioEstado(existente, EstadoSolicitud.PENDIENTE);
        verify(observadorB).onCambioEstado(existente, EstadoSolicitud.PENDIENTE);
    }

    @Test
    void cambiarEstado_deSolicitudInexistente_lanzaNoEncontradaYNoNotifica() {
        when(repositorio.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarEstado(42L, EstadoSolicitud.APROBADA))
            .isInstanceOf(SolicitudNoEncontradaException.class)
            .hasMessage("Solicitud no encontrada: 42");
        verifyNoInteractions(observadorA, observadorB);
    }
}
