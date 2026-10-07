-- Ley 1581 de 2012: qué texto autorizó cada persona y cuentas anonimizadas.
--
-- data_consent_version: la versión del texto de autorización que la persona
-- aceptó. Hasta aquí solo ha existido un texto, la versión 1: es la que
-- aceptaron todas las cuentas que ya dieron su autorización.
--
-- anonymized_at: cuándo un administrador anonimizó la cuenta. Una cuenta con
-- historia (un equipo, un proyecto a cargo, una evaluación) no se puede borrar
-- sin romper ese historial; anonimizarla le quita el nombre, el correo, la foto
-- y el acceso, y deja la fila para que el historial siga en pie.

ALTER TABLE users
  ADD COLUMN data_consent_version VARCHAR(20) NULL AFTER data_consent_at,
  ADD COLUMN anonymized_at DATETIME NULL;

UPDATE users SET data_consent_version = '1' WHERE data_consent = TRUE;
