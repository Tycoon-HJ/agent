# AI Chat Frontend

基于 Vue3 + Vite + TypeScript 的 AI 对话前端项目。

## 技术栈

- Vue 3 (Composition API + script setup)
- Vite 6
- TypeScript
- marked - Markdown 解析
- DOMPurify - XSS 过滤
- highlight.js - 代码高亮
- github-markdown-css - GitHub 风格样式

## 功能特性

- ✅ SSE 流式通信，实现打字机效果
- ✅ 增量 Markdown 渲染（始终对完整文本渲染，防止半截代码块）
- ✅ 多会话管理（新建、切换、删除）
- ✅ 会话持久化（LocalStorage）
- ✅ 代码块语法高亮
- ✅ XSS 安全过滤
- ✅ 自动滚动到底部
- ✅ 停止生成功能
- ✅ 深色/浅色主题自适应

## 快速开始

### 安装依赖

```bash
cd web
npm install
```

### 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 启动，并自动代理 `/api` 请求到后端 http://localhost:8080。

### 构建生产版本

```bash
npm run build
```

## 后端接口

本项目对接 Java Spring Boot 后端的 SSE 流式接口：

```
GET /api/ask/stream?input={消息内容}&sessionId={会话ID}
```

- 响应类型：`text/event-stream`
- 数据格式：`data:内容\n\n`
- 结束标记：`data:[DONE]\n\n`

## 项目结构

```
web/
├── index.html              # 入口 HTML
├── package.json            # 项目配置
├── vite.config.ts          # Vite 配置
├── tsconfig.json           # TypeScript 配置
├── src/
│   ├── main.ts             # 应用入口
│   ├── App.vue             # 根组件
│   ├── style.css           # 全局样式
│   ├── types/
│   │   └── chat.ts         # 类型定义
│   ├── components/
│   │   ├── MarkdownRender.vue  # Markdown 渲染组件
│   │   └── ChatMessage.vue     # 消息气泡组件
│   └── views/
│       └── ChatIndex.vue       # 主页面
└── public/
    └── vite.svg            # 图标
```

## 组件说明

### MarkdownRender.vue

Markdown 渲染组件，负责：

- 使用 `marked` 解析 Markdown，启用 GFM 语法
- 使用 `highlight.js` 自动高亮代码块
- 使用 `DOMPurify` 过滤 XSS 攻击
- 引入 GitHub 风格的 Markdown 样式

### ChatMessage.vue

单条消息气泡组件，负责：

- 用户消息居右显示
- AI 消息居左显示，支持 Markdown 渲染
- 流式输出时显示闪烁光标动画

### ChatIndex.vue

主页面组件，负责：

- 左侧会话列表管理
- 右侧聊天消息展示
- 底部输入框和发送/停止按钮
- SSE 流式通信逻辑
- 会话持久化存储
