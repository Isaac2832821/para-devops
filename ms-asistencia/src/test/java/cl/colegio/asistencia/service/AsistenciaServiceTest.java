package cl.colegio.asistencia.service;

import cl.colegio.asistencia.dto.AsistenciaRequest;
import cl.colegio.asistencia.dto.ResumenAsistenciaDTO;
import cl.colegio.asistencia.entity.Asistencia;
import cl.colegio.asistencia.entity.EstadoAsistencia;
import cl.colegio.asistencia.repository.AsistenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsistenciaService — Tests unitarios")
class AsistenciaServiceTest {

    @Mock private AsistenciaRepository asistenciaRepository;

    @InjectMocks private AsistenciaService asistenciaService;

    private Asistencia asistenciaExistente;
    private AsistenciaRequest requestValido;

    @BeforeEach
    void setUp() {
        asistenciaExistente = Asistencia.builder()
                .id("asis-001")
                .estudianteId("12345678-9")
                .estudianteNombre("Juan Pérez")
                .docenteId("98765432-1")
                .asignaturaId("MAT-101")
                .asignaturaNombre("Matemáticas")
                .fecha("2026-05-13")
                .estado(EstadoAsistencia.PRESENTE)
                .observacion(null)
                .build();

        requestValido = new AsistenciaRequest(
                "12345678-9", "Juan Pérez",
                "MAT-101", "Matemáticas",
                "2026-05-13", EstadoAsistencia.PRESENTE, null
        );
    }

    // ── REGISTRAR ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Registrar asistencia")
    class RegistrarTests {

        @Test
        @DisplayName("Registro exitoso crea asistencia con datos del request y docenteId del JWT")
        void registroExitoso() {
            when(asistenciaRepository.save(any(Asistencia.class)))
                    .thenAnswer(inv -> {
                        Asistencia a = inv.getArgument(0);
                        a.setId("new-id-123");
                        return a;
                    });

            Asistencia resultado = asistenciaService.registrar(requestValido, "98765432-1");

            assertThat(resultado.getEstudianteId()).isEqualTo("12345678-9");
            assertThat(resultado.getDocenteId()).isEqualTo("98765432-1");
            assertThat(resultado.getEstado()).isEqualTo(EstadoAsistencia.PRESENTE);
            assertThat(resultado.getAsignaturaNombre()).isEqualTo("Matemáticas");
            verify(asistenciaRepository).save(any(Asistencia.class));
        }

