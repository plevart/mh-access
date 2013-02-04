package si.pele.friendly;

import sun.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * A {@link MethodHandles.Lookup} facade that uses @{@link Friend} annotation
 * to allow access to method handles for otherwise prohibited constructors, methods or fields.
 */
public class Friendly {
    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    public static MethodHandle method(Class<?> rcv, String methodName, Class<?>... parameterTypes) throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                         .unreflect(accessible(rcv.getDeclaredMethod(methodName, parameterTypes), cc));
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle constructor(Class<?> rcv, Class<?>... parameterTypes) throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                         .unreflectConstructor(accessible(rcv.getDeclaredConstructor(parameterTypes), cc));
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle getter(Class<?> rcv, String fieldName) throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                         .unreflectGetter(accessible(rcv.getDeclaredField(fieldName), cc));
        }
        catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    public static MethodHandle setter(Class<?> rcv, String fieldName) throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                         .unreflectSetter(accessible(rcv.getDeclaredField(fieldName), cc));
        }
        catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new FriendlyAccessException(e);
        }
    }

    private static final ClassValue<FriendlyProxyFactory<?>> PROXY_FACTORY_CV = new ClassValue<FriendlyProxyFactory<?>>() {
        @Override
        protected FriendlyProxyFactory<?> computeValue(Class<?> intf) {
            return new FriendlyProxyFactory<>(intf);
        }
    };

    private static final ClassValue<?> PROXY_CV = new ProxyClassValue();

    private static class ProxyClassValue extends ClassValue<Object> {
        @Override
        protected Object computeValue(Class<?> proxyClass) {
            MethodHandle mh = getter(proxyClass, FriendlyProxyFactory.PROXY_CLASS_FIELD_NAME);
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
        MethodHandles.Lookup ccLookup = lookup.in(cc);
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
            I proxy = (I) PROXY_CV.get(proxyClass);
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
    private static <A extends AccessibleObject> A accessible(A accessibleObject, Class<?> callerClass) {
        if (checkAccess(accessibleObject, callerClass)) {
            accessibleObject.setAccessible(true);
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

        // check if callerClass is proxy class just being initialized
        if (PROXY_CLASS_BEING_INITIALIZED.get() == callerClass)
            return true;

        // special case callers
        if (Friendly.class == callerClass || ProxyClassValue.class == callerClass)
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
}
