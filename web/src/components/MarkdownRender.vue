<template>
  <!--
    MarkdownRender 组件
    - 使用 marked 解析 Markdown
    - 使用 DOMPurify 过滤 XSS
    - 使用 highlight.js 高亮代码块
    - 支持 GFM 语法
  -->
  <div
      class="markdown-body"
      v-html="renderedHtml"
  ></div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Marked} from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import 'github-markdown-css/github-markdown.css'

// Props 定义
interface Props {
  content: string
}

const props = defineProps<Props>()

// 创建独立的 marked 实例（避免全局状态污染）
const marked = new Marked({
  gfm: true,
  breaks: true,
})

// 自定义渲染器 - 代码块高亮
marked.use({
  renderer: {
    code({text, lang}: { text: string; lang?: string }): string {
      let highlighted: string
      if (lang && hljs.getLanguage(lang)) {
        try {
          highlighted = hljs.highlight(text, {language: lang}).value
        } catch {
          highlighted = hljs.highlightAuto(text).value
        }
      } else {
        highlighted = hljs.highlightAuto(text).value
      }
      return `<pre><code class="hljs language-${lang || 'auto'}">${highlighted}</code></pre>`
    },
  },
})

/**
 * 计算渲染后的 HTML
 * 流程：Markdown -> marked 解析 -> DOMPurify 过滤 -> 输出
 */
const renderedHtml = computed(() => {
  if (!props.content) return ''

  // 1. 使用 marked 解析 Markdown（同步模式）
  const rawHtml = marked.parse(props.content, {async: false}) as string

  // 2. 使用 DOMPurify 过滤 XSS
  const cleanHtml = DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['code', 'pre', 'span'],
    ADD_ATTR: ['class', 'hljs'],
  })

  return cleanHtml
})
</script>

<style scoped>
/* Markdown 容器样式 */
.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

/* 代码块样式 - 深色背景，支持横向滚动 */
.markdown-body :deep(pre) {
  background-color: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(code) {
  font-family: 'Fira Code', 'Consolas', 'Monaco', 'Andale Mono', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

/* 行内代码样式 */
.markdown-body :deep(:not(pre) > code) {
  background-color: rgba(175, 184, 193, 0.2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

/* 引用块样式 */
.markdown-body :deep(blockquote) {
  border-left: 4px solid var(--accent-color);
  padding-left: 16px;
  margin: 12px 0;
  color: var(--text-secondary);
}

/* 表格样式 */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-color);
  padding: 8px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background-color: var(--bg-secondary);
}

/* 链接样式 */
.markdown-body :deep(a) {
  color: var(--accent-color);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 列表样式 */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

/* 图片样式 */
.markdown-body :deep(img) {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s;
  object-fit: contain;
}

.markdown-body :deep(img:hover) {
  transform: scale(1.02);
}

/* 图片链接样式 */
.markdown-body :deep(a > img) {
  border: 2px solid transparent;
}

.markdown-body :deep(a > img:hover) {
  border-color: var(--accent-color);
}

/* 分割线样式 */
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 16px 0;
}
</style>
