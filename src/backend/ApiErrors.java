package backend;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(LocalIdentity.LoginRequired.class)
    public ProblemDetail unauthorized(LocalIdentity.LoginRequired error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The record already exists or a linked record is invalid");
    }
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail forbidden(SecurityException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail databaseUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable");
    }
}
