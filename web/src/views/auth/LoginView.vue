<template>
  <div class="login-container" :class="{ 'dark-theme': isDark }">
    <!-- 左侧:背景 -->
    <div class="login-left">
      <div class="base-background">
        <div class="content">
          <!-- 左上角logo -->
          <div class="logo-box">
            <img src="@/assets/logo/logo.png" alt="Logo" />
          </div>

          <!-- svg中心图标 -->
          <ModerationIcon class="moderation-icon" :aria-label="t('商城购物袋')" />

          <!-- 文字描述 -->
          <div class="description-text">
            <div class="title-text">{{ t('AI 伙伴商城') }}</div>
            <div class="introduce-text">{{ t('把想象抱进怀里，让陪伴触手可及') }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧:登录表单 -->
    <div class="login-right">
      <div class="login-form-box">
        <!-- 主题切换按钮：图标表示点击后切换到的模式，亮色时显示月亮，暗色时显示太阳 -->
        <div class="theme-toggle-button_box">
          <LanguageSwitch />
          <button
            class="change-theme"
            @click="toggleTheme"
            :aria-label="isDark ? t('切换到亮色模式') : t('切换到暗色模式')"
          >
            <i v-if="isDark" class="bi bi-brightness-high sun-icon"></i>
            <i v-else class="bi bi-moon-stars moon-icon"></i>
          </button>
        </div>

        <ElForm
          ref="form"
          :model="model"
          @submit.prevent="login"
          :rules="rules"
          :label-width="formLabelWidth('80')"
          label-position="left"
        >
          <h2 class="form-title">{{ t('欢迎回来🤖') }}</h2>
          <p class="welcome-text">{{ t('请输入账号信息，登录商城') }}</p>

          <ElFormItem prop="username" :label="t('用户名')">
            <ElInput v-model="model.username" type="text" :placeholder="t('请输入用户名')">
              <template v-slot:suffix>
                <i class="bi bi-person-fill icon-suffix"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem prop="password" :label="t('密码')">
            <ElInput v-model="model.password" type="password" :placeholder="t('请输入密码')">
              <template v-slot:suffix>
                <i class="bi bi-lock-fill icon-suffix"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <!-- 记住账号和忘记密码 -->
          <div class="remember-forgot-box">
            <div class="remember-checkbox">
              <input
                type="checkbox"
                id="remember-account"
                v-model="rememberAccount"
                class="checkbox-input"
              />
              <label for="remember-account" class="checkbox-label">{{ t('记住账号') }}</label>
            </div>
            <span class="forgot-password-link">{{ t('忘记密码?') }}</span>
          </div>

          <ElFormItem>
            <ElButton type="primary" native-type="submit" style="width: 100%">{{
              t('登录')
            }}</ElButton>
          </ElFormItem>

          <!-- 创建账号链接 -->
          <div class="signup-link-wrapper">
            {{ t('还没有账号?') }}
            <RouterLink :to="{ name: 'register' }" class="signup-link">{{
              t('创建账号')
            }}</RouterLink>
          </div>
        </ElForm>

        <!-- 底部信息 -->
        <footer class="login-footer">
          <div>lyqy &copy; {{ currentYear }} Prompt Pals.</div>
        </footer>
      </div>
    </div>

    <div
      class="login-role-switch"
      :class="{ 'is-hidden': !roleSwitchVisible }"
      role="group"
      :aria-label="t('登录方式')"
      @mouseenter="onRoleSwitchMouseEnter"
      @mouseleave="onRoleSwitchMouseLeave"
      @focusin="showRoleSwitch"
      @focusout="onRoleSwitchFocusOut"
    >
      <button
        type="button"
        class="role-switch-button"
        :title="t('用户登录')"
        :aria-label="t('用户登录')"
        :aria-pressed="model.role === 'user'"
        @click="switchLoginRole('user')"
      >
        <span>{{ t('用户登录') }}</span>
      </button>
      <button
        type="button"
        class="role-switch-button"
        :title="t('管理员登录')"
        :aria-label="t('管理员登录')"
        :aria-pressed="model.role === 'admin'"
        @click="switchLoginRole('admin')"
      >
        <span>{{ t('管理员登录') }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t, formLabelWidth } from '@/i18n'
import { ref, reactive, onMounted, onUnmounted, watch, watchEffect } from 'vue'
import { ElForm, ElFormItem, ElInput, ElButton, ElMessage, type FormInstance } from 'element-plus'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import request from '@/utils/request'
import { useUserStore, type LoginResult, type LoginRole } from '@/stores/user'
import type { ApiResult } from '@/types/api'
import { useTheme } from '@/utils/useTheme'
import {
  clearRememberedAccount,
  loadRememberedAccount,
  saveRememberedAccount,
} from '@/utils/rememberAccount'
import ModerationIcon from '@/assets/svg/moderation-icon.svg?component'
import LanguageSwitch from '@/components/LanguageSwitch.vue'
// npm install bootstrap-icons
// 引入 Bootstrap Icons
import 'bootstrap-icons/font/bootstrap-icons.css'

const userStore = useUserStore()

const form = ref<FormInstance>()
const router = useRouter()
const route = useRoute()
const { isDark, toggleTheme } = useTheme() // 日夜主题全站共用
const rememberedAccount = loadRememberedAccount()
const rememberAccount = ref(rememberedAccount !== null)

watch(rememberAccount, (remember) => {
  if (!remember) clearRememberedAccount()
})
const currentYear = new Date().getFullYear()
const roleSwitchVisible = ref(true)
let roleSwitchHovered = false
let roleSwitchHideTimer: ReturnType<typeof setTimeout> | undefined

const clearRoleSwitchHideTimer = () => {
  if (roleSwitchHideTimer !== undefined) {
    clearTimeout(roleSwitchHideTimer)
    roleSwitchHideTimer = undefined
  }
}

const showRoleSwitch = () => {
  clearRoleSwitchHideTimer()
  roleSwitchVisible.value = true
}

const scheduleRoleSwitchHide = () => {
  clearRoleSwitchHideTimer()
  if (roleSwitchHovered) return

  roleSwitchHideTimer = setTimeout(() => {
    roleSwitchVisible.value = false
    roleSwitchHideTimer = undefined
  }, 1500)
}

const onRoleSwitchMouseEnter = () => {
  roleSwitchHovered = true
  showRoleSwitch()
}

const onRoleSwitchMouseLeave = () => {
  roleSwitchHovered = false
  scheduleRoleSwitchHide()
}

const onRoleSwitchFocusOut = (event: FocusEvent) => {
  if (
    event.currentTarget instanceof HTMLElement &&
    event.relatedTarget instanceof Node &&
    event.currentTarget.contains(event.relatedTarget)
  ) {
    return
  }
  scheduleRoleSwitchHide()
}

onMounted(scheduleRoleSwitchHide)

const registeredUsername = typeof route.query.username === 'string' ? route.query.username : ''
// 只接受站内路径（排除 // 开头的协议相对地址），防止 ?redirect= 被拼成外部地址
const redirect =
  typeof route.query.redirect === 'string' &&
  route.query.redirect.startsWith('/') &&
  !route.query.redirect.startsWith('//')
    ? route.query.redirect
    : ''

const model = reactive({
  role: (registeredUsername ? 'user' : (rememberedAccount?.role ?? 'user')) as LoginRole,
  username: registeredUsername || rememberedAccount?.username || '',
  password: '',
})

const switchLoginRole = (role: LoginRole) => {
  if (model.role === role) return

  model.role = role
  ElMessage.success(
    t('切换成功，当前为{role}登录', { role: role === 'admin' ? t('管理员') : t('用户') }),
  )
}

const rules = reactive({
  username: [
    {
      required: true,
      whitespace: true,
      get message() {
        return t('请输入用户名')
      },
      trigger: 'blur',
    },
    {
      min: 2,
      max: 16,
      get message() {
        return t('用户名需2–16个字符')
      },
      trigger: 'change',
    },
  ],
  password: [
    {
      required: true,
      whitespace: true,
      get message() {
        return t('请输入密码')
      },
      trigger: 'blur',
    },
    {
      min: 6,
      max: 32,
      get message() {
        return t('密码需6–32个字符')
      },
      trigger: 'change',
    },
  ],
})

// 给 body 添加暗黑主题类,让下拉框等弹出层也能应用暗黑样式（见下方全局 <style>）。
// 主题全站共享，进入登录页时可能已经是暗色，所以跟随状态同步，而不是只在点击时设置
watchEffect(() => {
  document.body.classList.toggle('dark-theme', isDark.value)
})

const login = () => {
  if (!form.value) return

  form.value
    .validate()
    .then((result) => {
      if (result === true) {
        // 统一使用 /login 接口，后端会根据角色进行验证
        const credentials = { ...model }
        request
          .post<ApiResult<LoginResult>>('/login', credentials)
          .then((response) => {
            if (response.data.status === true) {
              // 保存用户信息和token到store
              const userData = response.data.payload
              userStore.login(userData)

              if (rememberAccount.value) {
                saveRememberedAccount(userData.user.username, credentials.role)
              } else {
                clearRememberedAccount()
              }

              // 根据角色跳转到不同首页；普通用户若是从需登录的页面被拦过来的，跳回原页面
              if (userStore.isAdmin) {
                router.replace({ name: 'admin_dashboard' }) // 管理员首页
              } else if (redirect) {
                router.replace(redirect)
              } else {
                router.replace({ name: 'index' }) // 用户首页
              }
            } else {
              // 显示后端返回的错误消息
              const message = response.data.payload?.message || '登录失败。'
              ElMessage.error(t(message))
            }
          })
          .catch(() => {
            ElMessage.error(t('登录失败。'))
          })
      }
    })
    .catch(() => undefined)
}

onUnmounted(() => {
  clearRoleSwitchHideTimer()
  document.body.classList.remove('dark-theme')
})
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

// 左侧背景 比例2
.login-left {
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
}

.logo-box img {
  width: 10rem;
  height: auto;
  object-fit: contain;
  transition: transform 0.3s ease; //悬停动画
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
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0);
  transition: color 0.3s ease;
}

