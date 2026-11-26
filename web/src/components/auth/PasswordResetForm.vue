<template>
  <p class="reset-description">{{ t('通过已绑定的邮箱重置密码，验证码 10 分钟内有效。') }}</p>
  <ElForm
    ref="formRef"
    :model="fields"
    :rules="rules"
    label-position="top"
    :disabled="sending || resetting"
    class="password-reset-form"
    @submit.prevent="resetPassword"
  >
    <ElFormItem :label="t('邮箱')" prop="email">
      <ElInput
        v-model="fields.email"
        type="email"
        maxlength="128"
        autocomplete="email"
        :placeholder="t('请输入已绑定邮箱')"
      />
    </ElFormItem>
    <ElFormItem :label="t('邮箱验证码')" prop="code">
      <ElInput
        v-model="fields.code"
        maxlength="6"
        inputmode="numeric"
        autocomplete="one-time-code"
        :placeholder="t('请输入 6 位验证码')"
      >
        <template #append>
          <ElButton :loading="sending" :disabled="remaining > 0 || resetting" @click="sendCode">
            {{ remaining ? t('{seconds} 秒后重发', { seconds: remaining }) : t('发送验证码') }}
          </ElButton>
        </template>
      </ElInput>
    </ElFormItem>
    <ElFormItem :label="t('新密码')" prop="newPassword">
      <ElInput
        v-model="fields.newPassword"
        type="password"
        show-password
        autocomplete="new-password"
        maxlength="32"
      />
    </ElFormItem>
    <ElFormItem :label="t('确认密码')" prop="confirmPassword">
      <ElInput
        v-model="fields.confirmPassword"
        type="password"
        show-password
        autocomplete="new-password"
        maxlength="32"
      />
    </ElFormItem>
    <ElButton type="primary" native-type="submit" :loading="resetting" class="reset-submit">{{
      t('重置密码')
    }}</ElButton>
  </ElForm>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { t } from '@/i18n'
import type { ApiResult } from '@/types/api'
import request from '@/utils/request'
import { apiErrorMessage, apiRetryAfter } from '@/utils/apiError'
import { useEmailCodeCountdown } from '@/utils/useEmailCodeCountdown'

const props = withDefaults(defineProps<{ initialEmail?: string }>(), { initialEmail: '' })
const emit = defineEmits<{ success: [email: string] }>()
const formRef = ref<FormInstance>()
const sending = ref(false)
const resetting = ref(false)
const fields = reactive({
  email: props.initialEmail,
  code: '',
  newPassword: '',
  confirmPassword: '',
})
const { remaining, start } = useEmailCodeCountdown()
watch(
  () => fields.email,
  () => {
    fields.code = ''
  },
)

const rules: FormRules = {
  email: [
    {
      required: true,
      get message() {
        return t('请输入已绑定邮箱')
      },
      trigger: 'blur',
    },
    {
      type: 'email',
      max: 128,
      get message() {
        return t('请输入有效的邮箱地址')
      },
      trigger: 'blur',
    },
  ],
  code: [
    {
      required: true,
      pattern: /^[0-9]{6}$/,
      get message() {
        return t('验证码需 6 位数字')
      },
      trigger: 'blur',
    },
  ],
  newPassword: [
    {
      required: true,
      whitespace: true,
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
      validator: (_rule, value, callback) => {
        callback(
          value === fields.newPassword && value ? undefined : new Error(t('两次输入的密码不一致')),
        )
      },
      trigger: 'blur',
    },
  ],
}

const sendCode = async () => {
  if (sending.value || resetting.value || remaining.value) return
  const valid = await formRef.value
    ?.validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!valid || sending.value || resetting.value) return
  sending.value = true
  try {
    const { data } = await request.post<ApiResult<{ resendAfterSeconds: number }>>(
      '/account/password-reset/code',
      { email: fields.email.trim().toLowerCase() },
    )
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '验证码发送失败，请稍后重试'))
      return
    }
    start(data.payload.resendAfterSeconds)
    ElMessage.success(t(data.payload.message || '验证码已发送，请查收邮箱'))
  } catch (error) {
    const retryAfter = apiRetryAfter(error)
    if (retryAfter) start(retryAfter)
    ElMessage.error(t(apiErrorMessage(error, '验证码发送失败，请稍后重试')))
  } finally {
    sending.value = false
  }
}

const resetPassword = async () => {
  if (sending.value || resetting.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || sending.value || resetting.value) return
  resetting.value = true
  const email = fields.email.trim().toLowerCase()
  try {
    const { data } = await request.post<ApiResult>('/account/password-reset', {
      email,
      code: fields.code,
      newPassword: fields.newPassword,
    })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '密码重置失败，请稍后重试'))
      return
    }
    fields.code = ''
    fields.newPassword = ''
    fields.confirmPassword = ''
    ElMessage.success(t('密码已重置，请使用新密码登录'))
    emit('success', email)
  } catch (error) {
    ElMessage.error(t(apiErrorMessage(error, '密码重置失败，请稍后重试')))
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.reset-description {
  margin: 0 0 1rem;
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
  line-height: 1.6;
}
.reset-submit {
  width: 100%;
}
</style>
