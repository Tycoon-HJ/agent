<template>
  <div
    class="message fade-in-up"
    :class="message.role === 'user' ? 'message-user' : 'message-ai'"
  >
    <!-- AI:头像 + 内容 -->
    <template v-if="message.role === 'assistant'">
      <div class="ai-avatar">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <div class="ai-content">
        <!-- AI 生成的图片 -->
        <div v-if="message.images && message.images.length > 0" class="ai-images">
          <div
            v-for="(img, index) in message.images"
            :key="index"
            class="ai-image-item"
          >
            <template v-if="isRenderableImage(img)">
              <img :src="img" :alt="`生成的图片 ${index + 1}`" @click="previewImage(img)"/>
              <button class="image-download-btn" @click.stop="downloadImage(img, index)" title="下载图片">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </button>
            </template>
            <div v-else class="image-placeholder">图片不可用</div>
          </div>
        </div>

        <!-- Markdown 正文 -->
        <div class="ai-body" :class="{ 'is-streaming': isStreaming }">
          <div v-if="isStreaming && !displayedText" class="thinking-indicator">
            <span class="thinking-dot"></span>
            <span class="thinking-dot"></span>
            <span class="thinking-dot"></span>
            <span class="thinking-label">正在思考…</span>
          </div>
          <MarkdownRender v-else :content="isStreaming ? displayedText : message.content"/>
          <span v-if="isStreaming && displayedText" class="cursor-blink">▍</span>
        </div>

        <!-- 操作按钮(悬停显示) -->
        <div v-if="!isStreaming && message.content" class="ai-actions">
          <button class="action-btn" :class="{ copied }" @click="copyContent" :title="copied ? '已复制' : '复制'">
            <svg v-if="copied" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <rect x="9" y="9" width="13" height="13" rx="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
          </button>
          <button class="action-btn" @click="retry" title="重新生成">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M1 4v6h6"/>
              <path d="M3.51 15a9 9 0 102.13-9.36L1 10"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: liked }" @click="toggleLike" title="回答不错">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3H14z"/>
              <path d="M7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: disliked }" @click="toggleDislike" title="回答不好">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3H10z"/>
              <path d="M17 2h2.67A2.31 2.31 0 0122 4v7a2.31 2.31 0 01-2.33 2H17"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: favorite }" @click="toggleFavorite" title="收藏">
            <svg width="14" height="14" viewBox="0 0 24 24" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
          </button>
          <button class="action-btn" @click="share" title="分享">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="18" cy="5" r="3"/>
              <circle cx="6" cy="12" r="3"/>
              <circle cx="18" cy="19" r="3"/>
              <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
              <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
            </svg>
          </button>
        </div>
      </div>
    </template>

    <!-- 用户:右侧气泡 -->
    <template v-else>
      <div class="user-bubble">
        <!-- 图片 -->
        <div v-if="message.images && message.images.length > 0" class="user-images">
          <div
            v-for="(img, index) in message.images"
            :key="index"
            class="user-image-item"
          >
            <template v-if="isRenderableImage(img)">
              <img :src="img" :alt="`上传的图片 ${index + 1}`" @click="previewImage(img)"/>
            </template>
            <div v-else class="image-placeholder">图片不可用</div>
          </div>
        </div>
        <!-- 文件 -->
        <div v-if="message.files && message.files.length > 0" class="user-files">
          <div v-for="(file, index) in message.files" :key="index" class="user-file-chip">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            <span>{{ file.name }}</span>
          </div>
        </div>
        <!-- 文本 -->
        <div v-if="message.content" class="user-text">{{ message.content }}</div>
      </div>
    </template>

    <!-- 图片预览弹层 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="previewUrl" class="image-modal" @click="previewUrl = ''">
          <div class="image-modal-backdrop"></div>
          <img :src="previewUrl" alt="预览" class="image-modal-img"/>
          <button class="image-modal-close" @click.stop="previewUrl = ''">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ChatMessage as ChatMessageModel } from '../types/chat'
import MarkdownRender from './MarkdownRender.vue'

interface Props {
  message: ChatMessageModel
  isStreaming?: boolean
}

const { message, isStreaming = false } = defineProps<Props>()
const emit = defineEmits(['retry', 'like', 'dislike', 'favorite', 'share'])

const liked = ref(!!(message as any)?.liked)
const disliked = ref(!!(message as any)?.disliked)
const favorite = ref(!!(message as any)?.favorite)
const copied = ref(false)
const previewUrl = ref('')
const displayedText = ref(message.content || '')
let streamTimer: ReturnType<typeof window.setInterval> | null = null

watch(
  () => message.content,
  (newVal, oldVal) => {
    const next = newVal || ''
    const prev = oldVal || ''
    if (streamTimer) {
      window.clearInterval(streamTimer)
      streamTimer = null
    }
    if (!isStreaming || next.length <= prev.length) {
      displayedText.value = next
      return
    }
    const suffix = next.slice(prev.length)
    let i = 0
    streamTimer = window.setInterval(() => {
      displayedText.value += suffix[i++] || ''
      if (i >= suffix.length && streamTimer) {
        window.clearInterval(streamTimer)
        streamTimer = null
      }
    }, 12)
  },
  { immediate: true },
)

