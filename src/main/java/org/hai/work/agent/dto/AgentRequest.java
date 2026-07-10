package org.hai.work.agent.dto;

import java.util.List;

/**
 * Agent 请求对象
 * <p>
 * 封装调用 Agent 所需的所有信息。
 * 由 AgentResource 构建，传递给 Agent.execute()。
 */
public class AgentRequest {

    /**
     * 用户ID（预留字段，可用于多用户隔离、权限控制等）
     */
    private String userId;

    /**
     * 会话ID
     * 用于会话记忆隔离：相同 sessionId 的请求共享对话上下文。
     * 例如：用户在前端页面的 session ID、或自定义的会话标识。
     */
    private String sessionId;

    /**
     * 用户输入的自然语言
     * 例如："上海天气怎么样"、"下周去东京旅游，帮我看看天气"
     */
    private String input;

    /**
     * 用户上传的图片列表（base64 data URL）
     * 例如：["data:image/png;base64,iVBOR...", "data:image/jpeg;base64,..."]
     * 前端通过 POST body 发送，后端解析后传给多模态 LLM。
     */
    private List<String> images;

    /**
     * 用户上传的文件列表
     * 支持 CSV、Markdown、Excel（前端转CSV）、TXT 等文本类文件。
     * 前端读取文件内容后，通过 POST body 发送给后端。
     */
    private List<FileData> files;

    /**
     * 无参构造（用于 setter 方式构建）
     */
    public AgentRequest() {
    }

    /**
     * 便捷构造（快速构建请求）
     */
    public AgentRequest(String input, String sessionId) {
        this.input = input;
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<FileData> getFiles() {
        return files;
    }

    public void setFiles(List<FileData> files) {
        this.files = files;
    }
}
