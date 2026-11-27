<template>
  <ElCard
    shadow="never"
    class="super-admin-details"
    role="region"
    :aria-label="t('超级管理员资料')"
  >
    <dl class="super-admin-details-list">
      <div class="detail-item">
        <dt><i class="bi bi-github" aria-hidden="true"></i>GitHub</dt>
        <dd>
          <a href="https://github.com/Len-QianYu" target="_blank" rel="noopener noreferrer">
            Len-QianYu
            <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i>
          </a>
        </dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-phone" aria-hidden="true"></i>{{ t('爱逛的应用') }}</dt>
        <dd class="interest-tags">
          <span v-for="app in favoriteApps" :key="app" class="interest-tag">{{ t(app) }}</span>
        </dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-globe2" aria-hidden="true"></i>{{ t('爱逛的网站') }}</dt>
        <dd class="website-links">
          <a
            v-for="website in favoriteWebsites"
            :key="website.url"
            :href="website.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ t(website.label) }}
            <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i>
          </a>
        </dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-wallet2" aria-hidden="true"></i>{{ t('支付宝沙箱') }}</dt>
        <dd>
          <a
            href="https://open.alipay.com/develop/sandbox/account"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ t('沙箱账号') }}
            <i class="bi bi-box-arrow-up-right" aria-hidden="true"></i>
          </a>
        </dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-trophy" aria-hidden="true"></i>{{ t('擅长运动') }}</dt>
        <dd class="interest-tags">
          <span v-for="sport in sports" :key="sport" class="interest-tag">{{ t(sport) }}</span>
        </dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-music-note-beamed" aria-hidden="true"></i>{{ t('兴趣爱好') }}</dt>
        <dd>{{ t('喜欢听音乐') }}</dd>
      </div>

      <div class="detail-item">
        <dt><i class="bi bi-mortarboard" aria-hidden="true"></i>{{ t('实训经历') }}</dt>
        <dd>{{ t('曾在东莞松山湖青软基地实训') }}</dd>
      </div>
    </dl>
  </ElCard>
</template>

<script setup lang="ts">
import { ElCard } from 'element-plus'
import { t } from '@/i18n'

const favoriteApps = ['bilibili', '抖音', '小红书']
const favoriteWebsites = [
  { label: 'linux.do', url: 'https://linux.do/' },
  { label: 'Spring Boot 文档', url: 'https://docs.spring.io/spring-boot/reference/index.html' },
  { label: 'Vue 组合式 API', url: 'https://cn.vuejs.org/api/composition-api-setup#basic-usage' },
  { label: 'FastAPI 中文文档', url: 'https://fastapi.org.cn/' },
]
const sports = ['篮球', '乒乓球', '手球']
</script>

<style lang="scss" scoped>
.super-admin-details-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.6;

  dt {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.375rem;
    color: var(--el-text-color-secondary);
    font-weight: 500;

    i {
      color: var(--el-color-primary);
      font-size: 1rem;
    }
  }

  dd {
    margin: 0;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }

  a {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
      text-underline-offset: 3px;
    }

    &:focus-visible {
      border-radius: 2px;
      outline: 2px solid var(--el-color-primary);
      outline-offset: 3px;
    }

    i {
      font-size: 0.6875rem;
    }
  }
}

.website-links {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.375rem;
}

.interest-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.interest-tag {
  padding: 0.125rem 0.5rem;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
</style>
