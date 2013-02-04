package test.proxy;

import si.pele.friendly.Friendly;
import si.pele.friendly.MHThrows;

import java.lang.invoke.MethodHandle;

/**
 */
public final class SecretRandomAccess_FriendlyProxy implements SecretRandomAccess {

    private static final SecretRandomAccess_FriendlyProxy INSTANCE = new SecretRandomAccess_FriendlyProxy();

    private static final MethodHandle mh0 = Friendly.method(SecretRandom.class, "nextInt");

    private SecretRandomAccess_FriendlyProxy() {
        if (INSTANCE != null)
            throw new Error("No unauthorized instances");
    }

    @Override
    public int nextInt(SecretRandom tc) {
        try {
            return (int) mh0.invokeExact(tc);
        }
        catch (Throwable t) {
            throw MHThrows.unchecked(t);
        }
    }
}
