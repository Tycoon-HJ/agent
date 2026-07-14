<template>
  <div class="app-shell" :class="{ 'sidebar-open': sidebarOpen }">
    <!-- Background -->
    <div class="app-bg">
      <div class="app-bg-gradient"></div>
      <div class="app-bg-glow"></div>
    </div>

    <!-- Mobile sidebar toggle -->
    <button class="mobile-sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <line x1="3" y1="12" x2="21" y2="12"/>
        <line x1="3" y1="6" x2="21" y2="6"/>
        <line x1="3" y1="18" x2="21" y2="18"/>
      </svg>
    </button>

    <!-- Sidebar -->
    <div class="sidebar-overlay" v-if="sidebarOpen" @click="sidebarOpen = false"></div>
    <SessionSidebar
      :sessions="sessions"
      :current="currentSessionId"
      @create="createNewSession"
      @switch="switchSession"
      @delete="deleteSession"
    />

    <!-- Main Chat Area -->
    <main class="chat-main">
      <!-- Header -->
      <header class="chat-header">
        <div class="header-left">
          <h2 class="header-title">{{ currentSession?.title || 'New Chat' }}</h2>
        </div>
        <div class="header-right">
        </div>
      </header>

      <!-- Messages or Welcome -->
      <div ref="messageListRef" class="chat-body">
        <!-- Welcome Screen -->
        <div v-if="currentMessages.length === 0" class="welcome">
          <div class="welcome-logo">
            <div class="welcome-logo-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                <path d="M2 17l10 5 10-5"/>
                <path d="M2 12l10 5 10-5"/>
              </svg>
            </div>
          </div>
          <h1 class="welcome-title">Agent Studio</h1>
          <p class="welcome-subtitle">Build AI Agents Faster</p>

          <div class="welcome-cards stagger-children">
            <div class="welcome-card" v-for="cap in capabilities" :key="cap.label">
              <div class="welcome-card-icon">{{ cap.icon }}</div>
              <div class="welcome-card-label">{{ cap.label }}</div>
              <div class="welcome-card-desc">{{ cap.desc }}</div>
            </div>
          </div>
        </div>

        <!-- Message List -->
        <div v-else class="messages-container">
          <div class="messages-inner">
            <ChatMessage
              v-for="item in currentMessages"
              :key="item.id"
              :message="item"
              :is-streaming="isStreaming && item.id === streamingMessageId"
              @retry="handleRetry"
              @like="handleMessageLike"
              @dislike="handleMessageDislike"
              @favorite="handleMessageFavorite"
              @share="handleMessageShare"
              @confirm="handleConfirm"
            />
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="chat-footer">
        <!-- Upload Preview -->
        <UploadPreview
          v-if="uploadedImages.length > 0 || uploadedDocuments.length > 0"
          :images="uploadedImages"
          :docs="uploadedDocuments"
          @remove-image="removeUploadedImage"
          @remove-doc="removeUploadedDocument"
        />

        <!-- Input -->
        <ChatInput
          v-model="inputText"
          :placeholder="getPlaceholder()"
          :isStreaming="isStreaming"
          :canSend="canSend"
          @send="sendMessage"
          @stop="stopGeneration"
          @add-files="addFileFromExternal"
          @paste="handlePaste"
          @skill-selected="onSkillSelected"
        />
      </div>
    </main>

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useWebSocket } from '../composables/useWebSocket'
import type { ChatMessage as ChatMessageType, ChatSession } from '../types/chat'
import ChatMessage from '../components/ChatMessage.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import ChatInput from '../components/ChatInput.vue'
import UploadPreview from '../components/UploadPreview.vue'

// ── Constants ──
const userId = 'default-user'
const MAX_IMAGES = 5
const MAX_FILES = 3
const MAX_FILE_SIZE = 10 * 1024 * 1024
const STORAGE_KEY = 'ai-chat-sessions'
const SUPPORTED_FILE_EXTENSIONS = ['.csv', '.md', '.txt', '.xlsx', '.xls', '.pdf']

