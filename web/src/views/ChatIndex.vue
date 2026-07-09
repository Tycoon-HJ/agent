<template>
  <!--
    ChatIndex 主页面
    - 左侧：会话列表（新增、切换、删除会话）
    - 右侧：聊天主区域（消息展示、输入框）
    - 支持 SSE 流式通信
    - 支持图片上传（图生图）
  -->
  <div class="chat-container">
    <!-- ==================== 左侧会话列表 ==================== -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <h2>会话列表</h2>
        <button class="btn-new-chat" @click="createNewSession" title="新建会话">
          + 新建
        </button>
      </div>

      <div class="session-list">
        <div
            v-for="session in sessions"
            :key="session.sessionId"
            class="session-item"
            :class="{ active: session.sessionId === currentSessionId }"
            @click="switchSession(session.sessionId)"
        >
          <div class="session-title">{{ session.title }}</div>
          <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
          <button
              class="btn-delete"
              @click.stop="deleteSession(session.sessionId)"
              title="删除会话"
          >
            ×
          </button>
        </div>

        <!-- 空状态 -->
        <div v-if="sessions.length === 0" class="empty-sessions">
          暂无会话，点击上方新建
        </div>
      </div>
    </aside>

    <!-- ==================== 右侧聊天区域 ==================== -->
    <main class="chat-main">
      <!-- 聊天头部 -->
      <header class="chat-header">
        <h3>{{ currentSession?.title || '新建会话' }}</h3>
        <span class="session-id">ID: {{ currentSessionId }}</span>
      </header>

      <!-- 消息列表 -->
      <div ref="messageListRef" class="message-list">
        <!-- 空状态提示 -->
        <div v-if="currentMessages.length === 0" class="empty-chat">
          <div class="empty-icon">💬</div>
          <p>开始你的第一次对话吧！</p>
          <p class="empty-hint">支持上传图片进行图生图创作</p>
        </div>

        <!-- 消息组件 -->
        <ChatMessage
            v-for="msg in currentMessages"
            :key="msg.id"
            :message="msg"
            :is-streaming="isStreaming && msg.id === streamingMessageId"
        />
      </div>

      <!-- ==================== 底部输入区域 ==================== -->
      <div class="input-area">
        <!-- 图片预览区域 -->
        <div v-if="uploadedImages.length > 0" class="uploaded-images">
          <div
              v-for="(img, index) in uploadedImages"
              :key="index"
              class="uploaded-image-item"
          >
            <img :src="img.preview" alt="待发送图片"/>
            <button class="btn-remove-image" @click="removeUploadedImage(index)" title="移除图片">
              ×
            </button>
          </div>
          <div class="image-count">{{ uploadedImages.length }} 张图片</div>
        </div>

        <div class="input-wrapper">
          <!-- 图片上传按钮 -->
          <button
              class="btn-upload"
              @click="triggerFileInput"
              :disabled="isStreaming"
              title="上传图片"
          >
            🖼️
          </button>
          <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              multiple
              style="display: none"
              @change="handleFileUpload"
          />

          <textarea
              ref="inputRef"
              v-model="inputText"
              :placeholder="uploadedImages.length > 0 ? '描述图片编辑要求... (如：将图片改为赛博朋克风格)' : '输入消息... (Enter 发送，Shift+Enter 换行，可粘贴图片)'"
              :disabled="isStreaming"
              @keydown="handleKeydown"
              @paste="handlePaste"
              rows="1"
          ></textarea>
          <div class="btn-group">
            <!-- 停止生成按钮 -->
            <button
                v-if="isStreaming"
                class="btn-stop"
                @click="stopGeneration"
            >
              停止生成
            </button>
            <!-- 发送按钮 -->
            <button
                v-else
                class="btn-send"
                :disabled="!canSend"
                @click="sendMessage"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import type {ChatMessage as ChatMessageType, ChatSession} from '../types/chat'
