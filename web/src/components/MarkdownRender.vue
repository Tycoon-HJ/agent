<template>
  <div class="markdown-body" v-html="renderedHtml"></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'

interface Props {
  content: string
}

const props = defineProps<Props>()

const marked = new Marked({
  gfm: true,
  breaks: true,
})

marked.use({
  renderer: {
    code({ text, lang }: { text: string; lang?: string }): string {
      let highlighted: string
      if (lang && hljs.getLanguage(lang)) {
        try {
          highlighted = hljs.highlight(text, { language: lang }).value
        } catch {
          highlighted = hljs.highlightAuto(text).value
        }
      } else {
        highlighted = hljs.highlightAuto(text).value
      }

      const langLabel = lang || 'code'
      return `<div class="code-block"><div class="code-header"><span class="code-lang">${langLabel}</span><button class="code-copy" onclick="const b=this;navigator.clipboard.writeText(b.closest('.code-block').querySelector('code').textContent).then(()=>{b.textContent='已复制';setTimeout(()=>b.textContent='复制',1500)})">复制</button></div><pre><code class="hljs language-${lang || 'auto'}">${highlighted}</code></pre></div>`
    },
  },
})

const renderedHtml = computed(() => {
  if (!props.content) return ''
  const rawHtml = marked.parse(props.content, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['code', 'pre', 'span', 'div'],
    ADD_ATTR: ['class', 'hljs', 'onclick'],
  })
})
</script>

<style scoped>
.markdown-body {
  font-size: 14.5px;
  line-height: 1.8;
  color: var(--text-primary);
  word-break: break-word;
}

/* ── 标题 ───────────────────────────────── */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--text-primary);
  font-weight: 700;
  margin-top: 24px;
  margin-bottom: 12px;
  letter-spacing: -0.3px;
}

.markdown-body :deep(h1) { font-size: 22px; }
.markdown-body :deep(h2) { font-size: 18px; }
.markdown-body :deep(h3) { font-size: 16px; }

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child) {
  margin-top: 0;
}

/* ── 段落 ───────────────────────────────── */
.markdown-body :deep(p) {
  margin-bottom: 12px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

/* ── 代码块 ─────────────────────────────── */
.markdown-body :deep(.code-block) {
  background: var(--code-bg);
  border: 1px solid var(--code-border);
  border-radius: 14px;
  margin: 14px 0;
  overflow: hidden;
}

.markdown-body :deep(.code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: var(--code-header-bg);
  border-bottom: 1px solid var(--code-border);
}

.markdown-body :deep(.code-lang) {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.markdown-body :deep(.code-copy) {
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  font-family: var(--font-sans);
}

.markdown-body :deep(.code-copy:hover) {
  background: var(--bg-surface-hover);
  color: var(--text-primary);
}

.markdown-body :deep(pre) {
  margin: 0;
  padding: 14px 16px;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.65;
  color: var(--code-text);
}

/* ── highlight.js 主题(由 CSS 变量驱动,自动适配明暗) ── */
.markdown-body :deep(.hljs-keyword),
.markdown-body :deep(.hljs-selector-tag),
.markdown-body :deep(.hljs-built_in) {
  color: var(--token-keyword);
}

.markdown-body :deep(.hljs-string),
.markdown-body :deep(.hljs-regexp),
.markdown-body :deep(.hljs-addition) {
  color: var(--token-string);
}

.markdown-body :deep(.hljs-comment),
.markdown-body :deep(.hljs-quote),
.markdown-body :deep(.hljs-meta) {
  color: var(--token-comment);
  font-style: italic;
}

.markdown-body :deep(.hljs-number),
.markdown-body :deep(.hljs-literal) {
  color: var(--token-number);
}

.markdown-body :deep(.hljs-title),
.markdown-body :deep(.hljs-section),
.markdown-body :deep(.hljs-title.function_) {
  color: var(--token-title);
}

.markdown-body :deep(.hljs-attr),
.markdown-body :deep(.hljs-attribute),
.markdown-body :deep(.hljs-selector-class),
.markdown-body :deep(.hljs-selector-id) {
  color: var(--token-attr);
}

.markdown-body :deep(.hljs-name),
.markdown-body :deep(.hljs-tag),
.markdown-body :deep(.hljs-type) {
  color: var(--token-tag);
}

.markdown-body :deep(.hljs-variable),
.markdown-body :deep(.hljs-template-variable),
.markdown-body :deep(.hljs-deletion),
.markdown-body :deep(.hljs-symbol) {
  color: var(--token-variable);
}

.markdown-body :deep(.hljs-emphasis) { font-style: italic; }
.markdown-body :deep(.hljs-strong) { font-weight: 700; }

/* ── 行内代码 ───────────────────────────── */
.markdown-body :deep(:not(pre) > code) {
  background: var(--primary-soft);
  color: var(--primary-strong);
  padding: 2px 6px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 0.88em;
}

/* ── 引用块 ─────────────────────────────── */
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding: 2px 0 2px 14px;
  margin: 14px 0;
  color: var(--text-secondary);
}

.markdown-body :deep(blockquote p) {
  margin-bottom: 6px;
}

/* ── 表格 ───────────────────────────────── */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 14px 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
  display: block;
  overflow-x: auto;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 9px 14px;
  text-align: left;
  font-size: 13px;
}

.markdown-body :deep(th) {
  background: var(--bg-surface);
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-body :deep(td) {
  color: var(--text-secondary);
}

/* ── 链接 ───────────────────────────────── */
.markdown-body :deep(a) {
  color: var(--primary-strong);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color var(--duration-fast);
}

.markdown-body :deep(a:hover) {
  border-bottom-color: var(--primary-strong);
}

/* ── 列表 ───────────────────────────────── */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 22px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(li::marker) {
  color: var(--text-tertiary);
}

/* ── 图片 ───────────────────────────────── */
.markdown-body :deep(img) {
  max-width: 100%;
  max-height: 400px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-out);
  object-fit: contain;
}

.markdown-body :deep(img:hover) {
  transform: scale(1.01);
}

/* ── 分割线 ─────────────────────────────── */
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 22px 0;
}

/* ── 加粗 ───────────────────────────────── */
.markdown-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 700;
}
</style>
