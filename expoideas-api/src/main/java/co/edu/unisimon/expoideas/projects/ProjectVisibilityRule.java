package co.edu.unisimon.expoideas.projects;

import co.edu.unisimon.expoideas.users.User;

/**
 * Permiso para ver un proyecto que aporta otro módulo, además del equipo, el
 * profesor del grupo y la gestión, que ya conoce {@link ProjectPolicy}. El
 * módulo de jurados lo implementa para los jurados asignados. Si alguna regla
 * dice que sí, la persona ve el proyecto y sus entregables.
 */
public interface ProjectVisibilityRule {

    boolean canView(Project project, User viewer);
}
