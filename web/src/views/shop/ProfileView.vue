<template>
  <div class="shop-container">
    <h1 class="section-title"><i class="bi bi-person"></i>个人中心</h1>

    <div v-loading="loading" class="profile-body">
      <ElCard shadow="never" class="profile-side">
        <ElAvatar :size="88" :src="assetUrl(form.avatarUrl)">
          <span class="avatar-letter">{{ userStore.name.charAt(0).toUpperCase() }}</span>
        </ElAvatar>
        <div class="side-name">{{ form.name || userStore.name }}</div>
        <div class="text-secondary">@{{ userStore.name }}</div>
        <div v-if="registeredAt" class="text-secondary side-meta">
          注册于 {{ formatDateTime(registeredAt) }}
        </div>
        <div class="side-links">
          <ElButton @click="router.push({ name: 'orders' })">
            <i class="bi bi-receipt btn-icon"></i>我的订单
          </ElButton>
          <ElButton @click="router.push({ name: 'cart' })">
            <i class="bi bi-cart3 btn-icon"></i>购物车
          </ElButton>
        </div>
      </ElCard>

      <ElCard shadow="never" class="profile-main">
        <ElTabs v-model="activeTab">
          <ElTabPane label="基本资料" name="profile">
            <ElForm
              ref="profileFormRef"
              :model="form"
              :rules="profileRules"
              label-width="80px"
              class="profile-form"
            >
              <ElFormItem label="用户名">
                <ElInput :model-value="userStore.name" disabled />
              </ElFormItem>
              <ElFormItem label="名字" prop="name">
                <ElInput v-model="form.name" maxlength="32" placeholder="选填" />
              </ElFormItem>
              <ElFormItem label="性别" prop="gender">
                <ElRadioGroup v-model="form.gender">
                  <ElRadio value="M">男</ElRadio>
                  <ElRadio value="F">女</ElRadio>
                  <ElRadio value="">保密</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
              <ElFormItem label="邮箱" prop="email">
                <ElInput v-model="form.email" maxlength="128" placeholder="选填" />
              </ElFormItem>
              <ElFormItem label="手机号" prop="phone">
                <ElInput v-model="form.phone" maxlength="11" placeholder="选填，下单时自动带入" />
              </ElFormItem>
              <ElFormItem label="头像" prop="avatarUrl">
                <ImageUploader v-model="form.avatarUrl" type="avatars" :size="88" />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="saving" @click="saveProfile">保存</ElButton>
              </ElFormItem>
            </ElForm>
          </ElTabPane>

          <ElTabPane label="修改密码" name="password">
            <ElForm
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="80px"
              class="profile-form"
            >
              <ElFormItem label="当前密码" prop="oldPassword">
                <ElInput v-model="passwordForm.oldPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem label="新密码" prop="newPassword">
                <ElInput v-model="passwordForm.newPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem label="确认密码" prop="confirmPassword">
                <ElInput v-model="passwordForm.confirmPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="changingPassword" @click="changePassword">
                  修改密码
                </ElButton>
              </ElFormItem>
            </ElForm>
          </ElTabPane>
        </ElTabs>
      </ElCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAvatar,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTabPane,
  ElTabs,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, User } from '@/types/api'
import { assetUrl, formatDateTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import ImageUploader from '@/components/ImageUploader.vue'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('profile')
const loading = ref(false)
const registeredAt = ref<string | null>(null)

// 表单里空值统一用 ''，提交时再转回 null
const form = reactive({
  name: '',
  gender: '' as 'M' | 'F' | '',
  email: '',
  phone: '',
  avatarUrl: '',
})

const profileFormRef = ref<FormInstance>()

// 与后端 UserEntity 的校验规则一致
const profileRules: FormRules = {
  email: [{ type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号', trigger: 'blur' }],
}

// 本地存的是登录时的快照，进页面以服务端为准
onMounted(async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ user: User }>>('/user/profile')
    if (!data.status) {
      ElMessage.error(data.payload.message || '加载资料失败')
      return
    }
    const user = data.payload.user
    form.name = user.name ?? ''
    form.gender = user.gender ?? ''
    form.email = user.email ?? ''
    form.phone = user.phone ?? ''
    form.avatarUrl = user.avatarUrl ?? ''
    registeredAt.value = user.registeredAt
  } catch {
    ElMessage.error('加载资料失败')
  } finally {
    loading.value = false
  }
})

const saving = ref(false)

const saveProfile = async () => {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const fields = {
    name: form.name.trim() || null,
    gender: form.gender || null,
    email: form.email.trim() || null,
    phone: form.phone || null,
    avatarUrl: form.avatarUrl || null,
  }

  saving.value = true
  try {
    const { data } = await request.post<ApiResult>('/user/update', fields)
    if (!data.status) {
      ElMessage.error(data.payload.message || '保存失败')
      return
    }
    // 同步本地登录信息：顶栏头像、结算页预填收货人都读这里
    userStore.updateUser(fields)
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const passwordFormRef = ref<FormInstance>()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, whitespace: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码需6–32个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const changingPassword = ref(false)

// 改密成功后退出登录，用新密码重新登录
const changePassword = async () => {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  try {
    const { data } = await request.post<ApiResult>('/user/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    if (!data.status) {
      ElMessage.error(data.payload.message || '修改密码失败')
      return
    }
    ElMessage.success('密码已修改，请重新登录')
    // 先记下用户名再登出，登录页预填
    const username = userStore.name
    userStore.logout()
    router.replace({ name: 'login', query: { username } })
  } catch {
    ElMessage.error('修改密码失败')
  } finally {
    changingPassword.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-body {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  min-height: 300px;
}

.profile-side {
  flex-shrink: 0;
  width: 260px;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
  }
}

.avatar-letter {
  font-size: 2rem;
}

.side-name {
  margin-top: 0.75rem;
  color: var(--el-text-color-primary);
  font-size: 1.125rem;
  font-weight: 600;
}

.side-meta {
  margin-top: 0.5rem;
}

.side-links {
  display: flex;
  gap: 0.5rem;
  margin-top: 1.25rem;

  .el-button {
    margin: 0;
  }
}

.btn-icon {
  margin-right: 0.25rem;
}

.profile-main {
  flex: 1;
  min-width: 0;
}

.profile-form {
  max-width: 480px;
  padding-top: 0.5rem;
}

@media (max-width: 768px) {
  .profile-body {
    flex-direction: column;
  }

  .profile-side {
    width: 100%;
  }
}
</style>
