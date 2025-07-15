package com.jiwu.aiseo.siteclone.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 安全工具类，用于验证URL和路径的安全性
 */
@Slf4j
public class SecurityUtils {

    // 允许的域名字符模式（防止路径遍历攻击）
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+$");
    
    // 危险的路径字符模式 - 更严格的检查
    private static final Pattern DANGEROUS_PATH_PATTERN = Pattern.compile(".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]|[\\\\/]\\.\\.$|^\\.\\.$|^\\.\\./|^\\.\\.\\\\).*");
    
    // 支持的URL协议
    private static final Pattern ALLOWED_PROTOCOLS = Pattern.compile("^https?$");

    /**
     * 验证域名是否安全（防止路径遍历攻击）
     * 
     * @param domain 域名
     * @return 是否安全
     */
    public static boolean isDomainSafe(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            log.warn("域名为空");
            return false;
        }

        // 检查域名格式
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            log.warn("域名包含非法字符: {}", domain);
            return false;
        }

        // 检查是否包含路径遍历字符
        if (DANGEROUS_PATH_PATTERN.matcher(domain).matches()) {
            log.warn("域名包含危险路径字符: {}", domain);
            return false;
        }

        // 检查域名长度
        if (domain.length() > 253) {
            log.warn("域名长度超过限制: {}", domain);
            return false;
        }

        return true;
    }

    /**
     * 验证URL是否安全
     * 
     * @param url URL字符串
     * @return 是否安全
     */
    public static boolean isUrlSafe(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("URL为空");
            return false;
        }

        try {
            URI uri = new URI(url);
            
            // 检查协议
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_PROTOCOLS.matcher(scheme.toLowerCase()).matches()) {
                log.warn("不支持的URL协议: {}", scheme);
                return false;
            }

            // 检查主机名
            String host = uri.getHost();
            if (host == null || !isDomainSafe(host)) {
                log.warn("不安全的主机名: {}", host);
                return false;
            }

            // 检查端口
            int port = uri.getPort();
            if (port != -1 && (port < 1 || port > 65535)) {
                log.warn("无效的端口号: {}", port);
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            log.warn("URL格式无效: {}", url, e);
            return false;
        }
    }

    /**
     * 创建安全的输出路径
     * 
     * @param baseDir 基础目录
     * @param subDir 子目录
     * @param domain 域名
     * @return 安全的路径
     * @throws SecurityException 如果路径不安全
     */
    public static Path createSafeOutputPath(String baseDir, String subDir, String domain) {
        if (!isDomainSafe(domain)) {
            throw new SecurityException("不安全的域名: " + domain);
        }

        try {
            // 规范化域名，移除特殊字符
            String safeDomain = domain.replaceAll("[^a-zA-Z0-9.-]", "_");
            
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path outputPath = basePath.resolve(subDir).resolve(safeDomain).normalize();
            
            // 确保输出路径在基础目录内
            if (!outputPath.startsWith(basePath)) {
                throw new SecurityException("尝试访问基础目录外的路径: " + outputPath);
            }
            
            return outputPath;
        } catch (Exception e) {
            throw new SecurityException("创建安全路径失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证文件路径是否安全
     * 
     * @param filePath 文件路径
     * @param baseDir 基础目录
     * @return 是否安全
     */
    public static boolean isFilePathSafe(String filePath, String baseDir) {
        try {
            Path file = Paths.get(filePath).toAbsolutePath().normalize();
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            
            return file.startsWith(base);
        } catch (Exception e) {
            log.warn("路径验证失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 创建安全的文件保存路径 - 保留合法的相对路径结构
     * 
     * @param baseDir 基础目录
     * @param relativePath 相对路径
     * @return 安全的绝对路径
     * @throws SecurityException 如果路径不安全
     */
    public static Path createSafeFilePath(String baseDir, String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new SecurityException("文件路径为空");
        }

        try {
            // 先规范化路径，但保留合法的目录结构
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path fullPath = basePath.resolve(relativePath).normalize();
            
            // 确保解析后的路径仍在基础目录内
            if (!fullPath.startsWith(basePath)) {
                // 如果路径超出基础目录，我们需要重新映射它
                System.err.println("路径超出基础目录，重新映射: " + relativePath + " -> " + fullPath);
                
                // 创建一个安全的替代路径
                String safeName = createSafeFileName(relativePath);
                fullPath = basePath.resolve("safe_files").resolve(safeName).normalize();
                
                // 再次检查
                if (!fullPath.startsWith(basePath)) {
                    throw new SecurityException("无法创建安全路径: " + relativePath);
                }
            }
            
            return fullPath;
        } catch (Exception e) {
            throw new SecurityException("创建安全文件路径失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为危险路径创建安全的文件名
     */
    private static String createSafeFileName(String originalPath) {
        // 提取文件名和扩展名
        String fileName = originalPath;
        String extension = "";
        
        int lastSlash = Math.max(originalPath.lastIndexOf('/'), originalPath.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            fileName = originalPath.substring(lastSlash + 1);
        }
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot);
            fileName = fileName.substring(0, lastDot);
        }
        
        // 清理文件名
        fileName = fileName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (fileName.length() > 50) {
            fileName = fileName.substring(0, 50);
        }
        
        // 添加哈希避免冲突
        int hash = Math.abs(originalPath.hashCode());
        return fileName + "_" + hash + extension;
    }

    /**
     * 检查路径是否为明显的攻击路径
     */
    public static boolean isObviousAttackPath(String path) {
        if (path == null) return false;
        
        // 检查是否包含过多的../
        long dotDotCount = path.chars().filter(ch -> ch == '.').count() / 2;
        if (dotDotCount > 5) { // 超过5个../认为是攻击
            return true;
        }
        
        // 检查是否尝试访问系统目录
        String lowerPath = path.toLowerCase();
        return lowerPath.contains("/etc/") || 
               lowerPath.contains("\\windows\\") ||
               lowerPath.contains("/sys/") ||
               lowerPath.contains("/proc/") ||
               lowerPath.contains("system32");
    }

    /**
     * 清理相对路径，移除危险字符
     * 
     * @param relativePath 原始相对路径
     * @return 清理后的路径
     */
    private static String sanitizeRelativePath(String relativePath) {
        // 移除开头的斜杠
        String cleaned = relativePath.replaceFirst("^[/\\\\]+", "");
        
        // 移除所有 ../ 和 ..\\ 序列
        cleaned = cleaned.replaceAll("\\.\\.[\\\\/]", "");
        cleaned = cleaned.replaceAll("[\\\\/]\\.\\.[\\\\/]", "/");
        cleaned = cleaned.replaceAll("[\\\\/]\\.\\.$", "");
        
        // 移除 ./ 和 .\\ 序列
        cleaned = cleaned.replaceAll("\\./", "");
        cleaned = cleaned.replaceAll("\\.\\\\", "");
        
        // 规范化多个连续斜杠
        cleaned = cleaned.replaceAll("[\\\\/]+", "/");
        
        // 移除空白字符
        cleaned = cleaned.trim();
        
        return cleaned;
    }

    /**
     * 验证参数范围
     * 
     * @param value 参数值
     * @param min 最小值
     * @param max 最大值
     * @param paramName 参数名称
     * @return 是否在有效范围内
     */
    public static boolean isParameterInRange(int value, int min, int max, String paramName) {
        if (value < min || value > max) {
            log.warn("参数 {} 超出范围: {}, 有效范围: [{}, {}]", paramName, value, min, max);
            return false;
        }
        return true;
    }

    /**
     * 验证文件大小是否超过限制
     * 
     * @param fileSize 文件大小
     * @param maxSize 最大允许大小
     * @return 是否在限制内
     */
    public static boolean isFileSizeAllowed(long fileSize, long maxSize) {
        if (fileSize > maxSize) {
            log.warn("文件大小 {} 超过限制: {}", fileSize, maxSize);
            return false;
        }
        return true;
    }

    /**
     * 检查总下载大小是否超过限制
     * 
     * @param currentSize 当前已下载大小
     * @param newFileSize 新文件大小
     * @param maxTotalSize 最大总大小限制
     * @return 是否允许下载
     */
    public static boolean isTotalSizeAllowed(long currentSize, long newFileSize, long maxTotalSize) {
        long newTotal = currentSize + newFileSize;
        if (newTotal > maxTotalSize) {
            log.warn("总下载大小 {} 将超过限制: {}", newTotal, maxTotalSize);
            return false;
        }
        return true;
    }
}