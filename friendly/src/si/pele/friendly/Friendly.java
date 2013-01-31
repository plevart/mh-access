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

    public static MethodHandle method(Class<?> rcv, String methodName, Class<?>... parameterTypes) throws IllegalArgumentException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflect(friendly(rcv.getDeclaredMethod(methodName, parameterTypes), cc));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static MethodHandle constructor(Class<?> rcv, Class<?>... parameterTypes) throws IllegalArgumentException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectConstructor(friendly(rcv.getDeclaredConstructor(parameterTypes), cc));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static MethodHandle getter(Class<?> rcv, String fieldName) throws IllegalArgumentException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectGetter(friendly(rcv.getDeclaredField(fieldName), cc));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static MethodHandle setter(Class<?> rcv, String fieldName) throws IllegalArgumentException {
        Class<?> cc = Reflection.getCallerClass(2);
        try {
            return lookup.in(cc)
                .unreflectSetter(friendly(rcv.getDeclaredField(fieldName), cc));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public static <I> I proxy(Class<I> intf) throws IllegalArgumentException {
        Class<?> cc = Reflection.getCallerClass(2);
        MethodHandles.Lookup ccLookup = lookup.in(cc);
        FriendlyProxyFactory<? extends I> proxyFactory = FriendlyProxyFactory.getFactory(intf);
        Method[] methods = proxyFactory.getTargetMethods();
        MethodHandle[] mhs = new MethodHandle[methods.length];
        for (int i = 0; i < methods.length; i++) {
            try {
                mhs[i] = ccLookup.unreflect(friendly(methods[i], cc));
            }
            catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        return proxyFactory.createProxy(mhs);
    }

    /**
     * Modifies the "accessible" flag of given {@code accessibleObject} according to permissions
     * of the {@code callerClass} governed by @{@link Friend} annotations attached to the
     * field/method/constructor.
     *
     * @param accessibleObject field, method or constructor
     * @param callerClass      the class directly calling into public {@link Friendly} methods
     * @param <A>              the type of accessible object
     * @return the same {@code accessibleObject} but with "accessible" flag possibly modified
     */
    private static <A extends AccessibleObject> A friendly(A accessibleObject, Class<?> callerClass) {
        Friend friendAnn = accessibleObject.getAnnotation(Friend.class);
        if (friendAnn != null) {
            if (contains(friendAnn.value(), callerClass) || privileged(accessibleObject, callerClass)) {
                accessibleObject.setAccessible(true);
            }
        }
        return accessibleObject;
    }

    /**
     * @return true if {@code callerClass} is implicitly allowed to access the {@code accessibleObject}
     *         without the {@code accessibleObject} being annotated with @{@link Friend} pointing to {@code callerClass}.
     */
    private static boolean privileged(AccessibleObject accessibleObject, Class<?> callerClass) {
        if (accessibleObject instanceof Method) {
            Method m = (Method) accessibleObject;
            return (
                callerClass == FriendlyProxyFactory.class &&
                m.getDeclaringClass() == Proxy.class &&
                m.getName().equals("defineClass0")
            );
        }
        return false;
    }

    private static <E> boolean contains(E[] array, E element) {
        for (E e : array)
            if (Objects.equals(e, element))
                return true;
        return false;
    }
}
