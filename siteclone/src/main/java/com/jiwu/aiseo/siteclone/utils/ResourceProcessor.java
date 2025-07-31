package com.jiwu.aiseo.siteclone.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 资源处理器 - 负责处理各种资源文件中的URL引用
 */
@Slf4j
public class ResourceProcessor {

    private final WebsitePathMapper pathMapper;
    private final ResourceDownloader downloader;
    
    // 用于替换JS文件中的硬编码URL
    private final Map<String, String> jsUrlReplacements = new HashMap<>();
    
    // CSS中URL的正则表达式
    private static final Pattern CSS_URL_PATTERN = Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)");
    
    // JS中URL的正则表达式（简单匹配，可能需要根据实际情况调整）
    private static final Pattern JS_URL_PATTERN = Pattern.compile("(['\"])((https?://)[^'\"]+)\\1");
    
    // 内联样式中URL的正则表达式
    private static final Pattern INLINE_STYLE_URL_PATTERN = Pattern.compile("url\\(['\"]?([^'\")]+)['\"]?\\)");
    
    public ResourceProcessor(WebsitePathMapper pathMapper, ResourceDownloader downloader) {
        this.pathMapper = pathMapper;
        this.downloader = downloader;
    }
    
    /**
     * 添加JS URL替换规则
     * 
     * @param originalUrl 原始URL
     * @param replacementUrl 替换URL
     */
    public void addJsUrlReplacement(String originalUrl, String replacementUrl) {
        jsUrlReplacements.put(originalUrl, replacementUrl);
    }
    
    /**
     * 处理CSS文件中的URL引用
     * 
     * @param cssFilePath CSS文件路径
     * @param baseUrl 基础URL
     * @return 是否成功处理
     */
    public boolean processCssFile(String cssFilePath, String baseUrl) {
        try {
            Path path = Paths.get(cssFilePath);
            if (!Files.exists(path)) {
                log.warn("CSS文件不存在: {}", cssFilePath);
                return false;
            }
            
            String cssContent = new String(Files.readAllBytes(path));
            
            // 使用正则表达式查找CSS中的URL
            Matcher matcher = CSS_URL_PATTERN.matcher(cssContent);
            StringBuffer newCssContent = new StringBuffer();
            
            while (matcher.find()) {
                String originalUrl = matcher.group(1);
                
                // 跳过数据URI和空URL
                if (originalUrl.isEmpty() || originalUrl.startsWith("data:") || 
                    originalUrl.startsWith("#") || originalUrl.startsWith("javascript:")) {
                    matcher.appendReplacement(newCssContent, "url(" + originalUrl + ")");
                    continue;
                }
                
                try {
                    // 获取绝对URL
                    String absUrl = getAbsoluteUrl(baseUrl, originalUrl);
                    
                    // 使用路径映射器处理资源URL
                    WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(absUrl);
                    
                    // 下载资源
                    downloader.downloadResource(absUrl, resourceMapping.getLocalPath());
                    
                    // 计算CSS文件到资源的相对路径
                    String relativePathFromCss = pathMapper.calculateRelativePath(
                        cssFilePath, 
                        resourceMapping.getLocalPath()
                    );
                    
                    // 替换URL
                    matcher.appendReplacement(newCssContent, "url(" + relativePathFromCss + ")");
                    log.debug("CSS中URL已替换: {} -> {}", originalUrl, relativePathFromCss);
                } catch (Exception e) {
                    log.error("处理CSS中的URL失败: {}", originalUrl, e);
                    matcher.appendReplacement(newCssContent, "url(" + originalUrl + ")");
                }
            }
            matcher.appendTail(newCssContent);
            
            // 写回CSS文件
            Files.write(path, newCssContent.toString().getBytes());
            log.info("成功处理CSS文件: {}", cssFilePath);
            return true;
            
        } catch (Exception e) {
            log.error("处理CSS文件失败: {}", cssFilePath, e);
            return false;
        }
    }
    
    /**
     * 处理JavaScript文件中的硬编码URL
     * 
     * @param jsFilePath JS文件路径
     * @param baseUrl 基础URL
     * @return 是否成功处理
     */
    public boolean processJsFile(String jsFilePath, String baseUrl) {
        try {
            Path path = Paths.get(jsFilePath);
            if (!Files.exists(path)) {
                log.warn("JS文件不存在: {}", jsFilePath);
                return false;
            }
            
            String jsContent = new String(Files.readAllBytes(path));
            boolean modified = false;
            
            // 1. 应用配置的URL替换规则
            for (Map.Entry<String, String> replacement : jsUrlReplacements.entrySet()) {
                String originalContent = jsContent;
                jsContent = jsContent.replace(replacement.getKey(), replacement.getValue());
                if (!originalContent.equals(jsContent)) {
                    modified = true;
                    log.debug("JS中替换硬编码URL: {} -> {}", replacement.getKey(), replacement.getValue());
                }
            }
            
            // 2. 使用正则表达式查找并处理JS中的URL
            Matcher matcher = JS_URL_PATTERN.matcher(jsContent);
            StringBuffer newJsContent = new StringBuffer();
            
            while (matcher.find()) {
                String quote = matcher.group(1); // 引号类型 (' 或 ")
                String fullUrl = matcher.group(2); // 完整URL
                
                // 检查是否是同域URL
                if (isSameDomain(fullUrl, baseUrl)) {
                    try {
                        // 使用路径映射器处理资源URL
                        WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(fullUrl);
                        
                        // 下载资源
                        downloader.downloadResource(fullUrl, resourceMapping.getLocalPath());
                        
                        // 计算JS文件到资源的相对路径
                        String relativePathFromJs = pathMapper.calculateRelativePath(
                            jsFilePath, 
                            resourceMapping.getLocalPath()
                        );
                        
                        // 替换URL
                        matcher.appendReplacement(newJsContent, quote + relativePathFromJs + quote);
                        log.debug("JS中URL已替换: {} -> {}", fullUrl, relativePathFromJs);
                        modified = true;
                    } catch (Exception e) {
                        log.error("处理JS中的URL失败: {}", fullUrl, e);
                        matcher.appendReplacement(newJsContent, quote + fullUrl + quote);
                    }
                } else {
                    // 不是同域URL，保持不变
                    matcher.appendReplacement(newJsContent, quote + fullUrl + quote);
                }
            }
            
            if (modified) {
                matcher.appendTail(newJsContent);
                // 写回JS文件
                Files.write(path, newJsContent.toString().getBytes());
                log.info("成功处理JS文件: {}", jsFilePath);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("处理JS文件失败: {}", jsFilePath, e);
            return false;
        }
    }
    
    /**
     * 处理HTML中的内联样式
     * 
     * @param styleContent 样式内容
     * @param baseUrl 基础URL
     * @param currentPagePath 当前页面路径
     * @return 处理后的样式内容
     */
    public String processInlineStyle(String styleContent, String baseUrl, String currentPagePath) {
        if (styleContent == null || styleContent.isEmpty() || !styleContent.contains("url(")) {
            return styleContent;
        }
        
        try {
            Matcher matcher = INLINE_STYLE_URL_PATTERN.matcher(styleContent);
            StringBuffer newStyle = new StringBuffer();
            
            while (matcher.find()) {
                String originalUrl = matcher.group(1);
                
                // 跳过数据URI和空URL
                if (originalUrl.isEmpty() || originalUrl.startsWith("data:") || 
                    originalUrl.startsWith("#") || originalUrl.startsWith("javascript:")) {
                    matcher.appendReplacement(newStyle, "url(" + originalUrl + ")");
                    continue;
                }
                
                try {
                    // 获取绝对URL
                    String absUrl = getAbsoluteUrl(baseUrl, originalUrl);
                    
                    // 使用路径映射器处理资源URL
                    WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(absUrl);
                    
                    // 下载资源
                    downloader.downloadResource(absUrl, resourceMapping.getLocalPath());
                    
                    // 计算当前页面到资源的相对路径
                    String relativePathFromPage = pathMapper.calculateRelativePath(
                        currentPagePath, 
                        resourceMapping.getLocalPath()
                    );
                    
                    // 替换URL
                    matcher.appendReplacement(newStyle, "url(" + relativePathFromPage + ")");
                    log.debug("内联样式中URL已替换: {} -> {}", originalUrl, relativePathFromPage);
                } catch (Exception e) {
                    log.error("处理内联样式中的URL失败: {}", originalUrl, e);
                    matcher.appendReplacement(newStyle, "url(" + originalUrl + ")");
                }
            }
            matcher.appendTail(newStyle);
            
            return newStyle.toString();
            
        } catch (Exception e) {
            log.error("处理内联样式失败", e);
            return styleContent;
        }
    }
    
    /**
     * 处理srcset属性
     * 
     * @param srcset srcset属性值
     * @param baseUrl 基础URL
     * @param currentPagePath 当前页面路径
     * @return 处理后的srcset内容
     */
    public String processSrcset(String srcset, String baseUrl, String currentPagePath) {
        if (srcset == null || srcset.isEmpty()) {
            return srcset;
        }
        
        try {
            String[] srcsetParts = srcset.split(",");
            StringBuilder newSrcset = new StringBuilder();
            
            for (int i = 0; i < srcsetParts.length; i++) {
                String part = srcsetParts[i].trim();
                String[] urlAndDescriptor = part.split("\\s+", 2);
                String url = urlAndDescriptor[0];
                String descriptor = urlAndDescriptor.length > 1 ? " " + urlAndDescriptor[1] : "";
                
                // 跳过数据URI和空URL
                if (url.isEmpty() || url.startsWith("data:") || 
                    url.startsWith("#") || url.startsWith("javascript:")) {
                    if (i > 0) newSrcset.append(", ");
                    newSrcset.append(url).append(descriptor);
                    continue;
                }
                
                try {
                    // 获取绝对URL
                    String absUrl = getAbsoluteUrl(baseUrl, url);
                    
                    // 使用路径映射器处理资源URL
                    WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(absUrl);
                    
                    // 下载资源
                    downloader.downloadResource(absUrl, resourceMapping.getLocalPath());
                    
                    // 计算当前页面到资源的相对路径
                    String relativePathFromPage = pathMapper.calculateRelativePath(
                        currentPagePath, 
                        resourceMapping.getLocalPath()
                    );
                    
                    // 添加到新的srcset
                    if (i > 0) {
                        newSrcset.append(", ");
                    }
                    newSrcset.append(relativePathFromPage).append(descriptor);
                    log.debug("srcset中URL已替换: {} -> {}", url, relativePathFromPage);
                } catch (Exception e) {
                    log.error("处理srcset中的URL失败: {}", url, e);
                    if (i > 0) newSrcset.append(", ");
                    newSrcset.append(url).append(descriptor);
                }
            }
            
            return newSrcset.toString();
            
        } catch (Exception e) {
            log.error("处理srcset失败", e);
            return srcset;
        }
    }
    
    /**
     * 创建拦截器脚本，用于处理动态加载的资源
     * 
     * @return 拦截器脚本内容
     */
    public String createInterceptorScript() {
        return "// 资源拦截器脚本 - 处理动态加载的资源\n" +
               "document.addEventListener('DOMContentLoaded', function() {\n" +
               "  // 保存原始方法\n" +
               "  const originalFetch = window.fetch;\n" +
               "  const originalXHR = window.XMLHttpRequest.prototype.open;\n" +
               "  const originalImageSrc = Object.getOwnPropertyDescriptor(Image.prototype, 'src');\n" +
               "\n" +
               "  // 相对路径转换函数\n" +
               "  function convertToLocalPath(url) {\n" +
               "    if (!url || typeof url !== 'string') return url;\n" +
               "    \n" +
               "    // 跳过已经是相对路径或特殊URL的情况\n" +
               "    if (url.startsWith('./') || url.startsWith('../') || \n" +
               "        url.startsWith('data:') || url.startsWith('#') || \n" +
               "        url.startsWith('javascript:') || url.startsWith('blob:')) {\n" +
               "      return url;\n" +
               "    }\n" +
               "    \n" +
               "    // 处理以/开头的绝对路径\n" +
               "    if (url.startsWith('/') && !url.startsWith('//')) {\n" +
               "      return '.' + url;\n" +
               "    }\n" +
               "    \n" +
               "    // 处理完整URL\n" +
               "    try {\n" +
               "      const urlObj = new URL(url);\n" +
               "      const currentDomain = window.location.hostname;\n" +
               "      \n" +
               "      // 如果是同域请求，转换为相对路径\n" +
               "      if (urlObj.hostname === currentDomain) {\n" +
               "        return '.' + urlObj.pathname + (urlObj.search || '') + (urlObj.hash || '');\n" +
               "      }\n" +
               "    } catch (e) {\n" +
               "      console.warn('URL解析失败:', url, e);\n" +
               "    }\n" +
               "    \n" +
               "    return url;\n" +
               "  }\n" +
               "\n" +
               "  // 拦截fetch请求\n" +
               "  window.fetch = function(resource, options) {\n" +
               "    if (typeof resource === 'string') {\n" +
               "      resource = convertToLocalPath(resource);\n" +
               "    } else if (resource instanceof Request) {\n" +
               "      resource = new Request(\n" +
               "        convertToLocalPath(resource.url),\n" +
               "        resource\n" +
               "      );\n" +
               "    }\n" +
               "    return originalFetch.call(this, resource, options);\n" +
               "  };\n" +
               "\n" +
               "  // 拦截XHR请求\n" +
               "  window.XMLHttpRequest.prototype.open = function(method, url, async, user, password) {\n" +
               "    const newUrl = convertToLocalPath(url);\n" +
               "    return originalXHR.call(this, method, newUrl, async, user, password);\n" +
               "  };\n" +
               "\n" +
               "  // 拦截Image.src设置\n" +
               "  Object.defineProperty(Image.prototype, 'src', {\n" +
               "    set: function(url) {\n" +
               "      originalImageSrc.set.call(this, convertToLocalPath(url));\n" +
               "    },\n" +
               "    get: originalImageSrc.get\n" +
               "  });\n" +
               "\n" +
               "  console.log('资源拦截器已启用 - 动态加载的资源将使用本地路径');\n" +
               "});\n";
    }
    
    /**
     * 获取绝对URL
     * 
     * @param baseUrl 基础URL
     * @param resourceUrl 资源URL
     * @return 绝对URL
     */
    private String getAbsoluteUrl(String baseUrl, String resourceUrl) {
        try {
            // 处理特殊情况
            if (resourceUrl.isEmpty() || 
                resourceUrl.startsWith("data:") || 
                resourceUrl.startsWith("#") ||
                resourceUrl.startsWith("javascript:")) {
                return resourceUrl;
            }
            
            // 处理已经是绝对URL的情况
            if (resourceUrl.startsWith("http://") || resourceUrl.startsWith("https://")) {
                return resourceUrl;
            }
            
            // 处理以/开头的绝对路径
            if (resourceUrl.startsWith("/")) {
                java.net.URI baseUri = new java.net.URI(baseUrl);
                String normalizedBase = baseUri.getScheme() + "://" + baseUri.getHost();
                if (baseUri.getPort() != -1 && baseUri.getPort() != 80 && baseUri.getPort() != 443) {
                    normalizedBase += ":" + baseUri.getPort();
                }
                return normalizedBase + resourceUrl;
            }
            
            // 处理相对路径
            java.net.URI baseUri = new java.net.URI(baseUrl);
            java.net.URI resolvedUri = baseUri.resolve(resourceUrl);
            
            return resolvedUri.toString();
        } catch (Exception e) {
            log.error("URL解析失败: base={}, resource={}", baseUrl, resourceUrl, e);
            return resourceUrl;
        }
    }
    
    /**
     * 检查两个URL是否属于同一域名
     * 
     * @param url1 URL1
     * @param url2 URL2
     * @return 是否同域
     */
    private boolean isSameDomain(String url1, String url2) {
        try {
            java.net.URI uri1 = new java.net.URI(url1);
            java.net.URI uri2 = new java.net.URI(url2);
            
            String host1 = uri1.getHost();
            String host2 = uri2.getHost();
            
            return host1 != null && host2 != null && host1.equals(host2);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 资源下载器接口
     */
    public interface ResourceDownloader {
        /**
         * 下载资源
         * 
         * @param url 资源URL
         * @param localPath 本地保存路径
         * @return 是否成功下载
         */
        boolean downloadResource(String url, String localPath);
    }
}