# Agent

一个基于 **Spring Boot + Spring AI + Vue 3** 的多能力智能体项目，支持文本对话、天气查询、联网搜索、表情包检索与图片理解，并提供流式（SSE）输出。

## 功能特性

- 多 Agent 路由：自动将请求分发到不同业务 Agent
- 流式回复：后端 SSE，前端打字机效果
- 多会话管理：前端支持新建/切换/删除会话并本地持久化
- 图片对话：支持上传图片并结合文本提问
- 工具调用：天气查询、联网搜索、图片解析、表情包搜索

## 技术栈

### 后端

- Java 25
- Spring Boot 4.1
- Spring AI 2.0（DeepSeek）
- Maven

### 前端

- Vue 3 + TypeScript
- Vite
- marked + DOMPurify + highlight.js

## 项目结构

```text
.
├── src/main/java/org/hai/work
│   ├── agent/core         # RouterAgent / WeatherAgent / CodeAgent / ImageAgent
│   ├── resource           # API 入口
│   ├── tool/impl          # 工具实现（天气、搜索、图片、表情包）
│   └── config             # AI、跨域等配置
├── src/main/resources
│   └── application.yaml
├── web                    # Vue 前端
└── pom.xml
```

## 快速开始

## 1) 后端启动

```bash
cd /home/runner/work/agent/agent
mvn spring-boot:run
```

默认地址：`http://localhost:8080`

> 说明：`pom.xml` 配置了 `java.version=25`，请先准备 JDK 25。

## 2) 前端启动

```bash
cd /home/runner/work/agent/agent/web
npm install
npm run dev
```

默认地址：`http://localhost:3000`（已代理 `/api` 到 `http://localhost:8080`）

## 配置说明

后端基础配置文件：

- `/home/runner/work/agent/agent/src/main/resources/application.yaml`

需要配置或注入的关键项：

- `spring.ai.deepseek.api-key`（DeepSeek）
- `TAVILY_API_KEY`（联网搜索工具）
- `ALAPI_TOKEN`（表情包工具）
- `AGNES_API_KEY`（图片解析工具）

## API 概览

### 非流式

- `GET /api/ask`
- Query 参数：
    - `input`：用户输入
    - `sessionId`（可选）

### 流式（推荐）

- `POST /api/ask/stream?sessionId={sessionId}`
- Content-Type: `application/json`
- Body 示例：

```json
{
  "input": "帮我分析这张图",
  "images": ["data:image/png;base64,..."]
}
```

返回：SSE 流（以 JSON 文本片段和 `[DONE]` 结束标记输出）

## 常用命令

```bash
# 后端测试
cd /home/runner/work/agent/agent
mvn test

# 前端构建
cd /home/runner/work/agent/agent/web
npm run build
```

## 已知说明

- 当前会话记忆使用内存实现，服务重启后会丢失历史对话。
- `RouterAgent` 会根据意图将请求路由到对应子 Agent；图片请求会优先进入 `image-agent`。
