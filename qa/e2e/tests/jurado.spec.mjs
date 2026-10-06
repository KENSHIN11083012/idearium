import { createProject, creds, deleteProjects, expect, loginAs, path, stamp, test } from "../fixtures.mjs";

// Un solo recorrido encadenado: la gestión asigna, el jurado ve, la gestión quita.
test.describe.configure({ mode: "serial" });

const title = `QA E2E Jurado ${stamp()}`;
const juror = `${creds.jurado.firstName} ${creds.jurado.lastName}`;
let projectPath;

test.beforeAll(async () => {
  await deleteProjects("estudiante4", "QA ");
  const project = await createProject("estudiante4", title);
  projectPath = path(`/proyectos/${project.id}`);
});
test.afterAll(() => deleteProjects("estudiante4"));

test("MacondoLab asigna un jurado desde la ficha; al profesor del grupo no lo deja", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(projectPath);
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByText("Este proyecto todavía no tiene jurados.")).toBeVisible();
  const correo = page.getByLabel("Asignar jurado");

  // Un profesor puede ser jurado de proyectos de otros, nunca del suyo.
  await correo.fill(creds.docente1.email);
  await page.getByRole("button", { name: "Asignar" }).click();
  await expect(correo).toHaveAttribute("aria-invalid", "true");
  await expect(page.getByText(/no puede ser jurado de su propio proyecto/)).toBeVisible();

  await correo.fill(creds.jurado.email);
  await page.getByRole("button", { name: "Asignar" }).click();
  const jurors = page.getByRole("list", { name: "Jurados" });
  await expect(jurors.getByRole("listitem").filter({ hasText: creds.jurado.email })).toContainText(juror);
});

test("el jurado encuentra el proyecto en Evaluar y abre su ficha", async ({ page }) => {
  await loginAs(page, "jurado");
  await page.goto(path("/jurado/proyectos"));
  await expect(page.getByRole("heading", { name: "Proyectos por evaluar" })).toBeVisible();
  const card = page.locator("[data-slot=card]").filter({ hasText: title });
  await expect(card.getByText("Pendiente")).toBeVisible();
  await expect(card.getByRole("link", { name: "Calificar" })).toBeVisible();

  await card.getByRole("link", { name: /Ver proyecto/ }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Entregables" })).toBeVisible();
  // Quién más califica no es asunto del jurado: el panel de jurados es de la gestión.
  await expect(page.getByLabel("Asignar jurado")).toHaveCount(0);
});

test("quien no es jurado del proyecto no lo tiene en su lista ni puede abrirlo", async ({ page }) => {
  await loginAs(page, "docente2");
  await page.goto(path("/jurado/proyectos"));
  await expect(page.getByRole("heading", { name: "Proyectos por evaluar" })).toBeVisible();
  await expect(page.getByText(title)).toHaveCount(0);

  await page.goto(projectPath);
  await expect(page.getByText("No pudimos cargar el proyecto")).toBeVisible();
  await page.goto(`${projectPath.replace("proyectos/", "jurado/proyectos/")}/calificar`);
  await expect(page.getByText("No puedes calificar este proyecto")).toBeVisible();
});

test("MacondoLab quita al jurado y el proyecto sale de su lista", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(projectPath);
  await page.getByRole("button", { name: `Quitar a ${juror} como jurado` }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Quitar" }).click();
  await expect(page.getByText("Este proyecto todavía no tiene jurados.")).toBeVisible();
});
