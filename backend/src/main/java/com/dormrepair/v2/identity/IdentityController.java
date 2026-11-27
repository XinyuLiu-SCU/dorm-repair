package com.dormrepair.v2.identity;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import com.dormrepair.v2.platform.api.V2Id;
@RestController
@RequestMapping("/api/v2")
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class IdentityController {
 private final RbacService service;
 public IdentityController(RbacService service){this.service=service;}
 private long id(String id){return V2Id.parse(id).value();}
 @PostMapping("/auth/login") public Object login(@Valid @RequestBody Inputs.Login input){return service.login(input.username,input.password);}
 @GetMapping("/auth/me") public Object me(){return service.me();}
 @GetMapping("/profile") public Object profile(){return service.profile();}
 @PatchMapping("/profile") public Object profile(@Valid @RequestBody Inputs.Basics input){return service.profile(input);}
 @PostMapping("/profile/password") public Object password(@Valid @RequestBody Inputs.PasswordChange input){return service.password(input);}
 @GetMapping("/users") public Object users(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String keyword){return service.users(page,size,keyword);}
 @PostMapping("/users") public Object createUser(@Valid @RequestBody Inputs.CreateUser input){return service.createUser(input);}
 @GetMapping("/users/{id}") public Object user(@PathVariable String id){return service.user(id(id));}
 @PatchMapping("/users/{id}") public Object editUser(@PathVariable String id,@Valid @RequestBody Inputs.Basics input){return service.editUser(id(id),input);}
 @PostMapping("/users/{id}/enable") public Object enable(@PathVariable String id,@Valid @RequestBody Inputs.Version input){return service.enabled(id(id),input,true);}
 @PostMapping("/users/{id}/disable") public Object disable(@PathVariable String id,@Valid @RequestBody Inputs.Version input){return service.enabled(id(id),input,false);}
 @PostMapping("/users/{id}/reset-password") public Object reset(@PathVariable String id,@Valid @RequestBody Inputs.PasswordReset input){return service.resetPassword(id(id),input);}
 @PutMapping("/users/{id}/roles") public Object userRoles(@PathVariable String id,@Valid @RequestBody Inputs.UserRoles input){return service.userRoles(id(id),input);}
 @GetMapping("/roles") public Object roles(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.roles(page,size);}
 @PostMapping("/roles") public Object createRole(@Valid @RequestBody Inputs.RoleCreate input){return service.createRole(input);}
 @GetMapping("/roles/{id}") public Object role(@PathVariable String id){return service.role(id(id));}
 @PatchMapping("/roles/{id}") public Object editRole(@PathVariable String id,@Valid @RequestBody Inputs.RoleEdit input){return service.editRole(id(id),input);}
 @PostMapping("/roles/{id}/enable") public Object enableRole(@PathVariable String id,@Valid @RequestBody Inputs.Version input){return service.roleEnabled(id(id),input,true);}
 @PostMapping("/roles/{id}/disable") public Object disableRole(@PathVariable String id,@Valid @RequestBody Inputs.Version input){return service.roleEnabled(id(id),input,false);}
 @PutMapping("/roles/{id}/permissions") public Object permissions(@PathVariable String id,@Valid @RequestBody Inputs.RolePermissions input){return service.rolePermissions(id(id),input);}
 @GetMapping("/permissions") public Object permissions(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.permissions(page,size);}
 @GetMapping("/audit-events") public Object audit(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.audits(page,size);}
}
