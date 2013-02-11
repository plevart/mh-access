/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package si.pele.friendly;

import sun.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

/**
 * A {@link MethodHandles.Lookup} facade that uses @{@link Friend} annotation
 * to govern access to method handles for otherwise prohibited constructors ({@link #constructor}),
 * methods ({@link #method}) or fields ({@link #getter}, {@link #setter}).<p>
 * It also provides a factory for proxies that invoke otherwise prohibited target methods
 * ({@link #proxy})...
 */
public class Friendly {
    private static final MethodHandles.Lookup lookup;

    static {
        lookup = AccessController.doPrivileged(new PrivilegedAction<MethodHandles.Lookup>() {
            @Override
            public MethodHandles.Lookup run() {
                try {
                    Field lookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                    lookupField.setAccessible(true);
                    return (MethodHandles.Lookup) lookupField.get(null);
                }
                catch (IllegalAccessException | NoSuchFieldException e) {
                    throw new Error(e);
                }
            }
        });
    }

    public static MethodHandle method(Class<?> rcv, String methodName, Class<?>... parameterTypes)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflect(
                    accessible(
                        AccessController.doPrivileged(
                            new GetDeclaredMethodAction(rcv, methodName, parameterTypes)
                        ),
                        cc
                    )
                );
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle constructor(Class<?> rcv, Class<?>... parameterTypes)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectConstructor(
                    accessible(
                        AccessController.doPrivileged(
                            new GetDeclaredConstructorAction(rcv, parameterTypes)
                        ),
                        cc
                    )
                );
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle getter(Class<?> rcv, String fieldName)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectGetter(
                    accessible(
                        AccessController.doPrivileged(
                            new GetDeclaredFieldAction(rcv, fieldName)
                        ),
                        cc
                    )
                );
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle setter(Class<?> rcv, String fieldName)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectSetter(
                    accessible(
                        AccessController.doPrivileged(
                            new GetDeclaredFieldAction(rcv, fieldName)
                        ),
                        cc
                    )
                );
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    // public MethodHandle lookup methods that can only be accessed from friendly proxies' static initializer(s)

    public static MethodHandle findVirtual(Class<?> refc, String name, String methodTypeDescriptor)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        checkProxyClassBeingInitialized(cc);
        ClassLoader cl = cc.getClassLoader();
        try {
            MethodType methodType = MethodType.fromMethodDescriptorString(methodTypeDescriptor, cl);
            return lookup.findVirtual(refc, name, methodType);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    private static void checkProxyClassBeingInitialized(Class<?> cc) throws FriendlyAccessException {
        if (cc != PROXY_CLASS_BEING_INITIALIZED.get())
            throw new FriendlyAccessException("Not called from friendly proxy class initializer");
    }

    private static final ClassValue<FriendlyProxyFactory<?>> PROXY_FACTORY_CV = new ClassValue<FriendlyProxyFactory<?>>() {
        @Override
        protected FriendlyProxyFactory<?> computeValue(Class<?> intf) {
            return new FriendlyProxyFactory<>(intf);
        }
    };

    private static final ClassValue<?> PROXY_INSTANCE_CV = new ProxyInstanceClassValue();

    private static class ProxyInstanceClassValue extends ClassValue<Object> {
        @Override
        protected Object computeValue(Class<?> proxyClass) {
            MethodHandle mh = getter(proxyClass, FriendlyProxyFactory.PROXY_INSTANCE_FIELD_NAME);
            try {
                return proxyClass.cast(mh.invoke());
            }
            catch (Throwable t) {
                throw MHThrows.unchecked(t);
            }
        }
    }

    private static final ThreadLocal<Class<?>> PROXY_CLASS_BEING_INITIALIZED = new ThreadLocal<>();

    public static <I> I proxy(Class<I> intf) throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        @SuppressWarnings("unchecked")
        FriendlyProxyFactory<? extends I> proxyFactory = (FriendlyProxyFactory<? extends I>) PROXY_FACTORY_CV.get(intf);

        // obtain proxy class - possibly uninitialized yet
        Class<? extends I> proxyClass = proxyFactory.getProxyClass();

        // validate access to target methods
        for (Method m : proxyFactory.getTargetMethods()) {
            if (!checkAccess(m, cc))
                throw new FriendlyAccessException("Class: " + cc.getName() + " has no access to method: " + m);
        }

        // establish thread-local context for eventual proxy class initialization
        PROXY_CLASS_BEING_INITIALIZED.set(proxyClass);
        // obtain sole proxy instance
        // this will force class initialization if not yet initialized
        try {
            I proxy = (I) PROXY_INSTANCE_CV.get(proxyClass);
            return proxy;
        }
        finally {
            // clear thread-local context
            PROXY_CLASS_BEING_INITIALIZED.remove();
        }
    }

