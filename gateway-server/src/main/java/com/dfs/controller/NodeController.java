package com.dfs.controller;

import com.dfs.model.StorageNode;
import com.dfs.service.LoadBalancerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final LoadBalancerService loadBalancerService;

    /** Admin: list all nodes with health status */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StorageNode>> listNodes() {
        return ResponseEntity.ok(loadBalancerService.getAllNodes());
    }

    /** Admin: list only currently healthy nodes */
    @GetMapping("/healthy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StorageNode>> listHealthyNodes() {
        return ResponseEntity.ok(loadBalancerService.getHealthyNodes());
    }
}
