package co.edu.unisimon.expoideas.deliverables;

import co.edu.unisimon.expoideas.editions.Track;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Alta o edición de un entregable de una cátedra. La edición y la cátedra solo
 * cuentan al crearlo: un entregable no se muda de cátedra. La plantilla se sube
 * aparte (PUT /{id}/template).
 *
 * @param closesOn cierre propio del entregable; null para usar el de la edición
 */
public record DeliverableTypeRequest(
        @NotNull(message = "La edición es obligatoria") Integer editionId,
        @NotNull(message = "La cátedra es obligatoria") Track track,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String name,

        @Size(max = 300, message = "La descripción no puede exceder 300 caracteres")
        String description,

        @NotNull(message = "Indica qué archivos se aceptan") DeliverableKind kind,
        boolean required,

        @Min(value = 1, message = "Debe aceptar al menos un archivo")
        @Max(value = 10, message = "Como máximo 10 archivos")
        int maxFiles,

        int sortOrder,

        LocalDate closesOn,

        /** Solo en INNPRENDE II: el entregable se pide solo a los proyectos de ese tipo. Null: a todos. */
        Integer prototypeTypeId) {}
