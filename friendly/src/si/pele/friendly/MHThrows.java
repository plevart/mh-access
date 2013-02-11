/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package si.pele.friendly;

import java.lang.reflect.UndeclaredThrowableException;

/**
 * Utility methods for cultivating checked / unchecked exceptions thrown by MethodHandle invocations
 */
public class MHThrows {

    public static Nothing unchecked(
        Throwable t
    ) {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new UndeclaredThrowableException(t, t.getMessage());
    }

    public static <CT1 extends Exception> Nothing checked(
        Throwable t,
        Class<CT1> checkedType1
    ) throws CT1 {
        if (checkedType1.isInstance(t))
            throw checkedType1.cast(t);
        else
            throw unchecked(t);
    }

    public static <CT1 extends Exception, CT2 extends Exception> Nothing checked(
        Throwable t,
        Class<CT1> checkedType1,
        Class<CT2> checkedType2
    ) throws CT1, CT2 {
        if (checkedType1.isInstance(t))
            throw checkedType1.cast(t);
        else if (checkedType2.isInstance(t))
            throw checkedType2.cast(t);
        else
            throw unchecked(t);
    }

    public static <CT1 extends Exception, CT2 extends Exception, CT3 extends Exception> Nothing checked(
        Throwable t,
        Class<CT1> checkedType1,
        Class<CT2> checkedType2,
        Class<CT3> checkedType3
    ) throws CT1, CT2, CT3 {
        if (checkedType1.isInstance(t))
            throw checkedType1.cast(t);
        else if (checkedType2.isInstance(t))
            throw checkedType2.cast(t);
        else if (checkedType3.isInstance(t))
            throw checkedType3.cast(t);
        else
            throw unchecked(t);
    }

    /**
     * An unchecked exception that can never be constructed
     */
    public static final class Nothing extends RuntimeException {
        private Nothing() {
            throw new AssertionError("No instances");
        }
    }
}
