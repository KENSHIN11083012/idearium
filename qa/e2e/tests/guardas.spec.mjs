import { expect, loginAs, path, test } from "../fixtures.mjs";

const ADMIN_PAGES = ["/admin/usuarios", "/admin/ediciones", "/admin/catalogos"];

for (const key of ["estudiante1", "docente1", "jurado"]) {
  test(`${key} no entra a la gestión`, async ({ page }) => {
    await loginAs(page, key);
    for (const p of ADMIN_PAGES) {
      await page.goto(path(p));
      await expect(page, p).toHaveURL(/no-autorizado/);
      await expect(page.getByRole("heading", { name: "No tienes acceso a esta sección" })).toBeVisible();
    }
  });
}

test("estudiante y jurado no ven el directorio de proyectos", async ({ page }) => {
  for (const key of ["estudiante1", "jurado"]) {
    await page.goto(path("/"));
    await page.evaluate(() => localStorage.clear());
    await loginAs(page, key);
    await page.goto(path("/proyectos"));
    await expect(page, key).toHaveURL(/no-autorizado/);
  }
});

test("MacondoLab entra a toda la gestión", async ({ page }) => {
  await loginAs(page, "macondolab");
  for (const [p, heading] of [["/admin/usuarios", "Usuarios"], ["/admin/ediciones", "Ediciones"], ["/admin/catalogos", "Catálogos"], ["/proyectos", "Proyectos"]]) {
    await page.goto(path(p));
    await expect(page.getByRole("heading", { name: heading, exact: true })).toBeVisible();
  }
});

test("sin sesión, las rutas privadas llevan al login", async ({ page }) => {
  for (const p of ["/mis-proyectos", "/proyectos", "/perfil", ...ADMIN_PAGES]) {
    await page.goto(path(p));
    await expect(page, p).toHaveURL(/iniciar-sesion/);
  }
});

test("una ruta inexistente muestra la página 404", async ({ page }) => {
  await page.goto(path("/esto-no-existe"));
  await expect(page.getByText(/404|no existe|no encontramos/i).first()).toBeVisible();
});
