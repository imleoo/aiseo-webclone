package com.jiwu.aiseo.siteclone.controller;

import com.jiwu.aiseo.siteclone.dto.CloneRequest;
import com.jiwu.aiseo.siteclone.dto.CloneResponse;
import com.jiwu.aiseo.siteclone.service.SiteCloneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clone")
@RequiredArgsConstructor
public class SiteCloneController {

    private final SiteCloneService siteCloneService;

    @PostMapping
    public ResponseEntity<CloneResponse> startClone(@RequestBody CloneRequest request) {
        CloneResponse response = siteCloneService.startClone(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<CloneResponse> getTaskStatus(@PathVariable String taskId) {
        CloneResponse response = siteCloneService.getTaskStatus(taskId);
        if (response.getErrorMessage() != null && response.getErrorMessage().contains("Task not found")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
