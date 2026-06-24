package cl.colegio.asistencia.repository;

import cl.colegio.asistencia.entity.Asistencia;
import cl.colegio.asistencia.entity.EstadoAsistencia;
import com.google.cloud.firestore.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Repositorio de Firestore para la colección "asistencia".
 * Proporciona operaciones CRUD y consultas específicas por estudiante, docente y fecha.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
public class AsistenciaRepository {

    private static final Logger log = LoggerFactory.getLogger(AsistenciaRepository.class);

    private final Firestore firestore;
    
    public AsistenciaRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    private static final String COLLECTION = "asistencia";

    public Asistencia save(Asistencia asistencia) {
        try {
            if (asistencia.getId() == null || asistencia.getId().isBlank()) {
                DocumentReference docRef = firestore.collection(COLLECTION).document();
                asistencia.setId(docRef.getId());
                docRef.set(toMap(asistencia)).get();
            } else {
                firestore.collection(COLLECTION).document(asistencia.getId())
                        .set(toMap(asistencia)).get();
            }
            return asistencia;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error guardando registro de asistencia", e);
        }
    }

    public Optional<Asistencia> findById(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION).document(id).get().get();
            return doc.exists() ? Optional.of(fromDoc(doc)) : Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error buscando registro de asistencia", e);
        }
    }

    public List<Asistencia> findAll() {
        try {
            return firestore.collection(COLLECTION).get().get().getDocuments()
                    .stream().map(this::fromDoc).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error listando asistencias", e);
        }
    }

    public List<Asistencia> findByEstudianteId(String estudianteId) {
        try {
            return firestore.collection(COLLECTION)
                    .whereEqualTo("estudianteId", estudianteId).get().get()
                    .getDocuments().stream().map(this::fromDoc).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error buscando asistencia por estudiante", e);
        }
    }

    public List<Asistencia> findByDocenteId(String docenteId) {
        try {
            return firestore.collection(COLLECTION)
                    .whereEqualTo("docenteId", docenteId).get().get()
                    .getDocuments().stream().map(this::fromDoc).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error buscando asistencia por docente", e);
        }
    }

    public List<Asistencia> findByFecha(String fecha) {
        try {
            return firestore.collection(COLLECTION)
                    .whereEqualTo("fecha", fecha).get().get()
                    .getDocuments().stream().map(this::fromDoc).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error buscando asistencia por fecha", e);
        }
    }

    public List<Asistencia> findByEstudianteIdAndAsignaturaId(String estudianteId, String asignaturaId) {
        try {
            return firestore.collection(COLLECTION)
                    .whereEqualTo("estudianteId", estudianteId)
                    .whereEqualTo("asignaturaId", asignaturaId)
                    .get().get()
                    .getDocuments().stream().map(this::fromDoc).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error buscando asistencia por estudiante y asignatura", e);
        }
    }

    public void deleteById(String id) {
        try {
            firestore.collection(COLLECTION).document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error eliminando registro de asistencia", e);
        }
    }

    // ── Mapeo entidad ↔ Firestore ──────────────────────────────────────────

    private Map<String, Object> toMap(Asistencia a) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", a.getId());
        map.put("estudianteId", a.getEstudianteId());
        map.put("estudianteNombre", a.getEstudianteNombre());
        map.put("docenteId", a.getDocenteId());
        map.put("asignaturaId", a.getAsignaturaId());
        map.put("asignaturaNombre", a.getAsignaturaNombre());
        map.put("fecha", a.getFecha());
        map.put("estado", a.getEstado() != null ? a.getEstado().name() : null);
        map.put("observacion", a.getObservacion());
        return map;
    }

    private Asistencia fromDoc(DocumentSnapshot doc) {
        String estadoStr = doc.getString("estado");
        EstadoAsistencia estado = estadoStr != null ? EstadoAsistencia.valueOf(estadoStr) : null;

        Asistencia a = new Asistencia();
        a.setId(doc.getId());
        a.setEstudianteId(doc.getString("estudianteId"));
        a.setEstudianteNombre(doc.getString("estudianteNombre"));
        a.setDocenteId(doc.getString("docenteId"));
        a.setAsignaturaId(doc.getString("asignaturaId"));
        a.setAsignaturaNombre(doc.getString("asignaturaNombre"));
        a.setFecha(doc.getString("fecha"));
        a.setEstado(estado);
        a.setObservacion(doc.getString("observacion"));
        return a;
    }
}
