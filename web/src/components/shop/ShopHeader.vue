<template>
  <header class="shop-header">
    <div class="shop-container header-inner" :class="{ 'is-search-expanded': searchExpanded }">
      <RouterLink class="logo-link" :to="{ name: 'index' }">
        <img src="@/assets/logo/logo.png" class="logo-img" alt="Mall Platform" />
      </RouterLink>

      <nav class="header-nav">
        <RouterLink :to="{ name: 'index' }" class="nav-link" exact-active-class="is-active">
          首页
        </RouterLink>
        <RouterLink :to="{ name: 'products' }" class="nav-link" active-class="is-active">
          全部商品
        </RouterLink>
        <RouterLink :to="{ name: 'seckill' }" class="nav-link" active-class="is-active">
          限时秒杀
        </RouterLink>
      </nav>

      <!-- 搜索：跳到商品列表并带上关键字；在商品列表页时回显当前关键字 -->
      <form
        class="header-search"
        :class="{ 'is-expanded': searchExpanded }"
        @submit.prevent="search"
        @keydown.esc.prevent="closeSearch"
        @focusout="onSearchFocusOut"
      >
        <button
          ref="searchButton"
          type="button"
          class="search-toggle"
          :aria-label="searchExpanded ? '搜索商品' : '展开商品搜索'"
          :aria-expanded="searchExpanded"
          aria-controls="shop-header-search-input"
          @click="onSearchClick"
        >
          <i class="bi bi-search" aria-hidden="true"></i>
        </button>
        <ElInput
          v-show="searchExpanded"
          id="shop-header-search-input"
          ref="searchInput"
          v-model="keyword"
          class="search-input"
          placeholder="搜索商品"
          aria-label="搜索商品"
          clearable
        />
      </form>

      <div class="header-right">
        <RouterLink :to="{ name: 'seckill' }" class="seckill-mobile-link" aria-label="限时秒杀">
          <i class="bi bi-lightning-charge"></i>
        </RouterLink>
        <!-- 亮色 / 暗色切换：图标表示点击后切换到的模式 -->
        <ElButton
          class="header-btn"
          text
          :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
          @click="toggleTheme"
        >
          <i :class="isDark ? 'bi bi-sun' : 'bi bi-moon-stars'"></i>
        </ElButton>

        <RouterLink :to="{ name: 'notifications' }" class="notification-link" aria-label="消息通知">
          <ElBadge
            :value="notificationsStore.unreadCount"
            :hidden="notificationsStore.unreadCount === 0"
            :max="99"
          >
            <i class="bi bi-bell" aria-hidden="true"></i>
          </ElBadge>
        </RouterLink>

        <!-- 购物车：未登录时点击会被路由守卫带去登录页 -->
        <RouterLink :to="{ name: 'cart' }" class="cart-link" aria-label="购物车">
          <ElBadge :value="cartStore.count" :hidden="cartStore.count === 0" :max="99">
            <i class="bi bi-cart3"></i>
          </ElBadge>
        </RouterLink>

        <ElDropdown v-if="userStore.logged" trigger="click" @command="handleCommand">
          <div class="user-box">
            <ElAvatar :size="30" :src="avatarUrl">
              {{ userStore.name.charAt(0).toUpperCase() }}
            </ElAvatar>
            <span class="user-name">{{ userStore.name }}</span>
          </div>
          <template #dropdown>
            <ElDropdownMenu>
              <ElDropdownItem command="favorites">
                <i class="bi bi-heart dropdown-icon"></i>
                <span>我的收藏</span>
              </ElDropdownItem>
              <ElDropdownItem command="orders">
                <i class="bi bi-receipt dropdown-icon"></i>
                <span>我的订单</span>
              </ElDropdownItem>
              <ElDropdownItem command="profile">
                <i class="bi bi-person dropdown-icon"></i>
                <span>个人中心</span>
              </ElDropdownItem>
              <ElDropdownItem v-if="userStore.isAdmin" command="admin">
                <i class="bi bi-speedometer2 dropdown-icon"></i>
                <span>管理后台</span>
              </ElDropdownItem>
              <ElDropdownItem command="logout" divided>
                <i class="bi bi-box-arrow-right dropdown-icon"></i>
                <span>退出登录</span>
              </ElDropdownItem>
            </ElDropdownMenu>
          </template>
        </ElDropdown>

        <template v-else>
          <ElButton type="primary" @click="toLogin">登录</ElButton>
          <ElButton @click="router.push({ name: 'register' })">注册</ElButton>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ElAvatar,
  ElBadge,
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElInput,
  ElMessage,
  type InputInstance,
} from 'element-plus'
import request from '@/utils/request'
import { assetUrl } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { useNotificationsStore } from '@/stores/notifications'
import { useTheme } from '@/utils/useTheme'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const notificationsStore = useNotificationsStore()
const { isDark, toggleTheme } = useTheme()

