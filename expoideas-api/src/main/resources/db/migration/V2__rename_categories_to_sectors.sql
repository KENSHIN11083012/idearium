-- Las categorías pasan a llamarse sectores.
--
-- MacondoLab clasifica cada proyecto por el sector en el que se mueve (moda y
-- textil, gastronomía, salud...) y además premia por categorías en el ranking.
-- Eran dos cosas distintas con el mismo nombre: este catálogo es el de sectores
-- y "categoría" queda libre para los premios.

RENAME TABLE categories TO sectors;

ALTER TABLE sectors RENAME INDEX uk_categories_name TO uk_sectors_name;

-- Sectores más comunes, para no arrancar con el catálogo vacío. MacondoLab y el
-- administrador los editan y agregan los que falten. INSERT IGNORE: si el
-- catálogo ya traía alguno con ese nombre, se respeta el existente.
INSERT IGNORE INTO sectors (name) VALUES
  ('Agroindustria y alimentos'),
  ('Comercio y retail'),
  ('Construcción e infraestructura'),
  ('Educación'),
  ('Energía y sostenibilidad'),
  ('Industrias creativas y culturales'),
  ('Logística y transporte'),
  ('Moda y textil'),
  ('Salud y bienestar'),
  ('Servicios financieros'),
  ('Tecnología y software'),
  ('Turismo y gastronomía');
