package co.edu.unisimon.expoideas.catalogs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta o edición de un catálogo que solo tiene nombre. */
public record CatalogItemRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String name) {}
