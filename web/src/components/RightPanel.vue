<template>
  <aside class="agent-panel">
    <!-- Header -->
    <div class="panel-header">
      <h3 class="panel-title">Agent</h3>
      <div class="panel-status">
        <span class="status-dot"></span>
        <span class="status-text">Online</span>
      </div>
    </div>

    <!-- Cards -->
    <div class="panel-cards stagger-children">
      <!-- Model -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Model</div>
          <div class="card-value">Claude 4 Sonnet</div>
        </div>
      </div>

      <!-- Token Usage -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Tokens Used</div>
          <div class="card-value">{{ tokenUsage.toLocaleString() }}</div>
        </div>
        <div class="card-bar">
          <div class="card-bar-fill" :style="{ width: Math.min(tokenUsage / 1000, 100) + '%' }"></div>
        </div>
      </div>

      <!-- Memory -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <rect x="2" y="6" width="20" height="12" rx="2"/>
            <path d="M6 12h4"/>
            <path d="M14 12h4"/>
            <path d="M6 12v-2"/>
            <path d="M14 12v2"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Memory</div>
          <div class="card-value">128 MB</div>
        </div>
      </div>

      <!-- Knowledge Base -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20"/>
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Knowledge Base</div>
          <div class="card-value">3 documents</div>
        </div>
      </div>

      <!-- Tools -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Tools</div>
          <div class="card-value">{{ toolCount }} active</div>
        </div>
      </div>

      <!-- Execution Time -->
      <div class="panel-card">
        <div class="card-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
        </div>
        <div class="card-info">
          <div class="card-label">Avg Response</div>
          <div class="card-value">1.2s</div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const tokenUsage = ref(2847)
const toolCount = ref(5)
</script>

<style scoped>
.agent-panel {
  width: 320px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 20px;
  background: rgba(255,255,255,0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-left: 1px solid rgba(255,255,255,0.06);
  overflow-y: auto;
  flex-shrink: 0;
  animation: fadeIn var(--duration-slow) var(--ease-out) both;
  animation-delay: 100ms;
}

/* Header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

.panel-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 8px rgba(16,185,129,0.5);
  animation: pulse 2s infinite;
}

.status-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--success);
}

/* Cards */
.panel-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.panel-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 16px;
  transition: all var(--duration-normal) var(--ease-out);
  flex-wrap: wrap;
}

.panel-card:hover {
  background: var(--bg-glass-strong);
  border-color: var(--border-light);
  transform: translateY(-2px);
}

.card-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(99,102,241,0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-light);
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 2px;
}

.card-bar {
  width: 100%;
  height: 4px;
  background: var(--bg-surface);
  border-radius: 2px;
  overflow: hidden;
  margin-top: 8px;
}

.card-bar-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 2px;
  transition: width 0.5s var(--ease-out);
}

/* Section */
.panel-section {
  margin-top: 4px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding: 0 4px;
  margin-bottom: 10px;
}

/* Quick Actions */
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border);
  background: var(--bg-glass);
  color: var(--text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  border-radius: 10px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.quick-action-btn:hover {
  background: var(--bg-surface-hover);
  border-color: var(--border-light);
  color: var(--text-primary);
  transform: translateY(-1px);
}
</style>
