<template>
  <div class="shop-container seckill-page">
    <section class="seckill-hero" aria-labelledby="seckill-title">
      <div class="hero-copy">
        <span class="hero-eyebrow"
          ><i class="bi bi-lightning-charge-fill"></i>限时好价 · 限量开抢</span
        >
        <h1 id="seckill-title">限时秒杀</h1>
        <p>把心动好物，带回日常。每一场都有值得期待的好价。</p>
        <div class="hero-perks">
          <span><i class="bi bi-tag"></i>专属秒杀价</span>
          <span><i class="bi bi-box-seam"></i>限量发售</span>
          <span><i class="bi bi-shield-check"></i>正品保障</span>
        </div>
      </div>
      <div class="hero-clock">
        <template v-if="nextActivity">
          <span class="clock-label">{{
            phaseOf(nextActivity) === 'LIVE' ? '本场结束倒计时' : '下一场开抢倒计时'
          }}</span>
          <div class="clock-digits" aria-label="倒计时">
            <template v-for="(part, index) in heroCountdown.split(':')" :key="index">
              <span v-if="index" class="clock-colon">:</span>
              <strong>{{ part }}</strong>
            </template>
          </div>
          <span class="clock-caption">{{ nextActivity.title }}</span>
        </template>
        <template v-else>
          <i class="bi bi-lightning-charge-fill clock-decoration" aria-hidden="true"></i>
          <span class="clock-label">好物限量，先到先得</span>
          <span class="clock-caption">下一场惊喜，值得期待</span>
        </template>
      </div>
    </section>

    <div class="activity-toolbar">
      <div class="activity-tabs" role="tablist" aria-label="秒杀活动状态">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="filter === tab.value"
          :class="{ active: filter === tab.value }"
          @click="changeFilter(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
      <ElButton :loading="loading" text @click="fetchActivities()">
        <i class="bi bi-arrow-clockwise"></i><span>刷新活动</span>
      </ElButton>
    </div>

    <div v-if="error" class="activity-error" role="alert">
      <i class="bi bi-exclamation-circle"></i>
      <span>{{ error }}</span>
      <ElButton @click="fetchActivities()">重新加载</ElButton>
    </div>
    <div v-else-if="loading && !activities.length" class="seckill-grid" aria-busy="true">
      <ElSkeleton v-for="index in 4" :key="index" class="activity-skeleton" :rows="5" animated />
    </div>
    <div v-else-if="activities.length" class="seckill-grid">
      <article v-for="activity in activities" :key="activity.id" class="seckill-card">
        <RouterLink
          :to="{ name: 'product_detail', params: { id: activity.productId } }"
          class="card-image-link"
          :aria-label="activity.productName + '商品详情'"
        >
          <ElImage
            v-if="activity.productImage"
            :src="assetUrl(activity.productImage)"
            :alt="activity.productName"
            fit="contain"
            loading="lazy"
            class="card-image"
          />
          <div v-else class="card-image image-placeholder"><i class="bi bi-image"></i></div>
          <span class="activity-badge" :data-phase="phaseOf(activity)">
            {{ SECKILL_PHASE[phaseOf(activity)].label }}
          </span>
          <span class="discount-badge">{{ discount(activity) }}折</span>
        </RouterLink>
        <div class="card-content">
          <p class="activity-title">{{ activity.title }}</p>
          <RouterLink
            :to="{ name: 'product_detail', params: { id: activity.productId } }"
            class="product-title"
            ><h2>{{ activity.productName }}</h2></RouterLink
          >
          <p class="product-subtitle">{{ activity.productSubtitle || '限量好物，限时享优惠' }}</p>
          <div class="card-prices">
            <strong>{{ formatMoney(activity.seckillPrice) }}</strong>
            <del>{{ formatMoney(activity.originalPrice) }}</del>
          </div>
          <div class="quota-meta">
            <span>每人限购 {{ activity.limitPerUser }} 件</span>
            <span>{{
              activity.status === 1 ? '剩余 ' + activity.stock + ' 件' : '活动已结束'
            }}</span>
          </div>
          <progress
            class="quota-progress"
            :value="Math.max(0, activity.totalStock - activity.stock - activity.returnedStock)"
            :max="activity.totalStock"
            :aria-label="activity.productName + '已抢数量'"
          ></progress>
          <p class="card-countdown">
            <i class="bi bi-clock"></i>
            <template v-if="phaseOf(activity) === 'UPCOMING'"
              >距开抢 {{ seckillCountdown(activity.startsAtEpochMs, now) }}</template
            >
            <template v-else-if="phaseOf(activity) === 'LIVE'"
              >距结束 {{ seckillCountdown(activity.endsAtEpochMs, now) }}</template
            >
            <template v-else>{{
              phaseOf(activity) === 'SOLD_OUT' ? '本场配额已抢完' : '期待下一场好价'
            }}</template>
          </p>
          <ElButton
            class="buy-button"
            size="large"
            :type="phaseOf(activity) === 'LIVE' ? 'danger' : 'info'"
            :disabled="phaseOf(activity) !== 'LIVE'"
            @click="buy(activity)"
            >{{
              phaseOf(activity) === 'LIVE' ? '立即抢购' : SECKILL_PHASE[phaseOf(activity)].label
            }}</ElButton
          >
        </div>
      </article>
    </div>
    <ElEmpty v-else :description="filter ? '暂无此状态的秒杀活动' : '秒杀好物正在准备中'">
      <RouterLink :to="{ name: 'products' }"><ElButton>先逛逛全部商品</ElButton></RouterLink>
    </ElEmpty>

    <ElPagination
      v-if="pagination.total > pagination.pageSize"
      v-model:current-page="page"
      class="page-pagination"
      :page-size="pagination.pageSize"
      :total="pagination.total"
      layout="prev, pager, next"
      background
    />
    <p class="seckill-note">
      活动期间按秒杀价结算。提交订单后请及时支付，取消或支付超时将释放名额。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElButton, ElEmpty, ElImage, ElPagination, ElSkeleton } from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, Pagination, SeckillActivity, SeckillPhase } from '@/types/api'
