-- Ediciones de Expoideas y configuración de cada cátedra.
--
-- Una edición es la vuelta de la Expo de un semestre (p. ej. "Expoideas 2026-2")
-- con sus fechas: hasta cuándo se inscriben los proyectos y hasta cuándo se
-- suben los entregables. Un proyecto se inscribe en una edición y en una de las
-- dos cátedras, y se inscribe por separado en cada una.
--
-- Las reglas que cambian entre cátedras y entre semestres (por ahora, el tamaño
-- del grupo) son datos que escribe MacondoLab, no código: cuando definan los
-- suyos se cargan desde la plataforma, sin un despliegue nuevo.

CREATE TABLE editions (
  id                     INT AUTO_INCREMENT PRIMARY KEY,
  name                   VARCHAR(100) NOT NULL,
  -- Fechas en la zona horaria de Colombia, inclusivas: el último día cuenta entero.
  registration_opens_on  DATE NOT NULL,
  registration_closes_on DATE NOT NULL,
  submission_closes_on   DATE NOT NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_editions_name UNIQUE (name),
  -- Primero se inscriben los proyectos y después se cierran los entregables.
  CONSTRAINT chk_editions_dates CHECK (
    registration_opens_on <= registration_closes_on
    AND registration_closes_on <= submission_closes_on
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cada edición tiene las dos cátedras, cada una con su configuración.
-- INNPRENDE_I: investigación, termina en un póster. INNPRENDE_II: prototipado,
-- termina en un pitch ante jurados.
CREATE TABLE edition_tracks (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  edition_id  INT NOT NULL,
  track       VARCHAR(20) NOT NULL,
  min_members INT NOT NULL,
  max_members INT NOT NULL,
  CONSTRAINT fk_edition_tracks_edition FOREIGN KEY (edition_id) REFERENCES editions (id) ON DELETE CASCADE,
  CONSTRAINT uk_edition_tracks_edition_track UNIQUE (edition_id, track),
  CONSTRAINT chk_edition_tracks_track CHECK (track IN ('INNPRENDE_I', 'INNPRENDE_II')),
  CONSTRAINT chk_edition_tracks_members CHECK (
    min_members >= 1 AND max_members >= min_members AND max_members <= 20
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
