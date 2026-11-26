<template>
  <ElDialog
    :model-value="modelValue"
    :title="t('绑定邮箱')"
    width="min(460px, 94vw)"
    :close-on-click-modal="!sending && !binding"
    :close-on-press-escape="!sending && !binding"
    :show-close="!sending && !binding"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="clearSensitiveFields"
  >
    <p class="binding-description">{{ t('验证邮箱后可使用邮箱登录和找回密码。') }}</p>
    <ElForm
      ref="formRef"
      :model="fields"
      :rules="rules"
      label-position="top"
      :disabled="sending || binding"
      class="email-binding-form"
      @submit.prevent="bindEmail"
    >
      <ElFormItem :label="t('邮箱')" prop="email">
        <ElInput
          v-model="fields.email"
          type="email"
          maxlength="128"
          autocomplete="email"
          :placeholder="t('请输入有效的邮箱地址')"
        />
      </ElFormItem>
      <ElFormItem :label="t('当前密码')" prop="password">
        <ElInput
          v-model="fields.password"
          type="password"
          show-password
          autocomplete="current-password"
          maxlength="32"
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
            <ElButton :loading="sending" :disabled="remaining > 0 || binding" @click="sendCode">{{
              remaining ? t('{seconds} 秒后重发', { seconds: remaining }) : t('发送验证码')
            }}</ElButton>
          </template>
        </ElInput>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="sending || binding" @click="emit('update:modelValue', false)">{{
        t('取消')
      }}</ElButton>
      <ElButton type="primary" :loading="binding" :disabled="sending" @click="bindEmail">{{
        t('绑定邮箱')
      }}</ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { t } from '@/i18n'
import type { ApiResult } from '@/types/api'
import type { LoginResult } from '@/stores/user'
import request from '@/utils/request'
import { apiErrorMessage, apiRetryAfter } from '@/utils/apiError'
import { useEmailCodeCountdown } from '@/utils/useEmailCodeCountdown'

const props = withDefaults(defineProps<{ modelValue: boolean; initialEmail?: string }>(), {
  initialEmail: '',
})
const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  bound: [account: LoginResult]
}>()
const fields = reactive({ email: '', password: '', code: '' })
const formRef = ref<FormInstance>()
const sending = ref(false)
const binding = ref(false)
const { remaining, start } = useEmailCodeCountdown()
const clearSensitiveFields = () => {
  fields.password = ''
  fields.code = ''
  formRef.value?.clearValidate()
}
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      fields.email = props.initialEmail
      clearSensitiveFields()
    }
  },
  { immediate: true },
)
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
        return t('请输入有效的邮箱地址')
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
  password: [
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
}

const sendCode = async () => {
  if (sending.value || binding.value || remaining.value) return
  const valid = await formRef.value
    ?.validateField(['email', 'password'])
    .then(() => true)
    .catch(() => false)
  if (!valid || sending.value || binding.value) return
  sending.value = true
  try {
    const { data } = await request.post<ApiResult<{ resendAfterSeconds: number }>>(
      '/account/email/code',
      { email: fields.email.trim().toLowerCase(), password: fields.password },
    )
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '验证码发送失败，请稍后重试'))
      return
    }
    start(data.payload.resendAfterSeconds)
    ElMessage.success(t('验证码已发送，请查收邮箱'))
  } catch (error) {
    const retryAfter = apiRetryAfter(error)
    if (retryAfter) start(retryAfter)
    ElMessage.error(t(apiErrorMessage(error, '验证码发送失败，请稍后重试')))
  } finally {
    sending.value = false
  }
}

const bindEmail = async () => {
  if (sending.value || binding.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || sending.value || binding.value) return
  binding.value = true
  try {
    const { data } = await request.post<ApiResult<LoginResult>>('/account/email/bind', {
      email: fields.email.trim().toLowerCase(),
      password: fields.password,
      code: fields.code,
    })
    if (!data.status) {
      ElMessage.error(t(data.payload.message || '邮箱绑定失败'))
      return
    }
    emit('bound', data.payload)
    emit('update:modelValue', false)
    clearSensitiveFields()
    ElMessage.success(t('邮箱绑定成功'))
  } catch (error) {
    ElMessage.error(t(apiErrorMessage(error, '邮箱绑定失败')))
  } finally {
    binding.value = false
  }
}
</script>

<style scoped>
.binding-description {
  margin: 0 0 1rem;
  color: var(--el-text-color-secondary);
  font-size: 0.875rem;
  line-height: 1.6;
}
</style>
