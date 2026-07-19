<template>
  <div class="input-container">
    <!-- 拖拽上传遮罩 -->
    <Transition name="fade">
      <div v-if="isDragging" class="drop-overlay">
        <div class="drop-overlay-content">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span>松开以上传文件</span>
        </div>
      </div>
    </Transition>

    <!-- 输入栏 -->
    <div
      class="input-bar"
      :class="{ focused: isFocused, 'has-content': hasContent }"
      @drop.prevent="onDrop"
      @dragover.prevent
      @dragenter.prevent="onDragEnter"
      @dragleave.prevent="onDragLeave"
    >
      <!-- 左侧:附件 -->
      <button class="input-action-btn" @click="triggerFileSelect" title="添加附件">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
        </svg>
      </button>
      <input ref="fileInput" class="visually-hidden" type="file" multiple @change="onFilesSelected" />

      <!-- 文本域 -->
      <textarea
        ref="textarea"
        v-model="model"
        :placeholder="placeholder"
        @keydown.enter.exact.prevent="onEnter"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @paste="onPaste"
        :disabled="isStreaming"
        rows="1"
      ></textarea>

      <!-- 右侧操作 -->
      <div class="input-actions-right">
        <!-- 模型选择 -->
        <div class="model-selector" @click="toggleModelMenu" title="选择模型">
          <span class="model-name">{{ currentModel }}</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>

        <!-- 发送 / 停止 -->
        <button
          v-if="isStreaming"
          class="btn-stop"
          @click="$emit('stop')"
          title="停止生成"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
        <button
          v-else
          class="btn-send"
          :class="{ active: canSend }"
          :disabled="!canSend"
          @click="$emit('send')"
          title="发送"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="19" x2="12" y2="5"/>
            <polyline points="5 12 12 5 19 12"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- 提示行 -->
    <div class="input-hint">
      <span><kbd>Enter</kbd> 发送 · <kbd>Shift + Enter</kbd> 换行</span>
      <span class="input-hint-divider">·</span>
      <span>内容由 AI 生成,请注意甄别</span>
    </div>

    <!-- 模型菜单 -->
    <Transition name="menu">
      <div v-if="showModelMenu" class="model-menu">
        <button
          v-for="m in models"
          :key="m"
          class="model-menu-item"
          :class="{ active: m === currentModel }"
          @click="selectModel(m)"
        >
          <span class="model-menu-dot" :class="{ active: m === currentModel }"></span>
          {{ m }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  placeholder: string
  isStreaming: boolean
  canSend: boolean
}>()

const emit = defineEmits(['update:modelValue', 'send', 'stop', 'add-files', 'paste'])

const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const isFocused = ref(false)
const showModelMenu = ref(false)

const models = ['GPT-5', 'Claude', 'DeepSeek', 'Gemini']
const currentModel = ref('Claude')

const model = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const hasContent = computed(() => props.modelValue.trim().length > 0)

// 文本域自适应高度
watch(() => props.modelValue, () => {
  nextTick(() => {
    if (textarea.value) {
      textarea.value.style.height = 'auto'
      textarea.value.style.height = Math.min(textarea.value.scrollHeight, 180) + 'px'
    }
  })
})

function triggerFileSelect() {
  fileInput.value?.click()
}

function onFilesSelected(e: Event) {
  const t = e.target as HTMLInputElement
  if (!t.files) return
  emit('add-files', Array.from(t.files))
  t.value = ''
}

function onDrop(e: DragEvent) {
  const items = e.dataTransfer?.files
  if (!items) return
  emit('add-files', Array.from(items))
  isDragging.value = false
}

function onPaste(e: ClipboardEvent) {
  emit('paste', e)
}

function onDragEnter() { isDragging.value = true }
function onDragLeave() { isDragging.value = false }
function onEnter() { emit('send') }

function toggleModelMenu() {
  showModelMenu.value = !showModelMenu.value
}

function selectModel(m: string) {
  currentModel.value = m
  showModelMenu.value = false
}

// 点击外部关闭模型菜单
if (typeof window !== 'undefined') {
  window.addEventListener('click', (e) => {
    const target = e.target as HTMLElement
    if (!target.closest('.model-selector') && !target.closest('.model-menu')) {
      showModelMenu.value = false
    }
  })
}
</script>

<style scoped>
.input-container {
  position: relative;
  width: 100%;
}

/* ── 拖拽遮罩 ───────────────────────────── */
.drop-overlay {
  position: absolute;
  inset: -6px;
  border: 2px dashed var(--primary);
  border-radius: 24px;
  background: var(--primary-soft);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.drop-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--primary-strong);
  font-weight: 600;
  font-size: 14px;
}

/* ── 输入栏 ─────────────────────────────── */
.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: 22px;
  padding: 8px;
  transition: border-color var(--duration-normal) var(--ease-out),
              box-shadow var(--duration-normal) var(--ease-out),
              background-color var(--duration-normal) ease;
  min-height: 56px;
  box-shadow: var(--shadow-sm);
}

.input-bar.focused {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-glow);
}

/* 附件按钮 */
.input-action-btn {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-out);
  flex-shrink: 0;
}

.input-action-btn:hover {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

/* 文本域 */
.input-bar textarea {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 14.5px;
  line-height: 1.6;
  resize: none;
  outline: none;
  min-height: 40px;
  max-height: 180px;
  padding: 9px 4px;
}

.input-bar textarea::placeholder {
  color: var(--text-tertiary);
}

.input-bar textarea:disabled {
  opacity: 0.6;
}

/* 右侧操作 */
.input-actions-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* 模型选择器 */
.model-selector {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
  user-select: none;
}

.model-selector:hover {
  background: var(--bg-surface-hover);
}

.model-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}

.model-selector svg {
  color: var(--text-tertiary);
}

/* 发送按钮 */
.btn-send {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 14px;
  background: var(--bg-surface);
  color: var(--text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-normal) var(--ease-out);
  flex-shrink: 0;
}

.btn-send.active {
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}

.btn-send.active:hover {
  box-shadow: var(--shadow-glow-lg);
  transform: scale(1.05);
}

.btn-send:active {
  transform: scale(0.95);
}

.btn-send:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* 停止按钮 */
.btn-stop {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 14px;
  background: var(--primary);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-normal) var(--ease-out);
  flex-shrink: 0;
  animation: pulse 2s infinite;
}

.btn-stop:hover {
  transform: scale(1.05);
}

/* ── 提示行 ─────────────────────────────── */
.input-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding-top: 10px;
  font-size: 11.5px;
  color: var(--text-muted);
  user-select: none;
}

.input-hint kbd {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-tertiary);
}

.input-hint-divider {
  opacity: 0.6;
}

/* ── 模型菜单 ───────────────────────────── */
.model-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  right: 8px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-light);
  border-radius: 14px;
  padding: 6px;
  min-width: 160px;
  box-shadow: var(--shadow-lg);
  z-index: 20;
}

.model-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 9px;
  transition: all var(--duration-fast) var(--ease-out);
}

.model-menu-item:hover {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.model-menu-item.active {
  color: var(--primary-strong);
}

.model-menu-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  transition: all var(--duration-fast) var(--ease-out);
}

.model-menu-dot.active {
  background: var(--primary);
  box-shadow: 0 0 8px rgba(99, 102, 241, 0.5);
}

/* ── 过渡动画 ───────────────────────────── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.menu-enter-active {
  transition: all 0.2s var(--ease-out);
}

.menu-leave-active {
  transition: all 0.15s ease-in;
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.95);
}
</style>
