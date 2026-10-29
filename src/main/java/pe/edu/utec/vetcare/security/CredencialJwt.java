package pe.edu.utec.vetcare.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import pe.edu.utec.vetcare.entity.UsuarioEntity;

/** Huella HMAC: nunca expone el hash BCrypt en los claims. */
@Component
public class CredencialJwt {
    private final SecretKeySpec key;
    public CredencialJwt(SecretKeySpec key) { this.key = key; }
    public String huella(UsuarioEntity usuario) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String value = "vetcare:credencial:v1:" + usuario.getIdUsuario() + ":" + usuario.getContrasena();
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("No se pudo validar la sesión", ex); }
    }
    public boolean coincide(UsuarioEntity usuario, String claim) {
        return claim != null && MessageDigest.isEqual(huella(usuario).getBytes(StandardCharsets.US_ASCII), claim.getBytes(StandardCharsets.US_ASCII));
    }
}
