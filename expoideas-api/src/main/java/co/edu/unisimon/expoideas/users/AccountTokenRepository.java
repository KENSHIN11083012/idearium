package co.edu.unisimon.expoideas.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Integer> {

    /** El enlace con ese hash y ese propósito, con su cuenta ya cargada. */
    @EntityGraph(attributePaths = "user")
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    /** El último enlace de ese tipo que se le envió a la cuenta. */
    Optional<AccountToken> findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(
            Integer userId, AccountTokenPurpose purpose);

    /** Quita los enlaces anteriores de ese tipo: al pedir uno nuevo, los viejos dejan de servir. */
    void deleteByUserIdAndPurpose(Integer userId, AccountTokenPurpose purpose);
}
