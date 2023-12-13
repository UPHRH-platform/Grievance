package org.upsmf.grievance.exception.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.upsmf.grievance.dto.ErrorResponseDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.runtime.InvalidRequestException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ControllerAdvice
public class ExceptionHandler4xxController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler4xxController.class);

    /**
     * It generates all error list that occurs when argument validation fails.
     * @param exception
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, List<String>>> handleValidationErrors(MethodArgumentNotValidException exception) {

        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        return new ResponseEntity<>(getErrorsMap(errors), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }



//    /**
//     * Maintain error message uniformity with custom exception
//     * @param exception
//     * @return
//     */
//    @ExceptionHandler(MethodArgumentNotValidException.class)
//    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException exception) {
//
//        List<String> exceptionMessage = exception.getBindingResult().getFieldErrors().stream()
//                .map(FieldError::getDefaultMessage)
//                .collect(Collectors.toList());
//
//        Map<String, Object> errorResponse = new HashMap<>();
//
//        errorResponse.put("error_message", exceptionMessage);
//
//        return new ResponseEntity<>(errorResponse, new HttpHeaders(), HttpStatus.BAD_REQUEST);
//    }

    /**
     * Exception handler for Invalid request exception
     * @param e
     * @return
     */
    @ExceptionHandler(value = InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDto handleInvalidRequestException(InvalidRequestException e) {
        return new ErrorResponseDto(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundException(CustomException exception) {
        return new ResponseEntity<>(generateErrorMap(exception), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }


    private Map<String, List<String>> getErrorsMap(List<String> errors) {
        Map<String, List<String>> errorResponse = new HashMap<>();
        errorResponse.put("errors", errors);
        return errorResponse;
    }

    private Map<String, Object> generateErrorMap(CustomException exception) {
        Map<String, Object> errorResponse = new HashMap<>();
        String errorCode = exception.getErrorCode() != null ? exception.getErrorCode().name() : "NA";

        errorResponse.put("error_code", errorCode);
        errorResponse.put("error_message", exception.getMessage());
        errorResponse.put("description", exception.getDescription());

        return errorResponse;
    }
}
