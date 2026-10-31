import { createApp } from 'vue'
import { createPinia } from 'pinia'

import 'element-plus/dist/index.css'
import '@/styles/element/rootConfig.scss'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注意：pinia 必须在 router 之前注册，路由守卫中会使用 store
app.use(createPinia())
app.use(router)

app.mount('#app')
