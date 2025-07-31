package com.jiwu.aiseo.siteclone.utils;

import java.net.URI;
import java.net.URISyntaxException;

import com.jiwu.aiseo.siteclone.config.SiteCloneProperties;
import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.model.CloneTask;

import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.Site;

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
            
            if (!fileDomain.equals(siteDomain)) {
                log.debug("跳过非镜像域名文件: {}", url);
                return false;
            }
            
            // 检查文件是否已存在
            java.io.File file = new java.io.File(localPath);
            if (file.exists()) {
                log.debug("文件已存在，跳过: {}", localPath);
                return true;
            }
            
            // 确保目录存在
            java.io.File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 下载文件
            byte[] content = downloader.download(url, site);
            if (content == null || content.length == 0) {
                log.warn("下载文件失败: {} 内容为空", url);
                return false;
            }
            
            // 写入文件
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(content);
                fos.flush();
            }
            
            log.info("成功下载文件: {} (大小: {} 字节)", localPath, content.length);
            return true;
        } catch (Exception e) {
            log.error("处理资源URL时发生意外错误 {}: {}", url, e.getMessage());
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