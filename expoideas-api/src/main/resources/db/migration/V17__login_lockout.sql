-- Bloqueo temporal tras varios intentos fallidos de inicio de sesión.
--
-- failed_logins cuenta los fallos seguidos; un ingreso correcto lo pone en cero.
-- Al llegar al tope (expoideas.login.max-failed-attempts) la cuenta queda
-- bloqueada hasta locked_until y el contador vuelve a empezar. Mientras dura el
-- bloqueo no entra ni la contraseña correcta: es lo que impide probar miles.
--
-- El bloqueo es corto y se quita solo. No corta las sesiones que la persona ya
-- tenga abiertas: solo los intentos de entrar.

ALTER TABLE users
  ADD COLUMN failed_logins INT NOT NULL DEFAULT 0 AFTER enabled,
  ADD COLUMN locked_until DATETIME NULL AFTER failed_logins;
