-- Jurados por asignación (reunión de septiembre de 2026, punto 2).
--
-- Ser jurado no es un rol más: es una asignación por proyecto. La gestión
-- asigna a profesores, a jurados externos o a cuentas de gestión, nunca al
-- profesor del grupo ni a alguien del equipo. Un jurado asignado ve el
-- proyecto y sus entregables; la evaluación con rúbrica llega después.

CREATE TABLE jury_assignments (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  project_id  INT      NOT NULL,
  user_id     INT      NOT NULL,
  assigned_by INT      NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_jury_assignments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_jury_assignments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_jury_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT uk_jury_assignments_project_user UNIQUE (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_jury_assignments_user ON jury_assignments (user_id);