const capabilities = [
  { icon: '🔍', label: 'Search', desc: 'Real-time web search' },
  { icon: '🧠', label: 'Reasoning', desc: 'Advanced chain-of-thought' },
  { icon: '📚', label: 'Knowledge', desc: 'Document analysis' },
  { icon: '⚡', label: 'Tools', desc: 'Function calling' },
]

// ── State ──
const sidebarOpen = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref('')
const inputText = ref('')
const isStreaming = ref(false)
const streamingMessageId = ref('')
const messageListRef = ref<HTMLElement | null>(null)
let abortControllerRef: AbortController | null = null
let wsDisconnect: (() => void) | null = null

interface UploadedImage {
  file: File | null
  preview: string
  base64: string
  name: string
}

interface UploadedDocument {
  file: File
  name: string
  type: string
  content: string
  size: number
}

const uploadedImages = ref<UploadedImage[]>([])
const uploadedDocuments = ref<UploadedDocument[]>([])

interface Skill {
  name: string
  description: string
  requiredTools: string[]
}

const selectedSkill = ref<Skill | null>(null)

// ── Computed ──
const currentSession = computed(() =>
  sessions.value.find(s => s.sessionId === currentSessionId.value)
)

const currentMessages = computed(() =>
  currentSession.value?.messages || []
)

const canSend = computed(() => {
  return (inputText.value.trim() || uploadedImages.value.length > 0 || uploadedDocuments.value.length > 0) && !isStreaming.value
})

// ── Helpers ──
function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2)
}

function getPlaceholder(): string {
  if (uploadedImages.value.length > 0) return 'Describe how to edit the image...'
  if (uploadedDocuments.value.length > 0) return 'Ask a question about the file...'
  return 'Ask me anything...'
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function onSkillSelected(skill: Skill | null) {
  selectedSkill.value = skill
}

// ── Session Management ──
function createNewSession(): void {
  const sessionId = generateId()
  const newSession: ChatSession = {
    sessionId,
    title: 'New Chat',
    messages: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = sessionId
  saveSessions()
}

function switchSession(sessionId: string): void {
  if (isStreaming.value) stopGeneration()
  uploadedImages.value = []
  uploadedDocuments.value = []
  currentSessionId.value = sessionId
  sidebarOpen.value = false
  saveSessions()
}

function deleteSession(sessionId: string): void {
  const index = sessions.value.findIndex(s => s.sessionId === sessionId)
  if (index === -1) return

  // 先从数组中移除
  sessions.value.splice(index, 1)

  // 如果删除的是当前会话，需要切换
  if (sessionId === currentSessionId.value) {
    if (sessions.value.length > 0) {
      // 切换到相邻会话（优先上方）
      const newIndex = Math.min(index, sessions.value.length - 1)
      currentSessionId.value = sessions.value[newIndex].sessionId
    } else {
      // 所有会话都删光了，创建一个新的
      createNewSession()
      return
    }
  }

  saveSessions()
}

// ── Image Upload ──
function addImageFromFile(file: File): void {
  if (!file.type.startsWith('image/')) return
  if (file.size > MAX_FILE_SIZE) return

  const reader = new FileReader()
  reader.onload = (e) => {
    const dataUrl = e.target?.result as string
    const base64 = dataUrl.split(',')[1] || ''
    uploadedImages.value.push({ file, preview: dataUrl, base64, name: file.name })
  }
  reader.readAsDataURL(file)
}

function removeUploadedImage(index: number): void {
  uploadedImages.value.splice(index, 1)
}

// ── Document Upload ──
async function addDocumentFromFile(file: File): Promise<void> {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!SUPPORTED_FILE_EXTENSIONS.includes(ext)) return
  if (file.size > MAX_FILE_SIZE) return

  try {
    let content: string
    let mimeType = file.type || 'text/plain'

    if (ext === '.xlsx' || ext === '.xls') {
      content = await parseExcelFile(file)
    } else if (ext === '.pdf') {
      content = await readPdfAsBase64(file)
      mimeType = 'application/pdf'
    } else {
      content = await readTextFile(file)
    }

    uploadedDocuments.value.push({ file, name: file.name, type: mimeType, content, size: file.size })
  } catch (e) {
    console.error('[FileUpload] Read failed:', e)
  }
}

function readTextFile(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target?.result as string)
    reader.onerror = reject
    reader.readAsText(file)
  })
}

