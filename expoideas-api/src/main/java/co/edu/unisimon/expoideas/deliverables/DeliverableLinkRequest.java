package co.edu.unisimon.expoideas.deliverables;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Un enlace (video, prototipo en línea) para un entregable de tipo {@link DeliverableKind#LINK}.
 * Admite espacios alrededor, porque se pega desde otro sitio; el servicio los quita.
 */
public record DeliverableLinkRequest(
        @NotNull(message = "El entregable es obligatorio") Integer deliverableTypeId,

        @NotBlank(message = "Ingresa la dirección del enlace")
        @Size(max = 500, message = "La dirección no puede exceder 500 caracteres")
        @Pattern(
                regexp = "^\\s*https?://\\S+\\s*$",
                message = "Ingresa una dirección completa, que empiece por http:// o https://")
        String url) {}
