package pe.edu.utec.vetcare.bootstrap;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utec.vetcare.entity.UsuarioEntity;
import pe.edu.utec.vetcare.repository.RolRepository;
import pe.edu.utec.vetcare.repository.UsuarioRepository;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "VETCARE_BOOTSTRAP_ADMIN_ENABLED", havingValue = "true")
public class AdminInicialBootstrap implements ApplicationRunner {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            var rolAdmin = roles.findByNombre("ADMIN")
                    .orElseThrow(() -> new IllegalStateException("No existe el rol ADMIN"));

            // La inicializacion nunca crea un segundo administrador.
            if (usuarios.findAll().stream().anyMatch(u ->
                    u.getRol().getIdRol().equals(rolAdmin.getIdRol()))) {
                return;
            }

            String nombres = obligatorio("VETCARE_ADMIN_NOMBRES");
            String apellidos = obligatorio("VETCARE_ADMIN_APELLIDOS");
            String tipoDocumento = obligatorio("VETCARE_ADMIN_TIPO_DOCUMENTO");
            String documento = obligatorio("VETCARE_ADMIN_NRO_DOCUMENTO");
            String correo = obligatorio("VETCARE_ADMIN_CORREO").toLowerCase(Locale.ROOT);
            String contrasena = obligatorio("VETCARE_ADMIN_PASSWORD");
            if (contrasena.length() < 12) {
                throw new IllegalStateException("La contrasena inicial debe tener al menos 12 caracteres");
            }
            if (usuarios.existsByCorreo(correo) || usuarios.existsByNroDocumento(documento)) {
                throw new IllegalStateException("Ya existe una cuenta con el correo o documento del ADMIN inicial");
            }

            UsuarioEntity admin = new UsuarioEntity();
            admin.setNombres(nombres);
            admin.setApellidos(apellidos);
            admin.setTipoDocumento(tipoDocumento);
            admin.setNroDocumento(documento);
            admin.setCorreo(correo);
            admin.setContrasena(passwordEncoder.encode(contrasena));
            admin.setRol(rolAdmin);
            usuarios.save(admin);
        });
    }

    private String obligatorio(String clave) {
        String valor = environment.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta configurar la variable de entorno " + clave);
        }
        return valor.trim();
    }
}
