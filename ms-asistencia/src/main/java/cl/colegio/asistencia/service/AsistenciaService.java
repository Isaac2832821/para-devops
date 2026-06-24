package cl.colegio.asistencia.service;

import cl.colegio.asistencia.dto.AsistenciaRequest;
import cl.colegio.asistencia.dto.ResumenAsistenciaDTO;
import cl.colegio.asistencia.dto.ResumenAsistenciaDTO.EstadoAsistenciaResumen;
import cl.colegio.asistencia.entity.Asistencia;
import cl.colegio.asistencia.entity.EstadoAsistencia;
import cl.colegio.asistencia.repository.AsistenciaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Servicio de gestión de asistencia escolar.
 *
 * <p>Encapsula la lógica de negocio sobre la colección "asistencia" de Firestore.
 * Incluye el cálculo del porcentaje de asistencia con la regla del 85% según
 * el <b>Decreto Nº 511 del MINEDUC</b> (Ministerio de Educación de Chile):
 *
 * <ul>
 *   <li>≥ 85% → {@code REGULAR} (situación normal)</li>
 *   <li>75% a 84.9% → {@code EN_RIESGO} (se notifica al apoderado)</li>
 *   <li>< 75% → {@code CRITICO} → reprueba por inasistencia</li>
 * </ul>
 *
 * <p>Las tardanzas y asistencias justificadas se cuentan como clases asistidas
 * para el cálculo del porcentaje.
 */
@Service
public class AsistenciaService {

    private final AsistenciaRepository asistenciaRepository;
    
    public AsistenciaService(AsistenciaRepository asistenciaRepository) {
        this.asistenciaRepository = asistenciaRepository;
    }

    /** Porcentaje mínimo para aprobación por asistencia (Decreto MINEDUC 511). */
    private static final double PORCENTAJE_MINIMO_APROBACION = 75.0;

    /** Umbral de alerta temprana: entre este valor y el mínimo se notifica al apoderado. */
    private static final double PORCENTAJE_ALERTA = 85.0;

    /**
     * Registra un nuevo control de asistencia.
     * El docenteId se obtiene del JWT (Principal), no del request.
     *
     * @param request   datos del registro de asistencia
     * @param docenteId RUT del docente autenticado
     * @return el registro persistido en Firestore
     */
    public Asistencia registrar(AsistenciaRequest request, String docenteId) {
        var asistencia = Asistencia.builder()
                .estudianteId(request.estudianteId())
                .estudianteNombre(request.estudianteNombre())
                .docenteId(docenteId)
                .asignaturaId(request.asignaturaId())
                .asignaturaNombre(request.asignaturaNombre())
                .fecha(request.fecha())
                .estado(request.estado())
                .observacion(request.observacion())
                .build();
        return asistenciaRepository.save(asistencia);
    }

    /**
     * Actualiza un registro existente (solo el docente que lo creó o un ADMIN).
     *
     * @param id        ID del registro a actualizar
     * @param request   nuevos datos
     * @param docenteId RUT del docente autenticado
     * @return el registro actualizado
     * @throws NoSuchElementException si no existe el registro
     */
    public Asistencia actualizar(String id, AsistenciaRequest request, String docenteId) {
        var existente = obtenerPorId(id);
        existente.setEstudianteNombre(request.estudianteNombre());
        existente.setAsignaturaId(request.asignaturaId());
        existente.setAsignaturaNombre(request.asignaturaNombre());
        existente.setFecha(request.fecha());
        existente.setEstado(request.estado());
        existente.setObservacion(request.observacion());
        return asistenciaRepository.save(existente);
    }

    /**
     * Obtiene un registro de asistencia por su ID.
     *
     * @param id ID del registro en Firestore
     * @return el registro encontrado
     * @throws NoSuchElementException si no existe el registro
     */
    public Asistencia obtenerPorId(String id) {
        return asistenciaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Registro de asistencia no encontrado con id: " + id));
    }

    /** Retorna todos los registros del sistema (uso ADMIN). */
    public List<Asistencia> listarTodas() {
        return asistenciaRepository.findAll();
    }

    /** Todos los registros del docente autenticado (para la vista del docente). */
    public List<Asistencia> listarPorDocente(String docenteId) {
        return asistenciaRepository.findByDocenteId(docenteId);
    }

    /** Todos los registros de un estudiante por RUT (para vista de alumno / apoderado). */
    public List<Asistencia> listarPorEstudiante(String estudianteId) {
        return asistenciaRepository.findByEstudianteId(estudianteId);
    }

    /** Registros de asistencia filtrados por fecha (para pasar lista diaria). */
    public List<Asistencia> listarPorFecha(String fecha) {
        return asistenciaRepository.findByFecha(fecha);
    }

