-- Evaluación con rúbrica (reunión de septiembre de 2026, punto 12).
--
-- La rúbrica de cada cátedra es un dato: criterios y, por criterio, niveles con
-- su valor y su descripción. Se cargan tal como las entregó MacondoLab (póster
-- para INNPRENDE I, pitch para INNPRENDE II); si cambian, se corrigen con otra
-- migración. En el póster los valores no son los mismos en todos los criterios:
-- así está en la tabla del documento.
--
-- Cada jurado asignado guarda una evaluación por proyecto: un nivel por
-- criterio, con una observación que es obligatoria por debajo de 3.0. La nota
-- del jurado es el promedio simple de los criterios; la del proyecto, el
-- promedio de sus jurados. Si el equipo no asistió, la evaluación vale 0.0.

CREATE TABLE rubrics (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  track       VARCHAR(20)  NOT NULL,
  name        VARCHAR(150) NOT NULL,
  description VARCHAR(300) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_rubrics_track UNIQUE (track),
  CONSTRAINT chk_rubrics_track CHECK (track IN ('INNPRENDE_I', 'INNPRENDE_II'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rubric_criteria (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  rubric_id  INT          NOT NULL,
  position   INT          NOT NULL,
  name       VARCHAR(200) NOT NULL,
  -- El nombre corto de la tabla de retroalimentación, para listas y resúmenes.
  short_name VARCHAR(150) NOT NULL,
  CONSTRAINT fk_rubric_criteria_rubric FOREIGN KEY (rubric_id) REFERENCES rubrics (id) ON DELETE CASCADE,
  CONSTRAINT uk_rubric_criteria_position UNIQUE (rubric_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rubric_levels (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  criterion_id INT           NOT NULL,
  position     INT           NOT NULL,
  label        VARCHAR(60)   NOT NULL,
  score        DECIMAL(2, 1) NOT NULL,
  description  VARCHAR(1000) NOT NULL,
  CONSTRAINT fk_rubric_levels_criterion FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id) ON DELETE CASCADE,
  CONSTRAINT uk_rubric_levels_position UNIQUE (criterion_id, position),
  CONSTRAINT chk_rubric_levels_score CHECK (score >= 0.0 AND score <= 5.0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Una evaluación por jurado y proyecto. Quien ya calificó no se elimina como
-- cuenta sin más (lo explica la API); si el proyecto se borra, se van con él.
CREATE TABLE evaluations (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  project_id INT      NOT NULL,
  juror_id   INT      NOT NULL,
  -- El equipo no asistió a la sustentación: vale 0.0 y no lleva niveles.
  absent     BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_evaluations_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
  CONSTRAINT fk_evaluations_juror FOREIGN KEY (juror_id) REFERENCES users (id),
  CONSTRAINT uk_evaluations_project_juror UNIQUE (project_id, juror_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_evaluations_juror ON evaluations (juror_id);

CREATE TABLE evaluation_scores (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  evaluation_id INT          NOT NULL,
  criterion_id  INT          NOT NULL,
  level_id      INT          NOT NULL,
  comment       VARCHAR(500) NULL,
  CONSTRAINT fk_evaluation_scores_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluations (id) ON DELETE CASCADE,
  CONSTRAINT fk_evaluation_scores_criterion FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id),
  CONSTRAINT fk_evaluation_scores_level FOREIGN KEY (level_id) REFERENCES rubric_levels (id),
  CONSTRAINT uk_evaluation_scores_criterion UNIQUE (evaluation_id, criterion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── INNPRENDE I · Despegue: rúbrica del póster ──
INSERT INTO rubrics (track, name, description) VALUES
  ('INNPRENDE_I', 'Póster de proyecto de innovación', 'Evaluación en stand ante jurado · Innovación y Emprendimiento I');
SET @rubric = LAST_INSERT_ID();

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 1, 'Aspectos formales — Nombre del proyecto, autores y referencias', 'Aspectos formales (nombre, autores, referencias)');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 0.0, 'Falta el nombre del proyecto y/o los autores, y no hay referencias o las que hay no son verificables.'),
  (@criterion, 2, 'Deficiente', 1.5, 'Hay nombre y autores, pero el nombre es genérico; referencias incompletas o sin formato consistente.'),
  (@criterion, 3, 'Aceptable', 4.0, 'Nombre y autores completos; referencias completas, pero con formato de citación inconsistente.'),
  (@criterion, 4, 'Bueno', 4.5, 'Nombre coherente con la propuesta, autores completos y bien ubicados; referencias pertinentes en formato consistente (ej. APA).'),
  (@criterion, 5, 'Excelente', 5.0, 'Nombre distintivo con identidad propia coherente con el proyecto; referencias variadas, pertinentes y correctamente citadas, respaldando datos clave del póster.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 2, 'Planteamiento del problema/necesidad', 'Planteamiento del problema');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 0.0, 'No se identifica un problema o necesidad; se salta directo a la solución.'),
  (@criterion, 2, 'Deficiente', 1.5, 'El problema se menciona de forma genérica ("mucha gente tiene este problema"), sin población afectada ni consecuencias claras.'),
  (@criterion, 3, 'Aceptable', 3.5, 'Se identifica la población afectada y una consecuencia del problema, pero sin datos ni evidencia que lo respalden.'),
  (@criterion, 4, 'Bueno', 4.5, 'Problema bien delimitado: población afectada, consecuencias y contexto claros, con al menos un dato o evidencia de respaldo.'),
  (@criterion, 5, 'Excelente', 5.0, 'Problema delimitado con precisión, evidencia/datos concretos (cifras, fuente, estudio propio) y una lectura crítica de por qué el problema persiste o se ha desatendido.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 3, 'Objetivos del proyecto', 'Objetivos');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 0.0, 'No hay objetivos o están mezclados con la descripción del problema/solución.'),
  (@criterion, 2, 'Deficiente', 3.0, 'Hay objetivos, pero son vagos, no están en infinitivo o son en realidad actividades ("hacer una encuesta").'),
  (@criterion, 3, 'Aceptable', 4.0, '2–3 objetivos en infinitivo, claros individualmente, pero sin relación evidente entre sí o con el problema planteado.'),
  (@criterion, 4, 'Bueno', 4.5, 'Objetivos claros, en infinitivo, coherentes entre sí y alineados directamente con el problema y la solución propuesta.'),
  (@criterion, 5, 'Excelente', 5.0, 'Objetivos claros, medibles/verificables y jerarquizados (uno general y específicos, o priorizados), que dejan ver una ruta lógica de trabajo.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 4, 'Contextualización — Segmentación, propuesta de valor, fuentes de ingreso y comparativo de competidores', 'Contextualización (segmentación, propuesta de valor, ingresos, competidores)');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 1.5, 'Falta uno o más de los cuatro elementos (segmento, propuesta de valor, fuente de ingreso, comparativo de competidores), o se presentan de forma confusa o inexistente.'),
  (@criterion, 2, 'Deficiente', 3.0, 'Los cuatro elementos están mencionados, pero de forma genérica: segmento "para todos", propuesta de valor sin diferenciación, ingreso sin justificar, competidores nombrados sin análisis.'),
  (@criterion, 3, 'Aceptable', 4.0, 'Segmento de cliente específico y al menos un beneficio diferenciador claro; fuente de ingreso identificada; comparativo con fortalezas/debilidades de al menos un competidor, pero el diferencial propio no es contundente.'),
  (@criterion, 4, 'Bueno', 4.5, 'Segmento bien definido, propuesta de valor clara y diferenciada, fuente(s) de ingreso coherente(s) con el modelo de negocio, y comparativo de 2 o más competidores con diferencial propio explícito y creíble.'),
  (@criterion, 5, 'Excelente', 5.0, 'Los cuatro elementos están integrados como un modelo de negocio coherente: segmento, propuesta de valor, ingreso y diferencial frente a competidores se explican entre sí, con evidencia y justificación de viabilidad.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 5, 'Impacto y sostenibilidad', 'Impacto y sostenibilidad');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 0.0, 'No se menciona impacto ni cómo se sostendría el proyecto en el tiempo.'),
  (@criterion, 2, 'Deficiente', 3.0, 'Se menciona impacto de forma aspiracional ("va a ayudar a mucha gente") sin plazo ni mecanismo de sostenibilidad.'),
  (@criterion, 3, 'Aceptable', 4.0, 'Se identifica un impacto a mediano/largo plazo, pero el mecanismo de sostenibilidad (financiera, operativa o social) es vago.'),
  (@criterion, 4, 'Bueno', 4.5, 'Impacto a mediano/largo plazo bien argumentado, con al menos un mecanismo concreto de sostenibilidad en el tiempo.'),
  (@criterion, 5, 'Excelente', 5.0, 'Impacto y sostenibilidad conectados explícitamente con el modelo de negocio y el problema inicial, mostrando pensamiento de largo plazo y posibles riesgos/mitigaciones.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 6, 'Presentación personal, herramientas de apoyo, sinergia de equipo y sustentación oral', 'Presentación, herramientas de apoyo, sinergia y sustentación oral');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Insuficiente', 1.5, 'No hay cuidado en la presentación personal del equipo; no usan herramientas de apoyo más allá del póster; el equipo se ve desorganizado (uno solo habla o hay fricciones); además, leen el póster palabra por palabra o no logran explicar la idea con claridad en el tiempo disponible.'),
  (@criterion, 2, 'Deficiente', 3.0, 'Presentación personal descuidada o dispareja; no hay herramientas de apoyo adicionales; participación desigual (uno lidera todo, los demás casi no intervienen); explican la idea de forma entrecortada, apoyándose casi todo el tiempo en la lectura del póster.'),
  (@criterion, 3, 'Aceptable', 4.0, 'Presentación personal aceptable y coherente entre el equipo; usan al menos una herramienta de apoyo simple (muestra física, folleto, tarjeta) aunque poco integrada al discurso; la mayoría participa, pero sin fluidez entre ellos; explican la idea con claridad básica en el tiempo dado, pero dudan o se bloquean ante preguntas espontáneas.'),
  (@criterion, 4, 'Bueno', 4.5, 'Presentación personal cuidada y coherente con la identidad del proyecto; usan herramientas de apoyo pertinentes (prototipo, muestra, demo digital, video corto) que refuerzan el mensaje; todos los integrantes participan con roles claros y se complementan; explican la idea con claridad y fluidez sin depender del póster, y responden con seguridad la mayoría de las preguntas del jurado.'),
  (@criterion, 5, 'Excelente', 5.0, 'Presentación personal impecable y alineada con la marca/proyecto; las herramientas de apoyo están integradas de forma natural y elevan la sustentación; el equipo muestra sinergia total (se turnan sin fricciones, se complementan al responder); explican la idea de forma clara, segura y memorable, y manejan con dominio cualquier pregunta espontánea, incluidas las incómodas.');

-- ── INNPRENDE II · Aterrizaje: rúbrica del pitch ──
INSERT INTO rubrics (track, name, description) VALUES
  ('INNPRENDE_II', 'Pitch de proyecto de innovación', 'Pitch de 5 minutos ante jurado · Innovación y Emprendimiento II');
SET @rubric = LAST_INSERT_ID();

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 1, 'Propuesta de valor', 'Propuesta de valor');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Muy deficiente', 0.0, 'No presenta propuesta de valor, o el equipo no asistió al pitch.'),
  (@criterion, 2, 'Deficiente', 1.5, 'La propuesta de valor es confusa, genérica o no resuelve ninguna necesidad identificable del cliente.'),
  (@criterion, 3, 'Aceptable', 3.0, 'Propuesta de valor genérica ("ofrecemos calidad", "buen servicio"), sin diferenciación clara ni evidencia de validación con clientes.'),
  (@criterion, 4, 'Bueno', 4.0, 'Propuesta de valor clara, con al menos un beneficio diferenciador, aunque sin evidencia de validación directa con clientes reales.'),
  (@criterion, 5, 'Muy bueno', 4.5, 'Propuesta de valor clara y diferenciada, respaldada con evidencia de validación (encuestas, entrevistas) realizada con clientes reales.'),
  (@criterion, 6, 'Excelente', 5.0, 'Propuesta de valor sólida y validada con datos de clientes reales; el equipo argumenta con criterio por qué es mejor que las alternativas existentes en el mercado.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 2, 'PitchDeck', 'PitchDeck');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Muy deficiente', 0.0, 'No presenta PitchDeck, o el equipo no asistió al pitch.'),
  (@criterion, 2, 'Deficiente', 1.5, 'El PitchDeck tiene errores graves de estructura (faltan secciones clave) o es ilegible y desorganizado.'),
  (@criterion, 3, 'Aceptable', 3.0, 'El PitchDeck cumple con la estructura básica esperada, pero el diseño es genérico y con exceso de texto en las diapositivas.'),
  (@criterion, 4, 'Bueno', 4.0, 'PitchDeck bien estructurado y visualmente claro, con información suficiente para seguir la narrativa del negocio en los 5 minutos.'),
  (@criterion, 5, 'Muy bueno', 4.5, 'PitchDeck profesional y visualmente atractivo, con jerarquía clara de información y una narrativa (storytelling) coherente de principio a fin.'),
  (@criterion, 6, 'Excelente', 5.0, 'PitchDeck de calidad profesional: diseño impecable, narrativa persuasiva y cada diapositiva refuerza un mensaje clave del negocio sin saturar de texto.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 3, 'Modelo de negocio', 'Modelo de negocio');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Muy deficiente', 0.0, 'No presenta modelo de negocio, o el equipo no asistió al pitch.'),
  (@criterion, 2, 'Deficiente', 1.5, 'El modelo de negocio es incoherente o no permite entender cómo el negocio genera ingresos.'),
  (@criterion, 3, 'Aceptable', 3.0, 'Modelo de negocio identificado (ej. Canvas) pero con bloques incompletos o poco desarrollados.'),
  (@criterion, 4, 'Bueno', 4.0, 'Modelo de negocio completo y coherente, con los bloques clave desarrollados (segmento, canales, fuentes de ingreso, estructura de costos).'),
  (@criterion, 5, 'Muy bueno', 4.5, 'Modelo de negocio coherente y viable, con relación clara entre sus bloques y evidencia de un análisis financiero básico.'),
  (@criterion, 6, 'Excelente', 5.0, 'Modelo de negocio robusto y validado con datos reales, con análisis de viabilidad financiera y potencial de escalabilidad.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 4, 'Prototipo', 'Prototipo');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Muy deficiente', 0.0, 'No presenta prototipo ni evidencia de desarrollo, o el equipo no asistió al pitch.'),
  (@criterion, 2, 'Deficiente', 1.5, 'El prototipo mencionado es solo una idea sin ningún desarrollo tangible.'),
  (@criterion, 3, 'Aceptable', 3.0, 'Prototipo básico o boceto de baja fidelidad que ilustra la idea, pero no es funcional ni ha sido testeado.'),
  (@criterion, 4, 'Bueno', 4.0, 'Prototipo de fidelidad media (mockup, maqueta o versión mínima) que permite visualizar claramente el producto o servicio.'),
  (@criterion, 5, 'Muy bueno', 4.5, 'Prototipo funcional o de alta fidelidad, testeado con al menos un usuario o cliente real.'),
  (@criterion, 6, 'Excelente', 5.0, 'Prototipo funcional, testeado con clientes reales, con evidencia de iteración y mejoras a partir de la retroalimentación recibida.');

INSERT INTO rubric_criteria (rubric_id, position, name, short_name) VALUES
  (@rubric, 5, 'Competidores', 'Competidores');
SET @criterion = LAST_INSERT_ID();
INSERT INTO rubric_levels (criterion_id, position, label, score, description) VALUES
  (@criterion, 1, 'Muy deficiente', 0.0, 'No se menciona ningún competidor, o el equipo no asistió al pitch.'),
  (@criterion, 2, 'Deficiente', 1.5, 'Se afirma "no tenemos competencia" sin ningún sustento, o se nombran competidores sin ningún análisis.'),
  (@criterion, 3, 'Aceptable', 3.0, 'Se identifican competidores, pero sin describir sus fortalezas/debilidades ni un diferencial propio claro.'),
  (@criterion, 4, 'Bueno', 4.0, 'Análisis de 2 o más competidores (directos e indirectos) con fortalezas y debilidades, y un diferencial propio explícito.'),
  (@criterion, 5, 'Muy bueno', 4.5, 'Análisis comparativo riguroso de competidores con evidencia (precios, reseñas, participación de mercado) y un diferencial propio defendible.'),
  (@criterion, 6, 'Excelente', 5.0, 'Análisis competitivo exhaustivo, con matriz comparativa, y una estrategia clara de cómo el negocio se posicionará frente a la competencia.');
