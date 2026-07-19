import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "/",
  build: {
    outDir: new URL("../src/main/resources/assets/progressivestages/editor", import.meta.url).pathname,
    emptyOutDir: true,
    sourcemap: false,
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        entryFileNames: "app.js",
        assetFileNames: asset => asset.name?.endsWith(".css") ? "app.css" : "asset.[ext]",
        chunkFileNames: "chunk.[hash].js"
      }
    }
  }
});
