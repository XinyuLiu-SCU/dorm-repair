package com.dormrepair.v2.core;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
@RestController @RequestMapping("/api/v2") @ConditionalOnProperty(name="app.v2.core.enabled",havingValue="true")
public class CoreController {
 final CoreService service;final com.dormrepair.v2.images.PrivateImageService images;public CoreController(CoreService s,com.dormrepair.v2.images.PrivateImageService i){service=s;images=i;}
 long id(String s){return com.dormrepair.v2.platform.api.V2Id.parse(s).value();}
 @PostMapping("/drafts/{id}/submit") Object submit(@PathVariable String id,@RequestBody Map<String,Object>b){return service.submit(id(id),b);}
 @GetMapping("/orders") Object list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String stage){return service.list(page,size,stage);}
 @GetMapping("/orders/{id}") Object detail(@PathVariable String id){return service.detail(id(id));}
 @PostMapping("/orders/{id}/review") Object review(@PathVariable String id,@RequestBody Map<String,Object>b){return service.review(id(id),b);}
 @PostMapping("/orders/{id}/assign") Object assign(@PathVariable String id,@RequestBody Map<String,Object>b){return service.assign(id(id),b);}
 @PostMapping("/orders/{id}/appointment") Object appointment(@PathVariable String id,@RequestBody Map<String,Object>b){return service.appointment(id(id),b);}
 @PostMapping("/orders/{id}/appointment/decision") Object decision(@PathVariable String id,@RequestBody Map<String,Object>b){return service.decideAppointment(id(id),b);}
 @PostMapping("/orders/{id}/start") Object start(@PathVariable String id,@RequestBody Map<String,Object>b){return service.start(id(id),b);}
 @PostMapping("/orders/{id}/result") Object result(@PathVariable String id,@RequestBody Map<String,Object>b){return service.result(id(id),b);}
 @PostMapping("/orders/{id}/exception") Object exception(@PathVariable String id,@RequestBody Map<String,Object>b){return service.exception(id(id),b);}
 @PostMapping("/orders/{id}/exception/handle") Object handle(@PathVariable String id,@RequestBody Map<String,Object>b){return service.handleException(id(id),b);}
 @PostMapping("/orders/{id}/acceptance") Object accept(@PathVariable String id,@RequestBody Map<String,Object>b){return service.accept(id(id),b);}
 @PostMapping("/orders/{id}/evaluation") Object evaluate(@PathVariable String id,@RequestBody Map<String,Object>b){return service.evaluate(id(id),b);}
 @GetMapping("/orders/{id}/recommendations") Object recommend(@PathVariable String id){return service.recommendations(id(id));}
 @GetMapping("/orders/{id}/workflow") Object workflow(@PathVariable String id){return service.workflow(id(id));}
 @GetMapping("/orders/{id}/bpmn") ResponseEntity<byte[]> bpmn(@PathVariable String id){return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).cacheControl(CacheControl.noStore()).body(service.bpmn(id(id)));}
 @GetMapping("/orders/{id}/events") Object events(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.events(id(id),page,size);}
 @GetMapping("/workflow/tasks") Object inbox(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.inbox(page,size);}
 @GetMapping("/statistics") Object stats(){return service.stats();}
 @PutMapping("/workers/{id}") Object worker(@PathVariable String id,@RequestBody Map<String,Object>b){return service.saveWorker(id(id),b,false);}
 @PutMapping("/workers/me/availability") Object availability(@RequestBody Map<String,Object>b){return service.saveOwnAvailability(b);}
 @GetMapping("/workers") Object workers(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.workers(false,page,size);}
 @GetMapping("/workers/me") Object own(){return service.workers(true,0,20);}

 @GetMapping("/orders/{id}/images") Object images(@PathVariable String id){return service.images(id(id));}
 @GetMapping("/orders/{id}/images/{image}/content") ResponseEntity<byte[]> content(@PathVariable String id,@PathVariable String image)throws java.io.IOException{var c=images.orderContent(id(id),id(image));return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=image").body(c.bytes());}

 @GetMapping("/orders/{id}/records") Object records(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.records(id(id),page,size,false);}
 @GetMapping("/orders/{id}/appointments") Object appointments(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.records(id(id),page,size,true);}
}