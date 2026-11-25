package com.dormrepair.v2.platform.system;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v2/system")
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PlatformController {
 private final PlatformInfoService service;
 public PlatformController(PlatformInfoService service){this.service=service;}
 @GetMapping("/info") public PlatformInfoService.Info info(){return service.info();}
}