import ChatMessage from '../components/ChatMessage.vue'

// ==================== 状态定义 ====================

/** 用户ID（与后端保持一致） */
const userId = 'default-user'

/** WebSocket 实例 */
let ws: WebSocket | null = null

/** WebSocket 重连定时器 */
let wsReconnectTimer: ReturnType<typeof setTimeout> | null = null

/** 所有会话列表 */
const sessions = ref<ChatSession[]>([])

/** 当前会话ID */
const currentSessionId = ref<string>('')

/** 输入框文本 */
const inputText = ref('')

/** 是否正在流式输出 */
const isStreaming = ref(false)

/** 当前正在流式输出的消息ID */
const streamingMessageId = ref<string>('')

/** AbortController 实例引用，用于取消 fetch 请求 */
let abortControllerRef: AbortController | null = null

/** 消息列表 DOM 引用 */
const messageListRef = ref<HTMLElement | null>(null)

/** 输入框 DOM 引用 */
const inputRef = ref<HTMLTextAreaElement | null>(null)

/** 文件输入框 DOM 引用 */
const fileInputRef = ref<HTMLInputElement | null>(null)

/** 已上传的图片列表 */
interface UploadedImage {
  file: File | null  // 文件对象（粘贴的图片可能没有 file）
  preview: string  // base64 data URL
  base64: string   // 纯 base64 数据（不包含前缀）
  name: string     // 图片名称
}

const uploadedImages = ref<UploadedImage[]>([])

// ==================== 计算属性 ====================

/** 当前会话 */
const currentSession = computed(() =>
    sessions.value.find(s => s.sessionId === currentSessionId.value)
)

/** 当前会话的消息列表 */
const currentMessages = computed(() =>
    currentSession.value?.messages || []
)

/** 是否可以发送消息 */
const canSend = computed(() => {
  return (inputText.value.trim() || uploadedImages.value.length > 0) && !isStreaming.value
})

// ==================== 会话管理 ====================

/**
 * 生成唯一 ID
 */
function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

/**
 * 创建新会话
 */
function createNewSession(): void {
  const sessionId = generateId()
  const newSession: ChatSession = {
    sessionId,
    title: '新会话',
    messages: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = sessionId
  saveSessions()
}

/**
 * 切换会话
 */
function switchSession(sessionId: string): void {
  if (isStreaming.value) {
    stopGeneration()
  }
  // 清空已上传的图片
  uploadedImages.value = []
  currentSessionId.value = sessionId
}

/**
 * 删除会话
 */
function deleteSession(sessionId: string): void {
  const index = sessions.value.findIndex(s => s.sessionId === sessionId)
  if (index === -1) return

  // 如果删除的是当前会话，切换到其他会话或创建新会话
  if (sessionId === currentSessionId.value) {
    if (sessions.value.length > 1) {
      const nextIndex = index === 0 ? 1 : index - 1
      currentSessionId.value = sessions.value[nextIndex].sessionId
    } else {
      createNewSession()
      return
    }
  }

  sessions.value.splice(index, 1)
  saveSessions()
}

// ==================== 图片上传 ====================

/**
 * 触发文件选择
 */
function triggerFileInput(): void {
  fileInputRef.value?.click()
}

/**
 * 处理文件上传
 */
function handleFileUpload(event: Event): void {
  const target = event.target as HTMLInputElement
  const files = target.files

  if (!files || files.length === 0) return

  // 限制最多上传 5 张图片
  const maxImages = 5
  const remainingSlots = maxImages - uploadedImages.value.length

  if (remainingSlots <= 0) {
    alert(`最多只能上传 ${maxImages} 张图片`)
    return
  }

  const filesToProcess = Array.from(files).slice(0, remainingSlots)

  filesToProcess.forEach(file => {
    addImageFromFile(file)
  })

  // 清空 input 以允许重复上传同一文件
  target.value = ''
}

/**
 * 从文件添加图片
 */
function addImageFromFile(file: File): void {
  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    alert(`"${file.name}" 不是图片文件`)
    return
  }

  // 验证文件大小（限制 10MB）
  if (file.size > 10 * 1024 * 1024) {
    alert(`"${file.name}" 超过 10MB 限制`)
    return
  }

  // 读取文件并生成预览
  const reader = new FileReader()
  reader.onload = (e) => {
    const dataUrl = e.target?.result as string
    const base64 = dataUrl.split(',')[1] || ''
    uploadedImages.value.push({
      file,
      preview: dataUrl,
      base64,
      name: file.name,
    })
  }
  reader.readAsDataURL(file)
}

