<template>
  <div class="register-container" :class="{ 'dark-theme': isDark }">
    <!-- 左侧:背景 -->
    <div class="register-left">
      <div class="base-background">
        <div class="content">
          <!-- 左上角logo -->
          <div class="logo-box">
            <img src="/favicon.ico" alt="Logo" />
            <p class="logo-text">Mall Platform</p>
          </div>

          <!-- svg中心图标 -->
          <ModerationIcon class="moderation-icon" />

          <!-- 文字描述 -->
          <div class="description-text">
            <div class="title-text">电商商城</div>
            <div class="introduce-text">发现好物，开启你的购物旅程</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧:注册表单 -->
    <div class="register-right">
      <div class="register-form-box">
        <!-- 主题切换按钮：图标表示点击后切换到的模式，亮色时显示月亮，暗色时显示太阳 -->
        <div class="theme-toggle-button_box">
          <button
            class="change-theme"
            @click="toggleTheme"
            :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
          >
            <i v-if="isDark" class="bi bi-brightness-high sun-icon"></i>
            <i v-else class="bi bi-moon-stars moon-icon"></i>
          </button>
        </div>

        <ElForm
          ref="form"
          :model="model"
          :disabled="submitting"
          @submit.prevent="register"
          :rules="rules"
          label-width="80"
          label-position="left"
        >
          <h2 class="form-title">创建商城账号</h2>
          <p class="welcome-text">注册后即可使用这个账号登录商城</p>
          <ElAlert
            v-if="submitError"
            class="register-alert"
            :title="submitError"
            type="error"
            :closable="false"
            show-icon
            role="alert"
          />
          <ElFormItem prop="username" label="用户名">
            <ElInput
              v-model="model.username"
              type="text"
              placeholder="2–16个字符"
              autocomplete="username"
              :maxlength="16"
            >
              <template v-slot:suffix>
                <i class="bi bi-person-fill icon-suffix"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem prop="password" label="密码">
            <ElInput
              v-model="model.password"
              type="password"
              placeholder="6–32个字符"
              autocomplete="new-password"
              :maxlength="32"
              show-password
            >
              <template v-slot:suffix>
                <i class="bi bi-lock-fill icon-suffix"></i>
              </template>
            </ElInput>
            <!-- 密码强度指示器 -->
            <div class="password-strength-box">
              <div class="strength-bars">
                <div class="strength-bar-boxs" v-for="i in 5" :key="i">
                  <span
                    class="strength-bar-fill"
                    :style="{ width: passwordStrength >= i ? '100%' : '0%' }"
                    :class="getStrengthClass(i)"
                  ></span>
                </div>
              </div>
              <p class="strength-reminder-text">
                密码需6–32个字符，建议使用至少8位并混合字母、数字和符号
              </p>
            </div>
          </ElFormItem>

          <ElFormItem prop="confirmPassword" label="确认密码">
            <ElInput
              v-model="model.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
              :maxlength="32"
              show-password
            >
              <template v-slot:suffix>
                <i class="bi bi-lock-fill icon-suffix"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem>
            <ElButton
              type="primary"
              native-type="submit"
              :loading="submitting"
              :disabled="submitting"
              style="width: 100%"
            >
              {{ submitting ? '正在注册…' : '注册' }}
            </ElButton>
          </ElFormItem>

          <!-- 登录链接 -->
          <div class="login-link-wrapper">
            已经有账号了?
            <RouterLink :to="{ name: 'login' }" class="login-link">去登录</RouterLink>
          </div>
        </ElForm>

        <!-- 底部信息 -->
        <footer class="register-footer">
          <div>Copyright &copy; {{ currentYear }} Mall Platform.</div>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { isAxiosError } from 'axios'