import { assetUrl, formatMoney } from '@/utils/format'
import { SECKILL_PHASE, seckillCountdown, seckillPhase } from '@/utils/seckill'

type Filter = Exclude<SeckillPhase, 'CLOSED'> | ''
const tabs: { label: string; value: Filter }[] = [
  { label: '全部活动', value: '' },
  { label: '正在秒杀', value: 'LIVE' },
  { label: '即将开始', value: 'UPCOMING' },
  { label: '已抢完', value: 'SOLD_OUT' },
  { label: '已结束', value: 'ENDED' },
]
const router = useRouter()
const filter = ref<Filter>('')
const page = ref(1)
const loading = ref(false)
const error = ref('')
const activities = ref<SeckillActivity[]>([])
const pagination = ref<Pagination>({ total: 0, pageSize: 12, pageCount: 1, currentPage: 1 })
const clockOffset = ref(0)
const now = ref(Date.now())
let requestSequence = 0
let clockTimer: ReturnType<typeof setInterval> | undefined
let refreshTimer: ReturnType<typeof setInterval> | undefined

const phaseOf = (activity: SeckillActivity) => seckillPhase(activity, now.value)
const nextActivity = computed(
  () =>
    activities.value.find((activity) => phaseOf(activity) === 'LIVE') ??
    activities.value
      .filter((activity) => phaseOf(activity) === 'UPCOMING')
      .sort((first, second) => first.startsAtEpochMs - second.startsAtEpochMs)[0],
)
const heroCountdown = computed(() => {
  const activity = nextActivity.value
  if (!activity) return '00:00:00'
  return seckillCountdown(
    phaseOf(activity) === 'LIVE' ? activity.endsAtEpochMs : activity.startsAtEpochMs,
    now.value,
  )
})
const discount = (activity: SeckillActivity) =>
  activity.originalPrice > 0
    ? ((activity.seckillPrice / activity.originalPrice) * 10).toFixed(1)
    : '—'