/**
 * 处理粘贴事件
 * 支持直接粘贴剪贴板中的图片
 */
function handlePaste(event: ClipboardEvent): void {
  const clipboardData = event.clipboardData
  if (!clipboardData) return

  const items = clipboardData.items
  if (!items) return

  // 遍历剪贴板中的所有项目
  for (let i = 0; i < items.length; i++) {
    const item = items[i]

    // 检查是否为图片类型
    if (item.type.startsWith('image/')) {
      event.preventDefault() // 阻止默认粘贴行为

      const file = item.getAsFile()
      if (!file) continue

      // 限制最多上传 5 张图片
      const maxImages = 5
      if (uploadedImages.value.length >= maxImages) {
        alert(`最多只能上传 ${maxImages} 张图片`)
        return
      }

      // 读取粘贴的图片
      const reader = new FileReader()
      reader.onload = (e) => {
        const dataUrl = e.target?.result as string
        const base64 = dataUrl.split(',')[1] || ''

        // 生成一个有意义的名称
        const timestamp = new Date().getTime()
        const ext = item.type.split('/')[1] || 'png'
        const name = `pasted-${timestamp}.${ext}`

        uploadedImages.value.push({
          file: file,
          preview: dataUrl,
          base64,
          name,
        })
      }
      reader.readAsDataURL(file)

      break // 只处理第一个图片
    }
  }
}

/**
 * 移除已上传的图片
 */
function removeUploadedImage(index: number): void {
  uploadedImages.value.splice(index, 1)
}

/**
 * 将图片文件转换为 Base64 Data URI
 */
async function imageToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// ==================== 消息发送与 SSE ====================

/**
 * 发送消息
 */
async function sendMessage(): Promise<void> {
  const text = inputText.value.trim()
  const hasImages = uploadedImages.value.length > 0

  if ((!text && !hasImages) || isStreaming.value) return

  // 确保有当前会话
  if (!currentSession.value) {
    createNewSession()
  }

  // 准备图片数据（直接使用已有的 base64 数据）
  const imageUrls: string[] = []
  if (hasImages) {
    for (const img of uploadedImages.value) {
      // 使用完整的 data URL 格式
      imageUrls.push(img.preview)
    }
  }

  // 添加用户消息
  const userMessage: ChatMessageType = {
    id: generateId(),
    role: 'user',
    content: text || (hasImages ? '[发送了图片]' : ''),
    timestamp: Date.now(),
    images: imageUrls.length > 0 ? imageUrls : undefined,
  }
  currentSession.value!.messages.push(userMessage)
  currentSession.value!.updatedAt = Date.now()

  // 如果是第一条消息，更新会话标题
  if (currentSession.value!.messages.length === 1) {
    const title = text || '图片对话'
    currentSession.value!.title = title.slice(0, 20) + (title.length > 20 ? '...' : '')
  }

  // 清空输入框和已上传图片
  inputText.value = ''
  uploadedImages.value = []

  // 保存并滚动
  saveSessions()
  scrollToBottom()

  // 调用 SSE 流式接口（POST，图片放 body 里）
  startSSE(text, imageUrls)
}