import {
  ElAlert,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElMessage,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { useRouter, RouterLink } from 'vue-router'
import request from '@/utils/request'
import { useTheme } from '@/utils/useTheme'
import ModerationIcon from '@/assets/svg/moderation-icon.svg?component'

// 引入 Bootstrap Icons
import 'bootstrap-icons/font/bootstrap-icons.css'

interface RegistrationResponse {
  status: boolean
  payload: { message?: string }
}

const form = ref<FormInstance>()
const router = useRouter()
const { isDark, toggleTheme } = useTheme() // 日夜主题全站共用
const submitting = ref(false)
const submitError = ref('')
const currentYear = new Date().getFullYear()

const model = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

// 密码强度计算
const passwordStrength = computed(() => {
  const pwd = model.password
  if (!pwd) return 0

  let strength = 0
  if (pwd.length >= 6) strength++
  if (/[a-z]/.test(pwd)) strength++
  if (/[A-Z]/.test(pwd)) strength++
  if (/[0-9]/.test(pwd)) strength++
  if (/[^a-zA-Z0-9]/.test(pwd)) strength++

  return strength
})

// 获取强度条的颜色类
const getStrengthClass = (index: number) => {
  const strength = passwordStrength.value
  if (strength < index) return ''

  if (strength <= 2) return 'strength-weak'
  if (strength === 3) return 'strength-medium'
  if (strength === 4) return 'strength-good'
  return 'strength-strong'
}

const rules: FormRules<typeof model> = {
  username: [
    { required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 16, message: '用户名需2–16个字符', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, whitespace: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码需6–32个字符', trigger: ['blur', 'change'] },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== model.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: ['blur', 'change'],
    },
  ],
}

watch(
  () => model.password,
  () => {
    if (form.value && model.confirmPassword) {
      form.value.validateField('confirmPassword').catch(() => undefined)
    }
  },
)

watch(
  () => [model.username, model.password, model.confirmPassword],
  () => {
    submitError.value = ''
  },
)

const register = async () => {
  if (!form.value || submitting.value) return

  submitting.value = true
  submitError.value = ''

  try {
    const valid = await form.value.validate().catch(() => false)
    if (!valid) return

    const response = await request.post<RegistrationResponse>(
      '/user/create',
      { username: model.username, password: model.password },
      { timeout: 15000 },
    )

    if (response.data.status !== true) {
      submitError.value = response.data.payload?.message || '注册失败，请稍后重试'
      return
    }

    ElMessage.success('注册成功，请登录')
    await router.replace({ name: 'login', query: { username: model.username } })
  } catch (error) {
    if (isAxiosError<RegistrationResponse>(error)) {
      submitError.value =
        error.response?.data?.payload?.message ||
        (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '注册失败，请稍后重试')
    } else {
      submitError.value = '注册失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  display: flex;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
}

// 左侧背景 比例2
.register-left {
  flex: 2;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.base-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #fdcdc5 0%, #7bc0fc 50%, #ffe4ba 100%);
  transition: background 0.3s ease;
}

.moderation-icon {
  width: 25rem;
  height: 25rem;
}
.content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 2rem;
}

// 左上角Logo
.logo-box {
  position: absolute;
  top: 2rem;
  left: 2rem;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.logo-text {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 500;
  color: #272e3b;
  transition: color 0.3s ease;
}

// 项目名
.description-text {
  text-align: center;
  color: #303133;
}

// 中心svg
.moderation-icon {
  width: 25rem;
  height: 25rem;
}

// 项目标题
.title-text {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #303133;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: color 0.3s ease;
}

.introduce-text {
  font-size: 1rem;
  color: #606266;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: color 0.3s ease;
}

// 右侧表单区域 比例1
.register-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
  padding: 2rem;
  min-height: 100vh;
  min-height: 100dvh;
  box-sizing: border-box;
}

.register-form-box {
  width: 100%;
  max-width: 450px;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-top: 4rem;
  gap: 1.5rem;
}

/* 主题切换按钮 */
.theme-toggle-button_box {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 1;
}

.change-theme {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 8px;
  border: none;
  background: rgba(255, 255, 255);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px #9fd4fd;
  color: #89e9e0;

  &:hover {
    background: white;
    transform: scale(1.2) rotate(15deg);
    box-shadow: 0 4px 16px white;
  }

  &:active {
    transform: scale(0.95);
  }
  // 太阳
  .sun-icon {
    font-size: 24px;
    animation: sunRotate 10s linear infinite;
  }

  // 月亮
  .moon-icon {
    font-size: 24px;
    animation: moonFloat 3s ease-in-out infinite;
  }
}

// 旋转
@keyframes sunRotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// 月亮浮动
@keyframes moonFloat {
  0%,
  100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-3px);
  }
}

.form-title {
  text-align: center;
  margin-bottom: 0.5rem;
  color: #1d2129;
  font-size: 1.8rem;
  transition: color 0.3s ease;
}

.welcome-text {
  text-align: center;
  color: #a9aeb8;
  margin-bottom: 2rem;
  font-size: 1rem;
  transition: color 0.3s ease;
}

.register-alert {
  margin-bottom: 1.25rem;
}

// label字体
:deep(.el-form-item__label) {
  font-weight: 500;
  color: #86909c;
  transition: color 0.3s ease;
}