// 没有头像时为 undefined，交给 ElAvatar 显示用户名首字母
const avatarUrl = computed(() => assetUrl(userStore.user?.user.avatarUrl))

// 登录状态变化时同步购物车角标：登录后拉取，退出后清零（401 被拦截器登出时也会走到这里）
watch(
  () => userStore.logged,
  (logged) => {
    if (logged) {
      cartStore.refresh()
    } else {
      cartStore.reset()
    }
  },
  { immediate: true },
)

const keyword = ref('')
const searchExpanded = ref(false)
const searchInput = ref<InputInstance>()
const searchButton = ref<HTMLButtonElement>()

const onSearchClick = async () => {
  if (searchExpanded.value) {
    search()
    return
  }

  searchExpanded.value = true
  await nextTick()
  searchInput.value?.focus()
}

const closeSearch = () => {
  searchExpanded.value = false
  searchButton.value?.focus()
}

const onSearchFocusOut = (event: FocusEvent) => {
  if (
    event.currentTarget instanceof HTMLElement &&
    event.relatedTarget instanceof Node &&
    event.currentTarget.contains(event.relatedTarget)
  ) {
    return
  }
  searchExpanded.value = false
}

// 输入框跟随地址栏：从商品列表页带关键字进来、或在列表页清掉关键字时同步
watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = route.name === 'products' && typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

const search = () => {
  const value = keyword.value.trim()
  router.push({ name: 'products', query: value ? { keyword: value } : {} })
}

// 登录后回到当前页
const toLogin = () => {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

const handleCommand = (command: string | number | object) => {
  if (command === 'orders' || command === 'profile' || command === 'favorites') {
    router.push({ name: command })
  } else if (command === 'admin') {
    router.push({ name: 'admin_dashboard' })
  } else if (command === 'logout') {
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
      // 留在需要登录的页面上没有意义，回首页
      if (route.meta.requiresAuth) {
        router.replace({ name: 'index' })
      }
    })
}
</script>

<style lang="scss" scoped>
.shop-header {
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  height: 60px;
}

.logo-link {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  text-decoration: none;
}

.logo-img {
  display: block;
  width: 10rem;
  height: auto;
  max-height: 50px;
  object-fit: contain;
  object-position: left;
}

.header-nav {
  display: flex;
  flex-shrink: 0;
  gap: 1.25rem;
}

.nav-link {
  color: var(--el-text-color-regular);
  font-size: 0.9375rem;
  text-decoration: none;
  transition: color 0.15s;

  &:hover,
  &.is-active {
    color: var(--el-color-primary);
  }

  &.is-active {
    font-weight: 600;
  }
}

.header-search {
  position: relative;
  flex: 0 1 auto;
  width: 32px;
  min-width: 32px;
  max-width: 360px;
  height: 32px;
  margin-left: auto;
  transition: width 0.25s ease;

  &.is-expanded {
    width: 360px;
  }
}

.search-toggle {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 1.125rem;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;

  &:hover {
    background: var(--el-fill-color-light);
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }
}

.search-input {
  width: 100%;
  height: 100%;

  :deep(.el-input__wrapper) {
    padding-left: 36px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .header-search,
  .search-toggle {
    transition: none;
  }
}

.header-right {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.75rem;

  // margin 抵消 .el-button + .el-button 的左外边距，间距统一由 gap 控制
  .el-button {
    margin: 0;
  }

  .header-btn {
    width: 32px;
    height: 32px;
    padding: 0;
    font-size: 1.125rem;
  }
}

.seckill-mobile-link {
  display: none;
  color: var(--el-color-danger);
  font-size: 1.25rem;
  text-decoration: none;
}

.cart-link,
.notification-link {
  display: flex;
  padding: 0 0.25rem;
  color: var(--el-text-color-regular);
  font-size: 1.25rem;
  text-decoration: none;

  &:hover {
    color: var(--el-color-primary);
  }
}

.user-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
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

// 窄屏：隐藏导航文字和用户名，给搜索框留位置
@media (max-width: 768px) {
  .seckill-mobile-link {
    display: flex;
  }
  .header-inner {
    gap: 0.75rem;

    &.is-search-expanded {
      flex-wrap: wrap;
      height: auto;
      padding-top: 0.875rem;
      padding-bottom: 0.875rem;

      .header-search {
        order: 1;
        width: 100%;
        max-width: none;
      }

      .header-right {
        margin-left: auto;
      }
    }
  }

  .logo-img {
    width: 28px;
    height: 28px;
    object-fit: cover;
  }

  .header-nav,
  .user-name {
    display: none;
  }
}

@media (max-width: 480px) {
  .header-inner {
    gap: 0.5rem;
  }

  .header-right {
    gap: 0.25rem;

    .el-button {
      padding-right: 0.5rem;
      padding-left: 0.5rem;
    }

    .header-btn {
      padding: 0;
    }
  }

  .cart-link,
  .notification-link {
    padding: 0 0.125rem;
  }
}
</style>