function readPdfAsBase64(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const dataUrl = e.target?.result as string
      resolve(dataUrl.split(',')[1] || '')
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function parseExcelFile(file: File): Promise<string> {
  const XLSX = await import('xlsx')
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target?.result as ArrayBuffer)
        const workbook = XLSX.read(data, { type: 'array' })
        let result = ''
        workbook.SheetNames.forEach((sheetName, index) => {
          const csv = XLSX.utils.sheet_to_csv(workbook.Sheets[sheetName])
          if (index > 0) result += '\n\n'
          result += `【Sheet: ${sheetName}】\n${csv}`
        })
        resolve(result)
      } catch (err) { reject(err) }
    }
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}

function removeUploadedDocument(index: number): void {
  uploadedDocuments.value.splice(index, 1)
}

function addFileFromExternal(files: File[] | File): void {
  const list = Array.isArray(files) ? files : [files]
  for (const f of list) {
    if (f.type.startsWith('image/')) {
      if (uploadedImages.value.length >= MAX_IMAGES) break
      addImageFromFile(f)
    } else {
      if (uploadedDocuments.value.length >= MAX_FILES) break
      addDocumentFromFile(f)
    }
  }
}

// ── Paste Handler ──
function handlePaste(event: ClipboardEvent): void {
  const clipboardData = event.clipboardData
  if (!clipboardData) return
  const items = clipboardData.items
  if (!items) return

  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.type.startsWith('image/')) {
      event.preventDefault()
      const file = item.getAsFile()
      if (!file) continue
      if (uploadedImages.value.length >= MAX_IMAGES) return
      const reader = new FileReader()
      reader.onload = (e) => {
        const dataUrl = e.target?.result as string
        const base64 = dataUrl.split(',')[1] || ''
        const timestamp = new Date().getTime()
        const ext = item.type.split('/')[1] || 'png'
        uploadedImages.value.push({ file, preview: dataUrl, base64, name: `pasted-${timestamp}.${ext}` })
      }
      reader.readAsDataURL(file)
      break
    }
  }
}

// ── Message Sending & SSE ──
async function sendMessage(): Promise<void> {
  const text = inputText.value.trim()
  const hasImages = uploadedImages.value.length > 0
  const hasFiles = uploadedDocuments.value.length > 0
  if ((!text && !hasImages && !hasFiles) || isStreaming.value) return

  if (!currentSession.value) createNewSession()
  const session = currentSession.value
  if (!session) return

  const imageUrls: string[] = uploadedImages.value.map(img => img.preview)
  const fileDataList = uploadedDocuments.value.map(doc => ({
    name: doc.name, type: doc.type, content: doc.content,
  }))

  const userMessage: ChatMessageType = {
    id: generateId(),
    role: 'user',
    content: text || (hasImages ? '[Sent image]' : (hasFiles ? '[Sent file]' : '')),
    timestamp: Date.now(),
    images: imageUrls.length > 0 ? imageUrls : undefined,
    files: fileDataList.length > 0 ? fileDataList : undefined,
  }

  session.messages.push(userMessage)
  session.updatedAt = Date.now()

  if (session.messages.length === 1) {
    const title = text || (hasFiles ? 'File Analysis' : 'Image Chat')
    session.title = title.slice(0, 20) + (title.length > 20 ? '...' : '')
  }

  const skillName = selectedSkill.value?.name
  inputText.value = ''
  uploadedImages.value = []
  uploadedDocuments.value = []
  selectedSkill.value = null
  saveSessions()
  scrollToBottom()
  startSSE(text, imageUrls, fileDataList, skillName)
}

