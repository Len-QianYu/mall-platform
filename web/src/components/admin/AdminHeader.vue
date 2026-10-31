<template>
  <header class="admin-header">
    <div class="header-left">
      <!-- 折叠/展开侧边栏 -->
      <ElButton
        class="header-btn"
        text
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="emit('toggleSidebar')"
      >
        <i :class="collapsed ? 'bi bi-text-indent-left' : 'bi bi-text-indent-right'"></i>
      </ElButton>

      <!-- 刷新当前页面 -->
      <ElButton class="header-btn" text aria-label="刷新" @click="emit('refresh')">
        <i class="bi bi-arrow-clockwise"></i>
      </ElButton>

      <!-- 面包屑：取当前路由 meta 上的 breadcrumbParent / breadcrumbCurrent -->
      <ElBreadcrumb class="header-breadcrumb">
        <ElBreadcrumbItem :to="{ name: 'admin_dashboard' }">管理后台</ElBreadcrumbItem>
        <ElBreadcrumbItem v-if="route.meta.breadcrumbParent">
          {{ route.meta.breadcrumbParent }}
        </ElBreadcrumbItem>
        <ElBreadcrumbItem>{{ route.meta.breadcrumbCurrent }}</ElBreadcrumbItem>
      </ElBreadcrumb>
    </div>

    <div class="header-right">
      <!-- 全屏 -->
      <ElButton
        class="header-btn"
        text
        :aria-label="isFullscreen ? '退出全屏' : '全屏'"
        @click="toggleFullscreen"
      >
        <i :class="isFullscreen ? 'bi bi-fullscreen-exit' : 'bi bi-fullscreen'"></i>
      </ElButton>

      <!-- 亮色 / 暗色切换：图标表示点击后切换到的模式，亮色时显示月亮，暗色时显示太阳 -->
      <ElButton
        class="header-btn"
        text
        :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
        @click="toggleTheme"
      >
        <i :class="isDark ? 'bi bi-sun' : 'bi bi-moon-stars'"></i>
      </ElButton>

      <!-- 用户菜单 -->
      <ElDropdown trigger="click" @command="handleCommand">
        <div class="user-box">
          <ElAvatar :size="30" :src="avatarUrl">
            {{ userStore.name.charAt(0).toUpperCase() }}
          </ElAvatar>
          <span class="user-name">{{ userStore.name }}</span>
          <ElTag size="small">管理员</ElTag>
        </div>
        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem command="logout">
              <i class="bi bi-box-arrow-right dropdown-icon"></i>
              <span>退出登录</span>
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAvatar,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElMessage,
  ElTag,
} from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/utils/useTheme'

defineProps<{ collapsed: boolean }>()

const emit = defineEmits<{
  toggleSidebar: []
  refresh: []
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggleTheme } = useTheme()

// 没有头像时后端返回 null，交给 ElAvatar 显示用户名首字母
const avatarUrl = computed(() => userStore.user?.user.avatarUrl || undefined)

// 全屏状态以 fullscreenchange 事件为准：按 Esc 退出全屏不会经过按钮
const isFullscreen = ref(false)

const syncFullscreen = () => {
  isFullscreen.value = document.fullscreenElement !== null
}

const toggleFullscreen = () => {
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => undefined)
  } else {
    document.documentElement.requestFullscreen().catch(() => undefined)
  }
}

onMounted(() => {
  syncFullscreen()
  document.addEventListener('fullscreenchange', syncFullscreen)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
})

const handleCommand = (command: string | number | object) => {
  if (command === 'logout') {
    logout()
  }
}

// 退出登录：JWT 无状态，后端 /logout 目前只是占位，请求成功与否都清掉本地登录状态
const logout = () => {
  request
    .post('/logout')
    .catch(() => undefined)
    .finally(() => {
      userStore.logout()
      ElMessage.success('已退出登录')
      router.replace({ name: 'login' })
    })
}
</script>

<style lang="scss" scoped>
.admin-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 1rem 0 0.5rem;
  border-bottom: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);

  // 顶栏图标按钮；margin 抵消 .el-button + .el-button 的左外边距
  .header-btn {
    width: 32px;
    height: 32px;
    margin: 0;
    padding: 0;
    font-size: 1.125rem;
  }
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.header-breadcrumb {
  margin-left: 0.5rem;
}

.user-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: 0.5rem;
  padding: 0.25rem 0.5rem;
  border-radius: 9999px;
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover {
    background-color: var(--el-fill-color-light);
  }
}

.user-name {
  color: var(--el-text-color-primary);
  font-size: 0.875rem;
}

.dropdown-icon {
  margin-right: 0.5rem;
}
</style>