.introduce-text {
  font-size: 1rem;
  color: #606266;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  transition: color 0.3s ease;
}

// 右侧表单 比例1
.login-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
  padding: 2rem;
}

.login-form-box {
  width: 100%;
  max-width: 450px;
  position: relative;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* 主题切换按钮 */
.theme-toggle-button_box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  position: absolute;
  top: 0rem;
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

// label字体
:deep(.el-form-item__label) {
  font-weight: 500;
  color: #86909c;
  transition: color 0.3s ease;
}

// 选择框
:deep(.el-select) {
  width: 100%;
  height: 3rem;
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

// 登录按钮
:deep(.el-button) {
  height: 3rem;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 500;
}

// 记住账号和忘记密码
.remember-forgot-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.remember-checkbox {
  display: flex;
  align-items: center;
}

.checkbox-input {
  width: 1rem;
  height: 1rem;
  border: 1px solid #86909c;
  border-radius: 0.25rem;
  cursor: pointer;
  transition: all 0.3s ease;
  flex-shrink: 0;
  // 清除默认样式
  // appearance: none;
}

// 记住账号字体标签
.checkbox-label {
  margin-left: 0.5rem;
  font-size: 0.875rem;
  color: #86909c;
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
}

.forgot-password-link {
  font-size: 0.875rem;
  color: #409eff;
  cursor: pointer;
  transition: color 0.3s ease;

  &:hover {
    color: #66b1ff;
    text-decoration: underline;
  }
}

// 创建账号
.signup-link-wrapper {
  margin-top: 0.75rem;
  text-align: center;
  font-size: 0.875rem;
  color: #86909c;
  transition: color 0.3s ease;
}

.signup-link {
  color: #409eff;
  cursor: pointer;
  transition: color 0.3s ease;

  &:hover {
    color: #66b1ff;
    text-decoration: underline;
  }
}

// 底部字体样式
.login-footer {
  position: absolute;
  bottom: 0rem;
  left: 0;
  right: 0;
  display: flex;
  flex-flow: row nowrap;
  align-items: center;
  justify-content: space-between;
  color: #86909c;
  font-size: 0.75rem;
  padding: 0 1rem;
  transition: all 0.3s ease;
}

.login-role-switch {
  --role-button-width: 5rem;
  --role-cut: 0.75rem;
  --role-background: #fff;
  --role-text-color: #075c63;
  --role-border-color: #5edfd6;
  --role-active-background: #5edfd6;

  position: fixed;
  right: 2rem;
  bottom: 1.5rem;
  z-index: 2;
  width: calc(var(--role-button-width) * 2);
  height: 2.75rem;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--role-border-color) 60%, var(--role-background));
  border-radius: 999px;
  background: var(--role-background);
  box-shadow:
    0 5px 18px rgb(7 92 99 / 8%),
    0 1px 3px rgb(7 92 99 / 4%);

  // 只隐藏外观，保留鼠标移入时重新显示的悬停区域。
  &.is-hidden {
    opacity: 0;
  }
}

