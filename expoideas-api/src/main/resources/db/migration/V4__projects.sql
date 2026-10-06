-- Proyectos inscritos y su equipo.
--
-- Un proyecto pertenece a una edición y a una de sus dos cátedras, y ahí se
-- queda: si el grupo vuelve a participar, inscribe otro proyecto. El estudiante
-- que lo inscribe es el líder e invita a sus compañeros, que aceptan desde su
-- cuenta; nadie queda en un equipo sin enterarse.
--
-- El docente que acompaña al grupo lo elige el líder. Hace falta para que el
-- jurado nunca sea el profesor del grupo y para que cada docente vea los
-- proyectos que lo nombran.

CREATE TABLE projects (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  edition_id INT NOT NULL,
  track      VARCHAR(20) NOT NULL,
  title      VARCHAR(150) NOT NULL,
  -- Propuesta de valor: qué problema resuelve y para quién.
  summary    VARCHAR(500) NOT NULL,
  sector_id  INT NOT NULL,
  teacher_id INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  -- Apunta a la cátedra configurada de esa edición: no hay proyectos en una
  -- cátedra que la edición no tenga.
  CONSTRAINT fk_projects_edition_track FOREIGN KEY (edition_id, track) REFERENCES edition_tracks (edition_id, track),
  CONSTRAINT fk_projects_sector FOREIGN KEY (sector_id) REFERENCES sectors (id),
  CONSTRAINT fk_projects_teacher FOREIGN KEY (teacher_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_projects_teacher ON projects (teacher_id);

-- Una fila por persona del equipo. El líder nace aceptado; los demás empiezan
-- invitados. Rechazar una invitación borra la fila, así el líder puede volver a
-- invitar si fue un malentendido.
CREATE TABLE project_members (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  project_id   INT NOT NULL,
  user_id      INT NOT NULL,
  team_role    VARCHAR(20) NOT NULL,
  status       VARCHAR(20) NOT NULL,
  invited_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at DATETIME NULL,
  CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
  CONSTRAINT chk_project_members_role CHECK (team_role IN ('LEADER', 'MEMBER')),
  CONSTRAINT chk_project_members_status CHECK (status IN ('INVITED', 'ACCEPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_project_members_user ON project_members (user_id, status);
