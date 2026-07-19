<template>
  <div class="app-shell" :class="{ 'sidebar-open': sidebarOpen }">
    <!-- 侧边栏 -->
    <div class="sidebar-overlay" v-if="sidebarOpen" @click="sidebarOpen = false"></div>
    <SessionSidebar
      :sessions="sessions"
      :current="currentSessionId"
      @create="createNewSession"
      @switch="switchSession"
      @delete="deleteSession"
    />

    <!-- 主聊天区 -->
    <main class="chat-main">
      <!-- 顶栏 -->
      <header class="chat-header">
        <div class="header-left">
          <button
            class="header-btn"
            @click="sidebarOpen = !sidebarOpen"
            :title="sidebarOpen ? '收起侧边栏' : '展开侧边栏'"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <rect x="3" y="3" width="18" height="18" rx="2"/>
              <line x1="9" y1="3" x2="9" y2="21"/>
            </svg>
          </button>
          <h2 class="header-title">{{ currentSession?.title || '新对话' }}</h2>
        </div>
        <div class="header-right">
          <button
            class="header-btn"
            @click="toggleTheme"
            :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          >
            <svg v-if="theme === 'dark'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <circle cx="12" cy="12" r="4"/>
              <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/>
            </svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
          </button>
        </div>
      </header>

      <!-- 消息区 / 欢迎页 -->
      <div ref="messageListRef" class="chat-body" @scroll.passive="onMessageListScroll">
        <!-- 欢迎页 -->
        <div v-if="currentMessages.length === 0" class="welcome">
          <div class="welcome-logo">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
          </div>
          <h1 class="welcome-title">{{ greeting }},有什么可以帮你的?</h1>
          <p class="welcome-subtitle">Agent Studio 智能助手,支持搜索、推理、文档分析与图片生成</p>

          <div class="welcome-cards stagger-children">
            <button
              class="welcome-card"
              v-for="cap in suggestions"
              :key="cap.label"
              @click="applySuggestion(cap.prompt)"
            >
              <div class="welcome-card-icon">{{ cap.icon }}</div>
              <div class="welcome-card-text">
                <div class="welcome-card-label">{{ cap.label }}</div>
                <div class="welcome-card-desc">{{ cap.desc }}</div>
              </div>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="messages-container">
          <div class="messages-inner">
            <DynamicScroller
              :items="currentMessages"
              key-field="id"
              class="virtual-scroller"
              :min-item-size="80"
            >
              <template #default="{ item }">
                <DynamicScrollerItem :item="item" :key="item.id">
                  <ChatMessage
                    :message="item"
                    :is-streaming="isStreaming && item.id === streamingMessageId"
                    @retry="handleRetry"
                    @like="handleMessageLike"
                    @dislike="handleMessageDislike"
                    @favorite="handleMessageFavorite"
                    @share="handleMessageShare"
                  />
                </DynamicScrollerItem>
              </template>
            </DynamicScroller>
          </div>
        </div>
      </div>

      <!-- 回到底部按钮 -->
      <Transition name="fade">
        <button
          v-if="showScrollBottom"
          class="scroll-bottom-btn"
          @click="scrollToBottom(true)"
          title="回到底部"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
      </Transition>

      <!-- 输入区 -->
      <div class="chat-footer">
        <div class="footer-inner">
          <UploadPreview
            v-if="uploadedImages.length > 0 || uploadedDocuments.length > 0"
            :images="uploadedImages"
            :docs="uploadedDocuments"
            @remove-image="removeUploadedImage"
            @remove-doc="removeUploadedDocument"
          />

          <ChatInput
            v-model="inputText"
            :placeholder="getPlaceholder()"
            :isStreaming="isStreaming"
            :canSend="canSend"
            @send="sendMessage"
            @stop="stopGeneration"
            @add-files="addFileFromExternal"
            @paste="handlePaste"
          />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useWebSocket } from '../composables/useWebSocket'
import { useTheme } from '../composables/useTheme'
import type { ChatMessage as ChatMessageType, ChatSession } from '../types/chat'
import ChatMessage from '../components/ChatMessage.vue'
import SessionSidebar from '../components/SessionSidebar.vue'
import ChatInput from '../components/ChatInput.vue'
import UploadPreview from '../components/UploadPreview.vue'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'

// ── 常量 ──
const userId = 'default-user'
const MAX_IMAGES = 5
const MAX_FILES = 3
const MAX_FILE_SIZE = 10 * 1024 * 1024
const STORAGE_KEY = 'ai-chat-sessions'
const SUPPORTED_FILE_EXTENSIONS = ['.csv', '.md', '.txt', '.xlsx', '.xls', '.pdf']