/**
 * 启动 SSE 流式连接（POST，支持图片）
 *
 * EventSource 只支持 GET，图片 base64 数据放 query string 会超长导致 ERR_FAILED。
 * 改用 fetch POST + ReadableStream 手动解析 SSE。
 *
 * 接口：POST /api/ask/stream?sessionId=xxx
 * Body：{ "text": "...", "images": ["data:image/png;base64,..."] }
 */
function startSSE(text: string, imageUrls: string[] = []): void {
  const sessionId = currentSessionId.value

  // 创建 AI 消息占位
  const aiMessage: ChatMessageType = {
    id: generateId(),
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
  }
  currentSession.value!.messages.push(aiMessage)
  streamingMessageId.value = aiMessage.id
  isStreaming.value = true

  // 用于拼接完整文本的变量
  let fullText = ''

  // 构建请求体（字段名必须和后端 AgentRequest 一致：input, images）
  const body = JSON.stringify({
    input: text,
    images: imageUrls,
  })

  // 使用 fetch POST 发送 SSE 请求
  const abortController = new AbortController()

  fetch(`/api/ask/stream?sessionId=${encodeURIComponent(sessionId)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body: body,
    signal: abortController.signal,
  })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`)
        }

        const reader = response.body?.getReader()
        if (!reader) throw new Error('无法读取响应流')

        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        while (true) {
          const {done, value} = await reader.read()
          if (done) break

          buffer += decoder.decode(value, {stream: true})

          // 按行分割，处理完整的 SSE 行
          const lines = buffer.split('\n')
          buffer = lines.pop() || '' // 最后一个可能不完整，保留

          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed) continue

            if (trimmed.startsWith('data:')) {
              const data = trimmed.substring(5).trim()

              // 结束标记
              if (data === '[DONE]') {
                finishStreaming()
                return
              }

              if (data) {
                try {
                  const parsed = JSON.parse(data)
                  // 图片消息
                  if (parsed.type === 'image' && parsed.url) {
                    if (!aiMessage.images) aiMessage.images = []
                    aiMessage.images.push(parsed.url)
                    scrollToBottom()
                    continue
                  }
                  // 文本消息（后端 JSON 编码的 markdown 内容）
                  if (parsed.text !== undefined) {
                    fullText += parsed.text
                    aiMessage.content = fullText
                    currentSession.value!.updatedAt = Date.now()
                    scrollToBottom()
                    continue
                  }
                } catch {
                  // 非 JSON，作为普通文本（兼容旧格式）
                }

                fullText += data
                aiMessage.content = fullText
                currentSession.value!.updatedAt = Date.now()
                scrollToBottom()
              }
            }
          }
        }

        // 流正常结束
        finishStreaming()
      })
      .catch((err) => {
        if (err.name === 'AbortError') return // 用户主动取消
        console.error('SSE 请求错误:', err)
        finishStreaming()
      })

  // 保存 abortController 以便停止生成时取消请求
  abortControllerRef = abortController
}

/**
 * 停止生成
 */
function stopGeneration(): void {
  if (abortControllerRef) {
    abortControllerRef.abort()
    abortControllerRef = null
  }
  finishStreaming()
}

/**
 * 完成流式输出
 */
function finishStreaming(): void {
  isStreaming.value = false
  streamingMessageId.value = ''
  abortControllerRef = null
  saveSessions()
}

// ==================== 辅助功能 ====================

/**
 * 处理键盘事件
 * Enter 发送，Shift+Enter 换行
 */
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

/**
 * 滚动到消息列表底部
 */
