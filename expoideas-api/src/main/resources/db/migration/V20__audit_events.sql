-- Rastro de las acciones que después hay que poder explicar: quién cambió un
-- rol, quién restableció una contraseña, quién publicó u ocultó las notas, qué
-- nota tenía una evaluación antes de corregirla.
--
-- Cada fila se escribe en la misma transacción que la acción: si la acción se
-- revierte, su rastro también. Las filas no se editan ni se borran desde la
-- plataforma.
--
-- Guarda una copia de lo que identifica a cada cosa (el correo de quien actuó,
-- el nombre de aquello sobre lo que actuó) porque el rastro tiene que seguir
-- diciendo algo cuando la cuenta o el proyecto ya no existan. Por eso target_id
-- no es llave foránea, y actor_id se queda en NULL si la cuenta se elimina.
--
-- action y target_type no llevan CHECK, a diferencia de otras tablas: la lista
-- crece con la plataforma y cada acción nueva obligaría a otra migración. Los
-- valores válidos son los de AuditableAction.Action y AuditableAction.Target.

CREATE TABLE audit_events (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  occurred_at  DATETIME     NOT NULL,
  -- NULL si nadie tenía sesión (p. ej. el rol que se da al verificar el correo).
  actor_id     INT          NULL,
  actor_email  VARCHAR(150) NULL,
  action       VARCHAR(40)  NOT NULL,
  target_type  VARCHAR(20)  NOT NULL,
  target_id    INT          NULL,
  target_label VARCHAR(200) NULL,
  detail       VARCHAR(500) NULL,
  CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_events_occurred ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_action ON audit_events (action, occurred_at);
CREATE INDEX idx_audit_events_target ON audit_events (target_type, target_id);
