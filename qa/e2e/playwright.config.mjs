import { defineConfig, devices } from "@playwright/test";

// Pruebas de punta a punta contra la plataforma levantada con Docker (ver ../README.md).
// Las cuentas salen de ../seed.mjs y la edición «Expo QA», de ../smoke.mjs.
// Un solo worker: los recorridos comparten esa edición y las cuentas de prueba.
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 8_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.QA_APP ?? "http://localhost:8080/expoideas/",
    locale: "es-CO",
    timezoneId: "America/Bogota",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    ...devices["Desktop Chrome"],
  },
});
