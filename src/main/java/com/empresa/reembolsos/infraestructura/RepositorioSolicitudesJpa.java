package com.empresa.reembolsos.infraestructura;

import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistencia de solicitudes con Spring Data JPA. El servicio de aplicacion depende de
 * esta interfaz directamente, sin un puerto intermedio (decision que se revisa en la Parte 2).
 * El mapeo de {@link SolicitudReembolso} esta en META-INF/orm.xml.
 */
public interface RepositorioSolicitudesJpa extends JpaRepository<SolicitudReembolso, Long> {

    /**
     * @param empleadoId identificador del empleado
     * @param estado     estado buscado
     * @return solicitudes del empleado en ese estado
     */
    List<SolicitudReembolso> findByEmpleadoIdAndEstado(String empleadoId, EstadoSolicitud estado);
}
