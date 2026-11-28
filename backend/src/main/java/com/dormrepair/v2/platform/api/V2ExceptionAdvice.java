package com.dormrepair.v2.platform.api;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.dormrepair.v2")
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name="app.v2.enabled",havingValue="true")
public class V2ExceptionAdvice {
 @ExceptionHandler({org.springframework.web.multipart.MaxUploadSizeExceededException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class}) ResponseEntity<V2Error> upload(Exception ex,HttpServletRequest q){return error(400,"INVALID_IMAGE","Image upload limits exceeded or missing file",List.of(),q);}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<V2Error> validation(MethodArgumentNotValidException ex,HttpServletRequest q){return error(400,"VALIDATION_FAILED","Invalid request",ex.getBindingResult().getFieldErrors().stream().map(e->new V2Error.FieldError(e.getField(),e.getDefaultMessage())).toList(),q);}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<V2Error> invalid(Exception ex,HttpServletRequest q){return error(400,"INVALID_REQUEST","Invalid request",List.of(),q);}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class) ResponseEntity<V2Error> status(org.springframework.web.server.ResponseStatusException ex,HttpServletRequest q){int s=ex.getStatusCode().value();String code=switch(s){case 400->"INVALID_REQUEST";case 401->"UNAUTHORIZED";case 403->"FORBIDDEN";case 404->"NOT_FOUND";case 409->"STATE_CONFLICT";case 503->"SERVICE_UNAVAILABLE";default->"REQUEST_FAILED";};return error(s,code,"Request could not be completed",List.of(),q);}
 @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class,com.dormrepair.exception.WorkflowConflictException.class}) ResponseEntity<V2Error> conflict(Exception ex,HttpServletRequest q){return error(409,"STATE_CONFLICT","Reload before retrying",List.of(),q);}
 @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class,org.springframework.transaction.CannotCreateTransactionException.class}) ResponseEntity<V2Error> unavailable(Exception ex,HttpServletRequest q){return error(503,"SERVICE_UNAVAILABLE","Service temporarily unavailable",List.of(),q);}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<V2Error> integrity(Exception ex,HttpServletRequest q){return error(409,"STATE_CONFLICT","Conflicting resource state",List.of(),q);}
 @ExceptionHandler(Exception.class) ResponseEntity<V2Error> unexpected(Exception ex,HttpServletRequest q){org.slf4j.LoggerFactory.getLogger(V2ExceptionAdvice.class).error("V2 failure; requestId={}, exceptionType={}",V2RequestTraceFilter.requestId(q),ex.getClass().getName());java.util.Arrays.stream(ex.getStackTrace()).filter(frame->frame.getClassName().startsWith("com.dormrepair")).limit(5).forEach(frame->org.slf4j.LoggerFactory.getLogger(V2ExceptionAdvice.class).error("Failure location {}",frame));return error(500,"INTERNAL_ERROR","Unexpected server error",List.of(),q);}
 private ResponseEntity<V2Error> error(int status,String code,String message,List<V2Error.FieldError> fields,HttpServletRequest q){return ResponseEntity.status(status).body(new V2Error(status,code,message,V2RequestTraceFilter.requestId(q),fields));}
}
