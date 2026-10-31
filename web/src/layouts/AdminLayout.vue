<template>
  <ElConfigProvider :locale="zhCn">
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
          <span>Copyright &copy; {{ currentYear }} Mall Platform.</span>
          <span>管理后台</span>
        </footer>
      </div>
    </div>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterView } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
// Element Plus 默认英文，分页、空表格等文案切换为中文
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AdminSidebar from '@/components/admin/AdminSidebar.vue'
import AdminHeader from '@/components/admin/AdminHeader.vue'
// 引入 Bootstrap Icons（侧边栏、顶栏和各管理页面共用）
import 'bootstrap-icons/font/bootstrap-icons.css'

const collapsed = ref(false)
const refreshKey = ref(0)
const currentYear = new Date().getFullYear()
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
</style>
