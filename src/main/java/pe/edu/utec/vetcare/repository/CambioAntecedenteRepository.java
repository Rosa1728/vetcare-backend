package pe.edu.utec.vetcare.repository;
import pe.edu.utec.vetcare.entity.CambioAntecedenteEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
/** Solo inserción y consulta; sin operaciones ordinarias de actualización o borrado. */
public interface CambioAntecedenteRepository extends Repository<CambioAntecedenteEntity,Long> {
 CambioAntecedenteEntity save(CambioAntecedenteEntity cambio);
 @Query("select c from CambioAntecedenteEntity c where c.historia.idHistoria=:historia and (:seccion is null or c.seccion=:seccion) order by c.revision desc, c.id desc")
 Page<CambioAntecedenteEntity> consultar(@Param("historia") Long historia,@Param("seccion") String seccion,Pageable pagina);
}
