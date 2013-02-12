/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package reflect;

import sun.reflect.MethodAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author peter
 */
public class MHAccessors {
    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    public static MethodAccessor newMethodAccessor(Method m) {
        try {
            MethodHandle methodHandle = lookup.unreflect(m);
            if (Modifier.isStatic(m.getModifiers()))
                return new MHStaticMethodAccessor(methodHandle);
            else
                return new MHInstanceMethodAccessor(methodHandle);
        }
        catch (IllegalAccessException e) {
            throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
        }
    }
}
