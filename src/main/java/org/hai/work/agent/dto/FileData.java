package org.hai.work.agent.dto;

/**
 * 文件数据 DTO
 * <p>
 * 封装前端上传的文件信息，用于文件处理 Agent。
 * 支持 CSV、Markdown、Excel（转CSV）、TXT 等文本类文件。
 */
public class FileData {

    /**
     * 文件名（含扩展名）
     */
    private String name;

    /**
     * MIME 类型
     * 如 text/csv, text/markdown, text/plain, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     */
    private String type;

    /**
     * 文件内容（文本格式）
     * Excel 文件在前端已转换为 CSV 文本
     */
    private String content;

    public FileData() {
    }

    public FileData(String name, String type, String content) {
        this.name = name;
        this.type = type;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
