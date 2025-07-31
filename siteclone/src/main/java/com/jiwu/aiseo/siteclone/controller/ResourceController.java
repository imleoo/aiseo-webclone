package com.jiwu.aiseo.siteclone.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.jiwu.aiseo.siteclone.config.SiteCloneProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 资源控制器 - 处理静态资源的API请求
 */
@Controller
@RequestMapping("/api/files")
@Slf4j
public class ResourceController {

    @Autowired
    private SiteCloneProperties properties;

    /**
     * 获取网站资源文件
     * 
     * @param domain 域名
     * @param path 文件路径
     * @return 资源文件
     */
    @GetMapping("/{domain}/**")
    public ResponseEntity<Resource> getResource(@PathVariable String domain) {
        try {
            // 获取请求路径
            String requestPath = getRequestPath(domain);
            if (requestPath == null) {
                return ResponseEntity.notFound().build();
            }

            // 构建文件路径
            String downloadDir = properties.getDownload().getBaseDir() + "/" + properties.getDownload().getSubDir();
            Path filePath = Paths.get(downloadDir, requestPath);
            File file = filePath.toFile();

            // 检查文件是否存在
            if (!file.exists() || !file.isFile()) {
                log.warn("请求的资源文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // 获取文件的MIME类型
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = determineContentType(filePath.toString());
            }

            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("获取资源文件时发生错误", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取请求路径
     * 
     * @param domain 域名
     * @return 请求路径
     */
    private String getRequestPath(String domain) {
        try {
            // 获取完整请求路径
            String requestURI = org.springframework.web.context.request.RequestContextHolder
                    .currentRequestAttributes()
                    .getAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping", 
                            org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST)
                    .toString();

            // 移除前缀 "/api/files/"
            String path = requestURI.substring(requestURI.indexOf(domain));
            return path;
        } catch (Exception e) {
            log.error("获取请求路径时发生错误", e);
            return null;
        }
    }

    /**
     * 根据文件扩展名确定内容类型
     * 
     * @param filePath 文件路径
     * @return 内容类型
     */
    private String determineContentType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            case "json":
                return "application/json";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "ico":
                return "image/x-icon";
            case "pdf":
                return "application/pdf";
            case "zip":
                return "application/zip";
            case "ttf":
                return "font/ttf";
            case "woff":
                return "font/woff";
            case "woff2":
                return "font/woff2";
            default:
                return "application/octet-stream";
        }
    }
}