package cl.colegio.autenticacion.config;

import cl.colegio.autenticacion.entity.Rol;
import cl.colegio.autenticacion.entity.Usuario;
import cl.colegio.autenticacion.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga datos semilla en Firestore al arrancar la aplicación.
 * Solo inserta usuarios que aún no existan (compara por RUT).
 * Contraseña por defecto: Admin1234!
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("DataSeeder: verificando usuarios semilla...");
        String encoded = passwordEncoder.encode("Admin1234!");

        List<Object[]> semilla = List.of(
            // { rut, nombre, apellido, email, rol }
            new Object[]{"11111111-1", "Administrador", "Sistema",   "admin@colegio.cl",                    Rol.ADMIN},
            new Object[]{"21843711-7", "Isaac", "Usuario", "isaac@colegio.cl", Rol.ADMIN},
            // ── Docentes ──────────────────────────────────────────────────
            new Object[]{"22222222-2", "María",     "González",  "mgonzalez@colegio.cl",                   Rol.DOCENTE},
            new Object[]{"22222222-3", "Jorge",     "Muñoz",     "jmunoz@colegio.cl",                      Rol.DOCENTE},
            new Object[]{"22222222-4", "Claudia",   "Vega",      "cvega@colegio.cl",                       Rol.DOCENTE},
            new Object[]{"22222222-5", "Andrés",    "Fuentes",   "afuentes@colegio.cl",                    Rol.DOCENTE},
            new Object[]{"22222222-6", "Patricia",  "Soto",      "psoto@colegio.cl",                       Rol.DOCENTE},
            new Object[]{"22222222-7", "Felipe",    "Morales",   "fmorales@colegio.cl",                    Rol.DOCENTE},
            new Object[]{"22222222-8", "Valentina", "Castro",    "vcastro@colegio.cl",                     Rol.DOCENTE},
            new Object[]{"22222222-9", "Rodrigo",   "Navarro",   "rnavarro@colegio.cl",                    Rol.DOCENTE},
            new Object[]{"22222222-0", "Isabel",    "Ramírez",   "iramirez@colegio.cl",                    Rol.DOCENTE},
            new Object[]{"22222222-K", "Luis",      "Herrera",   "lherrera@colegio.cl",                    Rol.DOCENTE},
            // ── Apoderado ─────────────────────────────────────────────────
            new Object[]{"33333333-3", "Carlos",    "Rodríguez", "crodriguez@gmail.com",                   Rol.APODERADO},
            // ── Estudiante ────────────────────────────────────────────────
            new Object[]{"44444444-4", "Sofía",     "Rodríguez", "sofia.rodriguez@alumno.colegio.cl",      Rol.ESTUDIANTE}
        );

        int creados = 0;
        for (Object[] d : semilla) {
            String rut = (String) d[0];
            // Solo insertar si no existe ya ese RUT
            if (usuarioRepository.findByRut(rut).isEmpty()) {
                usuarioRepository.save(Usuario.builder()
                        .rut(rut)
                        .nombre((String) d[1])
                        .apellido((String) d[2])
                        .email((String) d[3])
                        .password(encoded)
                        .rol((Rol) d[4])
                        .activo(true)
                        .build());
                log.info("DataSeeder: usuario creado → {} {}", d[1], d[2]);
                creados++;
            }
        }

        if (creados > 0) {
            log.info("DataSeeder: {} usuario(s) nuevo(s) insertado(s).", creados);
        } else {
            log.info("DataSeeder: todos los usuarios semilla ya existen, nada que insertar.");
        }
    }
}

