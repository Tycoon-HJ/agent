/**
 * 聊天相关类型定义
 */

/** 单条消息 */
export interface ChatMessage {
    id: string
    role: 'user' | 'assistant'
    content: string
    timestamp: number
    /** 图片URL列表（用于图生图或AI生成的图片） */
    images?: string[]
}

/** 会话信息 */
export interface ChatSession {
    sessionId: string
    title: string
    messages: ChatMessage[]
    createdAt: number
    updatedAt: number
}
