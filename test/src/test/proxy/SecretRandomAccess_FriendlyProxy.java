package test.proxy;

import si.pele.friendly.Friendly;
import si.pele.friendly.FriendlyProxyFactory;
import si.pele.friendly.MHThrows;

import java.lang.invoke.MethodHandle;

/**
 */
public final class SecretRandomAccess_FriendlyProxy implements SecretRandomAccess {
    private static final MethodHandle mh0 = Friendly.method(SecretRandom.class, "nextInt");
    private final MethodHandle mh1;

    public SecretRandomAccess_FriendlyProxy(MethodHandle mh1) {
        this.mh1 = mh1;
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
