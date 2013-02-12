/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package reflect;

import sun.reflect.MethodAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
final class MHStaticMethodAccessor implements MethodAccessor {

    private final MethodHandle mh;

    MHStaticMethodAccessor(MethodHandle targetMh) {
        this.mh = targetMh.asSpreader(Object[].class, targetMh.type().parameterCount());
    }

    @Override
    public Object invoke(Object _ignored, Object[] args) throws IllegalArgumentException, InvocationTargetException {
        try {
            return mh.invoke(args);
        }
        catch (WrongMethodTypeException wmte) {
            throw new IllegalArgumentException(wmte.getMessage(), wmte);
        }
        catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }
}
