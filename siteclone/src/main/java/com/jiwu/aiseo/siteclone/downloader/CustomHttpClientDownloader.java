package com.jiwu.aiseo.siteclone.downloader;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;

import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.downloader.HttpClientDownloader;

/**
 * 安全的HttpClient下载器，实现合理的SSL配置和安全控制
 */
@Slf4j
public class CustomHttpClientDownloader extends HttpClientDownloader {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10000; // 10秒
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000;  // 30秒
    private static final int DEFAULT_REQUEST_TIMEOUT = 60000; // 60秒
    private static final int MAX_CONNECTIONS = 50;
    
    /**
     * 下载URL内容为字节数组
     * 
     * @param url 要下载的URL
     * @param site 站点配置
     * @return 下载的内容字节数组，如果下载失败则返回null
     */
    public byte[] download(String url, Site site) {
        try {
            Request request = new Request(url);
            // 设置站点配置
            if (site != null) {
                if (site.getHeaders() != null) {
                    for (Map.Entry<String, String> entry : site.getHeaders().entrySet()) {
                        request.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                if (site.getCookies() != null) {
                    for (Map.Entry<String, String> entry : site.getCookies().entrySet()) {
                        request.addCookie(entry.getKey(), entry.getValue());
                    }
                }
            }
            
            // 使用父类的download方法下载内容
            Page page = super.download(request, new Task() {
                @Override
                public String getUUID() {
                    return "download-task";
                }
                
                @Override
                public Site getSite() {
                    return site;
                }
            });
            
            if (page != null && page.getBytes() != null) {
                return page.getBytes();
            }
        } catch (Exception e) {
            log.error("下载资源失败: {}", url, e);
        }
        
        return null;
    }

    public CloseableHttpClient getHttpClient(Site site) {
        if (site == null) {
            return createHttpClient(null);
        }
        return createHttpClient(site);
    }

    private CloseableHttpClient createHttpClient(Site site) {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        // 创建安全的SSL上下文 - 使用系统默认证书验证
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            // 使用默认的TrustManager进行证书验证
            sslContext.init(null, null, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("创建SSL上下文失败", e);
            throw new RuntimeException("无法创建SSL上下文", e);
        }

        // 创建安全的SSL连接工厂，使用默认主机名验证
        SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1.3", "TLSv1.2"},  // 仅支持TLS 1.2和1.3
                new String[]{
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
                },  // 安全的密码套件
                new DefaultHostnameVerifier()); // 使用默认主机名验证

        // 注册HTTP和HTTPS协议
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionFactory)
                .build();

        // 创建连接管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        connectionManager.setMaxTotal(MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(10);
        httpClientBuilder.setConnectionManager(connectionManager);

        // 设置请求超时配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .setSocketTimeout(DEFAULT_SOCKET_TIMEOUT)
                .setConnectionRequestTimeout(DEFAULT_REQUEST_TIMEOUT)
                .build();
        httpClientBuilder.setDefaultRequestConfig(requestConfig);

        if (site != null) {
            // 设置用户代理
            if (site.getUserAgent() != null) {
                httpClientBuilder.setUserAgent(site.getUserAgent());
            }

            // 设置压缩支持
            if (site.isUseGzip()) {
                httpClientBuilder.addInterceptorFirst((org.apache.http.HttpRequest request, org.apache.http.protocol.HttpContext context) -> {
                    if (!request.containsHeader("Accept-Encoding")) {
                        request.addHeader("Accept-Encoding", "gzip, deflate");
                    }
                });
            }

            // 设置Cookie存储
            if (site.getCookies() != null && !site.getCookies().isEmpty()) {
                CookieStore cookieStore = new BasicCookieStore();
                for (Map.Entry<String, String> cookieEntry : site.getCookies().entrySet()) {
                    BasicClientCookie cookie = new BasicClientCookie(cookieEntry.getKey(), cookieEntry.getValue());
                    cookie.setDomain(site.getDomain());
                    cookieStore.addCookie(cookie);
                }
                httpClientBuilder.setDefaultCookieStore(cookieStore);
            }
        }

        log.info("创建HttpClient成功，SSL证书验证已启用");
        return httpClientBuilder.build();
    }
}