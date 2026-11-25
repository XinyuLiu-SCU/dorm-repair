package com.dormrepair.v2.platform.api;
import java.util.List;
/** Repositories must apply access control and SQL offset/limit before constructing a page. */
public record V2Page<T>(List<T> items,int page,int size,long total) {
 public V2Page {items=List.copyOf(items);new Request(page,size);if(total<0||items.size()>size||total<items.size())throw new IllegalArgumentException("Invalid page result");}
 public record Request(int page,int size){public Request {if(page<0||size<1||size>100)throw new IllegalArgumentException("page >= 0 and 1 <= size <= 100 required");}public long offset(){return Math.multiplyExact((long)page,size);}}
}
