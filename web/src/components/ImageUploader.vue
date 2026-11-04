<template>
  <div class="image-uploader">
    <!-- 自己发请求（http-request）：走 request 实例才会带上 Token；不显示文件列表，只预览当前这一张 -->
    <ElUpload
      :show-file-list="false"
      :accept="ACCEPT"
      :before-upload="checkFile"
      :http-request="upload"
      :disabled="uploading"
    >
      <div v-loading="uploading" class="uploader-box" :style="{ width: boxSize, height: boxSize }">
        <ElImage v-if="model" class="uploader-image" :src="assetUrl(model)" fit="cover" />
        <div v-else class="uploader-placeholder">
          <i class="bi bi-plus-lg"></i>
          <span>上传图片</span>
        </div>
      </div>
    </ElUpload>
    <div class="uploader-tip text-secondary">
      支持 JPG、PNG、GIF、WEBP，不超过 5MB
      <ElButton v-if="model" link type="danger" :disabled="uploading" @click="model = ''">
        移除
      </ElButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ElButton,
  ElImage,
  ElMessage,
  ElUpload,
  vLoading,
  type UploadRawFile,
  type UploadRequestOptions,
} from 'element-plus'
import request from '@/utils/request'
import type { ApiResult } from '@/types/api'
import { assetUrl } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    // 上传用途，对应后端 /upload/{type}：avatars 头像 / images 商品图片
    type: 'avatars' | 'images'
    // 预览框边长（px）
    size?: number
  }>(),
  { size: 120 },
)

// 图片的访问路径（如 /images/xxx.png），没有图片时为空字符串
const model = defineModel<string>({ default: '' })

// 与后端 ImageFormat、spring.servlet.multipart.max-file-size 保持一致；
// 这里只是提前拦下明显不对的文件，真正的格式校验在后端按文件内容做
const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'
const MAX_SIZE = 5 * 1024 * 1024

const boxSize = computed(() => `${props.size}px`)
const uploading = ref(false)

const checkFile = (file: UploadRawFile) => {
  if (!ACCEPT.split(',').includes(file.type)) {
    ElMessage.error('只支持 JPG、PNG、GIF、WEBP 格式的图片')
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('图片不能超过 5MB')
    return false
  }
  return true
}

const upload = async ({ file }: UploadRequestOptions) => {
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await request.post<ApiResult<{ url: string }>>(
      `/upload/${props.type}`,
      formData,
    )
    if (!data.status) {
      ElMessage.error(data.payload.message || '上传失败')
      return
    }
    // 只是拿到了访问路径，要随表单保存后才会写进库
    model.value = data.payload.url
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style lang="scss" scoped>
.uploader-box {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--el-color-primary);
  }
}

.uploader-image {
  width: 100%;
  height: 100%;
}

.uploader-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  color: var(--el-text-color-placeholder);
  font-size: 0.75rem;

  i {
    font-size: 1.5rem;
  }
}

.uploader-tip {
  margin-top: 0.25rem;
  font-size: 0.75rem;
  line-height: 1.5;
}
</style>
