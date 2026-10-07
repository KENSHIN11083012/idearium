package co.edu.unisimon.expoideas.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RosterRepository extends JpaRepository<RosterEntry, Integer> {

    /** Los correos del listado van en minúsculas; quien pregunta debe pasar el suyo igual. */
    Optional<RosterEntry> findByEmail(String email);

    /** Quita a esa persona del listado: su correo no debe quedar guardado tras anonimizar su cuenta. */
    void deleteByEmail(String email);
}
