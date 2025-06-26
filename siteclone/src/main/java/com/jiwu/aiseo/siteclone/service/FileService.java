package com.jiwu.aiseo.siteclone.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileService {

    /**
     * 获取克隆任务的输出目录中的所有文件
     *
     * @param outputDir 输出目录路径
     * @return 文件路径列表
     */
    public List<String> listFiles(String outputDir) {
        try (Stream<Path> walk = Files.walk(Paths.get(outputDir))) {
            return walk.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list files in directory: {}", outputDir, e);
            throw new RuntimeException("Failed to list files", e);
        }
    }

    /**
     * 加载指定路径的文件作为资源
     *
     * @param filePath 文件路径
     * @return 文件资源
     */
    public Resource loadFileAsResource(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Resource resource = new UrlResource(path.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + filePath, e);
        }
    }

    /**
     * 获取文件的MIME类型
     *
     * @param filePath 文件路径
     * @return MIME类型
     */
    public String getContentType(String filePath) {
        try {
            return Files.probeContentType(Paths.get(filePath));
        } catch (IOException e) {
            log.error("Failed to determine content type for file: {}", filePath, e);
            return "application/octet-stream";
        }
    }
}