const fetchActivities = async (background = false) => {
  if (background && loading.value) return
  const sequence = ++requestSequence
  if (!background) loading.value = true
  try {
    const { data } = await request.get<
      ApiResult<{ activities: SeckillActivity[]; pagination: Pagination }>
    >('/seckill', {
      params: { page: page.value, pageSize: 12, phase: filter.value || undefined },
    })
    if (sequence !== requestSequence) return
    if (!data.status) {
      error.value = data.payload.message || '秒杀活动加载失败，请重试'
      return
    }
    activities.value = data.payload.activities
    pagination.value = data.payload.pagination
    if (activities.value[0]) clockOffset.value = activities.value[0].serverTime - Date.now()
    now.value = Date.now() + clockOffset.value
    page.value = data.payload.pagination.currentPage
    error.value = ''
  } catch {
    if (sequence === requestSequence) error.value = '秒杀活动加载失败，请检查网络后重试'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

const changeFilter = (value: Filter) => {
  filter.value = value
  page.value = 1
}
const buy = (activity: SeckillActivity) => {
  if (phaseOf(activity) !== 'LIVE') return
  router.push({
    name: 'checkout',
    query: { seckillActivityId: String(activity.id), quantity: '1' },
  })
}
watch(
  () => [page.value, filter.value],
  () => fetchActivities(),
  { immediate: true },
)
onMounted(() => {
  clockTimer = setInterval(() => {
    now.value = Date.now() + clockOffset.value
  }, 1000)
  refreshTimer = setInterval(() => {
    if (!document.hidden) fetchActivities(true)
  }, 10000)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  clearInterval(refreshTimer)
  requestSequence++
})
</script>

<style lang="scss" scoped>
.seckill-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2rem;
  padding: 2.5rem 3rem;
  overflow: hidden;
  border-radius: 20px;
  background: linear-gradient(115deg, #df334c, #ed5537 65%, #f78b45);
  color: #fff;
}
.hero-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.875rem;
  letter-spacing: 0.12em;
}
.hero-copy h1 {
  margin: 0.65rem 0;
  font-size: 2.75rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.hero-copy p {
  margin: 0;
  color: #fffde8;
  line-height: 1.8;
}
.hero-perks {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 1.4rem;
  font-size: 0.8rem;
}
.hero-perks span {
  display: flex;
  gap: 0.35rem;
  align-items: center;
}
.hero-clock {
  display: flex;
  flex: 0 0 270px;
  flex-direction: column;
  align-items: center;
  gap: 0.85rem;
  padding: 1.6rem 1.2rem;
  border: 1px solid #ffffff45;
  border-radius: 16px;
  background: #ffffff15;
}
.clock-label {
  font-size: 0.9rem;
}
.clock-digits {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.clock-digits strong {
  min-width: 54px;
  padding: 0.6rem 0.4rem;
  border-radius: 8px;
  background: #fff;
  color: #cf3343;
  font-size: 1.7rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.clock-colon {
  font-size: 1.5rem;
}
.clock-caption {
  max-width: 250px;
  font-size: 0.8rem;
  text-align: center;
  overflow-wrap: anywhere;
}
.clock-decoration {
  font-size: 2.4rem;
}
.activity-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin: 1.6rem 0;
}
.activity-toolbar :deep(.el-button) {
  display: flex;
  gap: 0.35rem;
}
.activity-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.activity-tabs button {
  border: 0;
  border-radius: 8px;
  padding: 0.65rem 1.1rem;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 0.9rem;
  cursor: pointer;
}
.activity-tabs button.active {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-weight: 600;
}
.seckill-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1.2rem;
}
.seckill-card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  background: var(--el-bg-color);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.seckill-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px #0000000c;
}
.card-image-link {
  position: relative;
  display: block;
  background: var(--el-fill-color-extra-light);
  text-decoration: none;
}
.card-image {
  display: flex;
  width: 100%;
  height: 210px;
  padding: 0.9rem;
  box-sizing: border-box;
  font-size: 2rem;
}
.activity-badge {
  position: absolute;
  top: 0.8rem;
  left: 0.8rem;
  padding: 0.3rem 0.65rem;
  border-radius: 6px;
  background: #dc3545;
  color: #fff;
  font-size: 0.75rem;
}
.activity-badge[data-phase='UPCOMING'] {
  background: #c47a15;
}
.activity-badge[data-phase='SOLD_OUT'],
.activity-badge[data-phase='ENDED'],
.activity-badge[data-phase='CLOSED'] {
  background: #687583;
}
.discount-badge {
  position: absolute;
  right: 0.8rem;
  bottom: 0.8rem;
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 0.75rem;
  font-weight: 600;
}
.card-content {
  padding: 1.1rem;
}
.activity-title {
  margin: 0 0 0.4rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.7rem;
  color: var(--el-color-danger);
}
.product-title {
  color: var(--el-text-color-primary);
  text-decoration: none;
}
.product-title h2 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 1rem;
  line-height: 1.5;
}
.product-subtitle {
  margin: 0.45rem 0 0.9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
}
.card-prices {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
}
.card-prices strong {
  color: var(--el-color-danger);
  font-size: 1.55rem;
  font-weight: 700;
}
.card-prices del {
  color: var(--el-text-color-placeholder);
  font-size: 0.8rem;
}
.quota-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.3rem;
  color: var(--el-text-color-secondary);
  font-size: 0.7rem;
}
.quota-progress {
  display: block;
  width: 100%;
  height: 6px;
  margin-top: 0.5rem;
  border: 0;
  border-radius: 5px;
  overflow: hidden;
  background: var(--el-color-danger-light-9);
  accent-color: var(--el-color-danger);
}
.quota-progress::-webkit-progress-bar {
  background: var(--el-color-danger-light-9);
}
.quota-progress::-webkit-progress-value {
  background: var(--el-color-danger);
  border-radius: 5px;
}
.quota-progress::-moz-progress-bar {
  background: var(--el-color-danger);
  border-radius: 5px;
}
.card-countdown {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin: 0.85rem 0;
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
}
.buy-button {
  width: 100%;
}
.activity-skeleton {
  padding: 1rem;
  min-height: 360px;
  border-radius: 14px;
  background: var(--el-bg-color);
}
.activity-error {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.8rem;
  min-height: 220px;
  color: var(--el-color-danger);
}
.seckill-note {
  margin: 1.5rem 0 0;
  color: var(--el-text-color-secondary);
  text-align: center;
  font-size: 0.75rem;
  line-height: 1.8;
}
@media (max-width: 1050px) {
  .seckill-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 768px) {
  .seckill-hero {
    padding: 1.8rem;
    gap: 1.2rem;
    flex-direction: column;
    align-items: stretch;
  }
  .hero-copy h1 {
    font-size: 2.25rem;
  }
  .hero-clock {
    flex-basis: auto;
    padding: 1rem;
  }
  .seckill-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.8rem;
  }
  .activity-toolbar {
    align-items: flex-start;
    gap: 0.4rem;
  }
  .activity-tabs {
    gap: 0.2rem;
  }
  .activity-tabs button {
    padding: 0.5rem 0.6rem;
    font-size: 0.8rem;
  }
  .card-image {
    height: 170px;
  }
  .card-content {
    padding: 0.8rem;
  }
  .card-prices strong {
    font-size: 1.3rem;
  }
  .quota-meta {
    flex-direction: column;
  }
}
@media (max-width: 380px) {
  .seckill-grid {
    grid-template-columns: 1fr;
  }
}
@media (prefers-reduced-motion: reduce) {
  .seckill-card {
    transition: none;
  }
}
</style>
