package com.jiwu.aiseo.siteclone.service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jiwu.aiseo.siteclone.config.SiteCloneProperties;
import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.dto.CloneRequest;
import com.jiwu.aiseo.siteclone.dto.CloneResponse;
import com.jiwu.aiseo.siteclone.model.CloneTask;
import com.jiwu.aiseo.siteclone.processor.WebsiteMirrorProcessor;
import com.jiwu.aiseo.siteclone.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.Spider;

@Service
@Slf4j
public class SiteCloneService {

    @Autowired
    private SiteCloneProperties properties;

    @Value("${siteclone.download.base-dir}")
    private String downloadBaseDir;

    @Value("${siteclone.download.sub-dir}")
    private String downloadSubDir;

    private final Map<String, CloneTask> tasks = new ConcurrentHashMap<>();

    public CloneResponse startClone(CloneRequest request) {
        try {
            // 验证输入参数
            if (!SecurityUtils.isUrlSafe(request.getUrl())) {
                throw new IllegalArgumentException("Invalid or unsafe URL: " + request.getUrl());
            }

            // 验证参数范围
            if (!SecurityUtils.isParameterInRange(request.getThreadCount(), 1, properties.getSecurity().getMaxThreadCount(), "threadCount")) {
                throw new IllegalArgumentException("Thread count must be between 1 and " + properties.getSecurity().getMaxThreadCount());
            }
            if (!SecurityUtils.isParameterInRange(request.getRetryTimes(), 0, properties.getSecurity().getMaxRetryTimes(), "retryTimes")) {
                throw new IllegalArgumentException("Retry times must be between 0 and " + properties.getSecurity().getMaxRetryTimes());
            }
            if (!SecurityUtils.isParameterInRange(request.getSleepTime(), properties.getSecurity().getMinSleepTime(), properties.getSecurity().getMaxSleepTime(), "sleepTime")) {
                throw new IllegalArgumentException("Sleep time must be between " + properties.getSecurity().getMinSleepTime() + " and " + properties.getSecurity().getMaxSleepTime() + " ms");
            }

            // 解析URL获取域名
            URL url = URI.create(request.getUrl()).toURL();
            String domain = url.getHost();

            // 验证域名安全性
            if (!SecurityUtils.isDomainSafe(domain)) {
                throw new SecurityException("Unsafe domain: " + domain);
            }

            // 创建安全的输出目录
            Path outputPath = SecurityUtils.createSafeOutputPath(downloadBaseDir, downloadSubDir, domain);
            String outputDir = outputPath.toString();
            
            // 确保目录存在
            File dir = outputPath.toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Failed to create download directory: " + outputDir);
            }

            // 创建任务
            CloneTask task = new CloneTask(request.getUrl(), outputDir);
            tasks.put(task.getId(), task);

            // 异步执行克隆任务
            executeCloneTask(task, domain, request.getThreadCount(), request.getRetryTimes(), request.getSleepTime());

            // 返回响应
            return convertToResponse(task);
        } catch (IllegalArgumentException | SecurityException e) {
            log.error("Invalid input: {}", request.getUrl(), e);
            CloneTask task = new CloneTask(request.getUrl(), null);
            task.setFailed("Invalid input: " + e.getMessage());
            return convertToResponse(task);
        } catch (MalformedURLException e) {
            log.error("Malformed URL: {}", request.getUrl(), e);
            CloneTask task = new CloneTask(request.getUrl(), null);
            task.setFailed("Invalid URL format: " + e.getMessage());
            return convertToResponse(task);
        } catch (Exception e) {
            log.error("Unexpected error processing request: {}", request.getUrl(), e);
            CloneTask task = new CloneTask(request.getUrl(), null);
            task.setFailed("Internal error occurred");
            return convertToResponse(task);
        }
    }

    public CloneResponse getTaskStatus(String taskId) {
        CloneTask task = tasks.get(taskId);
        if (task == null) {
            CloneResponse response = new CloneResponse();
            response.setTaskId(taskId);
            response.setErrorMessage("Task not found");
            return response;
        }
        return convertToResponse(task);
    }

    @Async
    protected void executeCloneTask(CloneTask task, String domain, int threadCount, int retryTimes, int sleepTime) {
        task.setRunning();

        try {
            WebsiteMirrorProcessor processor = new WebsiteMirrorProcessor(
                domain, 
                retryTimes, 
                sleepTime, 
                task.getOutputDir(), 
                task,
                properties // 传递配置属性
            );

            Spider.create(processor)
                    .setDownloader(new CustomHttpClientDownloader())
                    .addUrl(task.getUrl())
                    .thread(threadCount)
                    .run();

            task.setCompleted();
            log.info("Clone task completed: {}", task.getId());
        } catch (Exception e) {
            task.setFailed(e.getMessage());
            log.error("Clone task failed: {}", task.getId(), e);
        }
    }

    /**
     * 定期清理过期任务
     */
    @Scheduled(fixedDelayString = "#{@siteCloneProperties.task.cleanupInterval}")
    public void cleanupExpiredTasks() {
        long currentTime = System.currentTimeMillis();
        long maxAge = properties.getTask().getMaxTaskAge();
        
        tasks.entrySet().removeIf(entry -> {
            CloneTask task = entry.getValue();
            long taskAge = currentTime - task.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            if (taskAge > maxAge) {
                log.info("Removing expired task: {} (age: {} ms)", entry.getKey(), taskAge);
                return true;
            }
            return false;
        });
        
        if (!tasks.isEmpty()) {
            log.debug("Task cleanup completed. Active tasks: {}", tasks.size());
        }
    }

    private CloneResponse convertToResponse(CloneTask task) {
        CloneResponse response = new CloneResponse();
        BeanUtils.copyProperties(task, response);
        response.setTaskId(task.getId()); // 显式设置taskId
        return response;
    }
}