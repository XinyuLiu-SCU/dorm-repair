package com.dormrepair.v2.images;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.dormrepair.v2.platform.api.V2Id;
@RestController @RequestMapping("/api/v2") @ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class PrivateImageController {
 private final PrivateImageService s;public PrivateImageController(PrivateImageService s){this.s=s;}
 @PostMapping("/images") Object upload(@RequestPart("file") MultipartFile file)throws java.io.IOException{return s.upload(file);}
 @GetMapping("/images/{id}") Object metadata(@PathVariable String id){return s.metadata(V2Id.parse(id).value());}
 @GetMapping("/images/{id}/content") ResponseEntity<byte[]> content(@PathVariable String id)throws java.io.IOException{var c=s.content(V2Id.parse(id).value());return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.contentType())).header("Cache-Control","no-store").header("Content-Disposition","inline; filename=image").header("X-Content-Type-Options","nosniff").body(c.bytes());}
 @PostMapping("/images/{id}/cancel") Object cancel(@PathVariable String id){return s.cancel(V2Id.parse(id).value());}
 @GetMapping("/drafts/{id}/revisions/{number}/images") Object revision(@PathVariable String id,@PathVariable int number){return s.revisionImages(V2Id.parse(id).value(),number);}
 @PostMapping("/images/cleanup") Object cleanup(){return s.cleanup();}
}