function startSSE(text: string, imageUrls: string[] = [], fileDataList: { name: string; type: string; content: string }[] = [], skillName?: string): void {
  startSSEInternal(text, imageUrls, fileDataList, undefined, skillName)
}

function startSSEInternal(text: string, imageUrls: string[] = [], fileDataList: { name: string; type: string; content: string }[] = [], existingAiMessageId?: string, skillName?: string): void {
  const sessionId = currentSessionId.value

  const sseSession = currentSession.value
  if (!sseSession) return

  let aiMessage: ChatMessageType | undefined
  if (existingAiMessageId) {
    aiMessage = sseSession.messages.find(m => m.id === existingAiMessageId)
    if (aiMessage) {
      aiMessage.content = ''
      aiMessage.images = []
    }
  }
  if (!aiMessage) {
    aiMessage = { id: generateId(), role: 'assistant', content: '', timestamp: Date.now() }
    sseSession.messages.push(aiMessage)
  }

  streamingMessageId.value = aiMessage.id
  isStreaming.value = true
  let fullText = ''

  const body = JSON.stringify({ input: text, images: imageUrls, files: fileDataList, skill: skillName })
  const abortController = new AbortController()

  fetch(`/api/ask/stream?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
    body,
    signal: abortController.signal,
  })
    .then(async (response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      const reader = response.body?.getReader()
      if (!reader) throw new Error('Cannot read response stream')

      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || !trimmed.startsWith('data:')) continue

          const data = trimmed.substring(5).trim()
          if (data === '[DONE]') { finishStreaming(); return }

          if (data) {
            try {
              const parsed = JSON.parse(data)
              // Handle confirmation event
              if (parsed.type === 'confirmation' && parsed.confirmationId) {
                console.log('Received confirmation event:', parsed)
                // Update the message with confirmation fields
                aiMessage!.needsConfirmation = true
                aiMessage!.confirmationId = parsed.confirmationId
                aiMessage!.confirmationMessage = parsed.confirmationMessage
                aiMessage!.partialResult = parsed.partialResult
                aiMessage!.pendingAction = parsed.pendingAction
                aiMessage!.content = (parsed.partialResult || '') +
                  '\n\n---\n\n⚠️ **需要确认**\n\n' +
                  (parsed.confirmationMessage || '此操作需要您的确认')
                sseSession.updatedAt = Date.now()
                scrollToBottom()
                finishStreaming()
                return
              }
              if (parsed.type === 'image' && parsed.url) {
                if (!aiMessage!.images) aiMessage!.images = []
                aiMessage!.images.push(parsed.url)
                scrollToBottom()
                continue
              }
              if (parsed.text !== undefined) {
                fullText += parsed.text
                aiMessage!.content = fullText
                sseSession.updatedAt = Date.now()
                scrollToBottom()
                continue
              }
            } catch (e) { console.warn('SSE data is not valid JSON, treating as plain text:', data) }

            fullText += data
            aiMessage!.content = fullText
            sseSession.updatedAt = Date.now()
            scrollToBottom()
          }
        }
      }
      finishStreaming()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      console.error('SSE error:', err)
      finishStreaming()
    })

  abortControllerRef = abortController
}

function stopGeneration(): void {
  if (abortControllerRef) {
    abortControllerRef.abort()
    abortControllerRef = null
  }
  finishStreaming()
}

function finishStreaming(): void {
  isStreaming.value = false
  streamingMessageId.value = ''
  abortControllerRef = null
  saveSessions()
}

// ── Scroll ──
function scrollToBottom(): void {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// ── Message Actions ──
function handleRetry(messageId: string): void {
  const session = currentSession.value
  if (!session) return
  const msgs = session.messages
  const idx = msgs.findIndex(m => m.id === messageId)
  if (idx === -1) return
  let userMsg = undefined
  for (let i = idx - 1; i >= 0; i--) {
    if (msgs[i].role === 'user') { userMsg = msgs[i]; break }
  }
  if (!userMsg) return
  startSSEInternal(userMsg.content || '', userMsg.images || [], userMsg.files || [], messageId)
}

function handleMessageLike(payload: { id: string; value: boolean }) {
  const session = currentSession.value
  if (!session) return
  const m = session.messages.find(x => x.id === payload.id)
  if (m) { m.liked = payload.value; saveSessions() }
}

function handleMessageDislike(payload: { id: string; value: boolean }) {
  const session = currentSession.value
  if (!session) return
  const m = session.messages.find(x => x.id === payload.id)
  if (m) { m.disliked = payload.value; saveSessions() }
}

function handleMessageFavorite(payload: { id: string; value: boolean }) {
  const session = currentSession.value
  if (!session) return
  const m = session.messages.find(x => x.id === payload.id)
  if (m) { m.favorite = payload.value; saveSessions() }
}

function handleMessageShare(messageId: string) {
  console.debug('share message', messageId)
}

async function handleConfirm(confirmationId: string): Promise<void> {
  console.log('Confirming:', confirmationId)
  isStreaming.value = true

  try {
    const response = await fetch(`/api/confirm/${confirmationId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const result = await response.json().catch(() => null)
    if (!result || typeof result.answer !== 'string') {
      throw new Error('Invalid confirmation response')
    }
    console.log('Confirmation result:', result)

    // Find and update the message with the confirmation result
    const confirmSession = currentSession.value
    if (!confirmSession) return
    const msgs = confirmSession.messages
    const confirmMsg = msgs.find(m => m.confirmationId === confirmationId)
    if (confirmMsg) {
      // Update the message with the full result
      confirmMsg.content = result.answer || '操作已完成'
      confirmMsg.needsConfirmation = false
      confirmMsg.confirmationId = undefined
      confirmMsg.confirmationMessage = undefined
      confirmMsg.partialResult = undefined
      confirmMsg.pendingAction = undefined
      saveSessions()
    }
  } catch (e) {
    console.error('Confirmation failed:', e)
    // Show error message
    const errorMsg: ChatMessageType = {
      id: generateId(),
      role: 'assistant',
      content: '确认失败: ' + (e instanceof Error ? e.message : String(e)),
      timestamp: Date.now(),
    }
    currentSession.value!.messages.push(errorMsg)
    saveSessions()
  } finally {
    isStreaming.value = false
    scrollToBottom()
  }
}

// ── Persistence ──
function saveSessions(): void {
  try {
    const sessionsToSave = sessions.value.map(session => ({
      ...session,
      messages: session.messages.map(msg => ({
        ...msg,
        images: msg.images?.map(img => img.startsWith('data:') ? '[image]' : img),
      })),
    }))
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessionsToSave))
    localStorage.setItem('ai-chat-current', currentSessionId.value)
  } catch (e) {
    console.error('Save failed:', e)
  }
}