// 输入框
:deep(.el-input) {
  height: 40px;

  .el-input__wrapper {
    border-radius: 6px;
    transition: all 0.3s ease;
  }

  .el-input__inner {
    transition: color 0.3s ease;
  }
}

// 用户名和密码图标
.icon-suffix {
  font-size: 1rem;
  color: #4e5969;
  transition: color 0.3s ease;
}

// 注册按钮
:deep(.el-button) {
  height: 3rem;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 500;
}

// 密码强度指示器
.password-strength-box {
  width: 100%;
  margin-top: 0.5rem;
  margin-left: 0;
}

// 强度条
.strength-bars {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 0.25rem;
  margin-top: 0.5rem;
}

// 强度格
.strength-bar-boxs {
  position: relative;
  flex: 1;
  height: 0.5rem;
  background-color: #e5e6eb;
  border-radius: 0.125rem;
  overflow: hidden;
}

.strength-bar-fill {
  position: absolute;
  left: 0;
  height: 100%;
  border-radius: 0.125rem;
  transition: all 0.5s ease;

  // 第一第二格
  &.strength-weak {
    background-color: #f56c6c;
  }

  // 第三格
  &.strength-medium {
    background-color: #e6a23c;
  }

  // 第四格
  &.strength-good {
    background-color: #95d475;
  }

  // 第五格
  &.strength-strong {
    background-color: #67c23a;
  }
}

// 文本
.strength-reminder-text {
  margin-top: 0.375rem;
  margin-bottom: 0;
  font-size: 0.75rem;
  color: #909399;
  transition: color 0.3s ease;
}

// 登录链接
.login-link-wrapper {
  margin-top: 0.75rem;
  text-align: center;
  font-size: 0.875rem;
  color: #86909c;
  transition: color 0.3s ease;
}

.login-link {
  color: #409eff;
  text-decoration: none;
  transition: color 0.3s ease;

  &:hover {
    color: #66b1ff;
    text-decoration: underline;
  }
}

// 底部信息样式
.register-footer {
  display: flex;
  flex-flow: row nowrap;
  align-items: center;
  justify-content: space-between;
  color: #86909c;
  font-size: 0.75rem;
  transition: all 0.3s ease;
}

// 响应式设计
@media (max-width: 768px) {
  .register-container {
    flex-direction: column;
  }

  .register-left {
    display: none;
  }

  .register-right {
    flex: 1;
    width: 100%;
    padding: 1rem;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .register-form-wrapper {
    width: 100%;
    max-width: 100%;
    padding: 0 1rem;
  }

  .theme-toggle-wrapper {
    top: 1rem;
    right: 1rem;
  }

  .register-footer {
    padding: 1rem 0;
  }

  .form-title {
    font-size: 1.5rem;
  }

  .welcome-text {
    font-size: 0.875rem;
  }
}

/* 暗黑主题 */
.dark-theme {
  /* 左侧背景区域 */
  .base-background {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  }

  .logo-box img {
    filter: brightness(0.9) drop-shadow(0 2px 8px rgba(255, 255, 255, 0.1));
  }

  .logo-text {
    color: #e0e0e0;
    text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .moderation-icon {
    filter: brightness(0.85) saturate(0.8);
    opacity: 0.9;
  }

  .title-text {
    color: #e5e5e5;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .introduce-text {
    color: #b0b0b0;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  }

  /* 右侧表单区域 */
  .register-right {
    background: #1a1a1a;
  }

  .form-title {
    color: #e5e5e5;
  }

  .welcome-text {
    color: #a0a0a0;
  }

  :deep(.el-form-item__label) {
    color: #e5e5e5;
  }

  :deep(.el-input__wrapper) {
    background-color: #2a2a2a;
    box-shadow: 0 0 0 1px #3a3a3a inset;

    &:hover {
      box-shadow: 0 0 0 1px #4a4a4a inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px #409eff inset;
    }
  }

  :deep(.el-input__inner) {
    color: #e5e5e5;
    background-color: transparent;

    &::placeholder {
      color: #6a6a6a;
    }
  }

  :deep(.el-form-item__error) {
    color: #f56c6c;
  }

  .icon-suffix {
    color: #a0a0a0;
  }

  .strength-bar-wrapper {
    background-color: #2a2a2a;
  }

  .strength-hint-text {
    color: #a0a0a0;
  }

  .login-link-wrapper {
    color: #a0a0a0;
  }

  .login-link {
    color: #409eff;

    &:hover {
      color: #66b1ff;
    }
  }

  .register-footer {
    color: #a0a0a0;
  }
}
</style>
