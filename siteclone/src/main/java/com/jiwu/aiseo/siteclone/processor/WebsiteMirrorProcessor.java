package com.jiwu.aiseo.siteclone.processor;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;
import com.jiwu.aiseo.siteclone.model.CloneTask;

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
    private final ConcurrentHashMap<String, Boolean> urlCache = new ConcurrentHashMap<>();

    public WebsiteMirrorProcessor(String domain, int retryTimes, int sleepTime, String outputDir, CloneTask cloneTask) {
        this.site = Site.me()
                .setDomain(domain)
                .setRetryTimes(retryTimes)
                .setSleepTime(sleepTime)
                .setTimeOut(10000)
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
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
        List<String> links = page.getHtml().links().regex("(https?://" + domain + "/[\\w\\-/\\.]+)").all();
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
            Path htmlFilePath = Paths.get(outputDir, fileName);
            Files.createDirectories(htmlFilePath.getParent());
            Files.write(htmlFilePath, doc.outerHtml().getBytes());
            logger.info("Saved HTML file: {}", htmlFilePath);
            
            // 增加页面爬取和文件下载计数
            synchronized(cloneTask) {
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
                String attrName = element.hasAttr("href") ? "href" : 
                                element.hasAttr("src") ? "src" : "data";
                String originalUrl = element.attr(attrName);

                // 跳过空URL、data URI和锚点
                if (originalUrl.isEmpty() || 
                    originalUrl.startsWith("data:") || 
                    originalUrl.startsWith("#") ||
                    originalUrl.startsWith("javascript:")) {
                    continue;
                }

                // 规范化URL
                String absUrl;
                try {
                    // 先尝试jsoup的absUrl方法
                    absUrl = element.absUrl(attrName);
                    
                    // 如果失败，手动构建绝对URL
                    if (absUrl.isEmpty()) {
                        if (originalUrl.startsWith("/")) {
                            // 处理根相对路径
                            URI uri = new URI(baseUrl).resolve(originalUrl);
                            absUrl = uri.toURL().toString();
                        } else if (originalUrl.startsWith("../") || originalUrl.startsWith("./")) {
                            // 处理相对路径
                            URI uri = new URI(baseUrl).resolve(originalUrl);
                            absUrl = uri.toURL().toString();
                        } else {
                            // 处理同级相对路径
                            absUrl = baseUrl + originalUrl;
                        }
                    }
                    
                    // 确保URL格式正确
                    absUrl = absUrl.replaceAll("(?<!:)/+", "/") // 去除多余斜杠
                                  .replaceAll("/\\./", "/")     // 处理./路径
                                  .replaceAll("/[^/]+/\\.\\./", "/"); // 处理../路径

                    // 获取相对路径并下载文件
                    String relativePath = getRelativePath(absUrl);
                    String savePath = outputDir + "/" + relativePath;
                    
                    logger.debug("Processing resource: element={}, attr={}, original={}, absUrl={}, relative={}, savePath={}",
                        element.tagName(), attrName, originalUrl, absUrl, relativePath, savePath);
                    
                    downloadFile(absUrl, savePath);
                    
                } catch (MalformedURLException e) {
                    logger.error("Invalid URL format for resource: {}={}, error: {}", 
                        attrName, originalUrl, e.getMessage());
                } catch (URISyntaxException e) {
                    logger.error("Invalid URI syntax for resource: {}={}, error: {}", 
                        attrName, originalUrl, e.getMessage());
                } catch (IOException e) {
                    logger.error("IO error processing resource: {}={}, error: {}", 
                        attrName, originalUrl, e.getMessage());
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
            String domain = uri.getHost();
            return domain == null ? "" : domain.startsWith("www.") ? domain.substring(4) : domain;
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
                    try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                        fileOutputStream.write(page.getRawText().getBytes());
                        logger.info("Successfully downloaded file: {}", outputPath);
                        
                        // 增加文件下载计数
                        synchronized(cloneTask) {
                            cloneTask.incrementFilesDownloaded();
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
            } catch (Exception e) {
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
     * 获取URL的相对路径部分
     * @param url 完整的URL
     * @return 相对路径字符串
     */
    private String getAbsoluteUrl(String baseUrl, String resourceUrl) {
        try {
            // 处理绝对路径
            if (resourceUrl.startsWith("/")) {
                URI baseUri = new URI(baseUrl);
                return baseUri.getScheme() + "://" + baseUri.getHost() + resourceUrl;
            }
            
            // 处理相对路径
            URI baseUri = new URI(baseUrl);
            URI resolvedUri = baseUri.resolve(resourceUrl);
            return resolvedUri.toString();
        } catch (URISyntaxException e) {
            logger.error("Invalid URL syntax: base={}, resource={}", baseUrl, resourceUrl, e);
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
            
            // 规范化路径
            path = path.replaceAll("/+", "/")
                     .replaceAll("/\\./", "/")
                     .replaceAll("/[^/]+/\\.\\./", "/");
            
            // 处理目录路径（自动添加index.html）
            if (path.endsWith("/")) {
                path += "index.html";
            }
            
            // 确保路径不为空
            if (path.isEmpty()) {
                return "index.html";
            }
            
            // 处理查询参数和片段
            String query = uri.getQuery();
            String fragment = uri.getFragment();
            String result = path;
            
            if (query != null && !query.isEmpty()) {
                result += "_" + query.replaceAll("[^a-zA-Z0-9]", "_");
            }
            if (fragment != null && !fragment.isEmpty()) {
                result += "_" + fragment.replaceAll("[^a-zA-Z0-9]", "_");
            }
            
            return result;
        } catch (URISyntaxException e) {
            logger.error("Invalid URL syntax: {}", url, e);
            return url.replaceFirst("^https?://[^/]+/", "")
                    .replaceAll("[^a-zA-Z0-9./-]", "_");
        }
    }

    private String sanitizeFileName(String path) {
        // 移除查询参数
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }

        // 获取文件名
        String fileName = Paths.get(path).getFileName().toString();

        // 如果文件名为空，生成随机文件名
        if (fileName.isEmpty()) {
            fileName = "file_" + System.currentTimeMillis();
        }

        // 替换不安全的字符
        fileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");

        // 确保文件名不超过255个字符
        if (fileName.length() > 255) {
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1) {
                extension = fileName.substring(dotIndex);
                fileName = fileName.substring(0, dotIndex);
            }
            fileName = fileName.substring(0, Math.min(fileName.length(), 255 - extension.length())) + extension;
        }

        return fileName;
    }

    @Override
    public Site getSite() {
        return site;
    }
}