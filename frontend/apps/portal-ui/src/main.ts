import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@fenglin/design-tokens/tokens.css'
import '@fenglin/design-tokens/themes/float.css'
import { permissionDirective } from '@fenglin/ui-admin'
import { setUnauthorizedHandler } from '@fenglin/api-client'
import App from './App.vue'
import router from './router'
import './styles.css'
import './styles/theme-float.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.directive('perm', permissionDirective)

setUnauthorizedHandler(() => {
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

app.mount('#app')