.role-switch-button {
  position: absolute;
  top: 0;
  bottom: 0;
  display: grid;
  place-items: center;
  width: calc(50% + var(--role-cut));
  padding: 0;
  border: none;
  border-radius: 0;
  background: var(--role-background);
  color: var(--role-text-color);
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;

  // 两侧圆弧由外框裁切，中间沿同一条斜线无缝拼接。
  &:first-child {
    left: 0;
    padding-right: var(--role-cut);
    clip-path: polygon(0 0, 100% 0, calc(100% - var(--role-cut) * 2) 100%, 0 100%);
  }

  &:last-child {
    right: 0;
    padding-left: var(--role-cut);
    clip-path: polygon(calc(var(--role-cut) * 2) 0, 100% 0, 100% 100%, 0 100%);
  }

  &:not([aria-pressed='true']):hover {
    color: #07828b;
  }

  &[aria-pressed='true'] {
    background: linear-gradient(
      135deg,
      color-mix(in srgb, var(--role-active-background) 75%, var(--role-background)),
      var(--role-active-background)
    );
    box-shadow: inset 0 1px 0 rgb(255 255 255 / 28%);
    color: var(--role-text-color);
  }

  &:focus-visible {
    outline: 2px solid currentColor;
    outline-offset: -4px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .role-switch-button {
    transition: none;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
  }

  .login-left {
    display: none;
  }

  .login-right {
    flex: 1;
    width: 100%;
    padding: 1rem;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .login-form-wrapper {
    width: 100%;
    max-width: 100%;
    padding: 0 1rem;
  }

  .theme-toggle-wrapper {
    top: 1rem;
    right: 1rem;
  }

  .login-footer {
    position: relative;
    bottom: auto;
    margin-top: 2rem;
    padding: 1rem 0;
    border-top: 1px solid #e4e7ed;
  }

  .login-role-switch {
    right: 1.5rem;
    bottom: 1rem;
  }

  h2 {
    font-size: 1.5rem;
  }

  .welcome-text {
    font-size: 0.875rem;
  }
}

// 暗黑主题
.dark-theme {
  /* 左侧背景区域 */
  .base-background {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  }

  .logo-box img {
    filter: brightness(0.9) drop-shadow(0 2px 8px rgba(255, 255, 255, 0.1));
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
  .login-right {
    background: #1a1a1a;
  }

  h2 {
    color: #e5e5e5;
  }

  .welcome-text {
    color: #a0a0a0;
  }

  .theme-toggle {
    background: rgba(50, 50, 50, 0.9);
    color: #e5e5e5;

    &:hover {
      background: #3a3a3a;
      transform: scale(1.1) rotate(15deg);
    }
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

  /* 图标暗黑主题 */
  .icon-suffix {
    color: #a0a0a0;
  }

  :deep(.el-select) {
    .el-input__wrapper {
      background-color: #2a2a2a;
      box-shadow: 0 0 0 1px #3a3a3a inset;

      &:hover {
        box-shadow: 0 0 0 1px #4a4a4a inset;
      }

      &.is-focus {
        box-shadow: 0 0 0 1px #409eff inset;
      }
    }

    .el-input__inner {
      color: #e5e5e5;
      background-color: transparent;
    }

    input::placeholder {
      color: #6a6a6a;
    }

    .el-select__caret {
      color: #e5e5e5;
    }
  }

  /* 记住账号和忘记密码暗黑主题 */
  .checkbox-input {
    border-color: #3a3a3a;
    background-color: #2a2a2a;

    &:checked {
      background-color: #409eff;
      border-color: #409eff;
    }
  }

  .checkbox-label {
    color: #e5e5e5;
  }

  .forgot-password-link {
    color: #409eff;

    &:hover {
      color: #66b1ff;
    }
  }

  /* 创建账号链接暗黑主题 */
  .signup-link-wrapper {
    color: #a0a0a0;
  }

  .signup-link {
    color: #409eff;

    &:hover {
      color: #66b1ff;
    }
  }

  .login-footer {
    color: #a0a0a0;
    border-top-color: #3a3a3a;
  }
}
</style>

<style lang="scss">
/* 下拉框暗黑主题全局样式 - 使用更高优先级的选择器 */
html body.dark-theme,
body.dark-theme {
  /* Select 输入框本身 */
  .el-select .el-select__wrapper {
    background-color: #2a2a2a !important;
    box-shadow: 0 0 0 1px #3a3a3a inset !important;
  }

  .el-select .el-select__wrapper:hover {
    box-shadow: 0 0 0 1px #4a4a4a inset !important;
  }

  .el-select .el-select__wrapper.is-focused,
  .el-select .el-select__wrapper:focus-within {
    box-shadow: 0 0 0 1px #409eff inset !important;
  }

  .el-select .el-select__input {
    color: #e5e5e5 !important;
    background-color: transparent !important;
  }

  .el-select .el-select__placeholder {
    color: #6a6a6a !important;
  }

  .el-select .el-select__placeholder span {
    color: #6a6a6a !important;
  }

  .el-select .el-select__caret,
  .el-select .el-select__icon,
  .el-select .el-icon {
    color: #e5e5e5 !important;
  }

  .el-select .el-select__selection {
    color: #e5e5e5 !important;
  }

  .el-select input::placeholder {
    color: #6a6a6a !important;
  }

  /* Select 下拉菜单 */
  .el-select-dropdown,
  .el-popper.is-light .el-select-dropdown {
    background-color: #2a2a2a !important;
    border: 1px solid #3a3a3a !important;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.5) !important;
  }

  .el-select-dropdown__item,
  .el-popper.is-light .el-select-dropdown__item {
    color: #e5e5e5 !important;
    background-color: transparent !important;

    &:hover,
    &.hover,
    &.is-hovering {
      background-color: #3a3a3a !important;
      color: #e5e5e5 !important;
    }

    &.selected,
    &.is-selected {
      color: #409eff !important;
      background-color: #2a2a2a !important;
      font-weight: 600;
    }
  }

  .el-select-dropdown__empty {
    color: #a0a0a0 !important;
  }

  .el-popper__arrow::before {
    background-color: #2a2a2a !important;
    border: 1px solid #3a3a3a !important;
  }

  .el-select-dropdown__list {
    background-color: #2a2a2a !important;
  }

  .el-scrollbar {
    background-color: #2a2a2a !important;
  }

  .el-scrollbar__view {
    background-color: #2a2a2a !important;
  }

  .el-select-dropdown__wrap {
    background-color: #2a2a2a !important;
  }
}
</style>
