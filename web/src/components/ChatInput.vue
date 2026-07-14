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

    <!-- Skill Menu -->
    <Transition name="menu">
      <div v-if="showSkillMenu" class="skill-menu">
        <div class="skill-menu-header">
          <span class="skill-menu-title">Skills</span>
          <span class="skill-menu-hint">Select a skill to use</span>
        </div>
        <div class="skill-menu-list">
          <button
            v-for="(skill, index) in filteredSkills"
            :key="skill.name"
            class="skill-menu-item"
            :class="{ active: index === selectedSkillIndex }"
            @click="selectSkill(skill)"
            @mouseenter="selectedSkillIndex = index"
          >
            <div class="skill-item-icon">⚡</div>
            <div class="skill-item-info">
              <div class="skill-item-name">{{ skill.name }}</div>
              <div class="skill-item-desc">{{ skill.description }}</div>
            </div>
          </button>
          <div v-if="filteredSkills.length === 0" class="skill-menu-empty">
            No matching skills found
          </div>
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

      <!-- Selected Skill Badge -->
      <div v-if="selectedSkill" class="selected-skill-badge">
        <span class="skill-badge-name">{{ selectedSkill.name }}</span>
        <button class="skill-badge-remove" @click="removeSkill">×</button>
      </div>

      <!-- Textarea -->
      <textarea
        ref="textarea"
        v-model="model"
        :placeholder="placeholder"
        @keydown="onKeydown"
        @focus="isFocused = true"
        @blur="onBlur"
        @paste="onPaste"
        :disabled="isStreaming"
        rows="1"
      ></textarea>

      <!-- Right Actions -->
      <div class="input-actions-right">

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
      <span>
        <kbd>Enter</kbd> send · <kbd>Shift+Enter</kbd> new line · <kbd>/</kbd> skills
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, watch, onMounted, onUnmounted } from 'vue'

interface Skill {
  name: string
  description: string
  requiredTools: string[]
}

const props = defineProps<{
  modelValue: string
  placeholder: string
  isStreaming: boolean
  canSend: boolean
}>()

const emit = defineEmits(['update:modelValue', 'send', 'stop', 'add-files', 'paste', 'skill-selected'])

const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const isFocused = ref(false)
let blurTimeout: ReturnType<typeof setTimeout> | null = null

// Skill related state
const skills = ref<Skill[]>([])
const showSkillMenu = ref(false)
const selectedSkillIndex = ref(0)
const selectedSkill = ref<Skill | null>(null)
const skillSearchQuery = ref('')

const model = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})

const hasContent = computed(() => props.modelValue.trim().length > 0)

// Filter skills based on search query
const filteredSkills = computed(() => {
  if (!skillSearchQuery.value) return skills.value
  const query = skillSearchQuery.value.toLowerCase()
  return skills.value.filter(s =>
    s.name.toLowerCase().includes(query) ||
    s.description.toLowerCase().includes(query)
  )
})

// Fetch skills from backend
async function fetchSkills() {
  try {
    const response = await fetch('/api/skills')
    if (response.ok) {
      skills.value = await response.json()
    }
  } catch (e) {
    console.error('Failed to fetch skills:', e)
  }
}

// Auto resize textarea
watch(() => props.modelValue, () => {
  nextTick(() => {
    if (textarea.value) {
      textarea.value.style.height = 'auto'
      textarea.value.style.height = Math.min(textarea.value.scrollHeight, 160) + 'px'
    }
  })
})

onMounted(() => {
  fetchSkills()
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

function onKeydown(e: KeyboardEvent) {
  // Handle skill menu navigation
  if (showSkillMenu.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedSkillIndex.value = Math.min(selectedSkillIndex.value + 1, filteredSkills.value.length - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedSkillIndex.value = Math.max(selectedSkillIndex.value - 1, 0)
      return
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (filteredSkills.value.length > 0) {
        selectSkill(filteredSkills.value[selectedSkillIndex.value])
      }
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      showSkillMenu.value = false
      return
    }
  }

  // Handle Enter to send
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

function onBlur() {
  isFocused.value = false
  // Delay hiding menu to allow click
  if (blurTimeout) clearTimeout(blurTimeout)
  blurTimeout = setTimeout(() => {
    showSkillMenu.value = false
    blurTimeout = null
  }, 200)
}

// Watch for '/' input to show skill menu
watch(() => props.modelValue, (newVal) => {
  // Check if user just typed '/'
  if (newVal === '/' || (newVal.endsWith('/') && !newVal.endsWith('//'))) {
    showSkillMenu.value = true
    selectedSkillIndex.value = 0
    skillSearchQuery.value = ''
  } else if (showSkillMenu.value) {
    // Extract search query after '/'
    const slashIndex = newVal.lastIndexOf('/')
    if (slashIndex >= 0) {
      skillSearchQuery.value = newVal.substring(slashIndex + 1)
    } else {
      showSkillMenu.value = false
    }
  }
})

function selectSkill(skill: Skill) {
  selectedSkill.value = skill
  showSkillMenu.value = false
  // Clear the '/' and search query from input
  const slashIndex = props.modelValue.lastIndexOf('/')
  if (slashIndex >= 0) {
    emit('update:modelValue', props.modelValue.substring(0, slashIndex))
  }
  // Notify parent about skill selection
  emit('skill-selected', skill)
  // Focus back to textarea
  nextTick(() => {
    textarea.value?.focus()
  })
}

function removeSkill() {
  selectedSkill.value = null
  emit('skill-selected', null)
}

// Close skill menu on click outside
function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.skill-menu') && !target.closest('.input-container')) {
    showSkillMenu.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  if (blurTimeout) clearTimeout(blurTimeout)
  document.removeEventListener('click', handleClickOutside)
})
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

/* Selected Skill Badge */
.selected-skill-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: rgba(99,102,241,0.15);
  border-radius: 12px;
  flex-shrink: 0;
}

.skill-badge-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary-light);
}

.skill-badge-remove {
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  padding: 0;
  line-height: 1;
}

.skill-badge-remove:hover {
  color: var(--error);
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

/* Skill Menu */
.skill-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--bg-elevated);
  border: 1px solid var(--border-light);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-lg);
  z-index: 20;
  max-height: 320px;
  display: flex;
  flex-direction: column;
}

.skill-menu-header {
  padding: 12px 16px 8px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.skill-menu-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.skill-menu-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.skill-menu-list {
  overflow-y: auto;
  padding: 6px;
  flex: 1;
}

.skill-menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 10px;
  transition: all var(--duration-fast) var(--ease-out);
  text-align: left;
}

.skill-menu-item:hover,
.skill-menu-item.active {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.skill-item-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(99,102,241,0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.skill-item-info {
  flex: 1;
  min-width: 0;
}

.skill-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.skill-item-desc {
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-menu-empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
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
