import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 简化的路径处理逻辑测试
 */
public class SimplePathMappingTest {
    
    public static void main(String[] args) {
        testPathSafety();
        testRelativePathCalculation();
    }
    
    private static void testPathSafety() {
        System.out.println("=== 相对路径安全处理测试 ===");
        
        List<String> testUrls = Arrays.asList(
            "https://example.com/images/logo.png",            // 正常图片
            "https://example.com/css/../images/pic.jpg",      // 合法的相对路径
            "https://example.com/../../../etc/passwd",        // 恶意路径遍历
            "https://example.com/normal/../styles.css",       // 正常的回退路径
            "https://example.com/../../secret.txt",           // 恶意路径
            "https://example.com/folder/subfolder/../file.js" // 深层相对路径
        );
        
        for (String url : testUrls) {
            String result = processUrlSafely(url, "/tmp/website");
            System.out.println("URL: " + url);
            System.out.println("  -> 安全路径: " + result);
            System.out.println();
        }
    }
    
    private static String processUrlSafely(String url, String baseDir) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            
            if (path == null || path.isEmpty()) {
                return baseDir + "/index.html";
            }
            
            // 移除开头的斜杠
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // 检查是否为明显的攻击
            if (isObviousAttack(path)) {
                return baseDir + "/safe_files/suspicious_" + Math.abs(url.hashCode()) + ".html";
            }
            
            // 使用Java标准库规范化路径
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path fullPath = basePath.resolve(path).normalize();
            
            // 检查是否在安全范围内
            if (fullPath.startsWith(basePath)) {
                return fullPath.toString();
            } else {
                // 路径超出安全范围，重定向到安全区域
                String fileName = extractFileName(path);
                return baseDir + "/safe_files/" + fileName;
            }
            
        } catch (Exception e) {
            return baseDir + "/safe_files/error_" + Math.abs(url.hashCode()) + ".html";
        }
    }
    
    private static boolean isObviousAttack(String path) {
        // 检查过多的../
        int dotDotCount = 0;
        int index = 0;
        while ((index = path.indexOf("..", index)) != -1) {
            dotDotCount++;
            index += 2;
        }
        
        if (dotDotCount > 3) { // 超过3个../认为是攻击
            return true;
        }
        
        // 检查系统目录
        String lower = path.toLowerCase();
        return lower.contains("etc/") || lower.contains("windows/") || 
               lower.contains("system32") || lower.contains("sys/");
    }
    
    private static String extractFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
        
        // 清理文件名
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (fileName.length() > 50) {
            fileName = fileName.substring(0, 50);
        }
        
        if (!fileName.contains(".")) {
            fileName += ".html";
        }
        
        return fileName;
    }
    
    private static void testRelativePathCalculation() {
        System.out.println("=== 相对路径计算测试 ===");
        
        String[][] testCases = {
            {"/website/pages/about.html", "/website/images/logo.png"},
            {"/website/products/item.html", "/website/css/style.css"},
            {"/website/index.html", "/website/js/script.js"}
        };
        
        for (String[] testCase : testCases) {
            String from = testCase[0];
            String to = testCase[1];
            String relative = calculateRelativePath(from, to);
            
            System.out.println("从: " + from);
            System.out.println("到: " + to);
            System.out.println("相对路径: " + relative);
            System.out.println();
        }
    }
    
    private static String calculateRelativePath(String fromPath, String toPath) {
        try {
            Path from = Paths.get(fromPath).getParent();
            Path to = Paths.get(toPath);
            
            if (from == null) {
                return toPath;
            }
            
            Path relative = from.relativize(to);
            return relative.toString().replace('\\', '/');
        } catch (Exception e) {
            return toPath;
        }
    }
}