package co.edu.unisimon.expoideas.common;

/**
 * Algo que pasó y que después hay que poder explicar. Lo publica como evento el
 * módulo donde ocurre, dentro de su transacción; el módulo {@code audit} lo
 * guarda en esa misma transacción, con la cuenta que tiene la sesión como
 * autora. Así ningún módulo sabe cómo se guarda el rastro, y si la acción se
 * revierte, el rastro también.
 *
 * @param targetId    el id de aquello sobre lo que se actuó (ver {@link Action#target()})
 * @param targetLabel cómo reconocerlo cuando ya no exista: el correo de la cuenta, el título del proyecto
 * @param detail      lo que cambió, en una frase; puede faltar
 */
public record AuditableAction(Action action, Integer targetId, String targetLabel, String detail) {

    /** Sobre qué se actúa. */
    public enum Target {
        ACCOUNT,
        EDITION,
        PROJECT,
        EVALUATION,
        DELIVERABLE,
        /** Un correo de la plataforma; se reconoce por su destinatario. */
        MAIL
    }

    /** Las acciones que dejan rastro. Una nueva se añade aquí y se publica donde ocurre. */
    public enum Action {
        ADMIN_BOOTSTRAPPED(Target.ACCOUNT, "Primer administrador"),
        ROLE_CHANGED(Target.ACCOUNT, "Cambio de rol"),
        PENDING_ROLE_DISCARDED(Target.ACCOUNT, "Rol del listado descartado"),
        PASSWORD_RESET(Target.ACCOUNT, "Contraseña restablecida por la gestión"),
        ACCOUNT_SUSPENDED(Target.ACCOUNT, "Cuenta suspendida"),
        ACCOUNT_REACTIVATED(Target.ACCOUNT, "Cuenta reactivada"),
        ACCOUNT_DELETED(Target.ACCOUNT, "Cuenta eliminada"),
        GRADES_PUBLISHED(Target.EDITION, "Notas publicadas"),
        GRADES_HIDDEN(Target.EDITION, "Notas ocultadas"),
        EVALUATION_EDITED(Target.EVALUATION, "Evaluación corregida"),
        JUROR_REMOVED(Target.PROJECT, "Jurado quitado"),
        PROJECT_DELETED(Target.PROJECT, "Proyecto eliminado"),
        DELIVERABLE_DELETED(Target.DELIVERABLE, "Entregable eliminado"),
        MAIL_FAILED(Target.MAIL, "Correo que no se pudo enviar");

        private final Target target;
        private final String label;

        Action(Target target, String label) {
            this.target = target;
            this.label = label;
        }

        public Target target() {
            return target;
        }

        /** Nombre para mostrar. */
        public String label() {
            return label;
        }
    }
}
