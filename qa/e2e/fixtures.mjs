// Ayudas compartidas por los specs: cuentas, login por la UI y limpieza por la API.
import { expect, test as base } from "@playwright/test";
import { api, loadCreds, login } from "../lib.mjs";

export { expect };
export const creds = loadCreds();
export const stamp = () => Date.now().toString(36);

// Archivos de prueba: se identifican por su firma, no por la extensión.
export const PDF = Buffer.from("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n");
export const PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);
export const FAKE_EXE = Buffer.concat([Buffer.from("MZ\x90\x00"), Buffer.alloc(256)]);

/** Rutas relativas a baseURL (/expoideas/): sin barra inicial. */
export const path = (p) => p.replace(/^\//, "");

/**
 * Llena el formulario de inicio de sesión y espera salir de él.
 *
 * Nginx frena los intentos en serie contra el inicio de sesión, y las pruebas entran muchas veces
 * seguidas desde la misma dirección: si aparece ese aviso, se espera y se vuelve a enviar.
 */
export async function uiLogin(page, email, password) {
  await page.goto(path("/iniciar-sesion"));
  await page.getByLabel("Correo institucional").fill(email);
  await page.getByLabel(field("Contraseña")).fill(password);
  for (let attempt = 1; ; attempt++) {
    await page.getByRole("button", { name: "Iniciar sesión" }).click();
    try {
      await expect(page).not.toHaveURL(/iniciar-sesion/);
      return;
    } catch (error) {
      const slowedDown = await page.getByText(/Demasiados intentos seguidos/).isVisible();
      if (!slowedDown || attempt >= 4) throw error;
      await page.waitForTimeout(3000);
    }
  }
}

/** Cierra la sesión sin pasar por el menú, para entrar con otra cuenta en la misma prueba. */
export async function forgetSession(page) {
  await page.goto(path("/"));
  await page.evaluate(() => localStorage.clear());
}

export const loginAs = (page, key) => uiLogin(page, creds[key].email, creds[key].password);

export async function uiLogout(page) {
  await page.getByRole("button", { name: "Abrir menú de la cuenta" }).click();
  await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
}

/** Token de la API para preparar o limpiar datos sin pasar por la UI. */
export async function apiToken(key) {
  return (await login(creds[key].email, creds[key].password)).token;
}

/** Borra con el administrador las cuentas QA E2E que haya dejado una corrida. */
export async function deleteUsersByEmail(pattern) {
  const token = await apiToken("admin");
  // El listado llega por páginas. Se vuelve a pedir la primera hasta que no quede ninguna que borrar:
  // al borrar, las de la página siguiente pasan a esta.
  const tried = new Set();
  for (;;) {
    const { body } = await api("GET", "/admin/users?search=qa.e2e.&size=200", { token });
    const leftovers = (body.items ?? []).filter((x) => pattern.test(x.email) && !tried.has(x.id));
    if (leftovers.length === 0) return;
    for (const u of leftovers) {
      tried.add(u.id);
      await api("DELETE", `/admin/users/${u.id}`, { token });
    }
  }
}

/** El líder borra sus proyectos cuyo título empiece por el prefijo. */
export async function deleteProjects(studentKey, prefix = "QA E2E") {
  const token = await apiToken(studentKey);
  const { body } = await api("GET", "/projects/mine", { token });
  for (const p of body ?? []) {
    if (p.title.startsWith(prefix)) await api("DELETE", `/projects/${p.id}`, { token });
  }
}

/** Inscribe por la API un proyecto de INNPRENDE I en la edición QA, con docente1 como profesor del grupo. */
export async function createProject(studentKey, title) {
  const edition = await qaEdition();
  const sector = (await api("GET", "/sectors")).body.find((s) => s.name === "QA Tecnología");
  const res = await api("POST", "/projects", {
    token: await apiToken(studentKey),
    json: {
      editionId: edition.id, track: "INNPRENDE_I", title, summary: "Preparado por las pruebas de punta a punta.",
      sectorId: sector.id, teacherId: creds.docente1.id, semester: 5, phone: "3001234567",
    },
  });
  if (res.status >= 300) throw new Error(`No se pudo preparar el proyecto: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body;
}

/** La gestión publica (true) u oculta (false) las notas de INNPRENDE I en la edición QA. */
export async function setGradesPublished(published) {
  const edition = await qaEdition();
  const res = await api(published ? "PUT" : "DELETE", `/editions/${edition.id}/tracks/INNPRENDE_I/grades-publication`, {
    token: await apiToken("macondolab"),
  });
  if (res.status !== 200) throw new Error(`No se pudo cambiar la publicación de notas: ${res.status}`);
}

export async function qaEdition() {
  const { body } = await api("GET", "/editions");
  const edition = body.find((e) => e.name === "Expo QA");
  if (!edition) throw new Error('Falta la edición "Expo QA": corre antes `node qa/smoke.mjs`');
  return edition;
}

export const test = base;
export { api };

/** Etiqueta exacta de un campo, con o sin el asterisco de obligatorio que forma parte de su nombre. */
export const field = (text) => new RegExp(`^${text}(\\s*\\*)?$`);
