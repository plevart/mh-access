package si.pele.friendly;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public abstract class FriendlyProxy {

    @SuppressWarnings("unchecked")
    public static <I> Class<? extends I> getProxyClass(Class<I> intf) throws IllegalArgumentException {
        return (Class<? extends I>) proxyClassCache.get(Objects.requireNonNull(intf)).proxyClass;
    }

    static class ClassWithTargetMethods {
        final Class<?> proxyClass;
        final Method[] methods;

        ClassWithTargetMethods(Class<?> proxyClass, Method[] methods) {
            this.proxyClass = proxyClass;
            this.methods = methods;
        }
    }

    private static final ClassValue<ClassWithTargetMethods> proxyClassCache = new ClassValue<ClassWithTargetMethods>() {
        @Override
        protected ClassWithTargetMethods computeValue(Class<?> intf) {
            return createProxyClass(intf);
        }
    };

    private static final String proxyClassNamePrefix = "$FriendlyProxy";
    private static final AtomicLong nextUniqueNumber = new AtomicLong();

    private static ClassWithTargetMethods createProxyClass(Class<?> intf) throws IllegalArgumentException {
        if (!intf.isInterface())
            throw new IllegalArgumentException(intf + " is not an interface.");

// TODO:
//        String pkg = Modifier.isPublic(intf.getModifiers())
//                     ? ""
//                     : intf.getClass().getPackage().getName();
//
//        String proxyName = pkg + '.' + proxyClassNamePrefix + nextUniqueNumber.getAndIncrement();
// ...

        try {
            Class<?> proxyClass = Class.forName(intf.getName() + "_FriendlyProxy", false, intf.getClassLoader());
            Method[] methods = null; // TODO
            return new ClassWithTargetMethods(proxyClass, methods);
        }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static final MethodHandle defineClass0MH = Friendly.method(
        Proxy.class, "defineClass0", ClassLoader.class, String.class, byte[].class, int.class, int.class
    );

    private static Class<?> defineClass0(ClassLoader loader, String name,
                                         byte[] b, int off, int len) {
        try {
            return (Class<?>) defineClass0MH.invokeExact(loader, name, b, off, len);
        }
        catch (Throwable t) {
            throw MHThrows.unchecked(t);
        }
    }
}
