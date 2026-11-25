package com.dormrepair.v2.platform.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class V2ErrorContractTest {
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new TestOnlyController()).setControllerAdvice(new V2ExceptionAdvice()).addFilters(new V2RequestTraceFilter()).build();}
 @RestController static class TestOnlyController {
  @GetMapping("/api/v2/test-errors/status/{status}") void status(@PathVariable int status){throw new ResponseStatusException(HttpStatusCode.valueOf(status),"sensitive-internal-details");}
  @GetMapping("/api/v2/test-errors/unexpected") void unexpected(){throw new IllegalStateException("sensitive-internal-details");}
  @PostMapping("/api/v2/test-errors/validation") void validation(@Valid @RequestBody Input input){}
  @GetMapping("/api/v2/test-errors/parameter") void parameter(@RequestParam int page){}
  record Input(@NotBlank String name){}
 }
 @Test void statusCodesUseStableRedactedV2Contract()throws Exception{
  for(int status:new int[]{400,401,403,404,409,503})mvc.perform(get("/api/v2/test-errors/status/"+status).header("X-Request-Id","contract_trace_123")).andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.requestId").value("contract_trace_123")).andExpect(jsonPath("$.message").value("Request could not be completed")).andExpect(jsonPath("$.fieldErrors").isArray());
 }
 @Test void unexpectedErrorNeverReturnsInternalExceptionMessage()throws Exception{mvc.perform(get("/api/v2/test-errors/unexpected")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.message").value("Unexpected server error"));}
 @Test void validationIdentifiesFieldWithoutRejectedValue()throws Exception{mvc.perform(post("/api/v2/test-errors/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fieldErrors[0].field").value("name")).andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());}
 @Test void malformedBodyAndParameterUse400()throws Exception{mvc.perform(post("/api/v2/test-errors/validation").contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));mvc.perform(get("/api/v2/test-errors/parameter?page=bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));}
}
