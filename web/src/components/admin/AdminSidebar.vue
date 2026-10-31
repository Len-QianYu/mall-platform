<template>
  <aside class="admin-sidebar" :class="{ 'is-collapsed': collapsed }">
    <!-- 左上角 Logo -->
    <RouterLink class="logo-link" :to="{ name: 'admin_dashboard' }">
      <img src="/favicon.ico" class="logo-img" alt="Mall Platform" />
      <span v-show="!collapsed" class="logo-text">Mall Platform</span>
    </RouterLink>

    <!-- 菜单：index 即路由路径，router 模式下点击直接跳转 -->
    <ElMenu
      class="sidebar-menu"
      :default-active="route.path"
      :default-openeds="['trade']"
      :collapse="collapsed"
      :collapse-transition="false"
      router
    >
      <ElMenuItem index="/admin">
        <i class="bi bi-speedometer2 menu-icon"></i>
        <template #title>仪表盘</template>
      </ElMenuItem>

      <ElMenuItem index="/admin/users">
        <i class="bi bi-people menu-icon"></i>
        <template #title>用户管理</template>
      </ElMenuItem>

      <ElMenuItem index="/admin/products">
        <i class="bi bi-box-seam menu-icon"></i>
        <template #title>商品管理</template>
      </ElMenuItem>

      <ElSubMenu index="trade">
        <template #title>
          <i class="bi bi-wallet2 menu-icon"></i>
          <span>交易管理</span>
        </template>
        <ElMenuItem index="/admin/orders">
          <i class="bi bi-receipt menu-icon"></i>
          <template #title>订单管理</template>
        </ElMenuItem>
        <ElMenuItem index="/admin/payments">
          <i class="bi bi-credit-card menu-icon"></i>
          <template #title>支付流水</template>
        </ElMenuItem>
      </ElSubMenu>
    </ElMenu>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus'

defineProps<{ collapsed: boolean }>()

const route = useRoute()
</script>

<style lang="scss" scoped>
.admin-sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 220px;
  border-right: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
  overflow: hidden;
  transition: width 0.3s ease;

  &.is-collapsed {
    width: 64px;
  }
}

// Logo 区域与顶栏等高
.logo-link {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.5rem;
  height: 50px;
  padding: 0 1rem;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-decoration: none;
}

.logo-img {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.logo-text {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 1.125rem;
  font-weight: 600;
  white-space: nowrap;
}

.sidebar-menu {
  // 覆盖 Element Plus 菜单的 CSS 变量：菜单项更紧凑，悬停用灰底，选中用主题色
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--el-text-color-regular);
  --el-menu-hover-bg-color: var(--el-fill-color-light);
  --el-menu-item-height: 40px;
  --el-menu-sub-item-height: 40px;
  --el-menu-base-level-padding: 12px;
  --el-menu-level-padding: 16px;

  flex: 1;
  width: 100%; // 折叠时也撑满侧边栏，不用 Element Plus 按图标算出的宽度
  padding: 0.5rem;
  border-right: none;
  overflow-x: hidden;
  overflow-y: auto;

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    margin-bottom: 0.25rem;
    border-radius: 6px;
  }

  :deep(.el-menu-item.is-active) {
    background-color: var(--el-color-primary-light-9);
    font-weight: 500;
  }
}

// Bootstrap Icons 按 Element Plus 菜单图标（.el-icon）的尺寸排版
.menu-icon {
  flex-shrink: 0;
  width: var(--el-menu-icon-width);
  margin-right: 5px;
  font-size: 18px;
  text-align: center;
}

.el-menu--collapse .menu-icon {
  margin-right: 0;
}
</style>
