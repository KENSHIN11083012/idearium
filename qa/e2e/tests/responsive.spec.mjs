import { expect, loginAs, path, test } from "../fixtures.mjs";

// Lo responsive se verifica en un navegador real (jsdom no calcula diseño).
test.use({ viewport: { width: 375, height: 812 }, hasTouch: true, isMobile: true });

const noHorizontalScroll = async (page, label) => {
  await page.waitForLoadState("networkidle");
  const { scroll, client } = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    client: document.documentElement.clientWidth,
  }));
  expect(scroll, `${label}: scrollWidth ${scroll} > ${client}`).toBeLessThanOrEqual(client);
};

test("páginas públicas a 375 px", async ({ page }) => {
  for (const p of ["/", "/iniciar-sesion", "/registro"]) {
    await page.goto(path(p));
    await noHorizontalScroll(page, p);
  }
  await page.goto(path("/")); // las páginas de acceso no llevan cabecera: el menú está en la portada
  await expect(page.getByRole("button", { name: "Abrir menú", exact: true })).toBeVisible();
});

test("estudiante a 375 px", async ({ page }) => {
  await loginAs(page, "estudiante1");
  for (const p of ["/", "/mis-proyectos", "/perfil", "/seguridad"]) {
    await page.goto(path(p));
    await noHorizontalScroll(page, p);
  }
});

test("MacondoLab a 375 px", async ({ page }) => {
  await loginAs(page, "macondolab");
  for (const p of ["/admin/usuarios", "/admin/ediciones", "/admin/catalogos", "/proyectos"]) {
    await page.goto(path(p));
    await noHorizontalScroll(page, p);
  }
});

test("el menú móvil abre la navegación y permite cerrar sesión", async ({ page }) => {
  await loginAs(page, "macondolab");
  await page.getByRole("button", { name: "Abrir menú", exact: true }).click();
  const sheet = page.getByRole("dialog");
  await expect(sheet.getByRole("navigation", { name: "Principal" })).toBeVisible();
  await sheet.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL(/iniciar-sesion/);
});

