package org.hai.work.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.hai.work.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 命令执行工具
 * <p>
 * 支持在不同操作系统上执行命令：
 * - Windows: 使用 cmd /c 执行
 * - Linux/Mac: 使用 sh -c 执行
 * <p>
 * 安全限制：
 * - 命令超时时间限制为 30 秒
 * - 禁止执行危险命令（rm -rf, format, del /f 等）
 * - 输出长度限制为 10000 字符
 */
@Slf4j
@Component
public class CommandTool implements Tool {

    private static final long TIMEOUT_MS = 30_000L;
    private static final int MAX_OUTPUT_LENGTH = 10_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "execute_command";
    }

    @Override
    public String description() {
        return """
                执行系统命令。根据操作系统自动选择合适的命令解释器。
                适用场景：
                - 查看系统信息（如 ipconfig, uname -a）
                - 列出文件目录（如 dir, ls）
                - 查看进程（如 tasklist, ps aux）
                - 网络诊断（如 ping, tracert）
                - 文件操作（如 cat, type）
                注意：禁止执行危险命令（如删除系统文件、格式化磁盘等）。
                """;
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "command": {
                      "type": "string",
                      "description": "要执行的命令，如 'ipconfig' 或 'ls -la'"
                    }
                  },
                  "required": ["command"]
                }
                """;
    }

    @Override
    public String execute(String args) {
        log.info("CommandTool 开始执行");

        try {
            JsonNode root = objectMapper.readTree(args);
            String command = root.get("command").asText();

            if (command == null || command.isBlank()) {
                return "错误：command 参数不能为空";
            }

            // 安全检查
            String safetyCheck = checkCommandSafety(command);
            if (safetyCheck != null) {
                return safetyCheck;
            }

            log.info("执行命令: {}", command);
            return executeCommand(command);

        } catch (Exception e) {
            log.error("CommandTool 执行异常", e);
            return "命令执行失败: " + e.getMessage();
        }
    }

    /**
     * 根据操作系统执行命令
     */
    private String executeCommand(String command) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

        // 构建命令行
        CommandLine cmdLine;
        if (isWindows) {
            cmdLine = CommandLine.parse("cmd");
            cmdLine.addArgument("/c");
            cmdLine.addArgument(command, false);
        } else {
            cmdLine = CommandLine.parse("sh");
            cmdLine.addArgument("-c");
            cmdLine.addArgument(command, false);
        }

        // 设置执行器
        DefaultExecutor executor = DefaultExecutor.builder().get();
        executor.setExitValues(new int[]{0, 1, 2}); // 允许的退出码

        // 设置超时
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofMillis(TIMEOUT_MS)).get();
        executor.setWatchdog(watchdog);

        // 捕获输出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);

        try {
            int exitCode = executor.execute(cmdLine);

            String stdout = outputStream.toString(StandardCharsets.UTF_8);
            String stderr = errorStream.toString(StandardCharsets.UTF_8);

            // 截断过长输出
            if (stdout.length() > MAX_OUTPUT_LENGTH) {
                stdout = stdout.substring(0, MAX_OUTPUT_LENGTH) + "\n... (输出已截断)";
            }
            if (stderr.length() > MAX_OUTPUT_LENGTH) {
                stderr = stderr.substring(0, MAX_OUTPUT_LENGTH) + "\n... (输出已截断)";
            }

            StringBuilder result = new StringBuilder();
            result.append("【命令执行完成】\n");
            result.append("命令: ").append(command).append("\n");
            result.append("操作系统: ").append(System.getProperty("os.name")).append("\n");
            result.append("退出码: ").append(exitCode).append("\n\n");

            if (!stdout.isEmpty()) {
                result.append("【标准输出】\n").append(stdout).append("\n");
            }
            if (!stderr.isEmpty()) {
                result.append("【错误输出】\n").append(stderr).append("\n");
            }

            if (stdout.isEmpty() && stderr.isEmpty()) {
                result.append("(无输出)\n");
            }

            return result.toString();

        } catch (IOException e) {
            if (watchdog.killedProcess()) {
                return "命令执行超时（" + TIMEOUT_MS / 1000 + "秒）: " + command;
            }
            throw e;
        }
    }

    /**
     * 命令安全检查
     *
     * @return 错误信息，如果安全返回 null
     */
    private String checkCommandSafety(String command) {
        String lower = command.toLowerCase().trim();

        // 危险命令黑名单
        String[] dangerousPatterns = {
                "rm -rf /", "rm -rf /*",
                "format ", "format c:",
                "del /f /s /q", "del /f /s",
                "rd /s /q",
                "mkfs.",
                "dd if=",
                ":(){ :|:& };:",  // fork bomb
                "chmod -r 777 /",
                "> /dev/sda",
                "shutdown -h now",
                "init 0",
                "systemctl stop",
        };

        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern)) {
                log.warn("拒绝执行危险命令: {}", command);
                return "安全警告：拒绝执行危险命令 '" + pattern + "'";
            }
        }

        return null;
    }
}
