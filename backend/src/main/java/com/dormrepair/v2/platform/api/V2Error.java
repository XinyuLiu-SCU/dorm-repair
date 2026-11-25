package com.dormrepair.v2.platform.api;
import java.util.List;
public record V2Error(int status,String code,String message,String requestId,List<FieldError> fieldErrors) {
 public record FieldError(String field,String message) {}
}
