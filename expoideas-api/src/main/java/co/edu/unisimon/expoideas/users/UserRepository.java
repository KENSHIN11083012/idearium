package co.edu.unisimon.expoideas.users;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    Optional<User> findByEmail(String email);

    /** La cuenta con su fila bloqueada hasta el final de la transacción, para contar sin pisarse. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedByEmail(String email);

    @EntityGraph(User.WITH_PROFILE)
    Optional<User> findWithProfileById(Integer id);

    @EntityGraph(User.WITH_PROFILE)
    Optional<User> findWithProfileByEmail(String email);

    @EntityGraph(User.WITH_PROFILE)
    List<User> findAllWithProfileBy();

    /**
     * Las cuentas que cumplen los filtros, por páginas. Un filtro en null no filtra. La
     * búsqueda mira el nombre y el correo; la base no distingue mayúsculas ni tildes.
     *
     * @param pendingOnly solo las que esperan que la gestión confirme el rol del listado
     */
    @EntityGraph(User.WITH_PROFILE)
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:pendingOnly = false or u.pendingRole is not null)
              and (:search is null
                   or concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''), ' ', u.email)
                      like concat('%', :search, '%'))
            """)
    Page<User> search(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("pendingOnly") boolean pendingOnly,
            Pageable pageable);

    /** Las cuentas de ese rol, con su perfil: el filtro lo hace la base, no la memoria. */
    @EntityGraph(User.WITH_PROFILE)
    List<User> findAllWithProfileByRole(Role role);

    /** De esos correos, los que ya tienen cuenta (la columna no distingue mayúsculas). */
    @Query("select u.email from User u where u.email in :emails")
    List<String> findEmailsIn(@Param("emails") Collection<String> emails);
}
