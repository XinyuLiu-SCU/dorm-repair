package com.dormrepair.v2.preparation;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.dormrepair.v2.platform.api.V2Id;
@RestController @RequestMapping("/api/v2") @ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PreparationController {
 private final PreparationService s;public PreparationController(PreparationService s){this.s=s;}long id(String s){return V2Id.parse(s).value();}
 @GetMapping("/addresses") Object addresses(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return s.addresses(page,size);}
 @GetMapping("/addresses/{id}") Object address(@PathVariable String id){return s.address(id(id));}
 @PostMapping("/addresses") Object address(@RequestBody Map<String,Object>b){return s.saveAddress(null,b);}
 @PatchMapping("/addresses/{id}") Object address(@PathVariable String id,@RequestBody Map<String,Object>b){return s.saveAddress(id(id),b);}
 @PostMapping("/addresses/{id}/default") Object defaultAddress(@PathVariable String id,@RequestBody Map<String,Object>b){return s.defaultAddress(id(id),b);}
 @PostMapping("/addresses/{id}/disable") Object disable(@PathVariable String id,@RequestBody Map<String,Object>b){return s.disableAddress(id(id),b);}
 @GetMapping("/residencies/me") Object mine(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return s.residencies(false,page,size);}
 @GetMapping("/residencies") Object residencies(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return s.residencies(true,page,size);}
 @PostMapping("/residencies") Object residency(@RequestBody Map<String,Object>b){return s.residency(null,b);}
 @PatchMapping("/residencies/{id}") Object residency(@PathVariable String id,@RequestBody Map<String,Object>b){return s.residency(id(id),b);}
 @GetMapping("/drafts") Object drafts(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return s.drafts(page,size);}
 @GetMapping("/drafts/{id}") Object draft(@PathVariable String id){return s.draft(id(id));}
 @PostMapping("/drafts") Object draft(@RequestBody Map<String,Object>b){return s.saveDraft(null,b);}
 @PutMapping("/drafts/{id}") Object draft(@PathVariable String id,@RequestBody Map<String,Object>b){return s.saveDraft(id(id),b);}
 @GetMapping("/drafts/{id}/revisions") Object revisions(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return s.revisions(id(id),page,size);}
 @PostMapping("/drafts/{id}/abandon") Object abandon(@PathVariable String id,@RequestBody Map<String,Object>b){return s.abandon(id(id),b);}
}
