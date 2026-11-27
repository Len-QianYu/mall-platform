<template>
  <div :class="admin ? 'admin-profile' : 'shop-container'">
    <div v-if="admin" class="page-heading">
      <h1 class="page-title">{{ t('个人中心') }}</h1>
    </div>
    <h1 v-else class="section-title"><i class="bi bi-person"></i>{{ t('个人中心') }}</h1>

    <div v-loading="loading" class="profile-body">
      <div class="profile-sidebar">
        <ElCard shadow="never" class="profile-side">
          <ImageUploader
            :model-value="form.avatarUrl"
            v-model:uploading="avatarUploading"
            type="avatars"
            :disabled="loading || saving || changingPassword || avatarUploading || !loaded"
            :show-tip="false"
            class="profile-avatar"
            @update:model-value="onAvatarUploaded"
          >
            <template #trigger="{ uploading, disabled }">
              <div
                v-loading="uploading"
                class="avatar-trigger"
                :class="{ 'is-disabled': disabled }"
                :title="t('支持 JPG、PNG、GIF、WEBP，不超过 5MB')"
              >
                <ElAvatar :size="88" :src="assetUrl(form.avatarUrl)">
                  <span class="avatar-letter">{{ userStore.name.charAt(0).toUpperCase() }}</span>
                </ElAvatar>
                <span class="avatar-action">{{ t('更换头像') }}</span>
              </div>
            </template>
          </ImageUploader>
          <div class="avatar-tip text-secondary">{{ t('点击头像更换') }}</div>
          <div class="side-name">{{ form.name || userStore.name }}</div>
          <div class="text-secondary">@{{ userStore.name }}</div>
          <div v-if="registeredAt" class="text-secondary side-meta">
            {{ t('注册于') }} {{ formatDateTime(registeredAt) }}
          </div>
          <div v-if="!admin" class="side-links">
            <ElButton @click="router.push({ name: 'orders' })">
              <i class="bi bi-receipt btn-icon"></i>{{ t('我的订单') }}
            </ElButton>
            <ElButton @click="router.push({ name: 'cart' })">
              <i class="bi bi-cart3 btn-icon"></i>{{ t('购物车') }}
            </ElButton>
          </div>
        </ElCard>
        <SuperAdminDetails v-if="userStore.role === 'super_admin'" />
      </div>

      <ElCard shadow="never" class="profile-main">
        <ElTabs v-model="activeTab">
          <ElTabPane :label="t('基本资料')" name="profile">
            <template #label>
              <span class="profile-tab-label">
                <i class="bi bi-person-vcard" aria-hidden="true"></i>{{ t('基本资料') }}
              </span>
            </template>
            <ElForm
              ref="profileFormRef"
              :model="form"
              :rules="profileRules"
              :disabled="loading || saving || changingPassword || avatarUploading || !loaded"
              :label-width="locale === 'en' ? '152px' : '100px'"
              class="profile-form"
            >
              <ElFormItem :label="t('用户名')">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-person-badge" aria-hidden="true"></i>{{ t('用户名') }}
                  </span>
                </template>
                <ElInput :model-value="userStore.name" disabled />
              </ElFormItem>
              <ElFormItem :label="t('名字')" prop="name">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-person" aria-hidden="true"></i>{{ t('名字') }}
                  </span>
                </template>
                <ElInput v-model="form.name" maxlength="32" :placeholder="t('选填')" />
              </ElFormItem>
              <ElFormItem :label="t('性别')" prop="gender">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-gender-ambiguous" aria-hidden="true"></i>{{ t('性别') }}
                  </span>
                </template>
                <ElRadioGroup v-model="form.gender">
                  <ElRadio value="M">{{ t('男') }}</ElRadio>
                  <ElRadio value="F">{{ t('女') }}</ElRadio>
                  <ElRadio value="">{{ t('保密') }}</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
              <ElFormItem :label="t('邮箱')" class="profile-email-field">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-envelope" aria-hidden="true"></i>{{ t('邮箱') }}
                  </span>
                </template>
                <ElInput v-if="form.email && emailVerified" :model-value="form.email" readonly />
                <ElButton v-else @click="emailBindingVisible = true">
                  <i class="bi bi-envelope-plus btn-icon" aria-hidden="true"></i>{{ t('绑定邮箱') }}
                </ElButton>
              </ElFormItem>
              <ElFormItem :label="t('手机号')" prop="phone">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-phone" aria-hidden="true"></i>{{ t('手机号') }}
                  </span>
                </template>
                <ElInput
                  v-model="form.phone"
                  maxlength="11"
                  :placeholder="t(admin ? '选填' : '选填，用于收货联系')"
                />
              </ElFormItem>
              <div class="profile-location-row">
                <ElFormItem :label="t('省份')" prop="province" label-width="100px">
                  <template #label>
                    <span class="profile-field-label">
                      <i class="bi bi-map" aria-hidden="true"></i>{{ t('省份') }}
                    </span>
                  </template>
                  <ElSelect
                    v-model="form.province"
                    :placeholder="t('请选择省份（选填）')"
                    filterable
                    clearable
                    @change="form.city = ''"
                  >
                    <ElOption
                      v-for="province in provinceList"
                      :key="province.value"
                      :label="t(province.label)"
                      :value="province.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem :label="t('城市')" prop="city" label-width="64px">
                  <template #label>
                    <span class="profile-field-label">
                      <i class="bi bi-buildings" aria-hidden="true"></i>{{ t('城市') }}
                    </span>
                  </template>
                  <ElSelect
                    v-model="form.city"
                    :placeholder="t(form.province ? '请选择城市（选填）' : '请先选择省份')"
                    :disabled="cityList.length === 0"
                    filterable
                    clearable
                  >
                    <ElOption
                      v-for="city in cityList"
                      :key="city.value"
                      :label="t(city.label)"
                      :value="city.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </div>
              <ElFormItem :label="t('收货地址')" prop="receiverAddress">
                <template #label>
                  <span class="profile-field-label">
                    <i class="bi bi-geo-alt" aria-hidden="true"></i>{{ t('收货地址') }}
                  </span>
                </template>
                <ElInput
                  v-model="form.receiverAddress"
                  type="textarea"
                  :rows="3"
                  maxlength="255"
                  show-word-limit
                  :placeholder="t('省 / 市 / 区 / 街道 / 门牌号')"
                  autocomplete="shipping street-address"
                />
                <div class="address-note">{{ t('下单时使用上方的名字、手机号和此地址。') }}</div>
              </ElFormItem>
              <ElFormItem>
                <ElButton
                  class="save-profile-button"
                  type="primary"
                  :loading="saving"
                  @click="saveProfile"
                >
                  <i class="bi bi-check2-circle btn-icon" aria-hidden="true"></i>
                  {{ returnToCheckout ? t('保存并返回下单') : t('保存') }}
                </ElButton>
              </ElFormItem>
            </ElForm>
          </ElTabPane>

          <ElTabPane :label="t('修改密码')" name="password">
            <template #label>
              <span class="profile-tab-label">
                <i class="bi bi-shield-lock" aria-hidden="true"></i>{{ t('修改密码') }}
              </span>
            </template>
            <ElForm
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              :disabled="loading || saving || changingPassword || avatarUploading || !loaded"
              :label-width="formLabelWidth('80px')"
              class="profile-form"
            >
              <ElFormItem :label="t('当前密码')" prop="oldPassword">
                <ElInput v-model="passwordForm.oldPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem :label="t('新密码')" prop="newPassword">
                <ElInput v-model="passwordForm.newPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem :label="t('确认密码')" prop="confirmPassword">
                <ElInput v-model="passwordForm.confirmPassword" type="password" show-password />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="changingPassword" @click="changePassword">
                  {{ t('修改密码') }}
                </ElButton>
              </ElFormItem>
            </ElForm>
          </ElTabPane>
        </ElTabs>
      </ElCard>
    </div>
    <EmailBindingDialog
      v-model="emailBindingVisible"
      :initial-email="form.email"
      @bound="onEmailBound"
    />
  </div>
