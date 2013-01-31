package test.proxy;

import si.pele.friendly.FriendlyProxyFactory;
import si.pele.friendly.MHThrows;

import java.lang.invoke.MethodHandle;

/**
 */
public class SecretRandomAccess_FriendlyProxy implements SecretRandomAccess {

    private final MethodHandle mh1;

    public SecretRandomAccess_FriendlyProxy(MethodHandle mh1) {
        this.mh1 = mh1;
    }

    @Override
    public final int nextInt(SecretRandom tc) {
        try {
            return (int) mh1.invokeExact(tc);
        }
        catch (Throwable t) {
            throw MHThrows.unchecked(t);
        }
    }
}
