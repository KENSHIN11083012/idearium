package co.edu.unisimon.expoideas.notifications;

/**
 * Alguien fue invitado a un equipo. Lo publica el módulo de proyectos con los
 * datos ya resueltos: aquí no se consultan entidades de otros módulos.
 *
 * @param inviteeEmail correo de la persona invitada
 * @param inviteeName  su nombre, o la parte local del correo si aún no lo puso
 * @param leaderName   quién invita
 * @param projectTitle proyecto al que la invitan
 * @param trackLabel   la cátedra con su muestra: "INNPRENDE I · Despegue"
 */
public record TeamInvitationEvent(
        String inviteeEmail, String inviteeName, String leaderName, String projectTitle, String trackLabel) {}
