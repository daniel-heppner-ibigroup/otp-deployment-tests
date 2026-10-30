import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "/suite-builder/",
  build: {
    outDir: "../../../target/generated-resources/web/suite-builder",
    emptyOutDir: true,
  },
});
