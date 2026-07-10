/**
 * 聊天相关类型定义
 */

/** 文件数据 */
export interface FileData {
    /** 文件名（含扩展名） */
    name: string
    /** MIME 类型 */
    type: string
    /** 文件内容（文本格式） */
    content: string
}

/** 单条消息 */
export interface ChatMessage {
    id: string
    role: 'user' | 'assistant'
    content: string
    timestamp: number
    /** 图片URL列表（用于图生图或AI生成的图片） */
    images?: string[]
    /** 文件列表 */
    files?: FileData[]
}

/** 会话信息 */
export interface ChatSession {
    sessionId: string
    title: string
    messages: ChatMessage[]
    createdAt: number
    updatedAt: number
}
