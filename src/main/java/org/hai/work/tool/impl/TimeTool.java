package org.hai.work.tool.impl;

import lombok.extern.slf4j.Slf4j;
import org.hai.work.config.AgentTool;
import org.springframework.ai.tool.annotation.Tool;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@AgentTool
public class TimeTool {

    @Tool(description = "获取当前最新的时间")
    public String now() {
        log.info("TimeTool 执行，获取当前最新时间");
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
