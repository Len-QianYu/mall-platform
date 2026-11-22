import type { SeckillActivity, SeckillPhase } from '@/types/api'

export const SECKILL_PHASE: Record<
  SeckillPhase,
  { label: string; type: 'success' | 'warning' | 'info' | 'danger' }
> = {
  LIVE: { label: '正在秒杀', type: 'danger' },
  UPCOMING: { label: '即将开始', type: 'warning' },
  SOLD_OUT: { label: '已抢完', type: 'info' },
  ENDED: { label: '已结束', type: 'info' },
  CLOSED: { label: '已关闭', type: 'info' },
}

// 使用后端返回的数据库时间与 epoch 时间，浏览器时区和本机时钟不决定开抢状态。
export const seckillPhase = (activity: SeckillActivity, now: number): SeckillPhase => {
  if (activity.status === 2 || now >= activity.endsAtEpochMs) return 'ENDED'
  if (activity.status !== 1 || activity.productStatus !== 1) return 'CLOSED'
  if (now < activity.startsAtEpochMs) return 'UPCOMING'
  return activity.stock > 0 ? 'LIVE' : 'SOLD_OUT'
}

export const seckillCountdown = (endsAt: number, now: number): string => {
  const seconds = Math.max(0, Math.ceil((endsAt - now) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return [hours, minutes, seconds % 60].map((value) => String(value).padStart(2, '0')).join(':')
}