function isRenderableImage(url: unknown): boolean {
  if (typeof url !== 'string') return false
  return url.startsWith('data:') || url.startsWith('http:') || url.startsWith('https:') || url.startsWith('//')
}

function previewImage(url: string) {
  previewUrl.value = url
}

function downloadImage(url: string, index: number) {
  const link = document.createElement('a')
  link.href = url
  link.download = `ai-image-${Date.now()}-${index}.png`
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function copyContent() {
  try {
    const text = message.content || ''
    if (!text) return
    navigator.clipboard.writeText(text)
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 1500)
  } catch (e) {
    console.warn('复制失败', e)
  }
}

function retry() {
  emit('retry', message.id)
}

function toggleLike() {
  liked.value = !liked.value
  if (liked.value) disliked.value = false
  emit('like', { id: message.id, value: liked.value })
}

function toggleDislike() {
  disliked.value = !disliked.value
  if (disliked.value) liked.value = false
  emit('dislike', { id: message.id, value: disliked.value })
}

function toggleFavorite() {
  favorite.value = !favorite.value
  emit('favorite', { id: message.id, value: favorite.value })
}

function share() {
  try {
    navigator.clipboard.writeText(`[AI 回复]\n${message.content || ''}`)
  } catch (e) {
    console.warn('分享失败', e)
  }
  emit('share', message.id)
}
</script>

<style scoped>
/* ── 消息容器 ───────────────────────────── */
.message {
  display: flex;
  gap: 14px;
  margin-bottom: 28px;
  max-width: 100%;
}

/* ── 用户消息 ───────────────────────────── */
.message-user {
  justify-content: flex-end;
}

.user-bubble {
  max-width: 75%;
  background: var(--user-bubble-bg);
  border-radius: 18px;
  padding: 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-text {
  font-size: 14.5px;
  line-height: 1.7;
  color: var(--user-bubble-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.user-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.user-image-item {
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
}

.user-image-item img {
  width: 100%;
  height: auto;
  display: block;
  max-height: 200px;
  object-fit: cover;
  transition: transform var(--duration-fast) var(--ease-out);
}

.user-image-item:hover img {
  transform: scale(1.03);
}

.user-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.user-file-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--bg-surface-active);
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 5px 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.user-file-chip span {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── AI 消息 ────────────────────────────── */
.message-ai {
  align-items: flex-start;
}

.ai-avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  margin-top: 2px;
  box-shadow: 0 3px 10px rgba(99, 102, 241, 0.25);
}

.ai-content {
  flex: 1;
  min-width: 0;
}

.ai-images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.ai-image-item {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--border);
}

.ai-image-item img {
  width: 100%;
  height: auto;
  display: block;
  max-height: 350px;
  object-fit: cover;
  transition: transform var(--duration-fast) var(--ease-out);
}

.ai-image-item:hover img {
  transform: scale(1.02);
}

.ai-image-item:hover .image-download-btn {
  opacity: 1;
}

.image-download-btn {
  position: absolute;
  bottom: 10px;
  right: 10px;
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(10px);
  color: white;
  border-radius: 9px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out);
}

.image-download-btn:hover {
  background: rgba(0, 0, 0, 0.75);
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80px;
  color: var(--text-tertiary);
  background: var(--bg-surface);
  font-size: 13px;
  border-radius: 12px;
  padding: 12px;
}

/* AI 正文:无卡片,直接排版,阅读更舒适 */
.ai-body {
  font-size: 14.5px;
  line-height: 1.8;
  color: var(--text-primary);
  position: relative;
  padding-top: 2px;
}

/* 思考中指示器 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
}

.thinking-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: typing-dot 1.4s infinite;
}

.thinking-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-dot:nth-child(3) {
  animation-delay: 0.4s;
}

.thinking-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-left: 4px;
  animation: pulse 2s infinite;
}

/* 流式光标 */
.cursor-blink {
  color: var(--primary);
  font-size: 15px;
  line-height: 1;
  animation: pulse 1s infinite;
  margin-left: 2px;
}

/* 操作按钮:默认隐藏,悬停消息时显示 */
.ai-actions {
  display: flex;
  gap: 2px;
  margin-top: 10px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.message-ai:hover .ai-actions,
.ai-actions:focus-within {
  opacity: 1;
}

.action-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-out);
}

.action-btn:hover {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.action-btn.active {
  color: var(--primary);
  background: var(--primary-soft);
}

.action-btn.copied {
  color: var(--success);
}

/* 触屏设备常显操作按钮 */
@media (hover: none) {
  .ai-actions {
    opacity: 1;
  }
}

/* ── 图片预览弹层 ───────────────────────── */
.image-modal {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.image-modal-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(20px);
}

.image-modal-img {
  position: relative;
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 12px;
  z-index: 1;
}

.image-modal-close {
  position: absolute;
  top: 24px;
  right: 24px;
  width: 40px;
  height: 40px;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
  color: white;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  transition: all var(--duration-fast) var(--ease-out);
}

.image-modal-close:hover {
  background: rgba(255, 255, 255, 0.22);
  transform: scale(1.05);
}

/* 弹层过渡 */
.modal-enter-active {
  transition: all 0.25s var(--ease-out);
}

.modal-leave-active {
  transition: all 0.2s ease-in;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .image-modal-img {
  transform: scale(0.95);
}
</style>
