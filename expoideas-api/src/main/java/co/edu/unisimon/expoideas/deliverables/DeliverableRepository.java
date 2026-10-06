package co.edu.unisimon.expoideas.deliverables;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliverableRepository extends JpaRepository<Deliverable, Integer> {

    @EntityGraph(attributePaths = {"type", "file", "uploadedBy"})
    List<Deliverable> findByProjectIdOrderByUploadedAtAsc(Integer projectId);

    int countByProjectIdAndTypeId(Integer projectId, Integer typeId);

    /** Si ya hay archivos subidos para ese entregable: entonces no se puede borrar. */
    boolean existsByTypeId(Integer typeId);

    @EntityGraph(
            attributePaths = {"project", "project.members", "project.members.user", "project.teacher", "file", "type"})
    Optional<Deliverable> findWithProjectById(Integer id);

    /** Cuántos entregables obligatorios distintos tiene con archivos cada proyecto. */
    @Query("""
            select d.project.id as projectId, count(distinct d.type.id) as total
            from Deliverable d
            where d.project.id in :projectIds and d.type.required = true
            group by d.project.id
            """)
    List<DeliveredByProject> countRequiredDelivered(@Param("projectIds") List<Integer> projectIds);

    /** Fila de {@link #countRequiredDelivered(List)}. */
    interface DeliveredByProject {
        Integer getProjectId();

        int getTotal();
    }

    /** Para autorizar la descarga: de qué proyecto es el archivo. */
    @EntityGraph(attributePaths = {"project", "project.members", "project.members.user", "project.teacher"})
    Optional<Deliverable> findByFileId(Integer fileId);
}