const suggestions = [
  { icon: '🔍', label: '联网搜索', desc: '查找最新的资讯与资料', prompt: '帮我搜索最近的 AI 行业新闻,并总结要点' },
  { icon: '💡', label: '深度思考', desc: '复杂问题逐步推理', prompt: '用第一性原理解释一下什么是量子计算' },
  { icon: '📄', label: '文档分析', desc: '上传文件,提炼重点', prompt: '帮我总结这份文档的核心内容' },
  { icon: '🎨', label: '图片生成', desc: '用文字描绘画面', prompt: '画一只在月球上喝咖啡的猫,赛博朋克风格' },
]

// ── 主题 ──
const { theme, toggleTheme } = useTheme()

// ── 状态 ──
const sidebarOpen = ref(typeof window !== 'undefined' ? window.innerWidth > 768 : true)
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref('')
const inputText = ref('')
const isStreaming = ref(false)
const streamingMessageId = ref('')
const messageListRef = ref<HTMLElement | null>(null)
const stickToBottom = ref(true)
const visibleCount = ref(200)
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

// ── 计算属性 ──
const currentSession = computed(() =>
  sessions.value.find(s => s.sessionId === currentSessionId.value)
)

const currentMessages = computed(() =>
  currentSession.value?.messages || []
)

const canSend = computed(() => {
  return (inputText.value.trim() || uploadedImages.value.length > 0 || uploadedDocuments.value.length > 0) && !isStreaming.value
})

const showScrollBottom = computed(() =>
  !stickToBottom.value && currentMessages.value.length > 0
)

/** 按时段的问候语 */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h >= 5 && h < 12) return '早上好'
  if (h >= 12 && h < 14) return '中午好'
  if (h >= 14 && h < 18) return '下午好'
  return '晚上好'
})

// ── 工具函数 ──
function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2)
}

function getPlaceholder(): string {
  if (uploadedImages.value.length > 0) return '描述一下想如何处理这张图片…'
  if (uploadedDocuments.value.length > 0) return '针对这个文件提个问题…'
  return '输入你的问题…'
}

/** 点击建议卡片:填入输入框并聚焦 */
function applySuggestion(prompt: string): void {
  inputText.value = prompt
  nextTick(() => {
    const textarea = document.querySelector('.input-bar textarea') as HTMLTextAreaElement | null
    textarea?.focus()
  })
}

