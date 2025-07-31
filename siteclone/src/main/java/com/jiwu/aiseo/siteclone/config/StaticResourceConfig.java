package com.jiwu.aiseo.siteclone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.Data;

/**
 * 静态资源配置
 */
@Configuration
@ConfigurationProperties(prefix = "siteclone.static-resources")
@Data
public class StaticResourceConfig implements WebMvcConfigurer {
    
    /**
     * 是否保留原始URL而不是下载资源文件
     * 当设置为true时，将保留CSS、JS、图片等资源的原始URL，不会下载这些资源
     * 当设置为false时，将下载这些资源并替换为本地路径
     */
    private boolean preserveOriginalUrls = false;
    
    /**
     * 允许的外部资源域名列表
     * 当preserveOriginalUrls=false时，这些域名的资源仍然会保留原始URL
     */
    private String[] allowedExternalDomains = {};
    
    /**
     * 配置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加API路径的资源映射
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations("file:///Users/leoobai/aiseo-downloads/siteclone/");
    }
}
