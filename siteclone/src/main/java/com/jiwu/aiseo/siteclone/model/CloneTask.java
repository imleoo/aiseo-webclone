package com.jiwu.aiseo.siteclone.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CloneTask {
    private String id;
    private String url;
    private String outputDir;
    private CloneTaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String errorMessage;
    private int pagesCrawled;
    private int filesDownloaded;
    private long totalBytesDownloaded; // 新增：总下载字节数

    public CloneTask(String url, String outputDir) {
        this.id = UUID.randomUUID().toString();
        this.url = url;
        this.outputDir = outputDir;
        this.status = CloneTaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.pagesCrawled = 0;
        this.filesDownloaded = 0;
        this.totalBytesDownloaded = 0L; // 初始化为0
    }

    public void setRunning() {
        this.status = CloneTaskStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCompleted() {
        this.status = CloneTaskStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void setFailed(String errorMessage) {
        this.status = CloneTaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementPagesCrawled() {
        this.pagesCrawled++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementFilesDownloaded() {
        this.filesDownloaded++;
        this.updatedAt = LocalDateTime.now();
    }

    public void addBytesDownloaded(long bytes) {
        this.totalBytesDownloaded += bytes;
        this.updatedAt = LocalDateTime.now();
    }

    public long getTotalBytesDownloaded() {
        return this.totalBytesDownloaded;
    }
}
