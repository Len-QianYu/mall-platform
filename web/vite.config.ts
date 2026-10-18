import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import ElementPlus from 'unplugin-element-plus/vite'
import tailwindcss from '@tailwindcss/vite'
import svgLoader from 'vite-svg-loader'

// https://vite.dev/config/
export default defineConfig({
  // 配置scss，下载命令为npm install -D sass
  // 下载命令npm install tailwindcss @tailwindcss/vite
  // 下载命令npm install vite-svg-loader --save-dev
  // npm i bootstrap@5.3.0-alpha1
  plugins: [
    vue(),
    vueDevTools(),
    ElementPlus({}),
    tailwindcss(),
    svgLoader(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
