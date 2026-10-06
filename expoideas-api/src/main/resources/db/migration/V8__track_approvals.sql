-- Prerrequisito de INNPRENDE I a II (reunión de septiembre de 2026, punto 9).
--
-- El profesor del grupo (o la gestión) marca el resultado del proyecto cuando
-- cierran las entregas. Aprobarlo deja constancia, por cada integrante
-- aceptado, de que aprobó esa cátedra: es lo que se exige para inscribirse en
-- INNPRENDE II. La gestión también puede registrar aprobaciones a mano, sin
-- proyecto, para quienes cursaron INNPRENDE I antes de existir la plataforma.

ALTER TABLE projects
  ADD COLUMN result        VARCHAR(20) NULL AFTER teacher_id,
  ADD COLUMN result_set_by INT         NULL AFTER result,
  ADD COLUMN result_set_at DATETIME    NULL AFTER result_set_by,
  ADD CONSTRAINT fk_projects_result_set_by FOREIGN KEY (result_set_by) REFERENCES users (id) ON DELETE SET NULL,
  ADD CONSTRAINT chk_projects_result CHECK (result IN ('APPROVED', 'NOT_APPROVED'));

CREATE TABLE track_approvals (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT         NOT NULL,
  track       VARCHAR(20) NOT NULL,
  -- NULL: aprobación histórica registrada a mano por la gestión.
  project_id  INT         NULL,
  approved_by INT         NULL,
  approved_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_track_approvals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_track_approvals_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
  CONSTRAINT fk_track_approvals_approved_by FOREIGN KEY (approved_by) REFERENCES users (id) ON DELETE SET NULL,
  -- Una cátedra se aprueba una vez por persona.
  CONSTRAINT uk_track_approvals_user_track UNIQUE (user_id, track),
  CONSTRAINT chk_track_approvals_track CHECK (track IN ('INNPRENDE_I', 'INNPRENDE_II'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
