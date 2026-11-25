package com.dormrepair.v2.platform.api;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
public final class V2RequestTraceFilter extends OncePerRequestFilter {
 public static final String ATTRIBUTE="v2.requestId";
 @Override protected boolean shouldNotFilter(HttpServletRequest q){return !(q.getRequestURI().equals("/api/v2")||q.getRequestURI().startsWith("/api/v2/"));}
 @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws ServletException,IOException {
  String supplied=q.getHeader("X-Request-Id");
  String id=supplied!=null&&supplied.matches("[A-Za-z0-9_-]{8,64}")?supplied:UUID.randomUUID().toString();
  q.setAttribute(ATTRIBUTE,id);r.setHeader("X-Request-Id",id);
  String previous=MDC.get("requestId");MDC.put("requestId",id);
  try{chain.doFilter(q,r);}finally{if(previous==null)MDC.remove("requestId");else MDC.put("requestId",previous);}
 }
 public static String requestId(HttpServletRequest q){Object id=q.getAttribute(ATTRIBUTE);return id==null?UUID.randomUUID().toString():id.toString();}
}
