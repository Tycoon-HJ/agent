<template>
  <!--
    ChatMessage 组件
    - 单条消息气泡
    - 用户消息居右，AI消息居左
    - AI消息支持 Markdown 渲染
    - 支持图片展示
    - 流式输出时显示闪烁光标
  -->
  <div
      class="message-wrapper"
      :class="[message.role === 'user' ? 'user-wrapper' : 'ai-wrapper']"
  >
    <!-- 头像 -->
    <div class="avatar" :class="message.role">
      {{ message.role === 'user' ? '👤' : '🤖' }}
    </div>

    <!-- 消息气泡 -->
    <div
        class="bubble"
        :class="[message.role === 'user' ? 'user-bubble' : 'ai-bubble']"
    >
      <!-- 用户消息 -->
      <div v-if="message.role === 'user'" class="user-content">
        <!-- 用户上传的图片 -->
        <div v-if="message.images && message.images.length > 0" class="image-grid user-images">
          <div
              v-for="(img, index) in message.images"
              :key="index"
              class="image-item"
          >
            <img :src="img" :alt="`上传的图片 ${index + 1}`" @click="previewImage(img)"/>
          </div>
        </div>
        <!-- 用户上传的文件 -->
        <div v-if="message.files && message.files.length > 0" class="file-list">
          <div
              v-for="(file, index) in message.files"
              :key="index"
              class="file-item"
          >
            <span class="file-icon">📄</span>
            <span class="file-name">{{ file.name }}</span>
          </div>
        </div>
        <!-- 文本内容 -->
        <div v-if="message.content" class="text-content">{{ message.content }}</div>
      </div>

      <!-- AI消息 - Markdown 渲染 -->
      <div v-else class="ai-content">
        <!-- AI生成的图片 -->
        <div v-if="message.images && message.images.length > 0" class="image-grid ai-images">
          <div
              v-for="(img, index) in message.images"
              :key="index"
              class="image-item"
          >
            <img :src="img" :alt="`生成的图片 ${index + 1}`" @click="previewImage(img)"/>
            <div class="image-actions">
              <button class="btn-download" @click.stop="downloadImage(img, index)" title="下载图片">
                ⬇️
              </button>
            </div>
          </div>
        </div>
        <!-- Markdown 文本内容 -->
        <MarkdownRender :content="message.content"/>
        <!-- 流式输出时的闪烁光标 -->
        <span v-if="isStreaming" class="cursor"></span>
      </div>
    </div>

    <!-- 图片预览模态框 -->
    <Teleport to="body">
      <div v-if="previewUrl" class="image-preview-modal" @click="previewUrl = ''">
        <img :src="previewUrl" alt="预览图片"/>
        <button class="btn-close-preview" @click.stop="previewUrl = ''">×</button>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import type {ChatMessage} from '../types/chat'
import MarkdownRender from './MarkdownRender.vue'

// Props 定义
interface Props {
  message: ChatMessage
  isStreaming?: boolean
}

defineProps<Props>()

/** 预览图片URL */
const previewUrl = ref('')

/**
 * 预览图片
 */
function previewImage(url: string): void {
  previewUrl.value = url
}

/**
 * 下载图片
 */
function downloadImage(url: string, index: number): void {
  const link = document.createElement('a')
  link.href = url
  link.download = `ai-image-${Date.now()}-${index}.png`
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>

<style scoped>
/* 消息包装器 */
.message-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 85%;
  position: relative;
}

/* 用户消息 - 居右 */
.user-wrapper {
  flex-direction: row-reverse;
  margin-left: auto;
}

/* AI消息 - 居左 */
.ai-wrapper {
  flex-direction: row;
  margin-right: auto;
}

/* 头像 */
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.avatar.user {
  background-color: var(--accent-color);
}

.avatar.assistant {
  background-color: var(--bg-tertiary);
}

/* 气泡基础样式 */
.bubble {
  padding: 12px 16px;
  border-radius: 16px;
  max-width: 100%;
  word-break: break-word;
}

/* 用户气泡 */
.user-bubble {
  background-color: var(--user-bubble);
  color: var(--user-text);
  border-top-right-radius: 4px;
}

/* AI气泡 */
.ai-bubble {
  background-color: var(--ai-bubble);
  color: var(--ai-text);
  border-top-left-radius: 4px;
}

/* 用户内容 */
.user-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.text-content {
  white-space: pre-wrap;
}

/* 文件列表 */
.file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  font-size: 13px;
}

.file-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* AI内容 */
.ai-content {
  min-height: 20px;
}

/* ==================== 图片网格样式 ==================== */
.image-grid {
  display: grid;
  gap: 8px;
  margin-bottom: 8px;
}

.image-grid:has(img):not(:has(div:nth-child(2))) {
  grid-template-columns: 1fr;
}

.image-grid:has(img:nth-child(2)) {
  grid-template-columns: repeat(2, 1fr);
}

.image-grid:has(img:nth-child(3)) {
  grid-template-columns: repeat(3, 1fr);
}

.image-item {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

.image-item img {
  width: 100%;
  height: auto;
  display: block;
  max-height: 300px;
  object-fit: cover;
  transition: transform 0.2s;
}

.image-item:hover img {
  transform: scale(1.02);
}

/* 用户图片样式 */
.user-images .image-item {
  border: 2px solid rgba(255, 255, 255, 0.2);
}

/* AI图片样式 */
.ai-images .image-item {
  border: 1px solid var(--border-color);
}

/* 图片操作按钮 */
.image-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.image-item:hover .image-actions {
  opacity: 1;
}

.btn-download {
  width: 32px;
  height: 32px;
  border: none;
  background-color: rgba(0, 0, 0, 0.6);
  color: white;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: background-color 0.2s;
}

.btn-download:hover {
  background-color: rgba(0, 0, 0, 0.8);
}

/* ==================== 图片预览模态框 ==================== */
.image-preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  cursor: pointer;
}

.image-preview-modal img {
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
}

.btn-close-preview {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  border: none;
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
  font-size: 24px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s;
}

.btn-close-preview:hover {
  background-color: rgba(255, 255, 255, 0.4);
}

/* 闪烁光标动画 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 16px;
  background-color: var(--accent-color);
  margin-left: 2px;
  vertical-align: middle;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}
</style>