    /**
     * Modifies the "accessible" flag of given {@code accessibleObject} according to permissions
     * of the {@code callerClass} governed among other things by @{@link Friend} annotations attached to the
     * field/method/constructor.
     *
     * @param accessibleObject field, method or constructor
     * @param callerClass      the class directly calling into public {@link Friendly} methods
     * @param <A>              the type of accessible object
     * @return the same {@code accessibleObject} but with "accessible" flag possibly modified
     */
    private static <A extends AccessibleObject> A accessible(final A accessibleObject, final Class<?> callerClass) {
        if (checkAccess(accessibleObject, callerClass)) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    accessibleObject.setAccessible(true);
                    return null;
                }
            });
        }
        return accessibleObject;
    }

    /**
     * @return true if {@code callerClass} is allowed to access the {@code accessibleObject}
     */
    private static boolean checkAccess(AccessibleObject accessibleObject, Class<?> callerClass) {
        // check for @Friend access
        Friend friendAnn = accessibleObject.getAnnotation(Friend.class);
        if (friendAnn != null && contains(friendAnn.value(), callerClass))
            return true;

        // special case callers
        if (Friendly.class == callerClass || ProxyInstanceClassValue.class == callerClass)
            return true;

        // special case for Proxy.defineClass0 method called from FriendlyProxyFactory
        if (accessibleObject instanceof Method) {
            Method m = (Method) accessibleObject;
            return (
                callerClass == FriendlyProxyFactory.class &&
                m.getDeclaringClass() == Proxy.class &&
                m.getName().equals("defineClass0")
            );
        }

        // no access
        return false;
    }

    private static <E> boolean contains(E[] array, E element) {
        for (E e : array)
            if (Objects.equals(e, element))
                return true;
        return false;
    }

    static class GetDeclaredMethodAction implements PrivilegedAction<Method> {
        private final Class<?> clazz;
        private final String methodName;
        private final Class<?>[] parameterTypes;
        private final String noSuchMethodMessage;

        GetDeclaredMethodAction(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
            this(clazz, methodName, parameterTypes, null);
        }

        GetDeclaredMethodAction(Class<?> clazz, String methodName, Class<?>[] parameterTypes, String noSuchMethodMessage) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.noSuchMethodMessage = noSuchMethodMessage;
        }

        @Override
        public Method run() {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            }
            catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(noSuchMethodMessage == null ? e.getMessage() : noSuchMethodMessage, e);
            }
        }
    }

    static class GetDeclaredConstructorAction implements PrivilegedAction<Constructor<?>> {
        private final Class<?> clazz;
        private final Class<?>[] parameterTypes;

        GetDeclaredConstructorAction(Class<?> clazz, Class<?>... parameterTypes) {
            this.clazz = clazz;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public Constructor<?> run() {
            try {
                return clazz.getDeclaredConstructor(parameterTypes);
            }
            catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }

    static class GetDeclaredFieldAction implements PrivilegedAction<Field> {
        private final Class<?> clazz;
        private final String fieldName;

        GetDeclaredFieldAction(Class<?> clazz, String fieldName) {
            this.clazz = clazz;
            this.fieldName = fieldName;
        }

        @Override
        public Field run() {
            try {
                return clazz.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
    }
}