    /** Historial de un estudiante en una asignatura específica. */
    public List<Asistencia> listarPorEstudianteYAsignatura(String estudianteId, String asignaturaId) {
        return asistenciaRepository.findByEstudianteIdAndAsignaturaId(estudianteId, asignaturaId);
    }

    /**
     * Elimina un registro de asistencia.
     *
     * @param id ID del registro a eliminar
     */
    public void eliminar(String id) {
        asistenciaRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LÓGICA DE NEGOCIO ESCOLAR — Cálculo de asistencia (Decreto MINEDUC 511)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calcula el resumen de asistencia de un estudiante en todas sus asignaturas.
     *
     * <p>Aplica la regla del 85% del Decreto Nº 511 del MINEDUC:
     * <ul>
     *   <li>PRESENTE y TARDANZA cuentan como asistencia efectiva</li>
     *   <li>JUSTIFICADO también cuenta (inasistencia con certificado no castiga porcentaje)</li>
     *   <li>AUSENTE es la única condición que reduce el porcentaje</li>
     * </ul>
     *
     * @param estudianteId RUT del estudiante
     * @return resumen con porcentaje, estado y flag de reprobación
     */
    public ResumenAsistenciaDTO calcularResumenGeneral(String estudianteId) {
        var registros = asistenciaRepository.findByEstudianteId(estudianteId);
        return construirResumen(estudianteId, obtenerNombreEstudiante(registros), null, registros);
    }

    /**
     * Calcula el resumen de asistencia de un estudiante en una asignatura específica.
     *
     * <p>Útil para el docente que quiere ver la asistencia de sus alumnos
     * en su ramo particular, o para el boletín por asignatura.
     *
     * @param estudianteId RUT del estudiante
     * @param asignaturaId ID de la asignatura
     * @return resumen de asistencia filtrado por asignatura
     */
    public ResumenAsistenciaDTO calcularResumenPorAsignatura(String estudianteId, String asignaturaId) {
        var registros = asistenciaRepository.findByEstudianteIdAndAsignaturaId(estudianteId, asignaturaId);
        String nombreAsignatura = registros.isEmpty() ? null : registros.get(0).getAsignaturaNombre();
        return construirResumen(estudianteId, obtenerNombreEstudiante(registros), nombreAsignatura, registros);
    }

    /**
     * Construye el DTO de resumen a partir de una lista de registros de asistencia.
     *
     * <p>Fórmula de porcentaje:
     * <pre>
     *   porcentaje = (PRESENTE + TARDANZA + JUSTIFICADO) / TOTAL * 100
     * </pre>
     *
     * @param estudianteId      RUT del estudiante
     * @param estudianteNombre  nombre del estudiante
     * @param asignaturaNombre  nombre de la asignatura (null = resumen general)
     * @param registros         lista de registros de asistencia a procesar
     * @return DTO con el resumen calculado
     */
    private ResumenAsistenciaDTO construirResumen(
            String estudianteId,
            String estudianteNombre,
            String asignaturaNombre,
            List<Asistencia> registros) {

        int total = registros.size();

        long presentes     = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.PRESENTE).count();
        long tardanzas     = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.TARDANZA).count();
        long justificadas  = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.JUSTIFICADO).count();
        long ausentes      = registros.stream().filter(r -> r.getEstado() == EstadoAsistencia.AUSENTE).count();

        // PRESENTE + TARDANZA + JUSTIFICADO = clases que cuentan como asistidas
        long asistidas = presentes + tardanzas + justificadas;

        double porcentaje = (total == 0) ? 0.0
                : Math.round((asistidas * 100.0 / total) * 10.0) / 10.0;

        EstadoAsistenciaResumen estado;
        if (porcentaje >= PORCENTAJE_ALERTA) {
            estado = EstadoAsistenciaResumen.REGULAR;
        } else if (porcentaje >= PORCENTAJE_MINIMO_APROBACION) {
            estado = EstadoAsistenciaResumen.EN_RIESGO;
        } else {
            estado = EstadoAsistenciaResumen.CRITICO;
        }

        return new ResumenAsistenciaDTO(
                estudianteId,
                estudianteNombre != null ? estudianteNombre : "",
                asignaturaNombre,
                total,
                (int) asistidas,
                (int) ausentes,
                (int) justificadas,
                (int) tardanzas,
                porcentaje,
                estado,
                porcentaje < PORCENTAJE_MINIMO_APROBACION
        );
    }

    /** Extrae el nombre del estudiante del primer registro disponible. */
    private String obtenerNombreEstudiante(List<Asistencia> registros) {
        return registros.isEmpty() ? "" : registros.get(0).getEstudianteNombre();
    }
}

