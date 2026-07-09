package org.hai.work.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度配置
 * <p>
 * 启用 Spring 的 @EnableScheduling 支持，并配置 TaskScheduler 线程池。
 * TaskScheduler 用于动态创建和管理定时任务（与 @Scheduled 固定注解不同，
 * 这里支持运行时按需创建/取消任务）。
 */
@Slf4j
@Configuration
@EnableScheduling
public class TaskSchedulerConfig {

    /**
     * 配置 TaskScheduler 线程池
     * <p>
     * 线程池大小设为 5，可同时执行 5 个定时任务。
     * 线程名前缀 "schedule-agent-" 方便日志排查。
     *
     * @return TaskScheduler 实例
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("schedule-agent-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        log.info("TaskScheduler 初始化完成，线程池大小: 5");
        return scheduler;
    }
}