// ── 会话管理 ──
function createNewSession(): void {
  const sessionId = generateId()
  const newSession: ChatSession = {
    sessionId,
    title: '新对话',
    messages: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = sessionId
  stickToBottom.value = true
  saveSessions()
}

function switchSession(sessionId: string): void {
  if (isStreaming.value) stopGeneration()
  uploadedImages.value = []
  uploadedDocuments.value = []
  currentSessionId.value = sessionId
  // 移动端切换会话后收起抽屉
  if (window.innerWidth <= 768) sidebarOpen.value = false
  visibleCount.value = 200
  stickToBottom.value = true
  scrollToBottom(true)
}

function deleteSession(sessionId: string): void {
  const index = sessions.value.findIndex(s => s.sessionId === sessionId)
  if (index === -1) return
  if (sessionId === currentSessionId.value) {
    if (sessions.value.length > 1) {
      currentSessionId.value = sessions.value[index === 0 ? 1 : index - 1].sessionId
    } else {
      createNewSession()
      return
    }
  }
  sessions.value.splice(index, 1)
  saveSessions()
}

// ── 图片上传 ──
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

// ── 文档上传 ──
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
    console.error('[FileUpload] 读取失败:', e)
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

// ── 粘贴处理 ──
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

// ── 消息发送 & SSE ──
async function sendMessage(): Promise<void> {
  const text = inputText.value.trim()
  const hasImages = uploadedImages.value.length > 0
  const hasFiles = uploadedDocuments.value.length > 0
  if ((!text && !hasImages && !hasFiles) || isStreaming.value) return

  if (!currentSession.value) createNewSession()

  const imageUrls: string[] = uploadedImages.value.map(img => img.preview)
  const fileDataList = uploadedDocuments.value.map(doc => ({
    name: doc.name, type: doc.type, content: doc.content,
  }))

  const userMessage: ChatMessageType = {
    id: generateId(),
    role: 'user',
    content: text || (hasImages ? '[发送了图片]' : (hasFiles ? '[发送了文件]' : '')),
    timestamp: Date.now(),
    images: imageUrls.length > 0 ? imageUrls : undefined,
    files: fileDataList.length > 0 ? fileDataList : undefined,
  }

  currentSession.value!.messages.push(userMessage)
  currentSession.value!.updatedAt = Date.now()

  if (currentSession.value!.messages.length === 1) {
    const title = text || (hasFiles ? '文件分析' : '图片对话')
    currentSession.value!.title = title.slice(0, 20) + (title.length > 20 ? '…' : '')
  }

  inputText.value = ''
  uploadedImages.value = []
  uploadedDocuments.value = []
  stickToBottom.value = true
  saveSessions()
  scrollToBottom(true)
  startSSE(text, imageUrls, fileDataList)
}

function startSSE(text: string, imageUrls: string[] = [], fileDataList: { name: string; type: string; content: string }[] = []): void {
  startSSEInternal(text, imageUrls, fileDataList)
}

function startSSEInternal(text: string, imageUrls: string[] = [], fileDataList: { name: string; type: string; content: string }[] = [], existingAiMessageId?: string): void {
  const sessionId = currentSessionId.value

  let aiMessage: ChatMessageType | undefined
  if (existingAiMessageId) {
    aiMessage = currentSession.value!.messages.find(m => m.id === existingAiMessageId)
    if (aiMessage) {
      aiMessage.content = ''
      aiMessage.images = []
    }
  }
  if (!aiMessage) {
    aiMessage = { id: generateId(), role: 'assistant', content: '', timestamp: Date.now() }
    currentSession.value!.messages.push(aiMessage)
  }

  streamingMessageId.value = aiMessage.id
  isStreaming.value = true
  let fullText = ''

  const body = JSON.stringify({ input: text, images: imageUrls, files: fileDataList })
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
      if (!reader) throw new Error('无法读取响应流')

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
              if (parsed.type === 'image' && parsed.url) {
                if (!aiMessage!.images) aiMessage!.images = []
                aiMessage!.images.push(parsed.url)
                scrollToBottom()
                continue
              }
              if (parsed.text !== undefined) {
                fullText += parsed.text
                aiMessage!.content = fullText
                currentSession.value!.updatedAt = Date.now()
                scrollToBottom()
                continue
              }
            } catch { /* 非 JSON 数据走纯文本兜底 */ }

            fullText += data
            aiMessage!.content = fullText
            currentSession.value!.updatedAt = Date.now()
            scrollToBottom()
          }
        }
      }
      finishStreaming()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      console.error('SSE 错误:', err)
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

// ── 滚动 ──
function scrollToBottom(force = false): void {
  if (!force && !stickToBottom.value) return
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function onMessageListScroll(e: Event) {
  const el = e.target as HTMLElement
  if (!el) return
  // 距底部 80px 以内视为贴底,流式输出时自动跟随;向上滚动则暂停跟随
  stickToBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  if (el.scrollTop < 120) {
    const total = currentMessages.value.length
    if (visibleCount.value < total) {
      visibleCount.value = Math.min(total, visibleCount.value + 200)
    }
  }
}

// ── 消息操作 ──
function handleRetry(messageId: string): void {
  const msgs = currentSession.value!.messages
  const idx = msgs.findIndex(m => m.id === messageId)
  if (idx === -1) return
  let userMsg = undefined
  for (let i = idx - 1; i >= 0; i--) {
    if (msgs[i].role === 'user') { userMsg = msgs[i]; break }
  }
  if (!userMsg) return
  stickToBottom.value = true
  startSSEInternal(userMsg.content || '', userMsg.images || [], userMsg.files as any || [], messageId)
}

function handleMessageLike(payload: { id: string; value: boolean }) {
  const m = currentSession.value!.messages.find(x => x.id === payload.id)
  if (m) { (m as any).liked = payload.value; saveSessions() }
}

function handleMessageDislike(payload: { id: string; value: boolean }) {
  const m = currentSession.value!.messages.find(x => x.id === payload.id)
  if (m) { (m as any).disliked = payload.value; saveSessions() }
}

function handleMessageFavorite(payload: { id: string; value: boolean }) {
  const m = currentSession.value!.messages.find(x => x.id === payload.id)
  if (m) { (m as any).favorite = payload.value; saveSessions() }
}

function handleMessageShare(messageId: string) {
  console.debug('分享消息', messageId)
}

// ── 持久化 ──
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
    console.error('保存失败:', e)
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
    console.error('加载失败:', e)
  }
}

