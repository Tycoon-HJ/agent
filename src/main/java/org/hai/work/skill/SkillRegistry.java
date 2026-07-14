package org.hai.work.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 技能注册中心
 * <p>
 * 支持两种 Skill 来源：
 * 1. Java 注解方式：通过 Spring 自动注入 Skill 实现类
 * 2. 文件方式：从外部目录加载 .md 文件作为 Skill（类似 Claude Code CLI）
 * <p>
 * 文件格式：
 * ```
 * ---
 * name: skill-name
 * description: Skill description
 * requiredTools:
 *   - tool1
 *   - tool2
 * ---
 *
 * # Skill Content (system prompt addon)
 * ```
 * <p>
 * 配置项：
 * - agent.skills.directory: Skill 文件目录（默认为 ./skills）
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * <p>
     * 1. 加载 Java 注解方式的 Skill
     * 2. 加载文件方式的 Skill
     *
     * @param javaSkills     Spring 注入的 Skill 实现
     * @param skillsDirectory Skill 文件目录（从配置读取）
     */
    public SkillRegistry(
            List<Skill> javaSkills,
            @Value("${agent.skills.directory:./skills}") String skillsDirectory) {

        // 1. 加载 Java 注解方式的 Skill
        for (Skill skill : javaSkills) {
            skills.put(skill.name(), skill);
            log.info("注册 Java Skill: {}", skill.name());
        }

        // 2. 加载文件方式的 Skill
        loadSkillsFromDirectory(skillsDirectory);

        log.info("SkillRegistry 初始化完成，共 {} 个 Skill: {}", skills.size(), skills.keySet());
    }

    /**
     * 从目录加载所有 .md 文件作为 Skill
     */
    private void loadSkillsFromDirectory(String directoryPath) {
        Path dir = Path.of(directoryPath).toAbsolutePath().normalize();

        if (!Files.exists(dir)) {
            log.info("Skill 目录不存在，跳过文件加载: {}", dir);
            return;
        }

        if (!Files.isDirectory(dir)) {
            log.warn("Skill 路径不是目录: {}", dir);
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".md") || name.endsWith(".skill.md");
                    })
                    .forEach(this::loadSkillFile);
        } catch (IOException e) {
            log.error("读取 Skill 目录失败: {}", dir, e);
        }
    }

    /**
     * 加载单个 Skill 文件
     */
    private void loadSkillFile(Path filePath) {
        try {
            FileBasedSkill skill = FileBasedSkill.loadFromFile(filePath);

            // 检查名称冲突
            if (skills.containsKey(skill.name())) {
                log.warn("Skill 名称冲突，文件 Skill 覆盖 Java Skill: name={}, file={}",
                        skill.name(), filePath);
            }

            skills.put(skill.name(), skill);
            log.info("注册文件 Skill: name={}, file={}", skill.name(), filePath);

        } catch (Exception e) {
            log.error("加载 Skill 文件失败: {}", filePath, e);
        }
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public boolean has(String name) {
        return skills.containsKey(name);
    }

    public Map<String, Skill> getAll() {
        return Collections.unmodifiableMap(skills);
    }

    /**
     * 为指定的 Skill 列表构建组合提示词
     */
    public String buildCombinedPrompt(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String name : skillNames) {
            Skill skill = skills.get(name);
            if (skill != null) {
                String addon = skill.getSystemPromptAddon();
                if (addon != null && !addon.isBlank()) {
                    sb.append("\n\n").append(addon);
                }
            } else {
                log.warn("Skill 未找到: {}", name);
            }
        }
        return sb.toString();
    }

    /**
     * 获取指定 Skill 列表依赖的所有 Tool 名称
     */
    public List<String> getRequiredTools(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }

        Set<String> tools = new LinkedHashSet<>();
        for (String name : skillNames) {
            Skill skill = skills.get(name);
            if (skill != null) {
                tools.addAll(skill.requiredTools());
            }
        }
        return List.copyOf(tools);
    }

    /**
     * 热重载：重新加载指定目录的所有文件 Skill
     * <p>
     * 可用于运行时动态更新 Skill，无需重启应用。
     */
    public void reloadFromFile(String directoryPath) {
        // 移除所有文件 Skill
        skills.entrySet().removeIf(entry -> entry.getValue() instanceof FileBasedSkill);

        // 重新加载
        loadSkillsFromDirectory(directoryPath);

        log.info("Skill 热重载完成，当前共 {} 个 Skill", skills.size());
    }
}
