package com.dormrepair.v2.identity;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import com.dormrepair.v2.platform.api.V2Id;
public final class Inputs {
 private Inputs(){}
 public static class Strict { @JsonAnySetter public void unknown(String field,Object value){throw new IllegalArgumentException("Unknown request field");} }
 public static class Version extends Strict { @NotNull @PositiveOrZero public Long version; }
 public static class Login extends Strict { @NotBlank @Size(max=100) public String username; @NotBlank @Size(max=72) public String password; }
 public static class Basics extends Version {
  @NotBlank @Size(max=50) public String realName;
  @Size(max=20) private String phone;private boolean phoneProvided;
  @com.fasterxml.jackson.annotation.JsonSetter("phone") public void setPhone(String value){phone=value;phoneProvided=true;}
  public String phoneValue(){return phone;}public boolean phoneProvided(){return phoneProvided;}
 }
 public static class CreateUser extends Strict {
  @NotBlank @Pattern(regexp="[A-Za-z0-9_][A-Za-z0-9_.-]{2,49}") public String username;
  @NotBlank @Size(max=72) public String password;
  @NotBlank @Size(max=50) public String realName;
  @Size(max=20) public String phone;
  @Pattern(regexp="STUDENT|REPAIRER|ADMIN") public String legacyRole="STUDENT";
  @NotNull @Size(max=50) @JsonDeserialize(contentUsing=IdText.class) public List<String> roleIds;
 }
 public static class UserRoles extends Version { @NotNull @Size(max=50) @JsonDeserialize(contentUsing=IdText.class) public List<String> roleIds; }
 public static class RoleCreate extends Strict {
  @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{1,39}") public String code;
  @NotBlank @Size(max=80) public String name;
  @NotNull @Size(max=100) public List<@NotBlank @Size(max=80) String> permissionCodes;
 }
 public static class RoleEdit extends Version { @NotBlank @Size(max=80) public String name; @NotNull public Boolean enabled; }
 public static class RolePermissions extends Version { @NotNull @Size(max=100) public List<@NotBlank @Size(max=80) String> permissionCodes; }
 public static class PasswordChange extends Version { @NotBlank @Size(max=72) public String oldPassword; @NotBlank @Size(max=72) public String newPassword; }
 public static class PasswordReset extends Version { @NotBlank @Size(max=72) public String newPassword; }
 public static final class IdText extends JsonDeserializer<String> {
  @Override public String deserialize(JsonParser p,DeserializationContext c)throws java.io.IOException {
   if(!p.hasToken(JsonToken.VALUE_STRING))throw JsonMappingException.from(p,"V2 IDs must be strings");
   try{return V2Id.parse(p.getText()).asString();}catch(IllegalArgumentException e){throw JsonMappingException.from(p,"Invalid V2 ID");}
  }
 }
}
