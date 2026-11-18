package com.giki.mess.campus.web;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.giki.mess.campus.domain.Conflict;

@RestControllerAdvice
class Errors {
    record Problem(String message) {}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    Problem invalid(IllegalArgumentException ex) { return new Problem(ex.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST) Problem malformed() { return new Problem("Check the submitted values and date format."); }
    @ExceptionHandler(Conflict.class) @ResponseStatus(HttpStatus.CONFLICT)
    Problem stale(Conflict ex) { return new Problem(ex.getMessage()); }
    @ExceptionHandler(DuplicateKeyException.class) @ResponseStatus(HttpStatus.CONFLICT)
    Problem exists() { return new Problem("A record for that slot already exists."); }
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    Problem missing() { return new Problem("No matching record."); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Problem db() { return new Problem("The database is unavailable. Try again shortly."); }
}
