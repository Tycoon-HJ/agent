package org.hai.work.skill;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文件的 Skill 实现
 * <p>
 * 从外部 markdown 文件加载 Skill 定义。
 * 文件格式类似 Claude Code CLI 的 skill 文件：
 * <p>
 * 文件结构：
 * ```
 * ---
 * name: skill-name
 * description: Skill description
 * requiredTools:
 *   - tool1
 *   - tool2
 * ---
 *
 * ## Skill Content
 *
 * This is the system prompt addon content...
 * ```
 * <p>
 * - YAML frontmatter（--- 之间）包含元数据
 * - Markdown 正文是 system prompt addon 内容
 */
@Slf4j
public class FileBasedSkill implements Skill {

    private final String name;
    private final String description;
    private final String systemPromptAddon;
    private final List<String> requiredTools;
    private final Path filePath;

    private FileBasedSkill(String name, String description, String systemPromptAddon,
                           List<String> requiredTools, Path filePath) {
        this.name = name;
        this.description = description;
        this.systemPromptAddon = systemPromptAddon;
        this.requiredTools = requiredTools;
        this.filePath = filePath;
    }

    /**
     * 从文件加载 Skill
     *
     * @param filePath skill 文件路径（.md 文件）
     * @return FileBasedSkill 实例
     * @throws IOException 如果文件读取失败
     */
    public static FileBasedSkill loadFromFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return parse(filePath.getFileName().toString(), content, filePath);
    }

    /**
     * 解析 skill 文件内容
     * <p>
     * 格式：
     * ```
     * ---
     * name: skill-name
     * description: description
     * requiredTools:
     *   - tool1
     *   - tool2
     * ---
     *
     * # Content (system prompt addon)
     * ```
     */
    static FileBasedSkill parse(String fileName, String content, Path filePath) {
        String name = fileName.replace(".skill.md", "").replace(".md", "");
        String description = "";
        List<String> requiredTools = new ArrayList<>();
        String systemPromptAddon = content;

        // 解析 YAML frontmatter
        Pattern frontmatterPattern = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
        Matcher matcher = frontmatterPattern.matcher(content);

        if (matcher.matches()) {
            String frontmatter = matcher.group(1);
            systemPromptAddon = matcher.group(2).trim();

            // 解析 frontmatter 字段
            for (String line : frontmatter.split("\\n")) {
                line = line.trim();
                if (line.startsWith("name:")) {
                    name = line.substring(5).trim();
                } else if (line.startsWith("description:")) {
                    description = line.substring(12).trim();
                } else if (line.startsWith("- ") && !requiredTools.isEmpty()) {
                    // 列表项（属于上一个字段）
                    requiredTools.add(line.substring(2).trim());
                } else if (line.startsWith("requiredTools:")) {
                    // 开始解析 requiredTools 列表
                    requiredTools.clear();
                }
            }

            // 更精确地解析 requiredTools 列表
            Pattern toolsPattern = Pattern.compile("requiredTools:\\s*\\n((?:\\s*-\\s+.+\\n?)+)");
            Matcher toolsMatcher = toolsPattern.matcher(frontmatter);
            if (toolsMatcher.find()) {
                String toolsBlock = toolsMatcher.group(1);
                requiredTools.clear();
                for (String toolLine : toolsBlock.split("\\n")) {
                    toolLine = toolLine.trim();
                    if (toolLine.startsWith("- ")) {
                        requiredTools.add(toolLine.substring(2).trim());
                    }
                }
            }
        }

        if (description.isEmpty()) {
            description = "Skill loaded from: " + fileName;
        }

        log.info("加载 Skill: name={}, file={}, requiredTools={}", name, filePath, requiredTools);
        return new FileBasedSkill(name, description, systemPromptAddon, requiredTools, filePath);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String getSystemPromptAddon() {
        return systemPromptAddon;
    }

    @Override
    public List<String> requiredTools() {
        return Collections.unmodifiableList(requiredTools);
    }

    public Path getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return "FileBasedSkill{name='%s', file=%s}".formatted(name, filePath);
    }
}
