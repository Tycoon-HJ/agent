<template>
  <aside class="sidebar">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <div class="logo-text">
        <span class="logo-name">Agent Studio</span>
        <span class="logo-badge">Pro</span>
      </div>
    </div>

    <!-- New Chat Button -->
    <button class="btn-new-chat" @click="$emit('create')">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      <span>New Chat</span>
    </button>

    <!-- Session List -->
    <div class="session-list">
      <div class="session-section-label">Recent</div>
      <div
        v-for="s in sessions"
        :key="s.sessionId"
        class="session-card"
        :class="{ active: s.sessionId === current }"
        @click="$emit('switch', s.sessionId)"
      >
        <div class="session-card-content">
          <div class="session-card-title">{{ s.title }}</div>
          <div class="session-card-time">{{ formatTime(s.updatedAt) }}</div>
        </div>
        <button
          class="session-card-delete"
          @click.stop="$emit('delete', s.sessionId)"
          title="Delete"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M3 6h18"/>
            <path d="M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
            <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/>
          </svg>
        </button>
      </div>

      <div v-if="sessions.length === 0" class="session-empty">
        <div class="session-empty-icon">💬</div>
        <div class="session-empty-text">No conversations yet</div>
      </div>
    </div>

    <!-- Sidebar Footer -->
    <div class="sidebar-footer">
      <div class="sidebar-user">
        <div class="user-avatar">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
        </div>
        <div class="user-info">
          <div class="user-name">User</div>
          <div class="user-plan">Free Plan</div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { ChatSession } from '../types/chat'

defineProps<{ sessions: ChatSession[]; current?: string }>()

function formatTime(ts: number) {
  const d = new Date(ts)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (mins < 1) return 'Just now'
  if (mins < 60) return `${mins}m ago`
  if (hours < 24) return `${hours}h ago`
  if (days < 7) return `${days}d ago`
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
</script>

<style scoped>
.sidebar {
  width: 280px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255,255,255,0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 20px 16px;
  gap: 8px;
  flex-shrink: 0;
  animation: slideInLeft var(--duration-slow) var(--ease-out) both;
}

/* Logo */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px;
  margin-bottom: 8px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 15px rgba(99,102,241,0.3);
}

.logo-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

.logo-badge {
  font-size: 10px;
  font-weight: 700;
  color: var(--primary-light);
  background: rgba(99,102,241,0.15);
  padding: 2px 7px;
  border-radius: 6px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

/* New Chat Button */
.btn-new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 14px;
  background: var(--gradient-primary);
  color: white;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  box-shadow: 0 4px 15px rgba(99,102,241,0.3);
}

.btn-new-chat:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-glow-lg);
}

.btn-new-chat:active {
  transform: scale(0.98);
}

/* Session List */
.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.session-section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding: 8px 8px 4px;
}

/* Session Card */
.session-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 12px;
  border-radius: 14px;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  position: relative;
}

.session-card:hover {
  background: var(--bg-surface-hover);
}

.session-card:hover .session-card-delete {
  opacity: 1;
}

.session-card.active {
  background: var(--gradient-primary);
  box-shadow: 0 4px 20px rgba(99,102,241,0.25);
}

.session-card.active .session-card-title {
  color: white;
}

.session-card.active .session-card-time {
  color: rgba(255,255,255,0.7);
}

.session-card.active .session-card-delete {
  color: rgba(255,255,255,0.7);
}

.session-card.active .session-card-delete:hover {
  background: rgba(255,255,255,0.15);
  color: white;
}

.session-card-content {
  flex: 1;
  min-width: 0;
}

.session-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-card-time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.session-card-delete {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  border-radius: 8px;
  cursor: pointer;
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out);
  flex-shrink: 0;
}

.session-card-delete:hover {
  background: rgba(239,68,68,0.15);
  color: var(--error);
  opacity: 1;
}

/* Empty State */
.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  gap: 12px;
}

.session-empty-icon {
  font-size: 32px;
  opacity: 0.5;
}

.session-empty-text {
  font-size: 13px;
  color: var(--text-tertiary);
}

/* Sidebar Footer */
.sidebar-footer {
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 12px;
  transition: background var(--duration-fast) var(--ease-out);
}

.sidebar-user:hover {
  background: var(--bg-surface-hover);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--bg-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.user-info {
  flex: 1;
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
</style>
