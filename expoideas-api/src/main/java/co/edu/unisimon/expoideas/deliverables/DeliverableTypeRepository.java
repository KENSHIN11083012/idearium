package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.editions.Track;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeliverableTypeRepository extends JpaRepository<DeliverableType, Integer> {

    /** Los entregables que pide una cátedra, en el orden configurado, con su plantilla. */
    @EntityGraph(attributePaths = {"template", "prototypeType"})
    List<DeliverableType> findByEditionIdAndTrackOrderBySortOrderAscIdAsc(Integer editionId, Track track);

    /** Para autorizar la descarga: si ese archivo es la plantilla de algún entregable. */
    boolean existsByTemplateId(Integer fileId);

    /** Si esa cuenta subió alguna plantilla vigente: entonces no se puede eliminar. */
    boolean existsByTemplateOwnerId(Integer ownerId);

    /**
     * Cuántos entregables obligatorios pide cada cátedra de cada edición, separando
     * los generales (tipo de prototipo nulo) de los de cada tipo.
     */
    @Query("""
            select t.edition.id as editionId, t.track as track, t.prototypeType.id as prototypeTypeId, count(t) as total
            from DeliverableType t where t.required = true
            group by t.edition.id, t.track, t.prototypeType.id
            """)
    List<RequiredByTrack> countRequiredByTrack();

    /** Fila de {@link #countRequiredByTrack()}. */
    interface RequiredByTrack {
        Integer getEditionId();

        Track getTrack();

        /** Null para los entregables que se piden a todos. */
        Integer getPrototypeTypeId();

        int getTotal();
    }
}
