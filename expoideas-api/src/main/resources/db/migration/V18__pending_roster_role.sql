-- El rol que el listado de la cátedra le da a una cuenta, pendiente de confirmar.
--
-- El registro no comprueba que quien se registra sea el dueño del correo. Con el
-- listado cargado, eso dejaba que cualquiera registrara el correo de un profesor
-- y naciera como profesor: vería los entregables de los proyectos que lo nombran
-- y podría registrar resultados. Ahora quien figura en el listado con un rol
-- distinto de estudiante nace estudiante, y el rol queda aquí hasta que la gestión
-- lo confirme o lo descarte desde Usuarios.
--
-- Es provisional: cuando el registro verifique el correo con un enlace, el rol
-- del listado se dará solo al verificarlo.

ALTER TABLE users
  ADD COLUMN pending_role VARCHAR(30) NULL AFTER role,
  ADD CONSTRAINT chk_users_pending_role
    CHECK (pending_role IS NULL OR pending_role IN ('ADMIN', 'MACONDOLAB', 'TEACHER', 'JUDGE', 'STUDENT'));
