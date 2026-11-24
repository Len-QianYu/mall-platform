<template>
  <ElConfigProvider :locale="elementLocale">
    <div class="admin-layout">
      <AdminSidebar :collapsed="collapsed" />

      <div class="admin-body">
        <AdminHeader
          :collapsed="collapsed"
          @toggle-sidebar="collapsed = !collapsed"
          @refresh="refreshKey++"
        />

        <main class="admin-main">
          <!-- refreshKey 变化时重新挂载当前页面，相当于只刷新内容区 -->
          <RouterView v-slot="{ Component }">
            <component :is="Component" :key="refreshKey" />
          </RouterView>
        </main>

        <footer class="admin-footer">
          <span>lyqy &copy; {{ currentYear }} Prompt Pals.</span>
          <span>{{ t('管理后台') }}</span>
        </footer>
      </div>
    </div>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
import { useLocale, t } from '@/i18n'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'
// 引入 Bootstrap Icons（侧边栏、顶栏和各管理页面共用）
import 'bootstrap-icons/font/bootstrap-icons.css'

const collapsed = ref(false)
const refreshKey = ref(0)
const currentYear = new Date().getFullYear()
const { elementLocale } = useLocale()
</script>

<style lang="scss" scoped>
// 铺满视口：不依赖 body 的默认 margin，滚动只发生在内容区
.admin-layout {
  position: fixed;
  inset: 0;
  display: flex;
  background-color: var(--el-bg-color-page);
}

.admin-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.admin-main {
  flex: 1;
  padding: 1.5rem;
  overflow-y: auto;
}

.admin-footer {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 2.5rem;
  padding: 0 1.25rem;
  border-top: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}
</style>

<style lang="scss">
// 各管理页面共用的结构样式：标题栏、筛选栏、分页、次要文字
.admin-main {
  .page-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1rem;
  }

  .page-title {
    margin: 0;
    color: var(--el-color-primary);
    font-size: 1.375rem;
    font-weight: 600;
  }

  .filter-form {
    margin-bottom: 0.25rem;
  }

  .table-pagination {
    justify-content: flex-end;
    margin-top: 1rem;
  }

  .text-secondary {
    color: var(--el-text-color-secondary);
    font-size: 0.75rem;
  }
}

// 短表单弹窗在较矮的视口中只滚动内容，标题和操作按钮保持可见。
.admin-form-dialog {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 2rem);

  .el-dialog__header,
  .el-dialog__footer {
    flex-shrink: 0;
  }

  .el-dialog__body {
    min-height: 0;
    overflow-y: auto;
  }
}
</style>
