package com.jiwu.aiseo.siteclone.dto;

import com.jiwu.aiseo.siteclone.model.CloneTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CloneResponse {
    private String taskId;
    private String url;
    private String outputDir;
    private CloneTaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String errorMessage;
    private int pagesCrawled;
    private int filesDownloaded;
}
