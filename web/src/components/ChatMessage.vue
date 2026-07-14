<template>
  <div
    class="message"
    :class="message.role === 'user' ? 'message-user' : 'message-ai'"
  >
    <!-- AI: Avatar + Content -->
    <template v-if="message.role === 'assistant'">
      <div class="ai-avatar">
        <div class="ai-avatar-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
      </div>
      <div class="ai-content">
        <!-- AI generated images -->
        <div v-if="message.images && message.images.length > 0" class="ai-images">
          <div
            v-for="(img, index) in message.images"
            :key="index"
            class="ai-image-item"
          >
            <template v-if="isRenderableImage(img)">
              <img :src="img" :alt="`Generated image ${index + 1}`" @click="previewImage(img)"/>
              <button class="image-download-btn" @click.stop="downloadImage(img, index)" title="Download">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
              </button>
            </template>
            <div v-else class="image-placeholder">Image unavailable</div>
          </div>
        </div>

        <!-- Markdown Body -->
        <div class="ai-body" :class="{ 'is-streaming': isStreaming }">
          <div v-if="isStreaming && !displayedText" class="thinking-indicator">
            <span class="thinking-dot"></span>
            <span class="thinking-dot"></span>
            <span class="thinking-dot"></span>
            <span class="thinking-label">Thinking...</span>
          </div>
          <MarkdownRender v-else :content="isStreaming ? displayedText : message.content"/>
          <span v-if="isStreaming && displayedText" class="cursor-blink">▋</span>
        </div>

        <!-- Confirmation Banner -->
        <div v-if="message.needsConfirmation && !isStreaming" class="confirmation-banner">
          <div class="confirmation-icon">⚠️</div>
          <div class="confirmation-content">
            <div class="confirmation-title">需要确认</div>
            <div class="confirmation-message">{{ message.confirmationMessage || '此操作需要您的确认' }}</div>
            <div v-if="message.pendingAction" class="confirmation-action">
              <strong>待执行操作：</strong>{{ message.pendingAction }}
            </div>
          </div>
          <button class="confirmation-btn" @click="confirm" :disabled="isConfirming">
            <span v-if="isConfirming" class="confirming-spinner"></span>
            <span v-else>确认继续</span>
          </button>
        </div>

        <!-- Actions -->
        <div v-if="!isStreaming" class="ai-actions">
          <button class="action-btn" @click="copyContent" title="Copy">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <rect x="9" y="9" width="13" height="13" rx="2"/>
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
            </svg>
          </button>
          <button class="action-btn" @click="retry" title="Regenerate">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M1 4v6h6"/>
              <path d="M3.51 15a9 9 0 102.13-9.36L1 10"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: liked }" @click="toggleLike" title="Good response">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3H14z"/>
              <path d="M7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: disliked }" @click="toggleDislike" title="Bad response">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M10 15v4a3 3 0 003 3l4-9V2H5.72a2 2 0 00-2 1.7l-1.38 9a2 2 0 002 2.3H10z"/>
              <path d="M17 2h2.67A2.31 2.31 0 0122 4v7a2.31 2.31 0 01-2.33 2H17"/>
            </svg>
          </button>
          <button class="action-btn" :class="{ active: favorite }" @click="toggleFavorite" title="Save">
            <svg width="14" height="14" viewBox="0 0 24 24" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
          </button>
          <button class="action-btn" @click="share" title="Share">
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

    <!-- User: Bubble -->
    <template v-else>
      <div class="user-bubble">
        <!-- Images -->
        <div v-if="message.images && message.images.length > 0" class="user-images">
          <div
            v-for="(img, index) in message.images"
            :key="index"
            class="user-image-item"
          >
            <template v-if="isRenderableImage(img)">
              <img :src="img" :alt="`Uploaded image ${index + 1}`" @click="previewImage(img)"/>
            </template>
            <div v-else class="image-placeholder">Image unavailable</div>
          </div>
        </div>
        <!-- Files -->
        <div v-if="message.files && message.files.length > 0" class="user-files">
          <div v-for="(file, index) in message.files" :key="index" class="user-file-chip">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            <span>{{ file.name }}</span>
          </div>
        </div>
        <!-- Text -->
        <div v-if="message.content" class="user-text">{{ message.content }}</div>
      </div>
    </template>

    <!-- Image Preview Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="previewUrl" class="image-modal" @click="previewUrl = ''">
          <div class="image-modal-backdrop"></div>
          <img :src="previewUrl" alt="Preview" class="image-modal-img"/>
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
import { ref, watch, onUnmounted } from 'vue'
import type { ChatMessage as ChatMessageModel } from '../types/chat'
import MarkdownRender from './MarkdownRender.vue'

