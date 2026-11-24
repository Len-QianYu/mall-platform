<template>
  <header class="shop-header">
    <div class="shop-container header-inner" :class="{ 'is-search-expanded': searchExpanded }">
      <RouterLink class="logo-link" :to="{ name: 'index' }">
        <img src="@/assets/logo/logo.png" class="logo-img" alt="Prompt Pals" />
      </RouterLink>

      <nav class="header-nav" :aria-label="t('商城导航')">
        <RouterLink :to="{ name: 'index' }" class="nav-link" exact-active-class="is-active">
          {{ t('首页') }}
        </RouterLink>
        <RouterLink
          :to="{ name: 'products' }"
          class="nav-link"
          :class="{ 'is-active': route.name === 'product_detail' }"
          :aria-current="
            route.name === 'product_detail'
              ? 'location'
              : route.name === 'products'
                ? 'page'
                : undefined
          "
          active-class="is-active"
        >
          {{ t('商品') }}
        </RouterLink>
        <RouterLink :to="{ name: 'seckill' }" class="nav-link" active-class="is-active">
          {{ t('秒杀') }}
        </RouterLink>
        <RouterLink :to="{ name: 'about' }" class="nav-link" active-class="is-active">
          {{ t('关于我们') }}
        </RouterLink>
      </nav>

      <div class="header-actions">
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
            :aria-label="searchExpanded ? t('搜索商品') : t('展开商品搜索')"
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
            :placeholder="t('搜索商品')"
            :aria-label="t('搜索商品')"
            clearable
          />
        </form>

        <div class="header-right">
          <div class="header-tools">
            <!-- 亮色 / 暗色切换：图标表示点击后切换到的模式 -->
            <ElButton
              class="header-btn header-tool"
              text
              :aria-label="isDark ? t('切换到亮色模式') : t('切换到暗色模式')"
              :title="isDark ? t('切换到亮色模式') : t('切换到暗色模式')"
              @click="toggleTheme"
            >
              <i
                class="header-icon"
                :class="isDark ? 'bi bi-sun' : 'bi bi-moon-stars'"
                aria-hidden="true"
              ></i>
            </ElButton>

            <LanguageSwitch icon-only class="header-tool" />

            <NotificationDropdown v-slot="{ open }">
              <button
                type="button"
                class="header-tool notification-link"
                :aria-label="t('消息通知')"
                :title="t('消息通知')"
                :aria-expanded="open"
                aria-haspopup="dialog"
              >
                <ElBadge
                  :value="notificationsStore.unreadCount"
                  :hidden="notificationsStore.unreadCount === 0"
                  :max="99"
                >
                  <i class="bi bi-bell header-icon" aria-hidden="true"></i>
                </ElBadge>
              </button>
            </NotificationDropdown>

            <!-- 购物车：未登录时点击会被路由守卫带去登录页 -->
            <RouterLink
              :to="{ name: 'cart' }"
              class="header-tool cart-link"
              :aria-label="t('购物车')"
              :title="t('购物车')"
            >
              <ElBadge :value="cartStore.count" :hidden="cartStore.count === 0" :max="99">
                <i class="bi bi-cart3 header-icon" aria-hidden="true"></i>
              </ElBadge>
            </RouterLink>
          </div>

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
                  <span>{{ t('我的收藏') }}</span>
                </ElDropdownItem>
                <ElDropdownItem command="orders">
                  <i class="bi bi-receipt dropdown-icon"></i>
                  <span>{{ t('我的订单') }}</span>
                </ElDropdownItem>
                <ElDropdownItem command="profile">
                  <i class="bi bi-person dropdown-icon"></i>
                  <span>{{ t('个人中心') }}</span>
                </ElDropdownItem>
                <ElDropdownItem v-if="userStore.isAdmin" command="admin">
                  <i class="bi bi-speedometer2 dropdown-icon"></i>
                  <span>{{ t('管理后台') }}</span>
                </ElDropdownItem>
                <ElDropdownItem command="logout" divided>
                  <i class="bi bi-box-arrow-right dropdown-icon"></i>
                  <span>{{ t('退出登录') }}</span>
                </ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>

          <ElButton v-else class="header-btn" text :aria-label="t('登录')" @click="toLogin">
            <i class="bi bi-person header-icon" aria-hidden="true"></i>
          </ElButton>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
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
import LanguageSwitch from '@/components/LanguageSwitch.vue'
import NotificationDropdown from '@/components/shop/NotificationDropdown.vue'

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
      ElMessage.success(t('已退出登录'))
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
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
  align-items: center;
  align-self: stretch;
  gap: 1.25rem;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1.5rem;
  min-width: 0;
}

.nav-link {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0.5rem 0;
  color: var(--el-text-color-regular);
  font-size: 0.9375rem;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.15s;

  &::after {
    position: absolute;
    right: 0;
    bottom: 2px;
    left: 0;
    height: 3px;
    border-radius: 3px 3px 0 0;
    background-color: var(--el-color-primary);
    content: '';
    transform: scaleX(0);
    transition: transform 0.2s ease;
  }

  &:hover,
  &.is-active {
    color: var(--el-color-primary);
  }

  &.is-active {
    font-weight: 600;

    &::after {
      transform: scaleX(1);
    }
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 4px;
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

.header-tools {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.5rem;
}

.header-tool {
  --el-button-text-color: var(--el-text-color-regular);
  --el-button-hover-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: var(--el-fill-color-light);

  display: inline-flex;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  width: 32px;
  min-width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: var(--el-border-radius-base);
  background-color: transparent;
  color: var(--el-text-color-regular);
  font-size: 1.125rem;
  line-height: 1;
  text-decoration: none;
  cursor: pointer;
  transition:
    color 0.15s,
    background-color 0.15s;

  .el-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1em;
    height: 1em;
    line-height: 1;
  }

  &:hover,
  &:focus-visible {
    background-color: var(--el-fill-color-light);
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}

.header-tools :deep(.el-button > span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.header-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  font-size: 1.125rem;
  line-height: 1;

  &::before {
    display: block;
    line-height: 1;
    vertical-align: 0;
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

// 窄屏：导航放到第二行，保留全部入口和当前页面指示。
@media (max-width: 960px) {
  .header-inner {
    display: flex;
    flex-wrap: wrap;
    gap: 0 0.75rem;
    height: auto;
    padding-top: 0.5rem;

    &.is-search-expanded {
      .header-search {
        order: 1;
        width: 100%;
        max-width: none;
        margin-top: 0.75rem;
      }

      .header-nav {
        order: 2;
      }

      .header-right {
        margin-left: auto;
      }
    }
  }

  .header-actions {
    display: contents;
  }

  .header-nav {
    order: 1;
    justify-content: center;
    gap: 1.5rem;
    width: 100%;
    height: 44px;
  }

  .logo-img {
    width: 28px;
    height: 28px;
    object-fit: cover;
  }

  .user-name {
    display: none;
  }
}

@media (max-width: 480px) {
  .header-inner {
    gap: 0 0.5rem;
  }

  .header-nav {
    gap: 1.25rem;
  }

  .header-right {
    gap: 0.25rem;
  }

  .header-tools {
    gap: 0.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nav-link,
  .nav-link::after,
  .header-search,
  .search-toggle,
  .header-tool {
    transition: none;
  }
}
</style>
