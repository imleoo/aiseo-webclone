package com.jiwu.aiseo.siteclone.utils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.jiwu.aiseo.siteclone.config.SiteCloneProperties;
import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.model.CloneTask;

import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Task;

/**
 * Web资源下载器 - 负责下载网站资源
 */
@Slf4j
public class WebResourceDownloader implements ResourceProcessor.ResourceDownloader {

    private final Site site;
    private final CloneTask cloneTask;
    private final SiteCloneProperties properties;
    private final CustomHttpClientDownloader downloader;

    public WebResourceDownloader(Site site, CloneTask cloneTask, SiteCloneProperties properties) {
        this.site = site;
        this.cloneTask = cloneTask;
        this.properties = properties;
        this.downloader = new CustomHttpClientDownloader();
    }

    @Override
    public boolean downloadResource(String url, String localPath) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            // 检查域名是否匹配
            String fileDomain = extractDomain(url);
            String siteDomain = site.getDomain();
            if (fileDomain.isEmpty() || !fileDomain.equals(siteDomain)) {
                log.debug("跳过非镜像域名文件: {}", url);
                return false;
            }

            // 规范化保存路径
            Path outputPath = Paths.get(localPath).normalize();

            // 如果文件已存在，跳过下载
            if (Files.exists(outputPath)) {
                log.debug("文件已存在，跳过: {}", outputPath);
                return true;
            }

            // 创建所有必要的父目录
            Files.createDirectories(outputPath.getParent());

            // 使用CustomHttpClientDownloader下载文件
            try {
                Page page = downloader.download(new Request(url), new Task() {
                    @Override
                    public String getUUID() {
                        return url;
                    }

                    @Override
                    public Site getSite() {
                        return site;
                    }
                });

                if (page.getStatusCode() == 200) {
                    byte[] content = page.getRawText().getBytes();
                    long fileSize = content.length;
                    
                    // 检查文件大小限制
                    if (!SecurityUtils.isFileSizeAllowed(fileSize, properties.getDownload().getMaxFileSize())) {
                        log.warn("文件大小 {} 超过限制: {}", fileSize, url);
                        return false;
                    }
                    
                    // 检查总下载大小限制
                    if (!SecurityUtils.isTotalSizeAllowed(cloneTask.getTotalBytesDownloaded(), fileSize, properties.getDownload().getMaxTotalSize())) {
                        log.warn("总下载大小将超过限制: {}", cloneTask.getId());
                        return false;
                    }
                    
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                        fileOutputStream.write(content);
                        log.info("成功下载文件: {} (大小: {} 字节)", outputPath, fileSize);

                        // 增加文件下载计数和字节数
                        synchronized (cloneTask) {
                            cloneTask.incrementFilesDownloaded();
                            cloneTask.addBytesDownloaded(fileSize);
                        }
                        return true;
                    } catch (FileNotFoundException e) {
                        log.error("输出目录未找到: {}: {}", url, e.getMessage());
                    } catch (SecurityException e) {
                        log.error("安全异常写入文件 {}: {}", url, e.getMessage());
                    } catch (IOException e) {
                        log.error("IO错误写入文件 {}: {}", url, e.getMessage());
                        // 删除可能损坏的文件
                        Files.deleteIfExists(outputPath);
                    }
                } else {
                    log.warn("下载文件失败: {} 状态码: {}", url, page.getStatusCode());
                }
            } catch (IOException e) {
                log.error("下载文件错误 {}: {}", url, e.getMessage());
            }
        } catch (Exception e) {
            log.error("下载资源时发生意外错误 {}: {}", url, e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 提取URL的域名
     * 
     * @param url URL
     * @return 域名
     */
    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "" : uri.getHost().startsWith("www.") ? uri.getHost().substring(4) : uri.getHost();
        } catch (URISyntaxException e) {
            log.error("无效的URL格式: {}", url, e);
            return "";
        }
    }
}