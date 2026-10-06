-- La rúbrica pasa a tener versiones, y cada edición queda atada a la suya.
--
-- Hasta aquí había una sola rúbrica por cátedra (UNIQUE (track)): cambiarle un
-- criterio la cambiaba también para las ediciones ya calificadas, que quedaban
-- mostrando criterios con los que nadie las evaluó. Desde ahora una rúbrica
-- nueva es otra fila, con el número de versión siguiente, y cada cátedra de
-- cada edición guarda con cuál se califica.
--
-- Una edición nueva estrena la versión más reciente de cada cátedra. Las que
-- ya existen se quedan con la que hay hoy, que pasa a ser la versión 1.
-- Cómo se carga una versión nueva está en docs/rubricas.md.

ALTER TABLE rubrics
  ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER track,
  DROP INDEX uk_rubrics_track,
  ADD CONSTRAINT uk_rubrics_track_version UNIQUE (track, version),
  ADD CONSTRAINT chk_rubrics_version CHECK (version >= 1);

-- Sin rúbrica (NULL) solo queda la cátedra de una edición creada cuando esa
-- cátedra todavía no tenía ninguna: la API lo dice al intentar calificar.
ALTER TABLE edition_tracks
  ADD COLUMN rubric_id INT NULL,
  ADD CONSTRAINT fk_edition_tracks_rubric FOREIGN KEY (rubric_id) REFERENCES rubrics (id);

UPDATE edition_tracks et
  JOIN rubrics r ON r.track = et.track
   SET et.rubric_id = r.id;
