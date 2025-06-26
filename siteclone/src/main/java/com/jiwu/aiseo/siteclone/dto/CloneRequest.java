package com.jiwu.aiseo.siteclone.dto;

import lombok.Data;

@Data
public class CloneRequest {
    private String url;
    private int threadCount = 5;
    private int retryTimes = 3;
    private int sleepTime = 1000;
}
