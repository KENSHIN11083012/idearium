package co.edu.unisimon.expoideas.projects;

/**
 * Estado de una persona en el equipo. Rechazar una invitación no deja un estado
 * nuevo: borra la fila, para que el líder pueda volver a invitar.
 */
public enum MembershipStatus {

    /** Invitada por el líder; todavía no responde. */
    INVITED,

    /** Aceptó: cuenta como integrante del equipo. */
    ACCEPTED
}
