package com.dormrepair.controller;

import com.dormrepair.service.RepairQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RepairQueryController {
    private final RepairQueryService service;
    public RepairQueryController(RepairQueryService service) { this.service=service; }
    @GetMapping("/repair-orders/{id}") public Object detail(@PathVariable Long id) { return service.detail(id); }
    @GetMapping("/repair-orders/{id}/records") public Object records(@PathVariable Long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.records(id,page,size); }
    @GetMapping("/repair-orders/{id}/workflow") public Object workflow(@PathVariable Long id) { return service.workflow(id); }
    @GetMapping("/workflow/tasks") public Object tasks(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.pendingTasks(page,size); }
    @GetMapping("/users/repairers") public Object repairers(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.repairers(page,size); }
}
