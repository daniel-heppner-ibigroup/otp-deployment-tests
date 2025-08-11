package com.arcadis.otpsmoketests.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration for task scheduling infrastructure.
 * Provides the TaskScheduler bean required by ScheduleManager.
 */
@Configuration
public class SchedulingConfiguration {

  /**
   * Creates a ThreadPoolTaskScheduler for managing scheduled test suite executions.
   *
   * @return A configured TaskScheduler instance
   */
  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(10); // Allow up to 10 concurrent test executions
    scheduler.setThreadNamePrefix("test-scheduler-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(30);
    return scheduler;
  }
}
