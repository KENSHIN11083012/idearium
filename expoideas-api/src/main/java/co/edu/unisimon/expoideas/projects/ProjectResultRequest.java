package co.edu.unisimon.expoideas.projects;

import jakarta.validation.constraints.NotNull;

/** El resultado del proyecto, que pone su profesor o la gestión al cerrar las entregas. */
public record ProjectResultRequest(
        @NotNull(message = "Indica el resultado") ProjectResult result) {}
