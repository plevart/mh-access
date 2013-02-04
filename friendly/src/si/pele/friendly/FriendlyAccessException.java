package si.pele.friendly;

/**
 * An unchecked wrapper for {@link IllegalAccessException} thrown from access checks of reflective operations.
 */
public class FriendlyAccessException extends RuntimeException {
    public FriendlyAccessException(IllegalAccessException iae) {
        super(iae.getMessage(), iae);
    }

    public FriendlyAccessException(String message) {
        super(message, null);
    }

    @Override
    public IllegalAccessException getCause() {
        return (IllegalAccessException) super.getCause();
    }
}
