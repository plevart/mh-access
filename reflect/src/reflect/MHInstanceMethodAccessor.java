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
final class MHInstanceMethodAccessor implements MethodAccessor {

    private final MethodHandle mh;

    MHInstanceMethodAccessor(MethodHandle targetMh) {
        this.mh = targetMh.asSpreader(Object[].class, targetMh.type().parameterCount() - 1);
    }

    @Override
    public Object invoke(Object obj, Object[] args) throws IllegalArgumentException, InvocationTargetException {
        try {
            return mh.invoke(obj, args);
        }
        catch (WrongMethodTypeException wmte) {
            throw new IllegalArgumentException(wmte.getMessage(), wmte);
        }
        catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }
}
