package org.hai.work.tool.impl;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.config.AgentTool;
import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@AgentTool
public class TimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Tool(description = "获取当前最新的时间")
    public String now() {
        log.info("TimeTool 执行，获取当前最新时间");
        return LocalDateTime.now().format(FORMATTER);
    }
}
