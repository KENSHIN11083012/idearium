package co.edu.unisimon.expoideas.users;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Autorización de tratamiento de datos dada por la propia persona (p. ej. en su primer ingreso). */
public record DataConsentRequest(
        @NotNull(message = "Debes indicar si autorizas el tratamiento de tus datos")
        @AssertTrue(message = "Debes autorizar el tratamiento de tus datos para usar la plataforma")
        Boolean dataConsent) {}
