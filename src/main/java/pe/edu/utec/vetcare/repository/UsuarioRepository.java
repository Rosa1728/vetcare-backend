package pe.edu.utec.vetcare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.vetcare.entity.UsuarioEntity;

import java.util.Optional;

public interface UsuarioRepository
        extends JpaRepository<UsuarioEntity, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="rol")
    Optional<UsuarioEntity> findConRolByIdUsuario(Long idUsuario);

    long countByRol_Nombre(String nombre);

    Optional<UsuarioEntity> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdUsuarioNot(String correo, Long idUsuario);

    boolean existsByNroDocumentoAndIdUsuarioNot(String nroDocumento, Long idUsuario);

    boolean existsByNroDocumento(String nroDocumento);
}