package com.jiwu.aiseo.siteclone.utils;

import java.util.Arrays;
import java.util.List;

/**
 * 测试新的网站路径映射器
 */
public class WebsitePathMapperTest {
    
    public static void main(String[] args) {
        testPathMapping();
        testRelativePathCalculation();
    }
    
    private static void testPathMapping() {
        System.out.println("=== 网站路径映射测试 ===");
        
        String baseDir = "/tmp/website";
        String domain = "example.com";
        WebsitePathMapper mapper = new WebsitePathMapper(baseDir, domain);
        
        // 测试各种URL类型
        List<String> testUrls = Arrays.asList(
            "https://example.com/",                           // 根路径
            "https://example.com/index.html",                 // 主页
            "https://example.com/about.html",                 // 简单页面
            "https://example.com/images/logo.png",            // 图片
            "https://example.com/css/style.css",              // CSS文件
            "https://example.com/js/script.js",               // JS文件
            "https://example.com/products/",                  // 目录
            "https://example.com/products/item1.html",        // 产品页面
            "https://example.com/search?q=test",              // 带查询参数
            "https://example.com/category/sub/item.html",     // 多级目录
            "https://example.com/../admin/secret.txt",        // 路径遍历尝试
            "https://example.com/../../etc/passwd",           // 更严重的路径遍历
            "https://example.com/normal/../images/pic.jpg"    // 混合路径
        );
        
        for (String url : testUrls) {
            try {
                WebsitePathMapper.PathMappingResult result = mapper.mapUrlToLocalPath(url);
                System.out.println("URL: " + url);
                System.out.println("  -> 本地路径: " + result.getLocalPath());
                System.out.println("  -> 相对路径: " + result.getRelativePath());
                System.out.println("  -> 是否重定位: " + (result.wasRelocated() ? "是" : "否"));
                System.out.println();
            } catch (Exception e) {
                System.out.println("❌ 错误处理 " + url + ": " + e.getMessage());
            }
        }
    }
    
    private static void testRelativePathCalculation() {
        System.out.println("=== 相对路径计算测试 ===");
        
        String baseDir = "/tmp/website";
        String domain = "example.com";
        WebsitePathMapper mapper = new WebsitePathMapper(baseDir, domain);
        
        // 测试相对路径计算
        String[][] pathPairs = {
            {"/tmp/website/index.html", "/tmp/website/style.css"},           // 同级文件
            {"/tmp/website/pages/about.html", "/tmp/website/images/logo.png"}, // 跨目录
            {"/tmp/website/products/item1.html", "/tmp/website/css/style.css"}, // 子目录到父目录
            {"/tmp/website/index.html", "/tmp/website/products/item1.html"},  // 父目录到子目录
            {"/tmp/website/cat1/sub1/page.html", "/tmp/website/cat2/sub2/other.html"} // 跨分支
        };
        
        for (String[] pair : pathPairs) {
            String fromPath = pair[0];
            String toPath = pair[1];
            String relativePath = mapper.calculateRelativePath(fromPath, toPath);
            
            System.out.println("从: " + fromPath);
            System.out.println("到: " + toPath);
            System.out.println("相对路径: " + relativePath);
            System.out.println();
        }
    }
}