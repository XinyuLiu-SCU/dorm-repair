package com.dormrepair.v2.platform.api;
import com.fasterxml.jackson.annotation.JsonValue;
/** Explicit DTO ID type; does not change V1 Long serialization. */
public record V2Id(long value) {
 public V2Id {if(value<=0)throw new IllegalArgumentException("ID must be positive");}
 @JsonValue public String asString(){return Long.toString(value);}
 public static V2Id parse(String s){if(s==null||!s.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("Invalid ID");try{return new V2Id(Long.parseLong(s));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid ID");}}
}
