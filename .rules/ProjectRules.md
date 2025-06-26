# 项目说明
这是一个aiseo的项目，使用最新的springboot 3.5.3版本，同时是一个多模块项目。目前已经实现的模块有：siteclone
## 项目结构
### siteclone模块使用 WebMagic 和 Jsoup 结合实现网站镜像功能。
样例代码
``` java 
public class WebsiteMirrorProcessor implements PageProcessor {

    private Site site = Site.me().setRetryTimes(3).setSleepTime(1000);

    @Override
    public void process(Page page) {
        // 提取页面中的所有链接
        page.addTargetRequests(page.getHtml().links().all());

        // 解析当前页面
        String htmlContent = page.getHtml().toString();
        Document doc = Jsoup.parse(htmlContent);

        // 保存 HTML 文件
        String fileName = "output/" + Paths.get(new URL(page.getUrl().toString()).getPath()).getFileName() + ".html";
        try {
            Files.write(Paths.get(fileName), doc.outerHtml().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 下载页面中的图片
        Elements images = doc.select("img");
        for (Element img : images) {
            String imgUrl = img.absUrl("src");
            if (!imgUrl.isEmpty()) {
                downloadFile(imgUrl, "output/images/");
            }
        }

        // 下载 CSS 和 JS 文件
        Elements cssFiles = doc.select("link[rel=stylesheet]");
        for (Element css : cssFiles) {
            String cssUrl = css.absUrl("href");
            if (!cssUrl.isEmpty()) {
                downloadFile(cssUrl, "output/css/");
            }
        }

        Elements jsFiles = doc.select("script[src]");
        for (Element js : jsFiles) {
            String jsUrl = js.absUrl("src");
            if (!jsUrl.isEmpty()) {
                downloadFile(jsUrl, "output/js/");
            }
        }
    }

    // 下载文件到指定目录
    private void downloadFile(String fileUrl, String outputDir) {
        try {
            URL url = new URL(fileUrl);
            String fileName = Paths.get(url.getPath()).getFileName().toString();
            Path outputPath = Paths.get(outputDir + fileName);

            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(outputPath.toFile())) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutputStream.write(dataBuffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    public static void main(String[] args) {
        Spider.create(new WebsiteMirrorProcessor())
                .addUrl("http://example.com") // 设置起始 URL
                .thread(5) // 设置线程数
                .run();
    }
}
```