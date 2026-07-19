<template>
  <aside class="sidebar">
    <div class="sidebar-inner">
      <!-- Logo -->
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <span class="logo-name">Agent Studio</span>
      </div>

      <!-- 新建会话 -->
      <button class="btn-new-chat" @click="$emit('create')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        <span>新对话</span>
      </button>

      <!-- 会话列表(按时间分组) -->
      <div class="session-list">
        <template v-for="group in groups" :key="group.label">
          <div v-if="group.items.length > 0" class="session-group">
            <div class="session-group-label">{{ group.label }}</div>
            <div
              v-for="s in group.items"
              :key="s.sessionId"
              class="session-item"
              :class="{ active: s.sessionId === current }"
              @click="$emit('switch', s.sessionId)"
            >
              <span class="session-item-title">{{ s.title }}</span>
              <button
                class="session-item-delete"
                @click.stop="$emit('delete', s.sessionId)"
                title="删除会话"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M3 6h18"/>
                  <path d="M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                  <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/>
                </svg>
              </button>
            </div>
          </div>
        </template>

        <div v-if="sessions.length === 0" class="session-empty">
          <div class="session-empty-icon">💬</div>
          <div class="session-empty-text">暂无会话</div>
        </div>
      </div>

      <!-- 底部用户信息 -->
      <div class="sidebar-footer">
        <div class="user-avatar">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="user-info">
          <div class="user-name">本地用户</div>
          <div class="user-plan">免费版</div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatSession } from '../types/chat'

const props = defineProps<{ sessions: ChatSession[]; current?: string }>()

defineEmits(['create', 'switch', 'delete'])

interface SessionGroup {
  label: string
  items: ChatSession[]
}

/** 按更新时间分组:今天 / 昨天 / 7 天内 / 更早 */
const groups = computed<SessionGroup[]>(() => {
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 86400000
  const startOfWeek = startOfToday - 6 * 86400000

  const result: SessionGroup[] = [
    { label: '今天', items: [] },
    { label: '昨天', items: [] },
    { label: '7 天内', items: [] },
    { label: '更早', items: [] },
  ]

  for (const s of props.sessions) {
    if (s.updatedAt >= startOfToday) result[0].items.push(s)
    else if (s.updatedAt >= startOfYesterday) result[1].items.push(s)
    else if (s.updatedAt >= startOfWeek) result[2].items.push(s)
    else result[3].items.push(s)
  }
  return result
})
</script>

<style scoped>
/* 外层宽度由 ChatIndex 控制折叠动画,内层固定宽度避免内容挤压 */
.sidebar {
  width: 272px;
  height: 100%;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border);
  transition: width var(--duration-slow) var(--ease-out),
              border-color var(--duration-normal) ease;
}

.sidebar-inner {
  width: 272px;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 12px;
}

/* ── Logo ───────────────────────────────── */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  flex-shrink: 0;
}

.logo-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

/* ── 新建会话按钮 ───────────────────────── */
.btn-new-chat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  box-shadow: var(--shadow-sm);
}

.btn-new-chat:hover {
  border-color: var(--border-light);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.btn-new-chat:active {
  transform: translateY(0);
}

.btn-new-chat svg {
  color: var(--text-secondary);
}

/* ── 会话列表 ───────────────────────────── */
.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  margin: 0 -4px;
  padding: 0 4px;
}

.session-group {
  margin-bottom: 8px;
}

.session-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  padding: 8px 10px 4px;
  user-select: none;
}

/* ── 会话项 ─────────────────────────────── */
.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
  position: relative;
}

.session-item:hover {
  background: var(--bg-surface-hover);
}

.session-item.active {
  background: var(--bg-surface-active);
}

.session-item.active .session-item-title {
  font-weight: 600;
}

.session-item-title {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item-delete {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  border-radius: 7px;
  cursor: pointer;
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out);
  flex-shrink: 0;
}

.session-item:hover .session-item-delete {
  opacity: 1;
}

.session-item-delete:hover {
  background: rgba(239, 68, 68, 0.12);
  color: var(--error);
}

/* ── 空状态 ─────────────────────────────── */
.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  gap: 10px;
}

.session-empty-icon {
  font-size: 28px;
  opacity: 0.5;
}

.session-empty-text {
  font-size: 13px;
  color: var(--text-tertiary);
}

/* ── 底部用户区 ─────────────────────────── */
.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-top: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-out);
  cursor: default;
}

.sidebar-footer:hover {
  background: var(--bg-surface-hover);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--bg-surface-active);
  border: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.user-plan {
  font-size: 11px;
  color: var(--text-tertiary);
}

/* 触屏设备始终显示删除按钮 */
@media (hover: none) {
  .session-item-delete {
    opacity: 0.6;
  }
}
</style>
