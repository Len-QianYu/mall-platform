import { createApp } from 'vue'
import { createPinia } from 'pinia'

import 'element-plus/dist/index.css'
// Element Plus 暗色主题变量，<html> 带 dark 类时生效；要放在 rootConfig 之前，让那里的暗色主题色覆盖它
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/element/rootConfig.scss'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注意：pinia 必须在 router 之前注册，路由守卫中会使用 store
app.use(createPinia())
app.use(router)

app.mount('#app')
