package com.jiwu.aiseo.siteclone.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 网站路径映射器 - 负责维护网站文件的正确相对路径关系
 */
@Slf4j
public class WebsitePathMapper {
    
    private final String baseOutputDir;
    private final String siteDomain;
    private final Map<String, String> urlToLocalPathMap = new HashMap<>();
    private final Map<String, String> localPathToRelativeMap = new HashMap<>();
    
    public WebsitePathMapper(String baseOutputDir, String siteDomain) {
        this.baseOutputDir = baseOutputDir;
        this.siteDomain = siteDomain;
    }
    
    /**
     * 将URL映射到本地文件路径
     * 
     * @param url 原始URL
     * @return 本地文件路径映射结果
     */
    public PathMappingResult mapUrlToLocalPath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            
            // 如果已经映射过，直接返回
            if (urlToLocalPathMap.containsKey(url)) {
                String localPath = urlToLocalPathMap.get(url);
                String relativePath = localPathToRelativeMap.get(localPath);
                return new PathMappingResult(localPath, relativePath, false);
            }
            
            // 检查是否为明显的攻击路径
            if (SecurityUtils.isObviousAttackPath(path)) {
                log.warn("检测到可疑路径，使用安全模式: {}", url);
                return createSafeMappingForSuspiciousPath(url);
            }
            
            // 创建保持目录结构的路径映射
            String relativePath = createRelativePathPreservingStructure(uri);
            Path safePath = SecurityUtils.createSafeFilePath(baseOutputDir, relativePath);
            
            // 如果路径安全，保存映射关系
            String localPath = safePath.toString();
            urlToLocalPathMap.put(url, localPath);
            localPathToRelativeMap.put(localPath, relativePath);
            
            return new PathMappingResult(localPath, relativePath, false);
            
        } catch (Exception e) {
            log.warn("URL路径映射失败，使用安全模式: {} - {}", url, e.getMessage());
            return createSafeMappingForSuspiciousPath(url);
        }
    }
    
    /**
     * 创建保持目录结构的相对路径
     */
    private String createRelativePathPreservingStructure(URI uri) {
        String path = uri.getPath();
        
        // 处理根路径
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "index.html";
        }
        
        // 移除开头的斜杠
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        // 处理目录路径
        if (path.endsWith("/")) {
            path += "index.html";
        }
        
        // 如果没有扩展名，可能是一个目录或动态页面
        if (!path.contains(".") || path.endsWith("/")) {
            // 检查是否已经以index.html结尾
            if (!path.endsWith("index.html")) {
                path += "/index.html";
            }
        }
        
        // 处理查询参数
        String query = uri.getQuery();
        if (query != null && !query.isEmpty()) {
            // 将查询参数转换为文件名的一部分
            String safeQuery = query.replaceAll("[^a-zA-Z0-9]", "_");
            if (safeQuery.length() > 30) {
                safeQuery = safeQuery.substring(0, 30);
            }
            
            // 在扩展名前插入查询参数
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0) {
                path = path.substring(0, lastDot) + "_" + safeQuery + path.substring(lastDot);
            } else {
                path += "_" + safeQuery + ".html";
            }
        }
        
        return path;
    }
    
    /**
     * 为可疑路径创建安全映射
     */
    private PathMappingResult createSafeMappingForSuspiciousPath(String url) {
        try {
            URI uri = new URI(url);
            String fileName = extractFileNameFromUri(uri);
            String safeRelativePath = "safe_files/" + fileName;
            Path safePath = SecurityUtils.createSafeFilePath(baseOutputDir, safeRelativePath);
            
            String localPath = safePath.toString();
            urlToLocalPathMap.put(url, localPath);
            localPathToRelativeMap.put(localPath, safeRelativePath);
            
            return new PathMappingResult(localPath, safeRelativePath, true);
        } catch (Exception e) {
            // 最后的fallback
            String fallbackName = "safe_file_" + Math.abs(url.hashCode()) + ".html";
            String fallbackRelative = "safe_files/" + fallbackName;
            try {
                Path safePath = SecurityUtils.createSafeFilePath(baseOutputDir, fallbackRelative);
                return new PathMappingResult(safePath.toString(), fallbackRelative, true);
            } catch (Exception ex) {
                throw new RuntimeException("无法创建安全路径映射", ex);
            }
        }
    }
    
    /**
     * 从URI中提取安全的文件名
     */
    private String extractFileNameFromUri(URI uri) {
        String path = uri.getPath();
        String fileName = "unknown";
        
        if (path != null && !path.isEmpty()) {
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                fileName = path.substring(lastSlash + 1);
            } else if (!path.equals("/")) {
                fileName = path.replaceFirst("^/", "");
            }
        }
        
        // 清理文件名
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (fileName.length() > 50) {
            fileName = fileName.substring(0, 50);
        }
        
        // 确保有扩展名
        if (!fileName.contains(".")) {
            fileName += ".html";
        }
        
        // 添加哈希避免冲突
        int hash = Math.abs(uri.toString().hashCode());
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot) + "_" + hash + fileName.substring(lastDot);
        } else {
            fileName += "_" + hash;
        }
        
        return fileName;
    }
    
    /**
     * 计算两个文件之间的相对路径
     * 
     * @param fromPath 源文件路径
     * @param toPath 目标文件路径
     * @return 相对路径
     */
    public String calculateRelativePath(String fromPath, String toPath) {
        try {
            Path from = Paths.get(fromPath).getParent(); // 获取源文件的目录
            Path to = Paths.get(toPath);
            
            if (from == null) {
                return toPath;
            }
            
            Path relativePath = from.relativize(to);
            return relativePath.toString().replace('\\', '/'); // 统一使用/分隔符
        } catch (Exception e) {
            log.warn("计算相对路径失败: {} -> {}", fromPath, toPath, e);
            return toPath; // fallback到绝对路径
        }
    }
    
    /**
     * 路径映射结果
     */
    public static class PathMappingResult {
        private final String localPath;
        private final String relativePath;
        private final boolean wasRelocated;
        
        public PathMappingResult(String localPath, String relativePath, boolean wasRelocated) {
            this.localPath = localPath;
            this.relativePath = relativePath;
            this.wasRelocated = wasRelocated;
        }
        
        public String getLocalPath() { return localPath; }
        public String getRelativePath() { return relativePath; }
        public boolean wasRelocated() { return wasRelocated; }
    }
}