package com.jiwu.aiseo.siteclone.downloader;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.CookieStore;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;

import lombok.extern.slf4j.Slf4j;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.downloader.HttpClientDownloader;

/**
 * 自定义HttpClient下载器，处理SSL问题
 */
@Slf4j
public class CustomHttpClientDownloader extends HttpClientDownloader {

    public CloseableHttpClient getHttpClient(Site site) {
        if (site == null) {
            return createHttpClient(null);
        }
        return createHttpClient(site);
    }

    private CloseableHttpClient createHttpClient(Site site) {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        // 创建信任所有证书的SSL上下文
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("创建SSL上下文失败", e);
            throw new RuntimeException("无法创建SSL上下文", e);
        }

        // 创建SSL连接工厂，允许所有主机名
        SSLConnectionSocketFactory sslConnectionFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1", "TLSv1.1", "TLSv1.2", "SSLv3"},
                null,
                NoopHostnameVerifier.INSTANCE);

        // 注册HTTP和HTTPS协议
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslConnectionFactory)
                .build();

        // 创建连接管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        connectionManager.setMaxTotal(100);
        httpClientBuilder.setConnectionManager(connectionManager);

        if (site != null) {
            // 设置用户代理
            httpClientBuilder.setUserAgent(site.getUserAgent());

            // 设置Cookie
            if (site.isUseGzip()) {
                httpClientBuilder.addInterceptorFirst(new HttpRequestInterceptor() {
                    @Override
                    public void process(org.apache.http.HttpRequest request, org.apache.http.protocol.HttpContext context) {
                        if (!request.containsHeader("Accept-Encoding")) {
                            request.addHeader("Accept-Encoding", "gzip");
                        }
                    }
                });
            }

            // 设置Cookie存储
            CookieStore cookieStore = new BasicCookieStore();
            for (Map.Entry<String, String> cookieEntry : site.getCookies().entrySet()) {
                BasicClientCookie cookie = new BasicClientCookie(cookieEntry.getKey(), cookieEntry.getValue());
                cookie.setDomain(site.getDomain());
                cookieStore.addCookie(cookie);
            }
            httpClientBuilder.setDefaultCookieStore(cookieStore);
        }

        return httpClientBuilder.build();
    }
}