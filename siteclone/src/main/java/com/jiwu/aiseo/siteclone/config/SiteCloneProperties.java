package com.jiwu.aiseo.siteclone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 站点克隆配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "siteclone")
public class SiteCloneProperties {

    private Download download = new Download();
    private Crawler crawler = new Crawler();
    private Security security = new Security();
    private Task task = new Task();

    @Data
    public static class Download {
        private String baseDir;
        private String subDir;
        private long maxFileSize = 52428800L; // 50MB
        private long maxTotalSize = 1073741824L; // 1GB
    }

    @Data
    public static class Crawler {
        private int timeout = 30000; // 30秒
        private int pollingInterval = 2000; // 2秒
        private int maxConcurrentDownloads = 10;
        private String userAgent = "Mozilla/5.0 (compatible; SiteClone/1.0; +https://github.com/aiseo)";
    }

    @Data
    public static class Security {
        private int maxThreadCount = 20;
        private int maxRetryTimes = 10;
        private int minSleepTime = 100;
        private int maxSleepTime = 10000;
    }

    @Data
    public static class Task {
        private long cleanupInterval = 3600000L; // 1小时
        private long maxTaskAge = 86400000L; // 24小时
    }
}