        @Test
        @DisplayName("Registro con estado AUSENTE se guarda correctamente")
        void registroAusente() {
            var requestAusente = new AsistenciaRequest(
                    "12345678-9", "Juan Pérez",
                    "MAT-101", "Matemáticas",
                    "2026-05-13", EstadoAsistencia.AUSENTE, "No asistió"
            );
            when(asistenciaRepository.save(any(Asistencia.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Asistencia resultado = asistenciaService.registrar(requestAusente, "98765432-1");

            assertThat(resultado.getEstado()).isEqualTo(EstadoAsistencia.AUSENTE);
            assertThat(resultado.getObservacion()).isEqualTo("No asistió");
        }
    }

    // ── ACTUALIZAR ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Actualizar asistencia")
    class ActualizarTests {

        @Test
        @DisplayName("Actualización exitosa modifica estado y observación")
        void actualizarExitoso() {
            var requestActualizado = new AsistenciaRequest(
                    "12345678-9", "Juan Pérez",
                    "MAT-101", "Matemáticas",
                    "2026-05-13", EstadoAsistencia.JUSTIFICADO, "Certificado médico"
            );
            when(asistenciaRepository.findById("asis-001")).thenReturn(Optional.of(asistenciaExistente));
            when(asistenciaRepository.save(any(Asistencia.class))).thenAnswer(inv -> inv.getArgument(0));

            Asistencia resultado = asistenciaService.actualizar("asis-001", requestActualizado, "98765432-1");

            assertThat(resultado.getEstado()).isEqualTo(EstadoAsistencia.JUSTIFICADO);
            assertThat(resultado.getObservacion()).isEqualTo("Certificado médico");
            verify(asistenciaRepository).save(asistenciaExistente);
        }

        @Test
        @DisplayName("Actualizar registro inexistente lanza NoSuchElementException")
        void actualizarInexistente() {
            when(asistenciaRepository.findById("no-existe")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> asistenciaService.actualizar("no-existe", requestValido, "98765432-1"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("no encontrado");
        }
    }

    // ── OBTENER POR ID ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Obtener por ID")
    class ObtenerPorIdTests {

        @Test
        @DisplayName("Obtener registro existente retorna la asistencia")
        void obtenerExistente() {
            when(asistenciaRepository.findById("asis-001")).thenReturn(Optional.of(asistenciaExistente));

            Asistencia resultado = asistenciaService.obtenerPorId("asis-001");

            assertThat(resultado.getId()).isEqualTo("asis-001");
            assertThat(resultado.getEstudianteNombre()).isEqualTo("Juan Pérez");
        }

        @Test
        @DisplayName("Obtener registro inexistente lanza NoSuchElementException")
        void obtenerInexistente() {
            when(asistenciaRepository.findById("no-existe")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> asistenciaService.obtenerPorId("no-existe"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    // ── LISTAR ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Listar asistencias")
    class ListarTests {

        @Test
        @DisplayName("Listar todas retorna lista completa")
        void listarTodas() {
            when(asistenciaRepository.findAll()).thenReturn(List.of(asistenciaExistente));

            List<Asistencia> resultado = asistenciaService.listarTodas();

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getEstudianteId()).isEqualTo("12345678-9");
        }

        @Test
        @DisplayName("Listar por docente filtra correctamente")
        void listarPorDocente() {
            when(asistenciaRepository.findByDocenteId("98765432-1")).thenReturn(List.of(asistenciaExistente));

            List<Asistencia> resultado = asistenciaService.listarPorDocente("98765432-1");

            assertThat(resultado).hasSize(1);
            verify(asistenciaRepository).findByDocenteId("98765432-1");
        }

        @Test
        @DisplayName("Listar por estudiante filtra correctamente")
        void listarPorEstudiante() {
            when(asistenciaRepository.findByEstudianteId("12345678-9")).thenReturn(List.of(asistenciaExistente));

            List<Asistencia> resultado = asistenciaService.listarPorEstudiante("12345678-9");

            assertThat(resultado).hasSize(1);
            verify(asistenciaRepository).findByEstudianteId("12345678-9");
        }

        @Test
        @DisplayName("Listar por fecha retorna registros del día")
        void listarPorFecha() {
            when(asistenciaRepository.findByFecha("2026-05-13")).thenReturn(List.of(asistenciaExistente));

            List<Asistencia> resultado = asistenciaService.listarPorFecha("2026-05-13");

            assertThat(resultado).hasSize(1);
            verify(asistenciaRepository).findByFecha("2026-05-13");
        }

        @Test
        @DisplayName("Listar por estudiante y asignatura retorna historial específico")
        void listarPorEstudianteYAsignatura() {
            when(asistenciaRepository.findByEstudianteIdAndAsignaturaId("12345678-9", "MAT-101"))
                    .thenReturn(List.of(asistenciaExistente));

            List<Asistencia> resultado = asistenciaService.listarPorEstudianteYAsignatura("12345678-9", "MAT-101");

            assertThat(resultado).hasSize(1);
            verify(asistenciaRepository).findByEstudianteIdAndAsignaturaId("12345678-9", "MAT-101");
        }
    }

    // ── ELIMINAR ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eliminar asistencia")
    class EliminarTests {

        @Test
        @DisplayName("Eliminar por ID invoca al repositorio correctamente")
        void eliminarExitoso() {
            doNothing().when(asistenciaRepository).deleteById("asis-001");

            asistenciaService.eliminar("asis-001");

            verify(asistenciaRepository).deleteById("asis-001");
        }
    }

    // ── RESUMEN ASISTENCIA (Regla 85% MINEDUC) ───────────────────────────

    @Nested
    @DisplayName("Resumen de asistencia — Decreto MINEDUC 511")
    class ResumenAsistenciaTests {

        @Test
        @DisplayName("100% asistencia retorna estado REGULAR y no reprueba")
        void resumenConAsistenciaTotal() {
            var registros = List.of(
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE)
            );
            when(asistenciaRepository.findByEstudianteId("12345678-9")).thenReturn(registros);

            var resumen = asistenciaService.calcularResumenGeneral("12345678-9");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(100.0);
            assertThat(resumen.estadoResumen()).isEqualTo(ResumenAsistenciaDTO.EstadoAsistenciaResumen.REGULAR);
            assertThat(resumen.repruebaPorInasistencia()).isFalse();
        }

        @Test
        @DisplayName("80% asistencia retorna estado EN_RIESGO pero no reprueba")
        void resumenEnRiesgo() {
            // 4 presentes, 1 ausente = 80%
            var registros = List.of(
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.AUSENTE)
            );
            when(asistenciaRepository.findByEstudianteId("12345678-9")).thenReturn(registros);

            var resumen = asistenciaService.calcularResumenGeneral("12345678-9");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(80.0);
            assertThat(resumen.estadoResumen()).isEqualTo(ResumenAsistenciaDTO.EstadoAsistenciaResumen.EN_RIESGO);
            assertThat(resumen.repruebaPorInasistencia()).isFalse();
        }

        @Test
        @DisplayName("60% asistencia retorna estado CRITICO y reprueba por inasistencia")
        void resumenCriticoReprueba() {
            // 3 presentes, 2 ausentes = 60%
            var registros = List.of(
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.AUSENTE),
                    registro(EstadoAsistencia.AUSENTE)
            );
            when(asistenciaRepository.findByEstudianteId("12345678-9")).thenReturn(registros);

            var resumen = asistenciaService.calcularResumenGeneral("12345678-9");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(60.0);
            assertThat(resumen.estadoResumen()).isEqualTo(ResumenAsistenciaDTO.EstadoAsistenciaResumen.CRITICO);
            assertThat(resumen.repruebaPorInasistencia()).isTrue();
        }

        @Test
        @DisplayName("TARDANZA y JUSTIFICADO cuentan como asistencia efectiva")
        void tardanzaYJustificadoCuentanComoAsistencia() {
            // 1 presente, 1 tardanza, 1 justificado = 100% asistencia efectiva
            var registros = List.of(
                    registro(EstadoAsistencia.PRESENTE),
                    registro(EstadoAsistencia.TARDANZA),
                    registro(EstadoAsistencia.JUSTIFICADO)
            );
            when(asistenciaRepository.findByEstudianteId("12345678-9")).thenReturn(registros);

            var resumen = asistenciaService.calcularResumenGeneral("12345678-9");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(100.0);
            assertThat(resumen.clasesAsistidas()).isEqualTo(3);
            assertThat(resumen.repruebaPorInasistencia()).isFalse();
        }

        @Test
        @DisplayName("Sin registros retorna porcentaje 0 sin excepción")
        void sinRegistrosRetornaCero() {
            when(asistenciaRepository.findByEstudianteId("99999999-9")).thenReturn(List.of());

            var resumen = asistenciaService.calcularResumenGeneral("99999999-9");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(0.0);
            assertThat(resumen.totalClases()).isEqualTo(0);
        }

        @Test
        @DisplayName("Resumen por asignatura filtra solo esa asignatura")
        void resumenPorAsignatura() {
            var registros = List.of(
                    registro(EstadoAsistencia.PRESENTE, "mat-001", "Matemáticas"),
                    registro(EstadoAsistencia.AUSENTE, "mat-001", "Matemáticas")
            );
            when(asistenciaRepository.findByEstudianteIdAndAsignaturaId("12345678-9", "mat-001"))
                    .thenReturn(registros);

            var resumen = asistenciaService.calcularResumenPorAsignatura("12345678-9", "mat-001");

            assertThat(resumen.porcentajeAsistencia()).isEqualTo(50.0);
            assertThat(resumen.asignaturaNombre()).isEqualTo("Matemáticas");
            assertThat(resumen.totalClases()).isEqualTo(2);
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private Asistencia registro(EstadoAsistencia estado) {
            return registro(estado, "MAT-101", "Matemáticas");
        }

        private Asistencia registro(EstadoAsistencia estado, String asignaturaId, String asignaturaNombre) {
            return Asistencia.builder()
                    .estudianteId("12345678-9")
                    .estudianteNombre("Juan Pérez")
                    .asignaturaId(asignaturaId)
                    .asignaturaNombre(asignaturaNombre)
                    .fecha("2024-06-01")
                    .estado(estado)
                    .build();
        }
    }
}
