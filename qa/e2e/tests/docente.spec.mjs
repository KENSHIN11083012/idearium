import { readFileSync } from "node:fs";
import { createProject, creds, deleteProjects, expect, loginAs, path, stamp, test } from "../fixtures.mjs";

const title = `QA E2E Docente ${stamp()}`;

// El proyecto se prepara por la API: aquí se prueba la vista del profesor, no la inscripción.
test.beforeAll(async () => {
  await deleteProjects("estudiante3", "QA ");
  await createProject("estudiante3", title);
});
test.afterAll(() => deleteProjects("estudiante3"));

test("docente1 lo ve, lo filtra y descarga el CSV", async ({ page }) => {
  await loginAs(page, "docente1");
  await page.goto(path("/proyectos"));
  await expect(page.getByRole("heading", { name: "Proyectos" })).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: title })).toBeVisible();
  await expect(page.getByLabel("Profesor", { exact: true })).toHaveCount(0); // el filtro por profesor es de la gestión

  await page.getByLabel("Cátedra").selectOption("INNPRENDE_II");
  await expect(page.getByRole("row").filter({ hasText: title })).toHaveCount(0);
  await page.getByLabel("Cátedra").selectOption("INNPRENDE_I");
  await expect(page.getByRole("row").filter({ hasText: title })).toBeVisible();

  await page.getByLabel("Buscar proyectos").fill("no-existe-nada");
  await expect(page.getByText("Sin resultados")).toBeVisible();
  await page.getByLabel("Buscar proyectos").fill("QA E2E Docente");
  await expect(page.getByRole("row").filter({ hasText: title })).toBeVisible();

  const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Descargar CSV" }).click()]);
  expect(download.suggestedFilename()).toMatch(/^proyectos-\d{4}-\d{2}-\d{2}\.csv$/);
  const csv = readFileSync(await download.path(), "utf8");
  expect(csv).toContain(title);
});

test("docente2 no lo ve y no puede descargar", async ({ page }) => {
  await loginAs(page, "docente2");
  await page.goto(path("/proyectos"));
  await expect(page.getByText(title)).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Descargar CSV" })).toBeDisabled();
});

test("MacondoLab lo ve y filtra por profesor", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(path("/proyectos"));
  await page.getByLabel("Profesor", { exact: true }).selectOption(String(creds.docente2.id));
  await expect(page.getByRole("row").filter({ hasText: title })).toHaveCount(0);
  await page.getByLabel("Profesor", { exact: true }).selectOption(String(creds.docente1.id));
  await expect(page.getByRole("row").filter({ hasText: title })).toBeVisible();
});
