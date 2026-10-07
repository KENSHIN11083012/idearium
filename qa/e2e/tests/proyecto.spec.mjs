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
  await dialog.getByLabel("Título del proyecto de innovación").fill(title);
  await dialog.getByLabel("Propuesta de valor").fill("Probado de punta a punta desde el navegador.");
  await dialog.getByLabel("Sector").selectOption({ label: "QA Tecnología" });
  await dialog.getByLabel("Profesor del grupo").selectOption(String(creds.docente1.id));
  await dialog.getByLabel("Semestre que cursas").selectOption("5");
  await dialog.getByLabel("Tu celular").fill("300 123 4567");
  await dialog.getByRole("button", { name: "Inscribir" }).click();
  await expect(page.getByText(`"${title}" quedó inscrito`)).toBeVisible();

  await page.goto(path("/mis-proyectos"));
  await page.locator("[data-slot=card]").filter({ hasText: title }).getByRole("link", { name: /Ver proyecto/ }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  // El equipo ve cuánto le queda para entregar.
  await expect(page.getByText(/para entregar/)).toBeVisible();
  projectUrl = page.url();
});

test("el líder invita: un profesor no se puede; a su compañero lo busca por el nombre", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(projectUrl);
  const correo = page.getByLabel("Invitar a un compañero");

  await correo.fill(creds.docente1.email);
  await page.getByRole("button", { name: "Invitar" }).click();
  await expect(correo).toHaveAttribute("aria-invalid", "true");

  // No hace falta saberse el correo: se escribe el nombre y se elige de la lista.
  await correo.fill("Estudiante2");
  const found = page.getByRole("list", { name: "Compañeros encontrados" });
  await expect(found.getByText(creds.estudiante2.email)).toHaveCount(0); // el correo completo no sale
  await found.getByRole("button", { name: "Invitar a Estudiante2 QA" }).click();
  await expect(correo).toHaveValue("");
  const team = page.getByRole("listitem").filter({ hasText: creds.estudiante2.email });
  await expect(team.getByText("Invitación enviada")).toBeVisible();
});

test("estudiante2 acepta la invitación desde Mis proyectos", async ({ page }) => {
  await loginAs(page, "estudiante2");
  await page.goto(path("/mis-proyectos"));
  await expect(page.getByRole("heading", { name: "Invitaciones" })).toBeVisible();
  const invitation = page.locator("[data-slot=card]").filter({ hasText: title });
  // Para entrar al equipo dice el semestre que cursa.
  await invitation.getByRole("button", { name: "Aceptar" }).click();
  await expect(invitation.getByRole("alert")).toBeVisible();
  await invitation.getByLabel("Semestre que cursas").selectOption("3");
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

test("un integrante no se sale por su cuenta: le dice a quién pedírselo", async ({ page }) => {
  await loginAs(page, "estudiante2");
  await page.goto(projectUrl);
  await expect(page.getByText("Para salir de este equipo o cambiarte a otro, pídeselo a MacondoLab.")).toBeVisible();
  await expect(page.getByRole("button", { name: /Salir del equipo|^Quitar a/ })).toHaveCount(0);
});

test("MacondoLab retira al integrante del equipo", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.goto(projectUrl);
  const member = page.getByRole("listitem").filter({ hasText: creds.estudiante2.email });
  await member.getByRole("button", { name: /^Quitar a/ }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Quitar" }).click();
  await expect(member).toHaveCount(0);
  await expect(page.getByText(/^1 de \d+ integrantes/)).toBeVisible();
});

test("el líder elimina la inscripción", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(projectUrl);
  await page.getByRole("button", { name: /Eliminar/ }).first().click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar" }).click();
  await expect(page).toHaveURL(/mis-proyectos/);
  await expect(page.getByText(title)).toBeHidden();
});
