package cl.colegio.calificaciones.service;

import cl.colegio.calificaciones.dto.CalificacionRequest;
import cl.colegio.calificaciones.entity.Calificacion;
import cl.colegio.calificaciones.messaging.NotificacionProducer;
import cl.colegio.calificaciones.repository.CalificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Servicio de gestión de calificaciones.
 *
 * <p>Encapsula la lógica de negocio sobre la colección "calificaciones" de Firestore.
 * Al registrar una nueva calificación, publica un evento en RabbitMQ para que
 * ms-notificaciones notifique automáticamente al estudiante afectado.
 *
 * <p>Patrón aplicado: Service Layer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;
    private final NotificacionProducer notificacionProducer;

    /**
     * Registra una nueva calificación y publica un evento en RabbitMQ.
     *
     * <p>El docenteId se recibe directamente del JWT (no del body del request),
     * por lo que el cliente no puede falsificarlo.
     *
     * @param request   DTO con los datos de la calificación
     * @param docenteId RUT del docente extraído del JWT
     * @return la calificación persistida con su ID de Firestore
     */
    public Calificacion registrar(CalificacionRequest request, String docenteId) {
        var calificacion = Calificacion.builder()
                .estudianteId(request.estudianteId())
                .estudianteNombre(request.estudianteNombre())
                .asignaturaId(request.asignaturaId())
                .asignaturaNombre(request.asignaturaNombre())
                .nota(request.nota())
                .tipo(request.tipo())
                .fecha(request.fecha())
                .observacion(request.observacion())
                .docenteId(docenteId)
                .build();

        var guardada = calificacionRepository.save(calificacion);

        // Publicar evento asíncrono en RabbitMQ para ms-notificaciones
        // La falla en mensajería NO interrumpe el guardado (ver NotificacionProducer)
        try {
            notificacionProducer.publicarNuevaCalificacion(guardada, docenteId);
        } catch (Exception e) {
            log.error("Error al invocar notificacionProducer: {}", e.getMessage());
        }

        log.info("Calificación registrada: estudiante={} nota={}", request.estudianteId(), request.nota());
        return guardada;
    }

    /**
     * Lista todas las calificaciones del sistema (uso ADMIN/DOCENTE).
     *
     * @return lista completa de calificaciones
     */
    public List<Calificacion> listarTodas() {
        return calificacionRepository.findAll();
    }

    /**
     * Obtiene una calificación por su ID.
     *
     * @param id ID de la calificación en Firestore
     * @return la calificación encontrada
     * @throws NoSuchElementException si no existe calificación con ese ID
     */
    public Calificacion obtenerPorId(String id) {
        return calificacionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Calificación no encontrada con id: " + id));
    }

    /**
     * Lista todas las calificaciones de un estudiante específico.
     *
     * @param estudianteId RUT del estudiante
     * @return lista de calificaciones del estudiante
     */
    public List<Calificacion> listarPorEstudiante(String estudianteId) {
        return calificacionRepository.findByEstudianteId(estudianteId);
    }

    /**
     * Lista todas las calificaciones de una asignatura específica.
     *
     * @param asignaturaId ID de la asignatura
     * @return lista de calificaciones de la asignatura
     */
    public List<Calificacion> listarPorAsignatura(String asignaturaId) {
        return calificacionRepository.findByAsignaturaId(asignaturaId);
    }

    /**
     * Lista todas las calificaciones registradas por un docente específico.
     *
     * @param docenteId RUT del docente
     * @return lista de calificaciones del docente
     */
    public List<Calificacion> listarPorDocente(String docenteId) {
        return calificacionRepository.findByDocenteId(docenteId);
    }

    /**
     * Actualiza una calificación existente.
     *
     * @param id        ID de la calificación a actualizar
     * @param request   DTO con los nuevos datos
     * @param docenteId RUT del docente (extraído del JWT)
     * @return la calificación actualizada
     * @throws NoSuchElementException si no existe calificación con ese ID
     */
    public Calificacion actualizar(String id, CalificacionRequest request, String docenteId) {
        var calificacion = obtenerPorId(id);
        calificacion.setEstudianteId(request.estudianteId());
        calificacion.setEstudianteNombre(request.estudianteNombre());
        calificacion.setAsignaturaId(request.asignaturaId());
        calificacion.setAsignaturaNombre(request.asignaturaNombre());
        calificacion.setNota(request.nota());
        calificacion.setTipo(request.tipo());
        calificacion.setFecha(request.fecha());
        calificacion.setObservacion(request.observacion());
        calificacion.setDocenteId(docenteId);
        return calificacionRepository.save(calificacion);
    }

    /**
     * Elimina una calificación del sistema.
     *
     * @param id ID de la calificación a eliminar
     */
    public void eliminar(String id) {
        calificacionRepository.deleteById(id);
    }
}

