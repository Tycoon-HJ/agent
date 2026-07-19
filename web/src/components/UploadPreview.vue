<template>
  <div class="upload-preview">
    <!-- 图片 -->
    <div v-if="images && images.length" class="preview-images">
      <div v-for="(img, idx) in images" :key="idx" class="preview-image-chip">
        <img :src="img.preview" alt="预览" />
        <button class="chip-remove" @click="$emit('remove-image', idx)" title="移除">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div v-if="images.length > 1" class="preview-count">{{ images.length }} 张图片</div>
    </div>

    <!-- 文档 -->
    <div v-if="docs && docs.length" class="preview-docs">
      <div v-for="(d, idx) in docs" :key="idx" class="preview-doc-chip">
        <div class="doc-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
        </div>
        <div class="doc-info">
          <span class="doc-name">{{ d.name }}</span>
          <span class="doc-size">{{ formatFileSize(d.size) }}</span>
        </div>
        <button class="chip-remove" @click="$emit('remove-doc', idx)" title="移除">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface PreviewImage {
  file: File | null
  preview: string
  base64: string
  name: string
}

interface PreviewDoc {
  file: File
  name: string
  type: string
  content: string
  size: number
}

defineProps<{ images: PreviewImage[]; docs: PreviewDoc[] }>()

defineEmits(['remove-image', 'remove-doc'])

function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.upload-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-bottom: 10px;
}

/* ── 图片卡片 ───────────────────────────── */
.preview-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.preview-image-chip {
  position: relative;
  width: 60px;
  height: 60px;
  border-radius: 14px;
  overflow: visible;
  transition: transform var(--duration-fast) var(--ease-out);
}

.preview-image-chip:hover {
  transform: scale(1.04);
}

.preview-image-chip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid var(--border-light);
  display: block;
}

.preview-count {
  font-size: 12px;
  color: var(--text-tertiary);
  padding-left: 2px;
}

/* ── 文档卡片 ───────────────────────────── */
.preview-docs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.preview-doc-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  transition: all var(--duration-fast) var(--ease-out);
}

.preview-doc-chip:hover {
  border-color: var(--border-light);
  box-shadow: var(--shadow-md);
}

.doc-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--primary-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-strong);
  flex-shrink: 0;
}

.doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.doc-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

.doc-size {
  font-size: 11px;
  color: var(--text-tertiary);
}

/* ── 移除按钮 ───────────────────────────── */
.chip-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all var(--duration-fast) var(--ease-out);
  z-index: 1;
  box-shadow: var(--shadow-sm);
}

.preview-image-chip:hover .chip-remove,
.preview-doc-chip:hover .chip-remove {
  opacity: 1;
}

.chip-remove:hover {
  background: var(--error);
  border-color: var(--error);
  color: white;
}

.preview-doc-chip .chip-remove {
  position: static;
  border: none;
  background: transparent;
  box-shadow: none;
  border-radius: 6px;
  opacity: 0.4;
  flex-shrink: 0;
}

.preview-doc-chip:hover .chip-remove {
  opacity: 1;
}

.preview-doc-chip .chip-remove:hover {
  background: rgba(239, 68, 68, 0.12);
  color: var(--error);
}

/* 触屏设备常显 */
@media (hover: none) {
  .chip-remove {
    opacity: 1;
  }
}
</style>
