package com.jiwu.aiseo.siteclone.processor;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.model.CloneTask;
import com.jiwu.aiseo.siteclone.utils.SecurityUtils;
import com.jiwu.aiseo.siteclone.utils.WebsitePathMapper;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.processor.PageProcessor;

public class WebsiteMirrorProcessor implements PageProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WebsiteMirrorProcessor.class);

    private final Site site;
    private final String outputDir;
    private final String domain;
    private final CloneTask cloneTask;
    private final SiteCloneProperties properties; // 添加配置属性
    private final ConcurrentHashMap<String, Boolean> urlCache = new ConcurrentHashMap<>();
    private final WebsitePathMapper pathMapper; // 添加路径映射器

    public WebsiteMirrorProcessor(String domain, int retryTimes, int sleepTime, String outputDir, CloneTask cloneTask, SiteCloneProperties properties) {
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

        // 创建必要的目录
        createDirectories();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(outputDir));
            Files.createDirectories(Paths.get(outputDir + "/images"));
            Files.createDirectories(Paths.get(outputDir + "/css"));
            Files.createDirectories(Paths.get(outputDir + "/js"));
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
        }
    }

    @Override
    public void process(Page page) {
        logger.info("Processing page: {}", page.getUrl());
        // 提取页面中的所有链接，保留.html等后缀
        // 使用LinkedHashSet保持顺序同时去重
        Set<String> uniqueLinks = new LinkedHashSet<>(
            page.getHtml().links().regex("(https?://" + domain + "/[\\w\\-/\\.]+)").all()
        );
        List<String> links = new ArrayList<>(uniqueLinks);
        logger.debug("Found {} unique links after deduplication", links.size());
        List<String> newLinks = new ArrayList<>();
        for (String link : links) {
            if (urlCache.putIfAbsent(link, true) == null) {
                newLinks.add(link);
                logger.debug("Adding new URL to queue: {}", link);
            } else {
                logger.debug("URL already in cache, skipping: {}", link);
            }
        }
        page.addTargetRequests(newLinks);

        // 解析当前页面
        String htmlContent = page.getHtml().toString();
        Document doc = Jsoup.parse(htmlContent);

        // 获取文件名
        String url = page.getUrl().toString();
        String fileName = getFileNameFromUrl(url);

        // 保存 HTML 文件
        try {
            // 使用路径映射器获取当前页面的本地路径
            WebsitePathMapper.PathMappingResult pageMapping = pathMapper.mapUrlToLocalPath(url);
            Path htmlFilePath = Paths.get(pageMapping.getLocalPath());
            
            Files.createDirectories(htmlFilePath.getParent());
            Files.write(htmlFilePath, doc.outerHtml().getBytes());
            logger.info("Saved HTML file: {}", htmlFilePath);

            // 增加页面爬取和文件下载计数
            synchronized (cloneTask) {
                cloneTask.incrementPagesCrawled();
                cloneTask.incrementFilesDownloaded(); // HTML文件也算作下载的文件
            }
        } catch (IOException e) {
            logger.error("Failed to save HTML file for URL: {}", url, e);
        }

        // 下载页面中的图片
        Elements images = doc.select("img");
        for (Element img : images) {
            String imgUrl = img.absUrl("src");
            if (!imgUrl.isEmpty()) {
                downloadFile(imgUrl, outputDir + "/images/");
            }
        }

        // 规范化base URL，确保不包含文件名
        String baseUrl = page.getUrl().toString();
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

        // 定义需要处理的资源类型
        String[] resourceSelectors = {
                "link[href]",
                "script[src]",
                "img[src]",
                "source[src]",
                "source[srcset]",
                "video[src]",
                "audio[src]",
                "iframe[src]",
                "embed[src]",
                "object[data]"
        };

        // 处理所有资源
        for (String selector : resourceSelectors) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                String attrName = element.hasAttr("href") ? "href" : element.hasAttr("src") ? "src" : "data";
                String originalUrl = element.attr(attrName);

                // 跳过空URL、data URI和锚点
                if (originalUrl.isEmpty() ||
                        originalUrl.startsWith("data:") ||
                        originalUrl.startsWith("#") ||
                        originalUrl.startsWith("javascript:")) {
                    continue;
                }

                // 修复绝对路径丢失前导斜杠的问题
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    originalUrl = "/" + originalUrl.replaceFirst("^/+", "");
                }

                // 规范化URL
                String absUrl;
                try {
                    // 更可靠的URL获取方式
                    String absUrlAttempt;
                    if (originalUrl.startsWith("/")) {
                        // 对于绝对路径，使用更可靠的处理方式
                        absUrlAttempt = element.attr("abs:" + attrName);
                        if (absUrlAttempt.isEmpty() || !absUrlAttempt.contains(originalUrl)) {
                            // 如果jsoup处理不理想，回退到手动处理
                            absUrlAttempt = getAbsoluteUrl(baseUrl, originalUrl);
                        }
                    } else {
                        // 对于相对路径，使用标准absUrl方法
                        absUrlAttempt = element.absUrl(attrName);
                        if (absUrlAttempt.isEmpty()) {
                            absUrlAttempt = getAbsoluteUrl(baseUrl, originalUrl);
                        }
                    }
                    absUrl = absUrlAttempt;
                    logger.debug("Processed URL: original={}, absUrl={}", originalUrl, absUrl);

                    // 使用路径映射器处理资源URL
                    WebsitePathMapper.PathMappingResult resourceMapping = pathMapper.mapUrlToLocalPath(absUrl);
                    
                    if (resourceMapping.wasRelocated()) {
                        logger.info("资源被重新定位到安全位置: {} -> {}", absUrl, resourceMapping.getRelativePath());
                    }

                    logger.debug(
                            "Processing resource: element={}, attr={}, original={}, absUrl={}, localPath={}",
                            element.tagName(), attrName, originalUrl, absUrl, resourceMapping.getLocalPath());

                    // 下载文件到映射的路径
                    downloadFile(absUrl, resourceMapping.getLocalPath());
                    
                    // 获取当前页面的本地路径，计算相对路径
                    WebsitePathMapper.PathMappingResult currentPageMapping = pathMapper.mapUrlToLocalPath(page.getUrl().toString());
                    String relativePathFromCurrentPage = pathMapper.calculateRelativePath(
                        currentPageMapping.getLocalPath(), 
                        resourceMapping.getLocalPath()
                    );
                    
                    // 修改HTML中的链接为本地相对路径
                    if (element.hasAttr("href")) {
                        element.attr("href", relativePathFromCurrentPage);
                    } else if (element.hasAttr("src")) {
                        element.attr("src", relativePathFromCurrentPage);
                    } else if (element.hasAttr("data")) {
                        element.attr("data", relativePathFromCurrentPage);
                    }
                    
                    logger.debug("更新链接: {} -> {}", originalUrl, relativePathFromCurrentPage);

                } catch (Exception e) {
                    logger.error("Unexpected error processing resource: {}={}, error: {}",
                            attrName, originalUrl, e.getMessage());
                }
            }
        }
    }

    private String getFileNameFromUrl(String url) {
        try {
            URL parsedUrl = URI.create(url).toURL();
            String path = parsedUrl.getPath();

            // 处理根路径
            if (path.isEmpty() || "/".equals(path)) {
                return "index.html";
            }

            // 移除查询参数和哈希
            path = path.split("[?#]")[0];

            // 处理路径，优先保留原始扩展名
            if (path.endsWith(".html") || path.endsWith(".htm") ||
                    path.endsWith(".php") || path.endsWith(".jsp") ||
                    path.endsWith(".asp") || path.endsWith(".aspx")) {
                return path.replaceAll("[^a-zA-Z0-9./-]", "_");
            }

            // 处理没有扩展名的路径
            if (!path.contains(".")) {
                // 仅对以斜杠结尾的路径添加index.html
                if (path.endsWith("/")) {
                    return path + "index.html";
                }
                // 否则保留原始路径
                return path;
            }

            // 其他情况保留原始文件名
            return path.replaceAll("[^a-zA-Z0-9./-]", "_");
        } catch (MalformedURLException e) {
            logger.error("Failed to parse URL: {}", url, e);
            return "page_" + url.hashCode() + ".html";
        }
    }

    // 提取URL的域名
    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "" : uri.getHost().startsWith("www.") ? uri.getHost().substring(4) : uri.getHost();
        } catch (URISyntaxException e) {
            logger.error("Invalid URL format: {}", url, e);
            return "";
        }
    }

    // 下载文件到指定目录
    private void downloadFile(String fileUrl, String savePath) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }

        // 检查域名是否匹配
        String fileDomain = extractDomain(fileUrl);
        if (fileDomain.isEmpty() || !fileDomain.equals(this.domain)) {
            logger.debug("Skipping non-mirror domain file: {}", fileUrl);
            return;
        }

        try {
            // 规范化保存路径
            Path outputPath = Paths.get(savePath).normalize();

            // 如果文件已存在，跳过下载
            if (Files.exists(outputPath)) {
                logger.debug("File already exists, skipping: {}", outputPath);
                return;
            }

            // 创建所有必要的父目录
            Files.createDirectories(outputPath.getParent());

            // 使用CustomHttpClientDownloader下载文件
            CustomHttpClientDownloader downloader = new CustomHttpClientDownloader();
            try {
                Page page = downloader.download(new Request(fileUrl), new Task() {
                    @Override
                    public String getUUID() {
                        return fileUrl;
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
                        logger.warn("File size {} exceeds limit for: {}", fileSize, fileUrl);
                        return;
                    }
                    
                    // 检查总下载大小限制
                    if (!SecurityUtils.isTotalSizeAllowed(cloneTask.getTotalBytesDownloaded(), fileSize, properties.getDownload().getMaxTotalSize())) {
                        logger.warn("Total download size would exceed limit for task: {}", cloneTask.getId());
                        return;
                    }
                    
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                        fileOutputStream.write(content);
                        logger.info("Successfully downloaded file: {} (size: {} bytes)", outputPath, fileSize);

                        // 增加文件下载计数和字节数
                        synchronized (cloneTask) {
                            cloneTask.incrementFilesDownloaded();
                            cloneTask.addBytesDownloaded(fileSize);
                        }
                    } catch (FileNotFoundException e) {
                        logger.error("Output directory not found for {}: {}", fileUrl, e.getMessage());
                    } catch (SecurityException e) {
                        logger.error("Security exception writing file {}: {}", fileUrl, e.getMessage());
                    } catch (IOException e) {
                        logger.error("IO error writing file {}: {}", fileUrl, e.getMessage());
                        // 删除可能损坏的文件
                        Files.deleteIfExists(outputPath);
                    }
                } else {
                    logger.warn("Failed to download file: {} with status code: {}", fileUrl, page.getStatusCode());
                }
            } catch (IOException e) {
                logger.error("Error downloading file {}: {}", fileUrl, e.getMessage());
            }
        } catch (MalformedURLException e) {
            logger.error("Invalid URL format: {} - {}", fileUrl, e.getMessage());
        } catch (IOException e) {
            logger.error("IO error preparing to download {}: {}", fileUrl, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error downloading {}: {}", fileUrl, e.getMessage());
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
                resourceUrl.startsWith("javascript:")) {
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
            logger.error("URL resolution failed: base={}, resource={}", baseUrl, resourceUrl, e);
            return resourceUrl;
        }
    }

    private String getRelativePath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();

            // 处理根路径
            if (path == null || path.isEmpty() || path.equals("/")) {
                return "index.html";
            }

            // 移除开头的斜杠，确保是相对路径
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            // 使用SecurityUtils清理路径（这会移除所有../等危险字符）
            String cleanPath;
            try {
                // 先进行基本清理
                cleanPath = path.replaceAll("/+", "/");
                
                // 移除路径中的所有..和.序列
                cleanPath = cleanPath.replaceAll("\\.\\./?", "");
                cleanPath = cleanPath.replaceAll("\\./", "");
                
                // 确保路径不为空
                if (cleanPath.isEmpty() || cleanPath.equals("/")) {
                    return "index.html";
                }
                
                // 处理目录路径（自动添加index.html）
                if (cleanPath.endsWith("/")) {
                    cleanPath += "index.html";
                }
                
            } catch (Exception e) {
                logger.warn("路径清理失败: {}", path, e);
                return "safe_file_" + Math.abs(url.hashCode()) + ".html";
            }

            // 处理查询参数和片段 - 安全化处理
            String query = uri.getQuery();
            String fragment = uri.getFragment();
            String result = cleanPath;

            if (query != null && !query.isEmpty()) {
                String safeQuery = query.replaceAll("[^a-zA-Z0-9]", "_");
                if (safeQuery.length() > 50) { // 限制长度
                    safeQuery = safeQuery.substring(0, 50);
                }
                result += "_q_" + safeQuery;
            }
            if (fragment != null && !fragment.isEmpty()) {
                String safeFragment = fragment.replaceAll("[^a-zA-Z0-9]", "_");
                if (safeFragment.length() > 30) { // 限制长度
                    safeFragment = safeFragment.substring(0, 30);
                }
                result += "_f_" + safeFragment;
            }

            return result;
        } catch (URISyntaxException e) {
            logger.error("Invalid URL syntax: {}", url, e);
            return "safe_file_" + Math.abs(url.hashCode()) + ".html";
        }
    }

    @Override
    public Site getSite() {
        return site;
    }
}