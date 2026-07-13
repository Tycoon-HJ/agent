<template>
  <div class="input-container">
    <!-- Drop overlay -->
    <Transition name="fade">
      <div v-if="isDragging" class="drop-overlay">
        <div class="drop-overlay-content">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span>Drop files here</span>
        </div>
      </div>
    </Transition>

    <!-- Input Bar -->
    <div
      class="input-bar"
      :class="{ focused: isFocused, 'has-content': hasContent }"
      @drop.prevent="onDrop"
      @dragover.prevent
      @dragenter.prevent="onDragEnter"
      @dragleave.prevent="onDragLeave"
    >
      <!-- Left Actions -->
      <div class="input-actions-left">
        <button class="input-action-btn" @click="triggerFileSelect" title="Attach file">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 01-8.49-8.49l9.19-9.19a4 4 0 015.66 5.66l-9.2 9.19a2 2 0 01-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <input ref="fileInput" class="visually-hidden" type="file" multiple @change="onFilesSelected" />
      </div>

      <!-- Textarea -->
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

      <!-- Right Actions -->
      <div class="input-actions-right">
        <!-- Model Selector -->
        <div class="model-selector" @click="toggleModelMenu">
          <span class="model-name">{{ currentModel }}</span>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>

        <!-- Send / Stop Button -->
        <button
          v-if="isStreaming"
          class="btn-stop"
          @click="$emit('stop')"
          title="Stop generating"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="6" y="6" width="12" height="12" rx="2"/>
          </svg>
        </button>
        <button
          v-else
          class="btn-send"
          :class="{ active: canSend }"
          :disabled="!canSend"
          @click="$emit('send')"
          title="Send message"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Input Hint -->
    <div class="input-hint">
      <span>Press <kbd>Enter</kbd> to send, <kbd>Shift+Enter</kbd> for new line</span>
    </div>

    <!-- Model Menu -->
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

// Auto resize textarea
watch(() => props.modelValue, () => {
  nextTick(() => {
    if (textarea.value) {
      textarea.value.style.height = 'auto'
      textarea.value.style.height = Math.min(textarea.value.scrollHeight, 160) + 'px'
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

// Close model menu on click outside
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
  max-width: 1000px;
  margin: 0 auto;
}

/* Drop Overlay */
.drop-overlay {
  position: absolute;
  inset: -4px;
  border: 2px dashed var(--primary);
  border-radius: 28px;
  background: rgba(99,102,241,0.08);
  backdrop-filter: blur(10px);
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
  color: var(--primary-light);
  font-weight: 600;
  font-size: 14px;
}

/* Input Bar */
.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--bg-glass);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 8px 8px 8px 4px;
  transition: all var(--duration-normal) var(--ease-out);
  min-height: 56px;
}

.input-bar.focused {
  border-color: var(--primary);
  box-shadow: var(--shadow-glow);
}

.input-bar.has-content {
  border-color: rgba(99,102,241,0.3);
}

/* Left Actions */
.input-actions-left {
  display: flex;
  align-items: center;
  gap: 2px;
  padding-left: 4px;
}

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
}

.input-action-btn:hover {
  background: var(--bg-surface-hover);
  color: var(--text-secondary);
}

/* Textarea */
.input-bar textarea {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.6;
  resize: none;
  outline: none;
  min-height: 40px;
  max-height: 160px;
  padding: 8px 4px;
}

.input-bar textarea::placeholder {
  color: var(--text-tertiary);
}

.input-bar textarea:disabled {
  opacity: 0.6;
}

/* Right Actions */
.input-actions-right {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 4px;
}

/* Model Selector */
.model-selector {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  user-select: none;
}

.model-selector:hover {
  background: var(--bg-surface-hover);
}

.model-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

/* Send Button */
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
  box-shadow: 0 4px 15px rgba(99,102,241,0.3);
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

/* Stop Button */
.btn-stop {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 14px;
  background: rgba(239,68,68,0.15);
  color: var(--error);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-normal) var(--ease-out);
  flex-shrink: 0;
}

.btn-stop:hover {
  background: rgba(239,68,68,0.25);
  transform: scale(1.05);
}

/* Input Hint */
.input-hint {
  text-align: center;
  padding: 8px 0 0;
  font-size: 11px;
  color: var(--text-muted);
}

.input-hint kbd {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 1px 5px;
  font-family: var(--font-mono);
  font-size: 10px;
}

/* Model Menu */
.model-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  right: 8px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-light);
  border-radius: 16px;
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
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 10px;
  transition: all var(--duration-fast) var(--ease-out);
}

.model-menu-item:hover {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.model-menu-item.active {
  color: var(--primary-light);
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
  box-shadow: 0 0 8px rgba(99,102,241,0.5);
}

/* Transitions */
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
