package co.edu.unisimon.expoideas.files;

import co.edu.unisimon.expoideas.users.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<StoredFile, Integer> {

    /** Con el propietario cargado: la descarga de archivos privados lo necesita para decidir. */
    @EntityGraph(attributePaths = "owner")
    Optional<StoredFile> findByUuid(String uuid);

    List<StoredFile> findByOwner(User owner);
}
