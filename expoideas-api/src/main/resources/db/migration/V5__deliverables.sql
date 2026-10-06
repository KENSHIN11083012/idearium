-- Entregables: qué pide cada cátedra y qué subió cada proyecto.
--
-- MacondoLab define la lista de entregables de cada cátedra en cada edición
-- (el póster de INNPRENDE I, las fotos del prototipo y las evidencias de
-- validación de INNPRENDE II...). Son datos, no código: cuando cambien los
-- requisitos de un semestre se cargan desde la plataforma.
--
-- Sube el equipo, no el docente, y solo hasta el cierre de entregas de la
-- edición. El contenido de cada archivo vive en disco con el módulo de
-- archivos; aquí solo queda a qué proyecto y a qué entregable corresponde.

CREATE TABLE deliverable_types (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  edition_id  INT NOT NULL,
  track       VARCHAR(20) NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(300) NULL,
  -- Qué se acepta: DOCUMENT (PDF), IMAGE (JPG, PNG o WEBP) o ANY (cualquiera).
  kind        VARCHAR(20) NOT NULL,
  is_required BOOLEAN NOT NULL DEFAULT TRUE,
  max_files   INT NOT NULL DEFAULT 1,
  sort_order  INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_deliverable_types_edition_track FOREIGN KEY (edition_id, track) REFERENCES edition_tracks (edition_id, track) ON DELETE CASCADE,
  CONSTRAINT uk_deliverable_types_name UNIQUE (edition_id, track, name),
  CONSTRAINT chk_deliverable_types_kind CHECK (kind IN ('DOCUMENT', 'IMAGE', 'ANY')),
  CONSTRAINT chk_deliverable_types_max_files CHECK (max_files BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Un archivo subido para un entregable de un proyecto. Varias filas del mismo
-- tipo son varios archivos (las fotos del prototipo), hasta max_files.
CREATE TABLE deliverables (
  id                  INT AUTO_INCREMENT PRIMARY KEY,
  project_id          INT NOT NULL,
  deliverable_type_id INT NOT NULL,
  file_id             INT NOT NULL,
  uploaded_by         INT NOT NULL,
  uploaded_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_deliverables_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_deliverables_type FOREIGN KEY (deliverable_type_id) REFERENCES deliverable_types (id),
  CONSTRAINT fk_deliverables_file FOREIGN KEY (file_id) REFERENCES files (id),
  CONSTRAINT fk_deliverables_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id),
  -- Un archivo pertenece a un solo entregable.
  CONSTRAINT uk_deliverables_file UNIQUE (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_deliverables_project ON deliverables (project_id);
