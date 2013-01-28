package si.pele.friendly;

/**
 * Unchecked exception thrown from {@link Friendly} factory methods
 * wrapping any {@link ReflectiveOperationException} thrown.
 */
public class FriendlyException extends RuntimeException {
    FriendlyException(ReflectiveOperationException cause) {
        super(cause.getMessage(), cause);
    }

    @Override
    public ReflectiveOperationException getCause() {
        return (ReflectiveOperationException) super.getCause();
    }
}
