package com.jiwu.aiseo.siteclone.processor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiwu.aiseo.siteclone.config.SiteCloneProperties;
import com.jiwu.aiseo.siteclone.model.CloneTask;
import com.jiwu.aiseo.siteclone.utils.ResourceProcessor;
import com.jiwu.aiseo.siteclone.utils.SecurityUtils;
import com.jiwu.aiseo.siteclone.utils.WebResourceDownloader;
import com.jiwu.aiseo.siteclone.utils.WebsitePathMapper;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

public class WebsiteMirrorProcessor implements PageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WebsiteMirrorProcessor.class);

    private final Site site;
    private final String outputDir;
    private final String domain;
    private final CloneTask cloneTask;
    private final SiteCloneProperties properties; // 添加配置属性
    private final ConcurrentHashMap<String, Boolean> urlCache = new ConcurrentHashMap<>();
    private final WebsitePathMapper pathMapper; // 路径映射器
    private final ResourceProcessor resourceProcessor; // 资源处理器
    private final WebResourceDownloader resourceDownloader; // 资源下载器
    private final boolean preserveOriginalUrls; // 是否保留原始URL

    public WebsiteMirrorProcessor(String domain, int retryTimes, int sleepTime, String outputDir, CloneTask cloneTask, 
            SiteCloneProperties properties, boolean preserveOriginalUrls) {
        this.preserveOriginalUrls = preserveOriginalUrls;
        this.site = Site.me()
                .setDomain(domain)
                .setRetryTimes(retryTimes)
                .setSleepTime(sleepTime)
                .setTimeOut(properties.getCrawler().getTimeout())
                .setUserAgent(properties.getCrawler().getUserAgent())
                .addHeader("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "none")
                .addHeader("Sec-Fetch-User", "?1")
                .setCharset("UTF-8");
        this.outputDir = outputDir;
        this.domain = domain;
        this.cloneTask = cloneTask;
        this.properties = properties; // 保存配置属性
        this.pathMapper = new WebsitePathMapper(outputDir, domain); // 初始化路径映射器
        
        // 初始化资源下载器
        this.resourceDownloader = new WebResourceDownloader(site, cloneTask, properties);
        
        // 初始化资源处理器
        this.resourceProcessor = new ResourceProcessor(pathMapper, resourceDownloader);
        
        // 添加常见的JS URL替换规则
        resourceProcessor.addJsUrlReplacement("https://" + domain + "/", "./");
        resourceProcessor.addJsUrlReplacement("http://" + domain + "/", "./");
        resourceProcessor.addJsUrlReplacement("'//'", "'./");
        resourceProcessor.addJsUrlReplacement("\"/\"", "\"./\"");

        // 创建必要的目录
        createDirectories();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(outputDir));
            Files.createDirectories(Paths.get(outputDir + "/images"));
            Files.createDirectories(Paths.get(outputDir + "/css"));
            Files.createDirectories(Paths.get(outputDir + "/js"));
            Files.createDirectories(Paths.get(outputDir + "/fonts"));
            Files.createDirectories(Paths.get(outputDir + "/media"));
            Files.createDirectories(Paths.get(outputDir + "/data"));
            Files.createDirectories(Paths.get(outputDir + "/safe_files"));
        } catch (IOException e) {
            logger.error("创建目录失败", e);
        }
    }

    @Override
    public void process(Page page) {
        logger.info("处理页面: {}", page.getUrl());
        // 提取页面中的所有链接，保留.html等后缀
        // 使用LinkedHashSet保持顺序同时去重
        // 修改正则表达式以支持端口和多语言字符
        Set<String> uniqueLinks = new LinkedHashSet<>(
            page.getHtml().links().regex("(https?://" + domain + "(?::\\d+)?/[\\w\\-\\u0080-\\uFFFF/.]+)").all()
        );
        List<String> links = new ArrayList<>(uniqueLinks);
        logger.debug("去重后找到 {} 个唯一链接", links.size());
        List<String> newLinks = new ArrayList<>();
        for (String link : links) {
            if (urlCache.putIfAbsent(link, true) == null) {
                newLinks.add(link);
                logger.debug("添加新URL到队列: {}", link);
            } else {
                logger.debug("URL已在缓存中，跳过: {}", link);
            }
        }
        page.addTargetRequests(newLinks);

        // 解析当前页面
        String htmlContent = page.getHtml().toString();
        Document doc = Jsoup.parse(htmlContent);

        // 获取当前页面URL
        String url = page.getUrl().toString();

        // 规范化base URL，确保不包含文件名
        String baseUrl = url;
        if (baseUrl.contains("?")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("?"));
        }
        if (baseUrl.contains("#")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("#"));
        }
        // 去除文件名部分，只保留目录路径
        if (!baseUrl.endsWith("/")) {
            int lastSlash = baseUrl.lastIndexOf('/');
            if (lastSlash > 8) { // 确保不是协议部分(https://)
                baseUrl = baseUrl.substring(0, lastSlash + 1);
            } else {
                baseUrl += "/";
            }
        }
        doc.setBaseUri(baseUrl);

        // 使用路径映射器获取当前页面的本地路径
        WebsitePathMapper.PathMappingResult pageMapping = pathMapper.mapUrlToLocalPath(url);
        String currentPagePath = pageMapping.getLocalPath();
        
        // 添加或更新base标签
        Elements baseElements = doc.select("head > base");
        if (baseElements.isEmpty()) {
            doc.head().prependElement("base").attr("href", "./");
        } else {
            baseElements.first().attr("href", "./");
        }
        
        // 处理HTML中的内联样式
        Elements elementsWithStyle = doc.select("[style]");
        for (Element element : elementsWithStyle) {
            String style = element.attr("style");
            if (style.contains("url(")) {
                String newStyle = resourceProcessor.processInlineStyle(style, baseUrl, currentPagePath);
                element.attr("style", newStyle);
            }
        }
        
        // 处理srcset属性
        Elements elementsWithSrcset = doc.select("[srcset]");
        for (Element element : elementsWithSrcset) {
            String srcset = element.attr("srcset");
            String newSrcset = resourceProcessor.processSrcset(srcset, baseUrl, currentPagePath);
            element.attr("srcset", newSrcset);
        }

        // 定义需要处理的资源类型
        String[] resourceSelectors = {
                "link[href]",           // CSS文件
                "script[src]",          // JS文件
                "img[src]",             // 图片
                "source[src]",          // 媒体源
                "video[src]",           // 视频
                "audio[src]",           // 音频
                "iframe[src]",          // 内嵌框架
                "embed[src]",           // 嵌入内容
                "object[data]",         // 对象数据
                "a[href]",              // 链接
                "form[action]",         // 表单
                "input[src]",           // 输入元素
                "track[src]"            // 字幕轨道
        };

        // 处理所有资源
        for (String selector : resourceSelectors) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                String attrName = element.hasAttr("href") ? "href" : 
                                 element.hasAttr("src") ? "src" : 
                                 element.hasAttr("action") ? "action" : "data";
                String originalUrl = element.attr(attrName);

                // 跳过空URL、data URI、锚点和JavaScript
                if (originalUrl.isEmpty() ||
                        originalUrl.startsWith("data:") ||
                        originalUrl.startsWith("#") ||
                        originalUrl.startsWith("javascript:") ||
                        originalUrl.startsWith("mailto:") ||
                        originalUrl.startsWith("tel:")) {
                    continue;
                }
                
                // 对于链接，只处理同域名的链接
                if (attrName.equals("href") && element.tagName().equals("a")) {
                    try {
                        URI uri = new URI(element.absUrl("href"));
                        String linkDomain = uri.getHost();
                        if (linkDomain == null || !linkDomain.equals(domain)) {
                            // 非同域链接，保留原始链接
                            continue;
                        }
                    } catch (Exception e) {
                        // 解析失败，保留原始链接
                        continue;
                    }
                }

                // 修复绝对路径丢失前导斜杠的问题
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    originalUrl = "/" + originalUrl.replaceFirst("^/+", "");
                }

                try {
                    // 获取绝对URL
                    String absUrl;
                    if (originalUrl.startsWith("/")) {
                        // 对于绝对路径，使用更可靠的处理方式
                        absUrl = element.attr("abs:" + attrName);
                        if (absUrl.isEmpty() || !absUrl.contains(originalUrl)) {
                            // 如果jsoup处理不理想，回退到手动处理
                            absUrl = getAbsoluteUrl(baseUrl, originalUrl);
                        }
                    } else {
                        // 对于相对路径，使用标准absUrl方法
                        absUrl = element.absUrl(attrName);
                        if (absUrl.isEmpty()) {
                            absUrl = getAbsoluteUrl(baseUrl, originalUrl);
                        }
                    }
                    
                    // 使用路径映射器处理资源URL
                    WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(absUrl);
                    
                    if (resourceMapping.wasRelocated()) {
                        logger.info("资源被重新定位到安全位置: {} -> {}", absUrl, resourceMapping.getRelativePath());
                    }

                    // 检查是否为当前域名资源
                    boolean isCurrentDomainResource = false;
                    try {
                        URI resourceUri = new URI(absUrl);
                        String resourceDomain = resourceUri.getHost();
                        isCurrentDomainResource = domain.equals(resourceDomain);
                    } catch (Exception e) {
                        logger.warn("解析资源域名失败: {}", absUrl);
                    }

                    // 处理逻辑：当前域名资源下载，外部资源保留原始URL
                    if (isCurrentDomainResource) {
                        // 下载当前域名资源并更新链接
                        if (resourceDownloader.downloadResource(absUrl, resourceMapping.getLocalPath())) {
                            // 如果是CSS文件，下载后处理其中的URL引用
                            if (element.tagName().equals("link") && attrName.equals("href") && element.attr("rel").equals("stylesheet")) {
                                // 异步处理CSS文件中的URL引用，避免阻塞主流程
                                processCssFileAsync(resourceMapping.getLocalPath(), baseUrl);
                            }
                            
                            // 计算从当前页面到资源的正确相对路径
                            String correctRelativePath = calculateCorrectRelativePath(currentPagePath, resourceMapping.getLocalPath());
                            element.attr(attrName, correctRelativePath);
                            logger.info("下载并更新当前域名资源: {} -> {} (从 {} 到 {})", originalUrl, correctRelativePath, currentPagePath, resourceMapping.getLocalPath());
                        } else {
                            // 下载失败，保留原始URL
                            element.attr(attrName, absUrl);
                            logger.warn("下载当前域名资源失败，保留原始URL: {}", originalUrl);
                        }
                    } else {
                        // 外部资源保留原始URL
                        element.attr(attrName, absUrl);
                        logger.debug("保留外部资源原始URL: {}", absUrl);
                    }
                } catch (Exception e) {
                    logger.error("处理资源时发生意外错误: {}={}, 错误: {}", 
                            attrName, originalUrl, e.getMessage());
                }
            }
        }
        
        // 添加拦截器脚本，处理动态加载的资源
        Element interceptorScript = doc.createElement("script");
        interceptorScript.attr("type", "text/javascript");
        interceptorScript.text(resourceProcessor.createInterceptorScript());
        doc.body().appendChild(interceptorScript);
        
        // 保存处理后的HTML文件
        try {
            Path htmlFilePath = Paths.get(currentPagePath);
            Files.createDirectories(htmlFilePath.getParent());
            Files.write(htmlFilePath, doc.outerHtml().getBytes());
            logger.info("保存HTML文件: {}", htmlFilePath);

            // 增加页面爬取和文件下载计数
            synchronized (cloneTask) {
                cloneTask.incrementPagesCrawled();
                cloneTask.incrementFilesDownloaded(); // HTML文件也算作下载的文件
            }
        } catch (IOException e) {
            logger.error("保存HTML文件失败: {}", url, e);
        }
    }

    /**
     * 获取绝对URL
     * 
     * @param baseUrl     基础URL
     * @param resourceUrl 资源URL（可以是相对或绝对路径）
     * @return 完整的绝对URL
     */
    private String getAbsoluteUrl(String baseUrl, String resourceUrl) {
        try {
            // 处理特殊情况：空URL、data URI、锚点、javascript等
            if (resourceUrl.isEmpty() || 
                resourceUrl.startsWith("data:") || 
                resourceUrl.startsWith("#") ||
                resourceUrl.startsWith("javascript:") ||
                resourceUrl.startsWith("mailto:") ||
                resourceUrl.startsWith("tel:")) {
                return resourceUrl;
            }
            
            // 处理已经是绝对URL的情况
            if (resourceUrl.startsWith("http://") || resourceUrl.startsWith("https://")) {
                return resourceUrl;
            }
            
            // 处理以/开头的绝对路径
            if (resourceUrl.startsWith("/")) {
                URI baseUri = new URI(baseUrl);
                String normalizedBase = baseUri.getScheme() + "://" + baseUri.getHost();
                if (baseUri.getPort() != -1 && baseUri.getPort() != 80 && baseUri.getPort() != 443) {
                    normalizedBase += ":" + baseUri.getPort();
                }
                return normalizedBase + resourceUrl;
            }
            
            // 处理相对路径 - 使用安全的URI解析
            URI baseUri = new URI(baseUrl);
            URI resolvedUri = baseUri.resolve(resourceUrl);
            
            // 验证解析后的URI是否安全
            String resolved = resolvedUri.toString();
            if (!SecurityUtils.isUrlSafe(resolved)) {
                logger.warn("URL解析后不安全: {}", resolved);
                return resourceUrl; // 返回原始URL，让后续处理决定是否跳过
            }
            
            return resolved;
        } catch (Exception e) {
            logger.error("URL解析失败: base={}, resource={}", baseUrl, resourceUrl, e);
            return resourceUrl;
        }
    }

    /**
     * 计算从当前页面到资源的正确相对路径
     * 
     * @param currentPagePath 当前页面路径
     * @param resourcePath 资源路径
     * @return 正确的相对路径
     */
    private String calculateCorrectRelativePath(String currentPagePath, String resourcePath) {
        logger.info("计算相对路径: {} -> {}", currentPagePath, resourcePath);
        try {
            Path currentFile = Paths.get(currentPagePath);
            Path resourceFile = Paths.get(resourcePath);
            
            // 获取当前文件的目录
            Path currentDir = currentFile.getParent();
            logger.info("当前文件目录: {}", currentDir);
            
            if (currentDir == null) {
                String result = resourceFile.getFileName().toString();
                logger.info("结果(文件名): {}", result);
                return result;
            }
            
            // 计算相对路径
            Path relativePath = currentDir.relativize(resourceFile);
            String result = relativePath.toString().replace('\\', '/');
            logger.info("计算出的相对路径: {}", result);
            return result;
        } catch (Exception e) {
            logger.warn("计算相对路径失败: {} -> {}", currentPagePath, resourcePath, e);
            // 回退到使用路径映射器的相对路径
            String fallback = pathMapper.calculateRelativePath(currentPagePath, resourcePath);
            logger.info("回退路径映射器结果: {}", fallback);
            return fallback;
        }
    }

    /**
     * 异步处理CSS文件中的URL引用
     * 
     * @param cssFilePath CSS文件路径
     * @param baseUrl 基础URL
     */
    private void processCssFileAsync(String cssFilePath, String baseUrl) {
        new Thread(() -> {
            try {
                // 等待一小段时间确保文件写入完成
                Thread.sleep(100);
                resourceProcessor.processCssFile(cssFilePath, baseUrl);
                logger.info("异步处理CSS文件完成: {}", cssFilePath);
            } catch (Exception e) {
                logger.error("异步处理CSS文件失败: {}", cssFilePath, e);
            }
        }).start();
    }

    @Override
    public Site getSite() {
        return site;
    }
}