import { creds, deleteProjects, expect, FAKE_EXE, loginAs, path, PDF, stamp, test } from "../fixtures.mjs";

// Un solo recorrido encadenado: cada paso depende del anterior.
test.describe.configure({ mode: "serial" });

const title = `QA E2E Proyecto ${stamp()}`;
let projectUrl;

test.beforeAll(async () => {
  for (const k of ["estudiante1", "estudiante2", "estudiante3", "estudiante4"]) await deleteProjects(k, "QA ");
});
test.afterAll(() => deleteProjects("estudiante1"));

test("estudiante1 inscribe un proyecto en INNPRENDE I", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(path("/mis-proyectos"));
  await page.getByRole("button", { name: "Inscribir proyecto" }).first().click();
  const dialog = page.getByRole("dialog", { name: "Inscribir proyecto" });
  await dialog.getByLabel("Edición").selectOption({ label: "Expo QA" });
  await dialog.getByLabel("Cátedra").selectOption("INNPRENDE_I");
  await dialog.getByLabel("Título del proyecto").fill(title);
  await dialog.getByLabel("Propuesta de valor").fill("Probado de punta a punta desde el navegador.");
  await dialog.getByLabel("Sector").selectOption({ label: "QA Tecnología" });
  await dialog.getByLabel("Profesor del grupo").selectOption(String(creds.docente1.id));
  await dialog.getByRole("button", { name: "Inscribir" }).click();
  await expect(page.getByText(`"${title}" quedó inscrito`)).toBeVisible();

  await page.goto(path("/mis-proyectos"));
  await page.locator("[data-slot=card]").filter({ hasText: title }).getByRole("link", { name: /Ver proyecto/ }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  projectUrl = page.url();
});

test("el líder invita: un profesor no se puede, un estudiante sí", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(projectUrl);
  const correo = page.getByLabel("Invitar a un compañero");

  await correo.fill(creds.docente1.email);
  await page.getByRole("button", { name: "Invitar" }).click();
  await expect(correo).toHaveAttribute("aria-invalid", "true");

  await correo.fill(creds.estudiante2.email);
  await page.getByRole("button", { name: "Invitar" }).click();
  const team = page.getByRole("listitem").filter({ hasText: creds.estudiante2.email });
  await expect(team.getByText("Invitación enviada")).toBeVisible();
});

test("estudiante2 acepta la invitación desde Mis proyectos", async ({ page }) => {
  await loginAs(page, "estudiante2");
  await page.goto(path("/mis-proyectos"));
  await expect(page.getByRole("heading", { name: "Invitaciones" })).toBeVisible();
  const invitation = page.locator("[data-slot=card]").filter({ hasText: title });
  await invitation.getByRole("button", { name: "Aceptar" }).click();
  await expect(page.getByText(`Ya eres del equipo de "${title}"`)).toBeVisible();
  await expect(page.locator("[data-slot=card]").filter({ hasText: title }).getByRole("link", { name: /Ver proyecto/ })).toBeVisible();
});

test("un estudiante ajeno no ve el proyecto", async ({ page }) => {
  await loginAs(page, "estudiante3");
  await page.goto(projectUrl);
  await expect(page.getByRole("heading", { name: title })).toBeHidden();
  await expect(page.getByText(/No pudimos cargar el proyecto|no tienes acceso|no existe/i).first()).toBeVisible();
});

test("estudiante2 sube el póster, rechaza un .exe disfrazado y descarga lo subido", async ({ page }) => {
  await loginAs(page, "estudiante2");
  await page.goto(projectUrl);
  await expect(page.getByRole("heading", { name: "Entregables" })).toBeVisible();

  await page.getByLabel("Subir archivo para Fotos QA").setInputFiles({ name: "foto.png", mimeType: "image/png", buffer: FAKE_EXE });
  await expect(page.locator("[data-sonner-toast][data-type=error]")).toBeVisible();
  await expect(page.getByRole("button", { name: "Descargar foto.png" })).toHaveCount(0);

  await page.getByLabel("Subir archivo para Póster QA").setInputFiles({ name: "poster.pdf", mimeType: "application/pdf", buffer: PDF });
  const descargar = page.getByRole("button", { name: "Descargar poster.pdf" });
  await expect(descargar).toBeVisible();

  const [download] = await Promise.all([page.waitForEvent("download"), descargar.click()]);
  expect(download.suggestedFilename()).toBe("poster.pdf");
});

test("el profesor del grupo ve el proyecto con el entregable completo", async ({ page }) => {
  await loginAs(page, "docente1");
  await page.goto(path("/proyectos"));
  const row = page.getByRole("row").filter({ hasText: title });
  await expect(row).toBeVisible();
  await expect(row).toContainText("2 integrantes");
  await expect(row).toContainText("Entregado");
});

test("el líder elimina la inscripción", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(projectUrl);
  await page.getByRole("button", { name: /Eliminar/ }).first().click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar" }).click();
  await expect(page).toHaveURL(/mis-proyectos/);
  await expect(page.getByText(title)).toBeHidden();
});
