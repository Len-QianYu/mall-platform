<template>
  <ElTag
    class="user-role-tag"
    :class="{ 'is-super-admin': role === 'super_admin' }"
    :type="role === 'admin' ? 'warning' : role === 'super_admin' ? 'primary' : 'success'"
    :size="size"
  >
    {{ t(labels[role]) }}
  </ElTag>
</template>

<script setup lang="ts">
import { t } from '@/i18n'
import { ElTag } from 'element-plus'
import type { UserRole } from '@/stores/user'

defineProps<{ role: UserRole; size?: 'large' | 'default' | 'small' }>()

const labels: Record<UserRole, string> = {
  user: '用户',
  admin: '管理员',
  super_admin: 'super管理员',
}
</script>

<style lang="scss" scoped>
.user-role-tag {
  border-color: var(--el-tag-text-color);

  &.is-super-admin {
    --el-tag-text-color: #a855f7;
    --el-tag-border-color: #a855f7;
    --el-tag-bg-color: color-mix(in srgb, var(--el-bg-color) 90%, #a855f7);
  }
}
</style>
