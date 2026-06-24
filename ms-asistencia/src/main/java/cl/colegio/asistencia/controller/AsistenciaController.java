package cl.colegio.asistencia.controller;

import cl.colegio.asistencia.dto.AsistenciaRequest;
import cl.colegio.asistencia.dto.ResumenAsistenciaDTO;
import cl.colegio.asistencia.entity.Asistencia;
import cl.colegio.asistencia.service.AsistenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controlador REST para gestión de asistencia escolar.
 *
 * <p>Endpoints disponibles por rol:
 * <ul>
 *   <li>DOCENTE : registrar, actualizar, listar sus registros, listar por fecha</li>
 *   <li>ADMIN   : ver todos, eliminar</li>
 *   <li>ESTUDIANTE / APODERADO : consultar asistencia y resumen de un estudiante</li>
 * </ul>
 *
 * <p>El endpoint {@code /resumen} aplica la regla del 85% del Decreto MINEDUC 511
 * para determinar si el estudiante está en situación regular, en riesgo o crítica.
 */
@RestController
@RequestMapping("/asistencia")
@Tag(name = "Asistencia", description = "CRUD y resumen de asistencia escolar — regla 85% MINEDUC")
public class AsistenciaController {

    private final AsistenciaService asistenciaService;
    
    public AsistenciaController(AsistenciaService asistenciaService) {
        this.asistenciaService = asistenciaService;
    }

    // ── Docente ───────────────────────────────────────────────────────────

    @Operation(summary = "Registrar asistencia (DOCENTE)")
    @PostMapping
    @PreAuthorize("hasRole('DOCENTE')")
    public ResponseEntity<Asistencia> registrar(@Valid @RequestBody AsistenciaRequest request,
                                                Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(asistenciaService.registrar(request, principal.getName()));
    }

    @Operation(summary = "Mis registros de asistencia (DOCENTE)")
    @GetMapping("/mis-registros")
    @PreAuthorize("hasRole('DOCENTE')")
    public ResponseEntity<List<Asistencia>> misRegistros(Principal principal) {
        return ResponseEntity.ok(asistenciaService.listarPorDocente(principal.getName()));
    }

    @Operation(summary = "Asistencia por fecha — pasar lista (DOCENTE/ADMIN)")
    @GetMapping("/fecha/{fecha}")
    @PreAuthorize("hasAnyRole('DOCENTE', 'ADMIN')")
    public ResponseEntity<List<Asistencia>> porFecha(@PathVariable String fecha) {
        return ResponseEntity.ok(asistenciaService.listarPorFecha(fecha));
    }

    @Operation(summary = "Actualizar registro de asistencia (DOCENTE)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ResponseEntity<Asistencia> actualizar(@PathVariable String id,
                                                 @Valid @RequestBody AsistenciaRequest request,
                                                 Principal principal) {
        return ResponseEntity.ok(asistenciaService.actualizar(id, request, principal.getName()));
    }

    // ── Estudiante / Apoderado ────────────────────────────────────────────

    @Operation(summary = "Ver asistencia de un estudiante por RUT")
    @GetMapping("/estudiante/{estudianteId}")
    public ResponseEntity<List<Asistencia>> porEstudiante(@PathVariable String estudianteId) {
        return ResponseEntity.ok(asistenciaService.listarPorEstudiante(estudianteId));
    }

    @Operation(summary = "Ver asistencia de un estudiante en una asignatura")
    @GetMapping("/estudiante/{estudianteId}/asignatura/{asignaturaId}")
    public ResponseEntity<List<Asistencia>> porEstudianteYAsignatura(
            @PathVariable String estudianteId,
            @PathVariable String asignaturaId) {
        return ResponseEntity.ok(
                asistenciaService.listarPorEstudianteYAsignatura(estudianteId, asignaturaId));
    }

    /**
     * Retorna el resumen de asistencia general del estudiante con porcentaje
     * y estado según la regla del 85% del Decreto MINEDUC 511.
     *
     * <p>Respuesta incluye:
     * <ul>
     *   <li>porcentaje de asistencia calculado</li>
     *   <li>estado: {@code REGULAR} (≥85%), {@code EN_RIESGO} (75-84.9%), {@code CRITICO} (&lt;75%)</li>
     *   <li>flag {@code repruebaPorInasistencia} si el porcentaje es menor al 75%</li>
     * </ul>
     */
    @Operation(
        summary = "Resumen de asistencia con % y estado MINEDUC (REGULAR/EN_RIESGO/CRITICO)",
        description = "Calcula el porcentaje de asistencia aplicando la regla del 85% del Decreto MINEDUC 511. "
                    + "Retorna estado REGULAR (≥85%), EN_RIESGO (75-84.9%) o CRITICO (<75%)."
    )
    @GetMapping("/estudiante/{estudianteId}/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCENTE', 'ESTUDIANTE', 'APODERADO')")
    public ResponseEntity<ResumenAsistenciaDTO> resumenGeneral(@PathVariable String estudianteId) {
        return ResponseEntity.ok(asistenciaService.calcularResumenGeneral(estudianteId));
    }

    /**
     * Resumen de asistencia de un estudiante filtrado por una asignatura específica.
     * Útil para el boletín por ramo.
     */
    @Operation(
        summary = "Resumen de asistencia por asignatura con % y estado MINEDUC",
        description = "Igual que el resumen general pero filtrado por asignatura. Útil para boletín por ramo."
    )
    @GetMapping("/estudiante/{estudianteId}/asignatura/{asignaturaId}/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCENTE', 'ESTUDIANTE', 'APODERADO')")
    public ResponseEntity<ResumenAsistenciaDTO> resumenPorAsignatura(
            @PathVariable String estudianteId,
            @PathVariable String asignaturaId) {
        return ResponseEntity.ok(
                asistenciaService.calcularResumenPorAsignatura(estudianteId, asignaturaId));
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Operation(summary = "Listar todos los registros de asistencia (ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Asistencia>> listarTodas() {
        return ResponseEntity.ok(asistenciaService.listarTodas());
    }

    @Operation(summary = "Obtener registro por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Asistencia> obtenerPorId(@PathVariable String id) {
        return ResponseEntity.ok(asistenciaService.obtenerPorId(id));
    }

    @Operation(summary = "Eliminar registro de asistencia (ADMIN)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        asistenciaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}


