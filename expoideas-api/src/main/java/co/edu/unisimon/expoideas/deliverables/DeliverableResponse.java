package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.files.StoredFile;
import java.time.LocalDateTime;

/**
 * Un archivo subido o un enlace registrado. Con archivo, {@code fileId} es el
 * identificador público con el que se descarga (GET /api/v1/files/{fileId}) y
 * {@code url} es null; con enlace, al revés.
 */
public record DeliverableResponse(
        Integer id,
        String fileId,
        String fileName,
        String contentType,
        Integer sizeBytes,
        String url,
        String uploadedBy,
        LocalDateTime uploadedAt) {

    public static DeliverableResponse from(Deliverable deliverable) {
        StoredFile file = deliverable.getFile();
        return new DeliverableResponse(
                deliverable.getId(),
                file != null ? file.getUuid() : null,
                file != null ? file.getOriginalName() : null,
                file != null ? file.getContentType() : null,
                file != null ? file.getSizeBytes() : null,
                deliverable.getUrl(),
                deliverable.getUploadedBy().fullName(),
                deliverable.getUploadedAt());
    }
}
