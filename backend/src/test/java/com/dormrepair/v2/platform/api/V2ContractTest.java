package com.dormrepair.v2.platform.api;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;
class V2ContractTest {
 @Test void v2IdsPreservePrecisionAndV1NumbersRemainNumbers()throws Exception{var json=new ObjectMapper();var tree=json.readTree(json.writeValueAsString(Map.of("v2",new V2Id(Long.MAX_VALUE),"v1",Long.MAX_VALUE)));assertTrue(tree.get("v2").isTextual());assertEquals(Long.toString(Long.MAX_VALUE),tree.get("v2").asText());assertTrue(tree.get("v1").isIntegralNumber());}
 @Test void idsRejectNonCanonicalAndOverflow(){for(String id:new String[]{"0","-1","01"," 1","1.0","9223372036854775808"})assertThrows(IllegalArgumentException.class,()->V2Id.parse(id));assertEquals(1,V2Id.parse("1").value());}
 @Test void pageBoundariesAndLargeOffset(){assertEquals(214748364700L,new V2Page.Request(Integer.MAX_VALUE,100).offset());for(int size:new int[]{0,101,-1})assertThrows(IllegalArgumentException.class,()->new V2Page.Request(0,size));assertThrows(IllegalArgumentException.class,()->new V2Page.Request(-1,20));}
 @Test void pageIsImmutableAndRejectsInconsistentRows(){var input=new ArrayList<>(List.of("a"));var page=new V2Page<>(input,0,20,1);input.add("b");assertEquals(List.of("a"),page.items());assertThrows(IllegalArgumentException.class,()->new V2Page<>(List.of("a","b"),0,1,2));assertThrows(IllegalArgumentException.class,()->new V2Page<>(List.of("a"),0,20,0));}
 @Test void traceRejectsHeaderInjectionAndClearsMdc()throws Exception{
  var q=new MockHttpServletRequest("GET","/api/v2/system/info");q.addHeader("X-Request-Id","not a safe trace id");var r=new MockHttpServletResponse();MDC.put("requestId","outer");try{new V2RequestTraceFilter().doFilter(q,r,(request,response)->{assertTrue(MDC.get("requestId").matches("[a-f0-9-]{36}"));assertEquals(r.getHeader("X-Request-Id"),q.getAttribute(V2RequestTraceFilter.ATTRIBUTE));});assertEquals("outer",MDC.get("requestId"));}finally{MDC.clear();}
 }
 @Test void v2RootAlsoGetsATraceId()throws Exception{var q=new MockHttpServletRequest("GET","/api/v2");var r=new MockHttpServletResponse();new V2RequestTraceFilter().doFilter(q,r,(req,res)->{});assertNotNull(r.getHeader("X-Request-Id"));}
 @Test void tracingDoesNotChangeV1Response()throws Exception{var q=new MockHttpServletRequest("GET","/api/auth/me");var r=new MockHttpServletResponse();new V2RequestTraceFilter().doFilter(q,r,(req,res)->{});assertNull(r.getHeader("X-Request-Id"));}
}
