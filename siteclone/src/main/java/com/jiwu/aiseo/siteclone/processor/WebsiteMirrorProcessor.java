package com.jiwu.aiseo.siteclone.processor;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiwu.aiseo.siteclone.downloader.CustomHttpClientDownloader;

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

    public WebsiteMirrorProcessor(String domain, int retryTimes, int sleepTime, String outputDir) {
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
        page.addTargetRequests(page.getHtml().links().regex("(https?://" + domain + "/[\\w\\-/\\.]+)").all());

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

        // 下载 CSS 和 JS 文件
        Elements cssFiles = doc.select("link[rel=stylesheet]");
        for (Element css : cssFiles) {
            String cssUrl = css.absUrl("href");
            if (!cssUrl.isEmpty()) {
                downloadFile(cssUrl, outputDir + "/css/");
            }
        }

        Elements jsFiles = doc.select("script[src]");
        for (Element js : jsFiles) {
            String jsUrl = js.absUrl("src");
            if (!jsUrl.isEmpty()) {
                downloadFile(jsUrl, outputDir + "/js/");
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
        } catch (Exception e) {
            logger.error("Failed to parse URL: {}", url, e);
            return "page_" + url.hashCode() + ".html";
        }
    }

    // 下载文件到指定目录
    private void downloadFile(String fileUrl, String outputDir) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return;
        }

        try {
            URL url = URI.create(fileUrl).toURL();
            String fileName = sanitizeFileName(url.getPath());
            Path outputPath = Paths.get(outputDir, fileName);

            // 如果文件已存在，跳过下载
            if (Files.exists(outputPath)) {
                logger.debug("File already exists, skipping: {}", outputPath);
                return;
            }

            // 创建父目录
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
                    } catch (FileNotFoundException e) {
                        logger.error("Output directory not found: {}", e.getMessage());
                    } catch (SecurityException | IOException e) {
                        logger.error("Failed to write file: {}", e.getMessage());
                    }
                } else {
                    logger.warn("Failed to download file: {} with status code: {}", fileUrl, page.getStatusCode());
                }
            } catch (Exception e) {
                logger.error("Error downloading file {}: {}", fileUrl, e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error downloading file: {} - {}", fileUrl, e.getMessage());
            // 如果下载失败，删除可能部分下载的文件
            try {
                Files.deleteIfExists(Paths.get(outputDir, URI.create(fileUrl).toURL().getPath()));
            } catch (Exception ex) {
                logger.error("Error cleaning up failed download: {}", ex.getMessage());
            }
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