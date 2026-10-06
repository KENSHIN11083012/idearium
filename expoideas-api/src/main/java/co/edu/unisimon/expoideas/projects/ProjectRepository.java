package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.editions.Track;
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

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    /**
     * Con la edición, el sector, el docente y el equipo ya cargados. La
     * configuración de la cátedra queda perezosa: traerla en la misma consulta
     * serían dos colecciones a la vez, que Hibernate no permite.
     */
    @EntityGraph(attributePaths = {"edition", "sector", "prototypeType", "teacher", "members", "members.user"})
    Optional<Project> findWithTeamById(Integer id);

    /**
     * El proyecto con su fila bloqueada hasta el final de la transacción: quien
     * llegue después espera aquí. Ver {@link ProjectPolicy#lock(Integer)}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Project> findLockedById(Integer id);

    /** Los proyectos en los que esa cuenta está en el equipo, invitada o aceptada. */
    @EntityGraph(attributePaths = {"edition", "sector", "prototypeType", "teacher", "members", "members.user"})
    @Query("select distinct p from Project p join p.members m where m.user.id = :userId order by p.createdAt desc")
    List<Project> findAllOfMember(@Param("userId") Integer userId);

    /**
     * Los ids de una página de los proyectos que cumplen los filtros. Va en dos pasos
     * (los ids de la página y después los proyectos con su equipo) porque paginar una
     * consulta que trae una colección obliga a Hibernate a traerlo todo y paginar en
     * memoria.
     */
    @Query("""
            select p.id from Project p
            where (:editionId is null or p.edition.id = :editionId)
              and (:track is null or p.track = :track)
              and (:teacherId is null or p.teacher.id = :teacherId)
              and (:sectorId is null or p.sector.id = :sectorId)
              and (:result is null or p.result = :result)
              and (:search is null or lower(p.title) like lower(concat('%', :search, '%')))
            """)
    Page<Integer> searchIds(
            @Param("editionId") Integer editionId,
            @Param("track") Track track,
            @Param("teacherId") Integer teacherId,
            @Param("sectorId") Integer sectorId,
            @Param("result") ProjectResult result,
            @Param("search") String search,
            Pageable pageable);

    /** Esos proyectos con lo que el listado muestra de cada uno; el orden lo pone quien llama. */
    @EntityGraph(attributePaths = {"edition", "sector", "prototypeType", "teacher", "members", "members.user"})
    List<Project> findWithTeamByIdIn(Collection<Integer> ids);

    /**
     * Todos los proyectos que cumplen los filtros, de la inscripción más reciente a
     * la más antigua: para exportar. Un filtro en null no filtra.
     */
    @EntityGraph(attributePaths = {"edition", "sector", "prototypeType", "teacher", "members", "members.user"})
    @Query("""
            select distinct p from Project p
            where (:editionId is null or p.edition.id = :editionId)
              and (:track is null or p.track = :track)
              and (:teacherId is null or p.teacher.id = :teacherId)
              and (:sectorId is null or p.sector.id = :sectorId)
              and (:result is null or p.result = :result)
              and (:search is null or lower(p.title) like lower(concat('%', :search, '%')))
            order by p.createdAt desc
            """)
    List<Project> search(
            @Param("editionId") Integer editionId,
            @Param("track") Track track,
            @Param("teacherId") Integer teacherId,
            @Param("sectorId") Integer sectorId,
            @Param("result") ProjectResult result,
            @Param("search") String search);

    /** Si esa cuenta es el docente de algún proyecto. */
    boolean existsByTeacherId(Integer teacherId);

    /** Si esa cuenta está en el equipo de algún proyecto, invitada o aceptada. */
    @Query("select count(m) > 0 from ProjectMember m where m.user.id = :userId")
    boolean isOnSomeTeam(@Param("userId") Integer userId);

    /**
     * Si esa cuenta ya está en un proyecto de esa cátedra: cada estudiante
     * inscribe uno solo por cátedra en cada edición. Las invitaciones sin
     * responder no cuentan, porque todavía puede rechazarlas.
     */
    @Query("""
            select count(p) > 0 from Project p join p.members m
            where p.edition.id = :editionId and p.track = :track
              and m.user.id = :userId and m.status = co.edu.unisimon.expoideas.projects.MembershipStatus.ACCEPTED
            """)
    boolean isAlreadyOnATeam(
            @Param("editionId") Integer editionId, @Param("track") Track track, @Param("userId") Integer userId);

    /** Si esa cuenta está aceptada en un proyecto de la otra cátedra de la misma edición. */
    @Query("""
            select count(p) > 0 from Project p join p.members m
            where p.edition.id = :editionId and p.track <> :track
              and m.user.id = :userId and m.status = co.edu.unisimon.expoideas.projects.MembershipStatus.ACCEPTED
            """)
    boolean isOnATeamOfAnotherTrack(
            @Param("editionId") Integer editionId, @Param("track") Track track, @Param("userId") Integer userId);
}