</template>

<script setup lang="ts">
import { t, formLabelWidth, useLocale } from '@/i18n'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAvatar,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTabPane,
  ElTabs,
  vLoading,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult, User } from '@/types/api'
import { assetUrl, formatDateTime } from '@/utils/format'
import { getCitiesByProvince, getProvinceList } from '@/utils/regions'
import { useUserStore, type LoginResult } from '@/stores/user'
import ImageUploader from '@/components/ImageUploader.vue'
import EmailBindingDialog from '@/components/auth/EmailBindingDialog.vue'
import SuperAdminDetails from '@/components/admin/SuperAdminDetails.vue'

withDefaults(defineProps<{ admin?: boolean }>(), { admin: false })
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { locale } = useLocale()

const returnToCheckout = computed(() => {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string' || !redirect.startsWith('/')) return ''
  return router.resolve(redirect).name === 'checkout' ? redirect : ''
})

const activeTab = ref('profile')
const loading = ref(false)
const loaded = ref(false)
const avatarUploading = ref(false)
const registeredAt = ref<string | null>(null)
const emailBindingVisible = ref(false)
const emailVerified = ref(false)

const onEmailBound = (account: LoginResult) => {
  form.email = account.user.email ?? ''
  emailVerified.value = account.user.emailVerified === true
  userStore.login(account)
}

// 表单里空值统一用 ''，提交时再转回 null
const form = reactive({
  name: '',
  gender: '' as 'M' | 'F' | '',
  email: '',
  phone: '',
  province: '',
  city: '',
  receiverAddress: '',
  avatarUrl: '',
})

const provinceList = getProvinceList()
const cityList = computed(() => getCitiesByProvince(form.province))

const profileFormRef = ref<FormInstance>()

// 与后端 UserEntity 的校验规则一致
const profileRules: FormRules = {
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      get message() {
        return t('请输入有效的手机号')
      },
      trigger: 'blur',
    },
  ],
  province: [
    {
      max: 50,
      get message() {
        return t('省份最多50个字符')
      },
      trigger: 'change',
    },
  ],
  city: [
    {
      max: 50,
      get message() {
        return t('城市最多50个字符')
      },
      trigger: 'change',
    },
  ],
  receiverAddress: [
    {
      max: 255,
      get message() {
        return t('收货地址最多255个字符')
      },
      trigger: 'blur',
    },
  ],
}

