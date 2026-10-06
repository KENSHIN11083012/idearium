package co.edu.unisimon.expoideas.security;

import co.edu.unisimon.expoideas.users.OnboardingStep;
import java.util.List;

/**
 * La cuenta tiene pasos de primer ingreso sin completar y pidió algo distinto de
 * resolverlos. GlobalExceptionHandler la traduce a 403 con {@code pendingSteps}.
 */
public class OnboardingRequiredException extends RuntimeException {

    private final List<OnboardingStep> pendingSteps;

    public OnboardingRequiredException(List<OnboardingStep> pendingSteps) {
        super("Antes de continuar, completa tu primer ingreso.");
        this.pendingSteps = List.copyOf(pendingSteps);
    }

    public List<OnboardingStep> getPendingSteps() {
        return pendingSteps;
    }
}
