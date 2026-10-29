package pe.edu.utec.vetcare.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.utec.vetcare.entity.CitaEntity;
import pe.edu.utec.vetcare.entity.MascotaEntity;

/** Reglas de pertenencia basadas únicamente en el JWT autenticado. */
public final class AccesoClinico {
    private AccesoClinico() {}

    private static Authentication auth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere autenticación");
        }
        return auth;
    }

    public static boolean es(String rol) {
        return auth().getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + rol).equals(a.getAuthority()));
    }

    public static String correo() { return auth().getName(); }

    public static boolean esPropietario(MascotaEntity mascota) {
        return mascota.getPropietario().getUsuario().getCorreo().equals(correo());
    }

    public static boolean esVeterinario(CitaEntity cita) {
        return cita.getVeterinario().getUsuario().getCorreo().equals(correo());
    }

    public static boolean esPropietario(CitaEntity cita) {
        return esPropietario(cita.getMascota());
    }

    public static void soloAdmin() {
        if (!es("ADMIN")) throw new AccessDeniedException("Operación exclusiva de ADMIN");
    }
}
