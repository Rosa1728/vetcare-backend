package pe.edu.utec.vetcare.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecretKeySpec jwtKey(@Value("${JWT_SECRET}") String encodedSecret) {
        byte[] key;
        try { key = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET debe estar codificado en Base64", ex); }
        if (key.length < 32) throw new IllegalStateException("JWT_SECRET debe contener al menos 32 bytes aleatorios");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec jwtKey, pe.edu.utec.vetcare.repository.UsuarioRepository usuarios, CredencialJwt credencial) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        org.springframework.security.oauth2.core.OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> vigente = jwt -> {
            Object id = jwt.getClaim("idUsuario");
            var usuario = id instanceof Number n ? usuarios.findConRolByIdUsuario(n.longValue()).orElse(null) : null;
            boolean valido = usuario != null && usuario.getCorreo().equals(jwt.getSubject())
                    && usuario.getRol()!=null && usuario.getRol().getNombre().equals(jwt.getClaimAsString("rol"))
                    && credencial.coincide(usuario, jwt.getClaimAsString("credencial"));
            return valido ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "La sesión ya no es válida. Inicia sesión nuevamente.", null));
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                org.springframework.security.oauth2.jwt.JwtValidators.createDefault(), vigente));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("rol");
            if (role == null || !List.of("ADMIN", "VETERINARIO", "PROPIETARIO").contains(role)) return List.of();
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${VETCARE_CORS_ORIGINS:http://localhost:5173}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.cors(cors -> {}).csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro", "/api/auth/recuperar-contrasena", "/api/auth/restablecer-contrasena").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/recordatorios/generar").hasRole("ADMIN")
                .requestMatchers("/api/recordatorios/**").hasRole("PROPIETARIO")
                .requestMatchers("/api/usuarios/me", "/api/usuarios/me/**").authenticated()
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers("/api/propietarios/**").hasAnyRole("ADMIN", "PROPIETARIO")
                .requestMatchers(HttpMethod.GET, "/api/veterinarios/me").hasRole("VETERINARIO")
                .requestMatchers(HttpMethod.PATCH, "/api/veterinarios/me").hasRole("VETERINARIO")
                .requestMatchers(HttpMethod.GET, "/api/veterinarios/**").authenticated()
                .requestMatchers("/api/veterinarios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/vacunas", "/api/vacunas/").hasRole("VETERINARIO")
                .requestMatchers(HttpMethod.GET, "/api/vacunas/**").hasAnyRole("ADMIN", "VETERINARIO", "PROPIETARIO")
                .requestMatchers("/api/mascotas/**", "/api/historias-clinicas/**", "/api/citas/**")
                    .hasAnyRole("ADMIN", "VETERINARIO", "PROPIETARIO")
                .requestMatchers(HttpMethod.GET, "/api/atenciones/**").hasAnyRole("ADMIN", "VETERINARIO", "PROPIETARIO")
                .requestMatchers("/api/atenciones/**").hasAnyRole("ADMIN", "VETERINARIO")
                .requestMatchers(HttpMethod.GET, "/api/publicaciones").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/publicaciones/*/foto").permitAll()
                .requestMatchers("/api/publicaciones/**").hasRole("PROPIETARIO")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
