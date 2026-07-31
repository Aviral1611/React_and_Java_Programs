package backend.exception;

/**
 * Expected request failure that should be returned to the client with a
 * specific HTTP status code.
 */
public final class ApiException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
