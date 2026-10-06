-- Publicación de notas (acordada con MacondoLab tras la fase de evaluación).
--
-- Mientras los jurados califican, el equipo no ve nada. Cuando la gestión
-- publica las notas de una cátedra en una edición, cada equipo ve su nota
-- final y las observaciones de los jurados, sin saber quién escribió cada una.
-- La gestión puede volver a ocultarlas. Las evaluaciones no se congelan: si un
-- jurado corrige después de publicar, el equipo ve lo corregido.

ALTER TABLE edition_tracks
  ADD COLUMN grades_published_at DATETIME NULL,
  ADD COLUMN grades_published_by INT NULL,
  ADD CONSTRAINT fk_edition_tracks_grades_published_by FOREIGN KEY (grades_published_by) REFERENCES users (id) ON DELETE SET NULL;