function loadSessions(): void {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) sessions.value = JSON.parse(saved)
    const savedCurrent = localStorage.getItem('ai-chat-current')
    if (savedCurrent && sessions.value.some(s => s.sessionId === savedCurrent)) {
      currentSessionId.value = savedCurrent
    }
  } catch (e) {
    console.error('Load failed:', e)
  }
}

// ── WebSocket ──
function handleTaskResult(taskId: string, content: string): void {
  const session = currentSession.value || sessions.value[0]
  if (!session) return

  const aiMessage: ChatMessageType = {
    id: generateId(),
    role: 'assistant',
    content: `⏰ **Scheduled Task Result** (ID: ${taskId})\n\n${content}`,
    timestamp: Date.now(),
  }

  session.messages.push(aiMessage)
  session.updatedAt = Date.now()
  saveSessions()
  nextTick(() => scrollToBottom())
}

// ── Lifecycle ──
onMounted(() => {
  loadSessions()
  if (sessions.value.length === 0) createNewSession()
  else if (!currentSessionId.value) currentSessionId.value = sessions.value[0].sessionId

  // WebSocket
  const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/notifications?userId=${userId}`
  const { connect, disconnect } = useWebSocket()
  wsDisconnect = disconnect
  connect(wsUrl, (event) => {
    try {
      const data = JSON.parse((event as MessageEvent).data)
      if (data.type === 'scheduled_task_result') handleTaskResult(data.taskId, data.content)
    } catch (e) { console.warn('WebSocket message parse error:', e) }
  })
})

onUnmounted(() => {
  try { wsDisconnect && wsDisconnect() } catch {}
})

// Note: textarea auto-resize is handled inside ChatInput.vue component
</script>

<style scoped>
/* ── Shell ────────────────────────────────── */
.app-shell {
  position: relative;
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* Background */
.app-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
}

.app-bg-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #0f172a 0%, #111827 100%);
}

.app-bg-glow {
  position: absolute;
  top: -200px;
  left: 50%;
  transform: translateX(-50%);
  width: 800px;
  height: 600px;
  background: radial-gradient(circle, rgba(99,102,241,0.12) 0%, transparent 60%);
  pointer-events: none;
}

/* Mobile toggle */
.mobile-sidebar-toggle {
  display: none;
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 200;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  color: var(--text-primary);
  border-radius: 12px;
  cursor: pointer;
  align-items: center;
  justify-content: center;
}

.sidebar-overlay {
  display: none;
}

/* ── Chat Main ────────────────────────────── */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

/* Header */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  background: rgba(15,23,42,0.5);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.header-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

.header-id {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--text-muted);
  background: var(--bg-surface);
  padding: 3px 8px;
  border-radius: 6px;
}

/* Body */
.chat-body {
  flex: 1;
  overflow-y: auto;
  position: relative;
}

/* ── Welcome Screen ───────────────────────── */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 60px 40px;
  text-align: center;
  animation: fadeInUp 0.6s var(--ease-out) both;
}

.welcome-logo {
  margin-bottom: 28px;
}

.welcome-logo-icon {
  width: 100px;
  height: 100px;
  border-radius: 28px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 20px 60px rgba(99,102,241,0.3);
  animation: float 4s ease-in-out infinite;
}

.welcome-title {
  font-size: 48px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -1.5px;
  line-height: 1.1;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #F8FAFC 0%, #94A3B8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 18px;
  color: var(--text-secondary);
  margin-bottom: 48px;
  font-weight: 500;
}

.welcome-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-width: 700px;
  width: 100%;
}

.welcome-card {
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 24px 16px;
  text-align: center;
  cursor: default;
  transition: all var(--duration-normal) var(--ease-out);
}

.welcome-card:hover {
  background: var(--bg-glass-strong);
  border-color: var(--border-light);
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(0,0,0,0.3);
}

.welcome-card-icon {
  font-size: 28px;
  margin-bottom: 12px;
}

.welcome-card-label {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.welcome-card-desc {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* ── Messages Container ───────────────────── */
.messages-container {
  padding: 40px;
}

.messages-inner {
  max-width: 900px;
  margin: 0 auto;
}

/* ── Footer ───────────────────────────────── */
.chat-footer {
  padding: 16px 32px 24px;
  flex-shrink: 0;
  background: linear-gradient(180deg, transparent 0%, rgba(15,23,42,0.8) 100%);
}

/* ── Responsive ───────────────────────────── */
@media (max-width: 1200px) {
  .app-shell :deep(.agent-panel) {
    display: none;
  }
}

@media (max-width: 768px) {
  .mobile-sidebar-toggle {
    display: flex;
  }

  .app-shell :deep(.sidebar) {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 150;
    transform: translateX(-100%);
    transition: transform 0.3s var(--ease-out);
  }

  .app-shell.sidebar-open :deep(.sidebar) {
    transform: translateX(0);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.5);
    z-index: 140;
  }

  .chat-header {
    padding: 16px 20px;
    padding-left: 56px;
  }

  .messages-container {
    padding: 20px;
  }

  .chat-footer {
    padding: 12px 16px 20px;
  }

  .welcome-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .welcome-title {
    font-size: 36px;
  }
}
</style>
