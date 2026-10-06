import { expect, loginAs, path, stamp, test } from "../fixtures.mjs";

test("catálogos: el ADMIN ve los institucionales; MacondoLab solo los de clasificación", async ({ page }) => {
  await loginAs(page, "admin");
  await page.goto(path("/admin/catalogos"));
  const tabsAdmin = page.getByRole("tablist", { name: "Catálogos" });
  await expect(tabsAdmin.getByRole("tab", { name: "Facultades" })).toBeVisible();
  await expect(tabsAdmin.getByRole("tab", { name: "Sectores" })).toBeVisible();

  await page.context().clearCookies();
  await page.evaluate(() => localStorage.clear());
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/catalogos"));
  const tabs = page.getByRole("tablist", { name: "Catálogos" });
  await expect(tabs.getByRole("tab", { name: "Sectores" })).toBeVisible();
  await expect(tabs.getByRole("tab", { name: "Facultades" })).toHaveCount(0);
});

test("MacondoLab crea y renombra una palabra clave", async ({ page }) => {
  const name = `QA E2E Clave ${stamp()}`;
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/catalogos"));
  await page.getByRole("tab", { name: "Palabras clave" }).click();
  await page.getByRole("tabpanel").getByRole("button", { name: "Agregar" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Nueva palabra clave" });
  await dialog.getByLabel("Nombre").fill(name);
  await dialog.getByRole("button", { name: "Crear" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("cell", { name, exact: true })).toBeVisible();

  await page.getByRole("button", { name: `Editar ${name}` }).click();
  const edit = page.getByRole("dialog");
  await edit.getByLabel("Nombre").fill(`${name} v2`);
  await edit.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByRole("cell", { name: `${name} v2`, exact: true })).toBeVisible();
});

test("cerrar un diálogo con algo escrito pregunta antes de descartarlo", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/catalogos"));
  await page.getByRole("tab", { name: "Palabras clave" }).click();
  await page.getByRole("tabpanel").getByRole("button", { name: "Agregar" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Nueva palabra clave" });
  await dialog.getByLabel("Nombre").fill("QA E2E a medias");

  // Escape es la forma más fácil de perder lo escrito sin querer.
  await page.keyboard.press("Escape");
  const question = page.getByRole("alertdialog");
  await expect(question).toContainText("¿Descartar lo que escribiste?");
  await question.getByRole("button", { name: "Seguir editando" }).click();
  await expect(dialog.getByLabel("Nombre")).toHaveValue("QA E2E a medias");

  await dialog.getByRole("button", { name: "Cancelar" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Descartar" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("cell", { name: "QA E2E a medias", exact: true })).toHaveCount(0);
});

test("MacondoLab agrega y elimina un entregable de INNPRENDE II en la edición QA", async ({ page }) => {
  const name = `QA E2E Pitch ${stamp()}`;
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/ediciones"));
  const card = page.locator("[data-slot=card]").filter({ hasText: "Expo QA" }).first();
  await expect(card.getByText("Inscripciones abiertas")).toBeVisible();

  await card.getByRole("button", { name: "Entregables" }).nth(1).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("INNPRENDE II");
  await dialog.getByRole("button", { name: "Agregar entregable" }).click();
  await dialog.getByLabel("Nombre").fill(name);
  await dialog.getByLabel("Archivos aceptados").selectOption("ANY");
  await dialog.getByLabel("Máximo de archivos").fill("2");
  await dialog.getByRole("button", { name: "Agregar", exact: true }).click();
  await expect(dialog.getByText(name)).toBeVisible();

  await dialog.getByRole("button", { name: `Eliminar ${name}` }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar" }).click();
  await expect(dialog.getByText(name)).toBeHidden();
  await dialog.getByRole("button", { name: "Listo" }).click();
  await expect(dialog).toBeHidden();
});

test("MacondoLab no puede crear una edición que se solape con la QA", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/ediciones"));
  await page.getByRole("button", { name: "Nueva edición" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/Nombre/).fill("QA E2E Solapada");
  const today = new Date().toLocaleDateString("en-CA", { timeZone: "America/Bogota" });
  for (const input of await dialog.locator('input[type="date"]').all()) await input.fill(today);
  await dialog.getByRole("button", { name: /Crear|Guardar/ }).click();
  await expect(dialog.getByRole("alert").first()).toBeVisible();
  await expect(dialog).toBeVisible();
});