// ── WebSocket ──
function handleTaskResult(taskId: string, content: string): void {
  const session = currentSession.value || sessions.value[0]
  if (!session) return

  const aiMessage: ChatMessageType = {
    id: generateId(),
    role: 'assistant',
    content: `⏰ **定时任务结果**(ID: ${taskId})\n\n${content}`,
    timestamp: Date.now(),
  }

  session.messages.push(aiMessage)
  session.updatedAt = Date.now()
  saveSessions()
  nextTick(() => scrollToBottom())
}

// ── 生命周期 ──
onMounted(() => {
  loadSessions()
  if (sessions.value.length === 0) createNewSession()
  else if (!currentSessionId.value) currentSessionId.value = sessions.value[0].sessionId

  // WebSocket 通知
  const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/notifications?userId=${userId}`
  const { connect, disconnect } = useWebSocket()
  wsDisconnect = disconnect
  connect(wsUrl, (event) => {
    try {
      const data = JSON.parse((event as MessageEvent).data)
      if (data.type === 'scheduled_task_result') handleTaskResult(data.taskId, data.content)
    } catch { /* 忽略无法解析的消息 */ }
  })
})

onUnmounted(() => {
  try { wsDisconnect && wsDisconnect() } catch {}
})

watch(currentSessionId, () => {
  scrollToBottom(true)
})
</script>

<style scoped>
/* ── 整体骨架 ───────────────────────────── */
.app-shell {
  position: relative;
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-base);
  transition: background-color var(--duration-normal) ease;
}

.sidebar-overlay {
  display: none;
}

/* ── 主聊天区 ───────────────────────────── */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

/* 顶栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast) var(--ease-out);
  flex-shrink: 0;
}

.header-btn:hover {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 消息滚动区 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  position: relative;
}

/* ── 欢迎页 ─────────────────────────────── */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 48px 32px;
  text-align: center;
  animation: fadeInUp 0.5s var(--ease-out) both;
}

.welcome-logo {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.3);
  margin-bottom: 24px;
  animation: float 4s ease-in-out infinite;
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.5px;
  line-height: 1.3;
  margin-bottom: 10px;
  color: var(--text-primary);
}

.welcome-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin-bottom: 40px;
}

.welcome-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 560px;
  width: 100%;
}

.welcome-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  cursor: pointer;
  text-align: left;
  font-family: var(--font-sans);
  transition: all var(--duration-normal) var(--ease-out);
}

.welcome-card:hover {
  border-color: var(--border-light);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.welcome-card-icon {
  font-size: 22px;
  flex-shrink: 0;
}

.welcome-card-text {
  min-width: 0;
}

.welcome-card-label {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.welcome-card-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── 消息列表 ───────────────────────────── */
.messages-container {
  padding: 24px 24px 32px;
}

.messages-inner {
  max-width: 768px;
  margin: 0 auto;
}

/* ── 回到底部按钮 ───────────────────────── */
.scroll-bottom-btn {
  position: absolute;
  bottom: 132px;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-light);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
  z-index: 20;
  transition: all var(--duration-fast) var(--ease-out);
}

.scroll-bottom-btn:hover {
  color: var(--text-primary);
  transform: translateX(-50%) translateY(-2px);
  box-shadow: var(--shadow-lg);
}

/* ── 输入区 ─────────────────────────────── */
.chat-footer {
  padding: 8px 24px 16px;
  flex-shrink: 0;
}

.footer-inner {
  max-width: 768px;
  margin: 0 auto;
}

/* ── 桌面端:侧边栏折叠 ──────────────────── */
@media (min-width: 769px) {
  .app-shell:not(.sidebar-open) :deep(.sidebar) {
    width: 0;
    border-right-color: transparent;
  }
}

/* ── 移动端:抽屉式侧边栏 ────────────────── */
@media (max-width: 768px) {
  .app-shell :deep(.sidebar) {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 150;
    width: 280px;
    transform: translateX(-105%);
    transition: transform var(--duration-slow) var(--ease-out);
    box-shadow: var(--shadow-lg);
  }

  .app-shell.sidebar-open :deep(.sidebar) {
    transform: translateX(0);
  }

  .sidebar-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: var(--overlay-bg);
    z-index: 140;
    animation: fadeIn var(--duration-normal) ease both;
  }

  .messages-container {
    padding: 16px 16px 24px;
  }

  .chat-footer {
    padding: 4px 12px 12px;
  }

  .welcome {
    padding: 32px 20px;
  }

  .welcome-cards {
    grid-template-columns: 1fr;
  }

  .welcome-title {
    font-size: 21px;
  }

  .welcome-subtitle {
    margin-bottom: 32px;
  }

  .scroll-bottom-btn {
    bottom: 118px;
  }
}

/* 回到底部按钮过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-fast) ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
