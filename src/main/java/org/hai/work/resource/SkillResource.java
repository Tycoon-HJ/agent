package org.hai.work.resource;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.skill.Skill;
import org.hai.work.skill.SkillRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Skill 管理接口
 * <p>
 * 提供 Skill 的查询、重载等管理功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillResource {

    private final SkillRegistry skillRegistry;
    private final String skillsDirectory;

    public SkillResource(SkillRegistry skillRegistry,
                         @Value("${agent.skills.directory:./skills}") String skillsDirectory) {
        this.skillRegistry = skillRegistry;
        this.skillsDirectory = skillsDirectory;
    }

    /**
     * 获取所有已注册的 Skill 列表
     */
    @GetMapping
    public List<Map<String, Object>> listSkills() {
        return skillRegistry.getAll().values().stream()
                .map(skill -> Map.<String, Object>of(
                        "name", skill.name(),
                        "description", skill.description(),
                        "requiredTools", skill.requiredTools()
                ))
                .toList();
    }

    /**
     * 获取指定 Skill 的详细信息
     */
    @GetMapping("/{name}")
    public Map<String, Object> getSkill(@PathVariable String name) {
        Skill skill = skillRegistry.get(name);
        if (skill == null) {
            return Map.of("error", "Skill not found: " + name);
        }
        return Map.of(
                "name", skill.name(),
                "description", skill.description(),
                "systemPromptAddon", skill.getSystemPromptAddon(),
                "requiredTools", skill.requiredTools()
        );
    }

    /**
     * 热重载 Skill（重新加载文件目录中的所有 Skill）
     */
    @PostMapping("/reload")
    public Map<String, Object> reloadSkills() {
        log.info("收到 Skill 热重载请求");
        skillRegistry.reloadFromFile(skillsDirectory);
        return Map.of(
                "status", "success",
                "message", "Skills reloaded from: " + skillsDirectory,
                "count", skillRegistry.getAll().size()
        );
    }
}
