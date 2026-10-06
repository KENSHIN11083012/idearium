package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.files.StoredFile;
import java.time.LocalDate;

/**
 * Un entregable configurado, tal como sale por la API.
 *
 * @param templateFileId identificador público de la plantilla (GET /api/v1/files/{id}), o null
 * @param closesOn       cierre propio, o null si vale el de la edición
 */
public record DeliverableTypeResponse(
        Integer id,
        Integer editionId,
        Track track,
        String name,
        String description,
        DeliverableKind kind,
        boolean required,
        int maxFiles,
        int sortOrder,
        String templateFileId,
        String templateFileName,
        LocalDate closesOn,
        Integer prototypeTypeId,
        String prototypeType) {

    public static DeliverableTypeResponse from(DeliverableType type) {
        StoredFile template = type.getTemplate();
        return new DeliverableTypeResponse(
                type.getId(),
                type.getEdition().getId(),
                type.getTrack(),
                type.getName(),
                type.getDescription(),
                type.getKind(),
                type.isRequired(),
                type.getMaxFiles(),
                type.getSortOrder(),
                template != null ? template.getUuid() : null,
                template != null ? template.getOriginalName() : null,
                type.getClosesOn(),
                type.getPrototypeType() != null ? type.getPrototypeType().getId() : null,
                type.getPrototypeType() != null ? type.getPrototypeType().getName() : null);
    }
}
