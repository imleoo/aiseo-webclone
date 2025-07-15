package com.jiwu.aiseo.siteclone.utils;

import java.util.regex.Pattern;

/**
 * SecurityUtils测试示例 - 验证路径遍历防护
 */
public class SecurityUtilsTest {
    
    // 复制SecurityUtils中的模式
    private static final Pattern DANGEROUS_PATH_PATTERN = Pattern.compile(".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]|[\\\\/]\\.\\.$|^\\.\\.$|^\\.\\./|^\\.\\.\\\\).*");
    
    public static void main(String[] args) {
        testPathTraversalDetection();
        testPathSanitization();
    }
    
    private static void testPathTraversalDetection() {
        System.out.println("=== 路径遍历检测测试 ===");
        
        String[] testPaths = {
            "normal/file.html",           // 安全路径
            "../../../etc/passwd",        // 经典路径遍历
            "..\\..\\windows\\system32",  // Windows风格路径遍历
            "./test/../../../secret",     // 混合路径遍历
            "test/../../etc/passwd",      // 部分路径遍历
            "./../etc/hosts",            // 简单路径遍历
            "test/./../../etc/shadow",   // 复杂路径遍历
            "normal/../../../etc/passwd", // 伪装的路径遍历
            "",                          // 空路径
            "normal_file.txt",           // 正常文件
            "../",                       // 单独的../
            "..",                        // 单独的..
            "../../",                    // 连续的../
            "dir/../file.txt",          // 中间有../的路径
            "file..txt"                 // 文件名中有..但不是路径遍历
        };
        
        for (String testPath : testPaths) {
            boolean isDangerous = DANGEROUS_PATH_PATTERN.matcher(testPath).matches();
            System.out.println((isDangerous ? "🚫 危险" : "✅ 安全") + ": " + testPath);
        }
    }
    
    private static void testPathSanitization() {
        System.out.println("\n=== 路径清理测试 ===");
        
        String[] testPaths = {
            "../../../etc/passwd",
            "test/../../secret",
            "./normal/../file.txt",
            "dir/../file.txt",
            "normal/file.txt"
        };
        
        for (String path : testPaths) {
            String cleaned = sanitizePath(path);
            System.out.println("原始: " + path + " -> 清理后: " + cleaned);
        }
    }
    
    // 模拟SecurityUtils中的路径清理逻辑
    private static String sanitizePath(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return "index.html";
        }
        
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
        
        if (cleaned.isEmpty()) {
            return "index.html";
        }
        
        return cleaned;
    }
}