function scrollToBottom(): void {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

/**
 * 格式化时间戳
 */
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`

  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ==================== 持久化 ====================

/** LocalStorage 键名 */
const STORAGE_KEY = 'ai-chat-sessions'

/**
 * 保存会话到 LocalStorage
 */
function saveSessions(): void {
  try {
    // 注意：图片数据（base64）可能很大，保存时需要考虑存储限制
    // 这里只保存消息内容，不保存图片的 base64 数据
    const sessionsToSave = sessions.value.map(session => ({
      ...session,
      messages: session.messages.map(msg => ({
        ...msg,
        // 如果是用户消息且有图片，只保留图片标记，不保存 base64
        images: msg.images?.map(img =>
            img.startsWith('data:') ? '[图片]' : img
        ),
      })),
    }))
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessionsToSave))
    localStorage.setItem('ai-chat-current', currentSessionId.value)
  } catch (e) {
    console.error('保存会话失败:', e)
  }
}

/**
 * 从 LocalStorage 加载会话
 */
function loadSessions(): void {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      sessions.value = JSON.parse(saved)
    }
    const savedCurrent = localStorage.getItem('ai-chat-current')
    if (savedCurrent && sessions.value.some(s => s.sessionId === savedCurrent)) {
      currentSessionId.value = savedCurrent
    }
  } catch (e) {
    console.error('加载会话失败:', e)
  }
}

// ==================== 自动调整输入框高度 ====================

/**
 * 自动调整输入框高度
 */
function autoResizeTextarea(): void {
  nextTick(() => {
    const textarea = inputRef.value
    if (textarea) {
      textarea.style.height = 'auto'
      textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
    }
  })
}

// 监听输入框内容变化
watch(inputText, autoResizeTextarea)

// ==================== WebSocket 通知 ====================

/**
 * 建立 WebSocket 连接，用于接收定时任务执行结果
 */
function connectWebSocket(): void {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return
  }

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/notifications?userId=${userId}`

  console.log('[WebSocket] 正在连接:', wsUrl)
  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    console.log('[WebSocket] 连接成功')
    // 清除重连定时器
    if (wsReconnectTimer) {
      clearTimeout(wsReconnectTimer)
      wsReconnectTimer = null
    }
    // 启动心跳
    startHeartbeat()
  }

  ws.onmessage = (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      console.log('[WebSocket] 收到消息:', data)

      if (data.type === 'scheduled_task_result') {
        handleTaskResult(data.taskId, data.content)
      }
    } catch (e) {
      console.warn('[WebSocket] 解析消息失败:', e)
    }
  }

  ws.onclose = () => {
    console.log('[WebSocket] 连接关闭，3秒后重连...')
    scheduleReconnect()
  }

  ws.onerror = (err) => {
    console.error('[WebSocket] 连接错误:', err)
  }
}

/** 心跳定时器 */
let heartbeatTimer: ReturnType<typeof setInterval> | null = null

/**
 * 启动心跳保活（每30秒发送一次 ping）
 */
function startHeartbeat(): void {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({type: 'ping'}))
    }
  }, 30000)
}

/**
 * 停止心跳
 */
