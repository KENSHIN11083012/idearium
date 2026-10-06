-- El registro pide solo correo y contraseña (reunión de septiembre de 2026):
-- el nombre y la adscripción se completan en el primer ingreso. Mientras
-- falten, la API deja la cuenta en el paso COMPLETE_PROFILE.

ALTER TABLE users
  MODIFY first_name VARCHAR(100) NULL,
  MODIFY last_name  VARCHAR(100) NULL;
