import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  use: { baseURL: "http://127.0.0.1:5174", trace: "retain-on-failure" },
  webServer: [
    {
      command:
        process.platform === "win32"
          ? "..\\backend\\mvnw.cmd -f ..\\backend\\pom.xml spring-boot:run"
          : "../backend/mvnw -f ../backend/pom.xml spring-boot:run",
      url: "http://127.0.0.1:8081/api/ducks",
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
    {
      command: "npm run dev",
      url: "http://127.0.0.1:5174",
      reuseExistingServer: !process.env.CI,
      timeout: 30000,
    },
  ],
});
