package cl.colegio.asistencia.entity;


/**
 * Entidad que representa un registro de asistencia diaria de un estudiante.
 * Se almacena en la colección "asistencia" de Firestore.
 */
public class Asistencia {
    private String id;
    private String estudianteId;      // RUT del estudiante
    private String estudianteNombre;  // Nombre completo (desnormalizado para consultas rápidas)
    private String docenteId;         // RUT del docente que registra
    private String asignaturaId;      // ID de la asignatura
    private String asignaturaNombre;  // Nombre legible de la asignatura
    private String fecha;             // Formato: "YYYY-MM-DD"
    private EstadoAsistencia estado;  // PRESENTE, AUSENTE, JUSTIFICADO, TARDANZA
    private String observacion;       // Opcional

    public Asistencia() {}

    public Asistencia(String id, String estudianteId, String estudianteNombre, String docenteId, String asignaturaId, String asignaturaNombre, String fecha, EstadoAsistencia estado, String observacion) {
        this.id = id;
        this.estudianteId = estudianteId;
        this.estudianteNombre = estudianteNombre;
        this.docenteId = docenteId;
        this.asignaturaId = asignaturaId;
        this.asignaturaNombre = asignaturaNombre;
        this.fecha = fecha;
        this.estado = estado;
        this.observacion = observacion;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEstudianteId() { return estudianteId; }
    public void setEstudianteId(String estudianteId) { this.estudianteId = estudianteId; }
    public String getEstudianteNombre() { return estudianteNombre; }
    public void setEstudianteNombre(String estudianteNombre) { this.estudianteNombre = estudianteNombre; }
    public String getDocenteId() { return docenteId; }
    public void setDocenteId(String docenteId) { this.docenteId = docenteId; }
    public String getAsignaturaId() { return asignaturaId; }
    public void setAsignaturaId(String asignaturaId) { this.asignaturaId = asignaturaId; }
    public String getAsignaturaNombre() { return asignaturaNombre; }
    public void setAsignaturaNombre(String asignaturaNombre) { this.asignaturaNombre = asignaturaNombre; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public EstadoAsistencia getEstado() { return estado; }
    public void setEstado(EstadoAsistencia estado) { this.estado = estado; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public static AsistenciaBuilder builder() {
        return new AsistenciaBuilder();
    }

    public static class AsistenciaBuilder {
        private String id;
        private String estudianteId;
        private String estudianteNombre;
        private String docenteId;
        private String asignaturaId;
        private String asignaturaNombre;
        private String fecha;
        private EstadoAsistencia estado;
        private String observacion;

        public AsistenciaBuilder id(String id) { this.id = id; return this; }
        public AsistenciaBuilder estudianteId(String estudianteId) { this.estudianteId = estudianteId; return this; }
        public AsistenciaBuilder estudianteNombre(String estudianteNombre) { this.estudianteNombre = estudianteNombre; return this; }
        public AsistenciaBuilder docenteId(String docenteId) { this.docenteId = docenteId; return this; }
        public AsistenciaBuilder asignaturaId(String asignaturaId) { this.asignaturaId = asignaturaId; return this; }
        public AsistenciaBuilder asignaturaNombre(String asignaturaNombre) { this.asignaturaNombre = asignaturaNombre; return this; }
        public AsistenciaBuilder fecha(String fecha) { this.fecha = fecha; return this; }
        public AsistenciaBuilder estado(EstadoAsistencia estado) { this.estado = estado; return this; }
        public AsistenciaBuilder observacion(String observacion) { this.observacion = observacion; return this; }

        public Asistencia build() {
            return new Asistencia(id, estudianteId, estudianteNombre, docenteId, asignaturaId, asignaturaNombre, fecha, estado, observacion);
        }
    }
}
