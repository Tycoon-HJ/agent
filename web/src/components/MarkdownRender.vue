<template>
  <div class="markdown-body" v-html="renderedHtml"></div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

interface Props {
  content: string
}

const props = defineProps<Props>()

const marked = new Marked({
  gfm: true,
  breaks: true,
})

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

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

      const safeLangLabel = escapeHtml(lang || 'code')
      const safeLang = escapeHtml(lang || 'auto')
      return `<div class="code-block"><div class="code-header"><span class="code-lang">${safeLangLabel}</span><button class="code-copy" data-copy-target>Copy</button></div><pre><code class="hljs language-${safeLang}">${highlighted}</code></pre></div>`
    },
  },
})

const renderedHtml = computed(() => {
  if (!props.content) return ''
  const rawHtml = marked.parse(props.content, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, {
    ADD_TAGS: ['code', 'pre', 'span', 'div'],
    ADD_ATTR: ['class', 'hljs', 'data-copy-target'],
  })
})

// Delegated click handler for code copy buttons (replaces inline onclick)
function handleCopyClick(e: Event) {
  const btn = (e.target as HTMLElement).closest('[data-copy-target]')
  if (!btn) return
  const code = btn.closest('.code-block')?.querySelector('code')
  if (code) {
    navigator.clipboard.writeText(code.textContent || '').catch(() => {})
  }
}

onMounted(() => {
  document.addEventListener('click', handleCopyClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleCopyClick)
})
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
  word-break: break-word;
}

/* Headings */
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

.markdown-body :deep(h1) { font-size: 24px; }
.markdown-body :deep(h2) { font-size: 20px; }
.markdown-body :deep(h3) { font-size: 16px; }

/* Paragraphs */
.markdown-body :deep(p) {
  margin-bottom: 12px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

/* Code Block */
.markdown-body :deep(.code-block) {
  background: #111827;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 16px;
  margin: 16px 0;
  overflow: hidden;
}

.markdown-body :deep(.code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.markdown-body :deep(.code-lang) {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.markdown-body :deep(.code-copy) {
  border: none;
  background: rgba(255,255,255,0.06);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}

.markdown-body :deep(.code-copy:hover) {
  background: rgba(255,255,255,0.1);
  color: var(--text-primary);
}

.markdown-body :deep(pre) {
  margin: 0;
  padding: 16px;
  overflow-x: auto;
}

.markdown-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
}

/* Inline code */
.markdown-body :deep(:not(pre) > code) {
  background: rgba(99,102,241,0.1);
  color: var(--primary-light);
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 0.9em;
}

/* Blockquote */
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 16px;
  margin: 16px 0;
  color: var(--text-secondary);
  font-style: italic;
}

/* Table */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--border);
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 10px 14px;
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

/* Links */
.markdown-body :deep(a) {
  color: var(--primary-light);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s;
}

.markdown-body :deep(a:hover) {
  border-bottom-color: var(--primary-light);
}

/* Lists */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(li::marker) {
  color: var(--text-tertiary);
}

/* Images */
.markdown-body :deep(img) {
  max-width: 100%;
  max-height: 400px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s;
  object-fit: contain;
}

.markdown-body :deep(img:hover) {
  transform: scale(1.01);
}

/* Horizontal Rule */
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 24px 0;
}

/* Strong / Bold */
.markdown-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 700;
}
</style>
