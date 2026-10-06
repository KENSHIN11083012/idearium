-- Agenda de sustentaciones (reunión de septiembre de 2026, punto 11).
--
-- La gestión asigna a cada proyecto la fecha, la hora y el lugar de su
-- sustentación, y la plataforma avisa por correo al equipo y al profesor.
-- Una sustentación por proyecto; cambiarla vuelve a avisar. La hora es la
-- local de Bogotá, sin zona: es una cita, no un instante.

CREATE TABLE presentations (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  project_id INT          NOT NULL,
  starts_at  DATETIME     NOT NULL,
  place      VARCHAR(150) NOT NULL,
  notes      VARCHAR(500) NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_presentations_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT uk_presentations_project UNIQUE (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_presentations_starts_at ON presentations (starts_at);
