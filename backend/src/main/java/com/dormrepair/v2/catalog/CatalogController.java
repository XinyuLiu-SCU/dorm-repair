package com.dormrepair.v2.catalog;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.dormrepair.v2.platform.api.V2Id;
@RestController @RequestMapping("/api/v2") @ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class CatalogController {
 private final CatalogService service;public CatalogController(CatalogService s){service=s;}
 @GetMapping("/catalog/admin/{kind}") public Object list(@PathVariable String kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(kind,page,size);}
 @GetMapping("/catalog/admin/{kind}/{id}") public Object detail(@PathVariable String kind,@PathVariable String id){return service.detail(kind,V2Id.parse(id).value());}
 @PostMapping("/catalog/admin/{kind}") public Object create(@PathVariable String kind,@RequestBody Map<String,Object> body){return service.save(kind,null,body);}
 @PatchMapping("/catalog/admin/{kind}/{id}") public Object update(@PathVariable String kind,@PathVariable String id,@RequestBody Map<String,Object> body){return service.save(kind,V2Id.parse(id).value(),body);}
 public record Items(int version,List<String> itemIds){}
 @PutMapping("/catalog/admin/facility-types/{id}/items") public Object items(@PathVariable String id,@RequestBody Items body){if(body.itemIds()==null)throw CatalogService.error(400);return service.bind(V2Id.parse(id).value(),body.version(),body.itemIds());}
 @GetMapping("/catalog/select/{kind}") public Object select(@PathVariable String kind,@RequestParam(required=false) String parentId,@RequestParam(defaultValue="false") boolean quick){return service.select(kind,parentId==null?null:V2Id.parse(parentId).value(),quick);}
 @GetMapping("/catalog/tree") public Object tree(){return service.tree();}
 @GetMapping("/facilities/{id}") public Object facility(@PathVariable String id){return service.visibleFacility(V2Id.parse(id).value());}
 @GetMapping("/facilities") public Object facilities(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.facilities(page,size);}
 @GetMapping("/facilities/resolve/{token}") public Object resolve(@PathVariable String token){return service.resolve(token);}
 @GetMapping(value="/catalog/admin/facilities/{id}/qr.png",produces="image/png") public byte[] qrImage(@PathVariable String id)throws Exception{String payload=service.qr(V2Id.parse(id).value());var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(payload,com.google.zxing.BarcodeFormat.QR_CODE,400,400);var image=new java.awt.image.BufferedImage(400,400,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int x=0;x<400;x++)for(int y=0;y<400;y++)image.setRGB(x,y,matrix.get(x,y)?0:0xffffff);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);return out.toByteArray();}
 @GetMapping("/catalog/admin/facilities/{id}/qr") public Object qr(@PathVariable String id){return Map.of("payload",service.qr(V2Id.parse(id).value()));}
}
