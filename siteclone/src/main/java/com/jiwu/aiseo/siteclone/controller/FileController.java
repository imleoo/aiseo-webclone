package com.jiwu.aiseo.siteclone.controller;

import com.jiwu.aiseo.siteclone.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 列出指定目录中的所有文件
     *
     * @param directory 目录路径
     * @return 文件路径列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> listFiles(@RequestParam String directory) {
        List<String> files = fileService.listFiles(directory);
        return ResponseEntity.ok(files);
    }

    /**
     * 下载指定路径的文件
     *
     * @param filePath 文件路径
     * @return 文件资源
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {
        Resource resource = fileService.loadFileAsResource(filePath);
        String contentType = fileService.getContentType(filePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * 查看指定路径的文件内容
     *
     * @param filePath 文件路径
     * @return 文件资源
     */
    @GetMapping("/view")
    public ResponseEntity<Resource> viewFile(@RequestParam String filePath) {
        Resource resource = fileService.loadFileAsResource(filePath);
        String contentType = fileService.getContentType(filePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
