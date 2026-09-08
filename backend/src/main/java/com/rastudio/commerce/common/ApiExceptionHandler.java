package com.rastudio.commerce.common;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e){return ResponseEntity.status(e.status).body(Map.of("success",false,"message",e.getMessage(),"errorCode",e.code));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){var f=e.getBindingResult().getFieldError(); return ResponseEntity.badRequest().body(Map.of("success",false,"message",f==null?"Validation failed":f.getDefaultMessage(),"errorCode","VALIDATION_ERROR","field",f==null?"":f.getField()));}
 @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class) ResponseEntity<?> badPathValue(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e){return ResponseEntity.badRequest().body(Map.of("success",false,"message","Invalid value for "+e.getName(),"errorCode","VALIDATION_ERROR"));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception e){ return ResponseEntity.status(500).body(Map.of("success",false,"message","An unexpected server error occurred","errorCode","INTERNAL_ERROR")); }
}