interface Props {
  message: ChatMessageModel
  isStreaming?: boolean
}

const { message, isStreaming = false } = defineProps<Props>()
const emit = defineEmits(['retry', 'like', 'dislike', 'favorite', 'share', 'confirm'])

const liked = ref(!!message?.liked)
const disliked = ref(!!message?.disliked)
const favorite = ref(!!message?.favorite)
const previewUrl = ref('')
const displayedText = ref(message.content || '')
const isConfirming = ref(false)
let streamTimer: ReturnType<typeof window.setInterval> | null = null

onUnmounted(() => {
  if (streamTimer) {
    window.clearInterval(streamTimer)
    streamTimer = null
  }
})

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
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

function copyContent() {
  try {
    const text = message.content || ''
    if (text) navigator.clipboard.writeText(text)
  } catch (e) {
    console.warn('Copy failed', e)
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
    navigator.clipboard.writeText(`[AI Response]\n${message.content || ''}`)
  } catch (e) {
    console.warn('Share failed', e)
  }
  emit('share', message.id)
}

async function confirm() {
  if (!message.confirmationId || isConfirming.value) return
  isConfirming.value = true
  try {
    emit('confirm', message.confirmationId)
  } finally {
    // Reset after a delay (parent will handle the actual confirmation)
    setTimeout(() => {
      isConfirming.value = false
    }, 2000)
  }
}
</script>

<style scoped>
/* ── Message Container ────────────────────── */
.message {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  max-width: 100%;
}

/* ── User Message ─────────────────────────── */
.message-user {
  justify-content: flex-end;
}

.user-bubble {
  max-width: 70%;
  background: var(--gradient-primary);
  border-radius: 20px 20px 6px 20px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 4px 20px rgba(99,102,241,0.2);
}

.user-text {
  font-size: 14px;
  line-height: 1.6;
  color: white;
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
  transition: transform 0.2s;
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
  gap: 5px;
  background: rgba(255,255,255,0.15);
  border-radius: 8px;
  padding: 5px 10px;
  font-size: 12px;
  color: rgba(255,255,255,0.9);
}

.user-file-chip span {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── AI Message ───────────────────────────── */
.message-ai {
  align-items: flex-start;
}

.ai-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-avatar-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(99,102,241,0.25);
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
  transition: transform 0.2s;
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
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(10px);
  color: white;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out);
}

.image-download-btn:hover {
  background: rgba(0,0,0,0.8);
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

/* AI Body */
.ai-body {
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 20px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
  position: relative;
  transition: border-color var(--duration-normal) var(--ease-out);
}

.ai-body:hover {
  border-color: var(--border-light);
}

.ai-body.is-streaming {
  border-color: rgba(99,102,241,0.2);
}

/* Thinking Indicator */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
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
  color: var(--text-secondary);
  margin-left: 4px;
  animation: pulse 2s infinite;
}

/* Cursor */
.cursor-blink {
  color: var(--primary);
  font-size: 16px;
  line-height: 1;
  animation: pulse 1s infinite;
  margin-left: 2px;
}

/* AI Actions */
.ai-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding-left: 4px;
}

/* Confirmation Banner */
.confirmation-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
  padding: 16px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 14px;
  transition: all var(--duration-normal) var(--ease-out);
}

.confirmation-banner:hover {
  background: rgba(245, 158, 11, 0.12);
  border-color: rgba(245, 158, 11, 0.3);
}

.confirmation-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.confirmation-content {
  flex: 1;
  min-width: 0;
}

.confirmation-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.confirmation-message {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.confirmation-action {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(245, 158, 11, 0.15);
}

.confirmation-btn {
  flex-shrink: 0;
  padding: 10px 20px;
  border: none;
  background: var(--gradient-primary);
  color: white;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  border-radius: 10px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 100px;
}

.confirmation-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(99,102,241,0.3);
}

.confirmation-btn:active:not(:disabled) {
  transform: translateY(0);
}

.confirmation-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.confirming-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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
  color: var(--text-secondary);
}

.action-btn.active {
  color: var(--primary);
  background: rgba(99,102,241,0.1);
}

/* ── Image Preview Modal ──────────────────── */
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
  background: rgba(0,0,0,0.85);
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
  background: rgba(255,255,255,0.1);
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
  background: rgba(255,255,255,0.2);
  transform: scale(1.05);
}

/* Modal Transition */
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
