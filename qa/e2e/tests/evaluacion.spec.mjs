import {
  api, apiToken, createProject, creds, deleteProjects, expect, forgetSession, loginAs, path, setGradesPublished, stamp, test,
} from "../fixtures.mjs";

// Un solo recorrido encadenado: calificar, corregir, ver la nota, publicarla.
test.describe.configure({ mode: "serial" });

const title = `QA E2E Evaluación ${stamp()}`;
const NOTICE = "Recuperamos lo que dejaste sin guardar";
let projectPath;
let boardPath;

test.beforeAll(async () => {
  await deleteProjects("estudiante4", "QA ");
  await setGradesPublished(false);
  const project = await createProject("estudiante4", title);
  projectPath = path(`/proyectos/${project.id}`);
  boardPath = path(`/jurado/proyectos/${project.id}/calificar`);
  const assigned = await api("POST", `/projects/${project.id}/jurors`, {
    token: await apiToken("macondolab"), json: { email: creds.jurado.email },
  });
  if (assigned.status !== 201) throw new Error(`No se pudo asignar el jurado: ${assigned.status}`);
});
test.afterAll(async () => {
  await setGradesPublished(false);
  await deleteProjects("estudiante4");
});

/** El criterio de esa posición (desde 0) en el tablero. */
const criterion = (page, index) => page.getByRole("radiogroup").nth(index);
/** Toca la tarjeta del nivel, como quien califica: el radio en sí no se ve, lo que se toca es su tarjeta. */
const choose = (page, index, level) =>
  criterion(page, index)
    .locator("label")
    .filter({ has: page.getByRole("radio", { name: new RegExp(`^${level},`) }) })
    .click();

test("el jurado califica: lo marcado sobrevive a recargar la página y la nota aparece al completar", async ({ page }) => {
  await loginAs(page, "jurado");
  await page.goto(boardPath);
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByRole("radiogroup")).toHaveCount(6);
  await expect(page.getByText("0 de 6 criterios")).toBeVisible();

  await choose(page, 0, "Excelente");
  await choose(page, 1, "Excelente");
  await choose(page, 2, "Excelente");
  const observation = page.getByRole("textbox").first();
  await observation.fill("Bien sustentado, con referencias.");
  await expect(page.getByText("3 de 6 criterios")).toBeVisible();

  // Con cambios sin guardar el navegador pregunta antes de salir; aquí se acepta, como quien recarga sin querer.
  page.on("dialog", (dialog) => dialog.accept());
  await page.reload();

  await expect(page.getByText(NOTICE)).toBeVisible();
  await expect(page.getByText("3 de 6 criterios")).toBeVisible();
  await expect(criterion(page, 0).getByRole("radio", { name: /^Excelente,/ })).toBeChecked();
  await expect(page.getByRole("textbox").first()).toHaveValue("Bien sustentado, con referencias.");

  // Sin todos los criterios no se guarda.
  await page.getByRole("button", { name: "Guardar evaluación" }).click();
  await expect(page.getByText("Elige un nivel para este criterio").first()).toBeVisible();
  await expect(page).toHaveURL(/calificar/);

  await choose(page, 3, "Excelente");
  await choose(page, 4, "Excelente");
  await choose(page, 5, "Excelente");
  await expect(page.getByText("6 de 6 criterios")).toBeVisible();
  await expect(page.getByText(/^Nota:.*5\.0.*Excelente/)).toBeVisible();

  await page.getByRole("button", { name: "Guardar evaluación" }).click();
  await expect(page).toHaveURL(/jurado\/proyectos$/);
  const card = page.locator("[data-slot=card]").filter({ hasText: title });
  await expect(card.getByText("Calificado · 5.0")).toBeVisible();
  await expect(card.getByRole("link", { name: "Corregir calificación" })).toBeVisible();
});

test("al volver ve lo que guardó, sin borrador, y puede corregirlo", async ({ page }) => {
  await loginAs(page, "jurado");
  await page.goto(boardPath);
  await expect(page.getByText(/Ya calificaste este proyecto: 5\.0/)).toBeVisible();
  await expect(page.getByText(NOTICE)).toHaveCount(0);
  await expect(page.getByRole("textbox").first()).toHaveValue("Bien sustentado, con referencias.");

  // (4.5 + 5.0 × 5) / 6 = 4.92, que queda en 4.9.
  await choose(page, 0, "Bueno");
  await page.getByRole("button", { name: "Guardar evaluación" }).click();
  await expect(page).toHaveURL(/jurado\/proyectos$/);
  await expect(page.locator("[data-slot=card]").filter({ hasText: title }).getByText("Calificado · 4.9")).toBeVisible();
});

test("el profesor del grupo ve la nota; el equipo, todavía no", async ({ page }) => {
  await loginAs(page, "docente1");
  await page.goto(projectPath);
  const evaluation = page.getByRole("region", { name: "Evaluación" });
  await expect(evaluation.getByText("Nota del proyecto")).toBeVisible();
  await expect(evaluation.getByText("4.9").first()).toBeVisible();
  await expect(evaluation.getByText("1 de 1 jurados calificaron")).toBeVisible();

  await forgetSession(page);
  await loginAs(page, "estudiante4");
  await page.goto(projectPath);
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Entregables" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Tu evaluación" })).toHaveCount(0);
});

test("MacondoLab publica las notas y el equipo ve la suya, sin el nombre del jurado", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(path("/admin/ediciones"));
  const card = page.locator("[data-slot=card]").filter({ hasText: "Expo QA" }).first();
  await expect(card.getByText("Notas sin publicar: los equipos no las ven").first()).toBeVisible();
  // El primer bloque de la edición es INNPRENDE I.
  await card.getByRole("button", { name: "Publicar notas" }).first().click();
  const confirm = page.getByRole("alertdialog");
  await expect(confirm).toContainText("¿Publicar las notas?");
  await confirm.getByRole("button", { name: "Publicar" }).click();
  await expect(card.getByText(/Notas publicadas el/)).toBeVisible();

  await forgetSession(page);
  await loginAs(page, "estudiante4");
  await page.goto(projectPath);
  const grade = page.getByRole("region", { name: "Tu evaluación" });
  await expect(grade.getByText("Nota final")).toBeVisible();
  await expect(grade.getByText("4.9")).toBeVisible();
  await expect(grade.getByText("«Bien sustentado, con referencias.»")).toBeVisible();
  await expect(page.getByText(creds.jurado.lastName)).toHaveCount(0);
});