function stopHeartbeat(): void {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/**
 * 安排重连
 */
function scheduleReconnect(): void {
  if (wsReconnectTimer) return
  wsReconnectTimer = setTimeout(() => {
    wsReconnectTimer = null
    connectWebSocket()
  }, 3000)
}

/**
 * 处理定时任务执行结果，将结果作为 AI 消息添加到当前会话
 */
function handleTaskResult(taskId: string, content: string): void {
  // 找到当前会话，如果没有则使用第一个会话
  const session = currentSession.value || sessions.value[0]
  if (!session) {
    console.warn('[WebSocket] 没有可用会话，无法展示任务结果')
    return
  }

  const aiMessage: ChatMessageType = {
    id: generateId(),
    role: 'assistant',
    content: `⏰ **定时任务执行结果** (任务ID: ${taskId})\n\n${content}`,
    timestamp: Date.now(),
  }

  session.messages.push(aiMessage)
  session.updatedAt = Date.now()
  saveSessions()

  // 自动滚动到底部
  nextTick(() => {
    scrollToBottom()
  })

  console.log('[WebSocket] 任务结果已添加到会话:', taskId)
}

/**
 * 断开 WebSocket 连接
 */
function disconnectWebSocket(): void {
  stopHeartbeat()
  if (wsReconnectTimer) {
    clearTimeout(wsReconnectTimer)
    wsReconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  // 加载历史会话
  loadSessions()

  // 如果没有会话，创建一个新会话
  if (sessions.value.length === 0) {
    createNewSession()
  } else if (!currentSessionId.value) {
    currentSessionId.value = sessions.value[0].sessionId
  }

  // 聚焦输入框
  nextTick(() => {
    inputRef.value?.focus()
  })

  // 建立 WebSocket 通知连接
  connectWebSocket()
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
/* ==================== 整体布局 ==================== */
.chat-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ==================== 左侧边栏 ==================== */
.sidebar {
  width: 280px;
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h2 {
  font-size: 16px;
  font-weight: 600;
}

.btn-new-chat {
  padding: 6px 12px;
  background-color: var(--accent-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background-color 0.2s;
}

.btn-new-chat:hover {
  background-color: var(--accent-hover);
}

/* 会话列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  position: relative;
  margin-bottom: 4px;
}

.session-item:hover {
  background-color: var(--bg-tertiary);
}

.session-item.active {
  background-color: var(--accent-color);
  color: white;
}

.session-item.active .session-time {
  color: rgba(255, 255, 255, 0.7);
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 24px;
}

.session-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.btn-delete {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 18px;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.session-item:hover .btn-delete {
  opacity: 1;
}

.btn-delete:hover {
  background-color: var(--danger-color);
  color: white;
}

.session-item.active .btn-delete {
  color: rgba(255, 255, 255, 0.7);
}

.session-item.active .btn-delete:hover {
  background-color: var(--danger-color);
  color: white;
}

.empty-sessions {
  text-align: center;
  color: var(--text-tertiary);
  padding: 24px;
  font-size: 13px;
}

/* ==================== 右侧聊天区域 ==================== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.session-id {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-hint {
  font-size: 13px;
  margin-top: 8px;
  opacity: 0.7;
}

/* ==================== 底部输入区域 ==================== */
.input-area {
  padding: 16px 24px;
  border-top: 1px solid var(--border-color);
  background-color: var(--bg-primary);
}

/* 已上传图片预览 */
.uploaded-images {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  padding: 12px;
  background-color: var(--bg-secondary);
  border-radius: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.uploaded-image-item {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid var(--accent-color);
}

.uploaded-image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.btn-remove-image {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  border: none;
  background-color: var(--danger-color);
  color: white;
  font-size: 14px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
}

.btn-remove-image:hover {
  background-color: var(--danger-hover);
}

.image-count {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: 8px;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
}

/* 图片上传按钮 */
.btn-upload {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.btn-upload:hover:not(:disabled) {
  background-color: var(--bg-tertiary);
}

.btn-upload:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-wrapper textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-primary);
  outline: none;
  max-height: 150px;
}

.input-wrapper textarea::placeholder {
  color: var(--text-tertiary);
}

.input-wrapper textarea:disabled {
  opacity: 0.6;
}

.btn-group {
  display: flex;
  gap: 8px;
}

.btn-send,
.btn-stop {
  padding: 8px 20px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-send {
  background-color: var(--accent-color);
  color: white;
}

.btn-send:hover:not(:disabled) {
  background-color: var(--accent-hover);
}

.btn-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-stop {
  background-color: var(--danger-color);
  color: white;
}

.btn-stop:hover {
  background-color: var(--danger-hover);
}

/* ==================== 响应式适配 ==================== */
@media (max-width: 768px) {
  .sidebar {
    width: 240px;
  }
}

@media (max-width: 640px) {
  .sidebar {
    position: absolute;
    z-index: 100;
    height: 100%;
    transform: translateX(-100%);
    transition: transform 0.3s;
  }

  .sidebar.open {
    transform: translateX(0);
  }
}
</style>
