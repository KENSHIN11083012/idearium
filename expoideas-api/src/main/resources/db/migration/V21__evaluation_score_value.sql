-- Cada criterio calificado guarda lo que valía su nivel cuando el jurado lo eligió.
--
-- Hasta ahora la nota se calculaba cada vez con el valor que el nivel tuviera en
-- la rúbrica en ese momento. Corregir un valor de la rúbrica (y la del póster
-- tiene valores por confirmar con MacondoLab) cambiaba de golpe todas las notas
-- ya puestas, incluidas las publicadas, sin que nadie las hubiera tocado.
--
-- Con score_value la nota sale de lo guardado. Una corrección de la rúbrica vale
-- para lo que se califique después; una evaluación anterior solo cambia si su
-- jurado la vuelve a guardar.
--
-- Las filas que ya existan toman el valor que su nivel tiene hoy, que es con el
-- que se venían calculando.

ALTER TABLE evaluation_scores
  ADD COLUMN score_value DECIMAL(2, 1) NULL AFTER level_id;

UPDATE evaluation_scores s
  JOIN rubric_levels l ON l.id = s.level_id
   SET s.score_value = l.score;

ALTER TABLE evaluation_scores
  MODIFY score_value DECIMAL(2, 1) NOT NULL,
  ADD CONSTRAINT chk_evaluation_scores_value CHECK (score_value >= 0.0 AND score_value <= 5.0);
