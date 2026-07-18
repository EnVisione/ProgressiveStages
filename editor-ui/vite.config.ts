import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

export default defineConfig({
  plugins: [preact()],
  base: "/",
  build: {
    outDir: new URL("../src/main/resources/assets/progressivestages/editor", import.meta.url).pathname,
    emptyOutDir: false,
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