// 本地存的是登录时的快照，进页面以服务端为准
onMounted(async () => {
  loading.value = true
  try {
    const { data } = await request.get<ApiResult<{ user: User }>>('/user/profile')
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '加载资料失败'))
      return
    }
    const user = data.payload.user
    form.name = user.name ?? ''
    form.gender = user.gender ?? ''
    form.email = user.email ?? ''
    emailVerified.value = user.emailVerified === true
    form.phone = user.phone ?? ''
    form.province = user.province ?? ''
    form.city = user.city ?? ''
    form.receiverAddress = user.receiverAddress ?? ''
    form.avatarUrl = user.avatarUrl ?? ''
    registeredAt.value = user.registeredAt
    loaded.value = true
  } catch {
    ElMessage.error(t('加载资料失败'))
  } finally {
    loading.value = false
  }
})

const saving = ref(false)

const onAvatarUploaded = (avatarUrl: string) => {
  form.avatarUrl = avatarUrl
  userStore.updateUser({ avatarUrl })
  ElMessage.success(t('头像已更新'))
}

const saveProfile = async () => {
  if (!loaded.value || saving.value || changingPassword.value || avatarUploading.value) return
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid || saving.value || changingPassword.value || avatarUploading.value) return

  const fields = {
    name: form.name.trim() || null,
    gender: form.gender || null,
    email: form.email.trim() || null,
    phone: form.phone.trim() || null,
    province: form.province.trim() || null,
    city: form.city.trim() || null,
    receiverAddress: form.receiverAddress.trim() || null,
    avatarUrl: form.avatarUrl || null,
  }

  saving.value = true
  try {
    const { data } = await request.post<ApiResult>('/user/update', fields)
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '保存失败'))
      return
    }
    // 同步本地登录信息，刷新页面后仍能恢复最新资料
    userStore.updateUser(fields)
    ElMessage.success(t('保存成功'))
    if (returnToCheckout.value) {
      void router.replace(returnToCheckout.value)
    }
  } catch {
    ElMessage.error(t('保存失败'))
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
  oldPassword: [
    {
      required: true,
      get message() {
        return t('请输入当前密码')
      },
      trigger: 'blur',
    },
  ],
  newPassword: [
    {
      required: true,
      whitespace: true,
      get message() {
        return t('请输入新密码')
      },
      trigger: 'blur',
    },
    {
      min: 6,
      max: 32,
      get message() {
        return t('密码需6–32个字符')
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      required: true,
      get message() {
        return t('请再次输入新密码')
      },
      trigger: 'blur',
    },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error(t('两次输入的密码不一致')))
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
  if (!loaded.value || saving.value || changingPassword.value || avatarUploading.value) return
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid || saving.value || changingPassword.value || avatarUploading.value) return

  changingPassword.value = true
  try {
    const { data } = await request.post<ApiResult>('/user/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '修改密码失败'))
      return
    }
    ElMessage.success(t('密码已修改，请重新登录'))
    // 先记下用户名再登出，登录页预填
    const username = userStore.name
    userStore.logout()
    router.replace({ name: 'login', query: { username } })
  } catch {
    ElMessage.error(t('修改密码失败'))
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

.profile-sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 1rem;
  width: 260px;
}

.profile-side {
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

.profile-avatar :deep(.el-upload:focus-visible) {
  border-radius: 50%;
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;

  .avatar-action {
    opacity: 1;
  }
}

.avatar-trigger {
  position: relative;
  display: flex;
  overflow: hidden;
  border-radius: 50%;
  cursor: pointer;

  &:hover .avatar-action {
    opacity: 1;
  }

  &.is-disabled {
    cursor: not-allowed;

    .avatar-action {
      opacity: 0;
    }
  }
}

.avatar-action {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgb(0 0 0 / 45%);
  font-size: 0.75rem;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-tip {
  margin-top: 0.25rem;
  font-size: 0.75rem;
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
  container-type: inline-size;

  :deep(.el-select) {
    width: 100%;
  }
}

.profile-location-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;

  :deep(.el-form-item) {
    min-width: 0;
  }
}

.profile-tab-label,
.profile-field-label {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  white-space: nowrap;

  i {
    flex-shrink: 0;
    width: 1rem;
    color: #000;
    text-align: center;
  }
}

.address-note {
  margin-top: 0.375rem;
  color: var(--el-text-color-secondary);
  font-size: 0.75rem;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .profile-body {
    flex-direction: column;
  }

  .profile-sidebar,
  .profile-main {
    width: 100%;
  }
}

@container (max-width: 400px) {
  .profile-location-row {
    :deep(.el-form-item) {
      flex-direction: column;
    }

    :deep(.el-form-item__label) {
      justify-content: flex-start;
      width: auto !important;
      padding: 0 0 0.25rem;
    }

    :deep(.el-form-item__content) {
      width: 100%;
    }
  }
}
</style>
