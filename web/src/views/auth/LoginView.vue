<template>
  <div class="login-container" :class="{ 'dark-theme': isDark }">
    <!-- 左侧:背景 -->
    <div class="login-left">
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

    <!-- 右侧:登录表单 -->
    <div class="login-right">
      <div class="login-form-box">
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
          @submit.prevent="login"
          :rules="rules"
          label-width="80"
          label-position="left"
        >
          <h2 class="form-title">欢迎回来🤖</h2>
          <p class="welcome-text">请输入账号信息，登录商城</p>
          <ElFormItem prop="role" label="账户类型">
            <ElSelect
              v-model="model.role"
              placeholder="请选择账户类型"
              :teleported="false"
              popper-class="login-select-dropdown"
            >
              <ElOption label="用户" value="user"></ElOption>
              <ElOption label="管理员" value="admin"></ElOption>
            </ElSelect>
          </ElFormItem>

          <ElFormItem prop="username" label="用户名">
            <ElInput v-model="model.username" type="text" placeholder="请输入用户名">
              <template v-slot:suffix>
                <i class="bi bi-person-fill icon-suffix"></i>
              </template>
            </ElInput>
          </ElFormItem>

          <ElFormItem prop="password" label="密码">
            <ElInput v-model="model.password" type="password" placeholder="请输入密码">
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
              <label for="remember-account" class="checkbox-label">记住账号</label>
            </div>
            <span class="forgot-password-link">忘记密码?</span>
          </div>

          <ElFormItem>
            <ElButton type="primary" native-type="submit" style="width: 100%">登录</ElButton>
          </ElFormItem>

          <!-- 创建账号链接 -->
          <div class="signup-link-wrapper">
            还没有账号?
            <RouterLink :to="{ name: 'register' }" class="signup-link">创建账号</RouterLink>
          </div>
        </ElForm>

        <!-- 底部信息 -->
        <footer class="login-footer">
          <div>Copyright &copy; {{ currentYear }} Mall Platform.</div>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h, onUnmounted, watchEffect } from 'vue'
import {
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElMessage,
  ElSelect,
  ElOption,
  ElNotification,
  type FormInstance,
} from 'element-plus'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/utils/useTheme'
import ModerationIcon from '@/assets/svg/moderation-icon.svg?component'
// npm install bootstrap-icons
// 引入 Bootstrap Icons
import 'bootstrap-icons/font/bootstrap-icons.css'

const userStore = useUserStore()

const form = ref<FormInstance>()
const router = useRouter()
const route = useRoute()
const { isDark, toggleTheme } = useTheme() // 日夜主题全站共用
const rememberAccount = ref(false)
const currentYear = new Date().getFullYear()
const registeredUsername = typeof route.query.username === 'string' ? route.query.username : ''

const model = reactive({
  role: registeredUsername ? 'user' : '',
  username: registeredUsername,
  password: '',
})

const rules = reactive({
  role: [{ required: true, message: '请选择账户类型。', trigger: ['blur'] }],
  username: [
    { required: true, whitespace: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 16, message: '用户名需2–16个字符', trigger: 'change' },
  ],
  password: [
    { required: true, whitespace: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码需6–32个字符', trigger: 'change' },
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
        request
          .post('/login', {
            username: model.username,
            password: model.password,
            role: model.role,
          })
          .then((response) => {
            if (response.data.status === true) {
              // 保存用户信息和token到store
              const userData = {
                ...response.data.payload,
                user: {
                  ...response.data.payload.user,
                  role: model.role,
                },
              }

              userStore.login(userData)
              userStore.cache('user', userData)

              ElNotification({
                title: '登录成功',
                message: h('span', { style: 'color: #5EDFD6' }, `欢迎回来：${model.username}`),
                duration: 3000,
              })

              // 根据角色跳转到不同首页（暂时都跳转到用户首页）
              if (model.role === 'admin') {
                router.replace({ name: 'admin_dashboard' }) // 管理员首页
              } else {
                router.replace({ name: 'index' }) // 用户首页
              }
            } else {
              // 显示后端返回的错误消息
              const message = response.data.payload?.message || '登录失败。'
              ElMessage.error(message)
            }
          })
          .catch(() => {
            ElMessage.error('登录失败。')
          })
      }
    })
    .catch(() => undefined)
}

onUnmounted(() => {
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
  gap: 0.5rem;
}

.logo-box img {
  width: 2rem;
  height: 2rem;
  object-fit: contain;
  transition: transform 0.3s ease; //悬停动画
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
