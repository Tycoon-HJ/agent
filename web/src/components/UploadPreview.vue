<template>
  <div class="upload-preview">
    <!-- Images -->
    <div v-if="images && images.length" class="preview-images">
      <div v-for="(img, idx) in images" :key="idx" class="preview-image-chip">
        <img :src="img.preview" alt="Preview" />
        <button class="chip-remove" @click="$emit('remove-image', idx)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
      <div v-if="images.length > 1" class="preview-count">{{ images.length }} images</div>
    </div>

    <!-- Documents -->
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
        <button class="chip-remove" @click="$emit('remove-doc', idx)">
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
defineProps<{ images: any[]; docs: any[] }>()

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
  gap: 8px;
  padding: 8px 0;
}

/* Image chips */
.preview-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.preview-image-chip {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  overflow: hidden;
  border: 2px solid rgba(99,102,241,0.3);
  transition: all var(--duration-fast) var(--ease-out);
}

.preview-image-chip:hover {
  border-color: var(--primary);
  transform: scale(1.05);
}

.preview-image-chip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-count {
  font-size: 12px;
  color: var(--text-tertiary);
  padding-left: 4px;
}

/* Doc chips */
.preview-docs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-doc-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: all var(--duration-fast) var(--ease-out);
}

.preview-doc-chip:hover {
  background: var(--bg-glass-strong);
  border-color: var(--border-light);
}

.doc-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(99,102,241,0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-light);
  flex-shrink: 0;
}

.doc-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.doc-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 140px;
}

.doc-size {
  font-size: 10px;
  color: var(--text-tertiary);
}

/* Remove button */
.chip-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  border: none;
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
}

.preview-image-chip:hover .chip-remove,
.preview-doc-chip:hover .chip-remove {
  opacity: 1;
}

.chip-remove:hover {
  background: var(--error);
  color: white;
}

.preview-doc-chip .chip-remove {
  position: static;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  opacity: 0.5;
  flex-shrink: 0;
}

.preview-doc-chip:hover .chip-remove {
  opacity: 1;
}
</style>
