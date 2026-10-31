<template>
  <ElConfigProvider :locale="zhCn">
    <div class="shop-layout">
      <ShopHeader />

      <main class="shop-main">
        <RouterView />
      </main>

      <footer class="shop-footer">
        <div class="shop-container footer-inner">
          <span>Copyright &copy; {{ currentYear }} Mall Platform.</span>
          <span>正品保障 · 极速发货 · 售后无忧</span>
        </div>
      </footer>
    </div>
  </ElConfigProvider>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ElConfigProvider } from 'element-plus'
// Element Plus 默认英文，分页、空状态等文案切换为中文
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import ShopHeader from '@/components/shop/ShopHeader.vue'
// 引入 Bootstrap Icons（顶栏和各商城页面共用）
import 'bootstrap-icons/font/bootstrap-icons.css'

const currentYear = new Date().getFullYear()
</script>

<style lang="scss" scoped>
// 页面整体滚动（顶栏 sticky），页脚在内容不足一屏时贴底
.shop-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--el-bg-color-page);
}

.shop-main {
  flex: 1;
  padding: 1.5rem 0 2.5rem;
}

.shop-footer {
  border-top: 1px solid var(--el-border-color-light);
  background-color: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 3rem;
}
</style>

<style lang="scss">
// body 默认 margin 会让 100vh 的布局多出滚动条
body {
  margin: 0;
}

// 各商城页面共用的结构样式：内容宽度、区块标题、商品网格、商品图占位、次要文字
.shop-container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  box-sizing: border-box;
}

.shop-main {
  .section-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin: 0 0 1rem;
    color: var(--el-text-color-primary);
    font-size: 1.25rem;
    font-weight: 600;

    i {
      color: var(--el-color-primary);
    }
  }

  // 卡片按宽度自动换行：大屏 4–5 列，手机 2 列
  .product-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 1rem;

    @media (max-width: 480px) {
      grid-template-columns: repeat(2, 1fr);
      gap: 0.75rem;
    }
  }

  .image-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: var(--el-fill-color-light);
    color: var(--el-text-color-placeholder);
  }

  .price {
    color: var(--el-color-danger);
    font-weight: 600;
  }

  .text-secondary {
    color: var(--el-text-color-secondary);
    font-size: 0.75rem;
  }

  .page-pagination {
    justify-content: center;
    margin-top: 1.5rem;
  }
}
</style>
