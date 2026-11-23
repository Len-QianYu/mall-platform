<template>
  <div class="shop-container about-page">
    <section class="about-intro" aria-labelledby="about-title">
      <p class="about-brand">Prompt Pals</p>
      <h1 id="about-title">关于我们</h1>
      <p class="about-tagline">小小玩偶，大大陪伴。</p>
      <p>
        Prompt Pals 是一个以 AI 玩偶为主题的线上商城。你可以在这里发现 Claude、DeepSeek、GPT 与 Grok
        系列玩偶，把喜欢的 AI 伙伴带进日常。我们把商品浏览、收藏、购物车和订单管理汇集在一起，
        希望让你从挑选心仪玩偶到完成购买，都能拥有清晰、顺手的体验。
      </p>
    </section>

    <article class="about-content" aria-label="网站介绍">
      <section class="about-section">
        <h2>从这里，发现你的下一位伙伴</h2>
        <p>
          在首页通过玩偶分类与热销榜单发现喜欢的伙伴，或进入“全部商品”，通过分类、关键词和排序找到适合自己的选择。
          商品详情展示价格、库存和商品介绍，方便你了解每件商品，再决定是否加入购物车。
        </p>
        <p>
          如果你喜欢关注优惠，“限时秒杀”会集中展示活动商品、活动时间和剩余名额，让每一次挑选都有更多可能。
        </p>
      </section>

      <section class="about-section">
        <h2>把喜欢留在身边，把购物安排妥当</h2>
        <p>
          登录后，你可以收藏感兴趣的商品，在商品上架或价格变化时收到提醒。
          购物车帮你整理待购清单，订单页面方便你查看购买记录和订单状态，个人中心则可以管理账号信息与收货地址。
        </p>
        <p>无论是先收藏、再考虑，还是选好后直接购买，都可以按照自己的节奏来。</p>
      </section>

      <section class="about-section">
        <h2>简单的界面，持续完善的体验</h2>
        <p>
          我们重视清楚的商品信息、便捷的页面导航和顺畅的购物流程，也提供亮色与暗色模式，
          让你在不同的使用环境下都能舒适浏览。网站会持续完善功能与细节，让日常购物更加轻松。
        </p>
        <p>感谢你的到访。希望你能在这里找到喜欢的商品，也享受挑选的过程。</p>
      </section>

      <div class="about-links">
        <RouterLink :to="{ name: 'products' }">
          逛逛全部商品 <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
        <RouterLink :to="{ name: 'seckill' }">
          看看限时秒杀 <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </RouterLink>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<style lang="scss" scoped>
.about-page {
  max-width: 960px;
}

.about-intro {
  margin-bottom: 1.5rem;
  padding: 2.5rem 3rem;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: linear-gradient(120deg, var(--el-color-primary-light-9), var(--el-bg-color));

  h1 {
    margin: 0 0 1rem;
    color: var(--el-text-color-primary);
    font-size: 2rem;
  }

  .about-brand {
    margin: 0 0 0.75rem;
    color: var(--el-color-primary);
    font-size: 0.875rem;
    font-weight: 600;
  }

  .about-tagline {
    margin-bottom: 1rem;
    color: var(--el-text-color-primary);
    font-size: 1.25rem;
  }
}

.about-page p {
  margin: 0 0 0.75rem;
  color: var(--el-text-color-regular);
  line-height: 1.9;

  &:last-child {
    margin-bottom: 0;
  }
}

.about-content {
  padding: 0.5rem 3rem 2rem;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background-color: var(--el-bg-color);
}

.about-section {
  padding: 1.75rem 0;

  & + & {
    border-top: 1px solid var(--el-border-color-lighter);
  }

  h2 {
    margin: 0 0 0.875rem;
    color: var(--el-text-color-primary);
    font-size: 1.125rem;
  }
}

.about-links {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--el-border-color-lighter);

  a {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
      text-underline-offset: 4px;
    }
  }
}

@media (max-width: 768px) {
  .about-intro {
    padding: 1.75rem 1.5rem;

    h1 {
      font-size: 1.75rem;
    }
  }

  .about-content {
    padding: 0.25rem 1.5rem 1.5rem;
  }
}
</style>
