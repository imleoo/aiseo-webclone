package com.jiwu.aiseo.siteclone.service;

import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.dto.CloneRequest;
import com.jiwu.aiseo.siteclone.dto.CloneResponse;
import com.jiwu.aiseo.siteclone.model.CloneTask;
import com.jiwu.aiseo.siteclone.processor.WebsiteMirrorProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Spider;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SiteCloneService {

    private final Map<String, CloneTask> tasks = new ConcurrentHashMap<>();

    public CloneResponse startClone(CloneRequest request) {
        try {
            // 解析URL获取域名
            URL url = new URL(request.getUrl());
            String domain = url.getHost();

            // 创建输出目录
            String outputDir = Paths.get(System.getProperty("java.io.tmpdir"), "siteclone", domain).toString();

            // 创建任务
            CloneTask task = new CloneTask(request.getUrl(), outputDir);
            tasks.put(task.getId(), task);

            // 异步执行克隆任务
            executeCloneTask(task, domain, request.getThreadCount(), request.getRetryTimes(), request.getSleepTime());

            // 返回响应
            return convertToResponse(task);
        } catch (MalformedURLException e) {
            log.error("Invalid URL: {}", request.getUrl(), e);
            CloneTask task = new CloneTask(request.getUrl(), null);
            task.setFailed("Invalid URL: " + e.getMessage());
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
            WebsiteMirrorProcessor processor = new WebsiteMirrorProcessor(domain, retryTimes, sleepTime, task.getOutputDir());

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

    private CloneResponse convertToResponse(CloneTask task) {
        CloneResponse response = new CloneResponse();
        BeanUtils.copyProperties(task, response);
        return response;
    }
}
