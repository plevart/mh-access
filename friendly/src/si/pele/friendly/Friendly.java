package si.pele.friendly;

import sun.reflect.Reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.util.Objects;

/**
 * A {@link MethodHandles.Lookup} facade that uses @{@link Friend} annotation
 * to allow access to method handles for otherwise prohibited constructors, methods or fields.
 */
public class Friendly {
    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    public static MethodHandle method(Class<?> rcv, String methodName, Class<?>... parameterTypes) throws FriendlyException {
        try {
            return lookup
                .in(Reflection.getCallerClass(2))
                .unreflect(friendly(rcv.getDeclaredMethod(methodName, parameterTypes)));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new FriendlyException(e);
        }
    }

    public static MethodHandle constructor(Class<?> rcv, Class<?>... parameterTypes) throws FriendlyException {
        try {
            return lookup
                .in(Reflection.getCallerClass(2))
                .unreflectConstructor(friendly(rcv.getDeclaredConstructor(parameterTypes)));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new FriendlyException(e);
        }
    }

    public static MethodHandle getter(Class<?> rcv, String fieldName) throws FriendlyException {
        try {
            return lookup
                .in(Reflection.getCallerClass(2))
                .unreflectGetter(friendly(rcv.getDeclaredField(fieldName)));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new FriendlyException(e);
        }
    }

    public static MethodHandle setter(Class<?> rcv, String fieldName) throws FriendlyException {
        try {
            return lookup
                .in(Reflection.getCallerClass(2))
                .unreflectSetter(friendly(rcv.getDeclaredField(fieldName)));
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new FriendlyException(e);
        }
    }

    private static <A extends AccessibleObject> A friendly(A accessibleObject) {
        Friend friendAnn = accessibleObject.getAnnotation(Friend.class);
        if (friendAnn != null) {
            Class<?> cc = Reflection.getCallerClass(3);
            if (contains(friendAnn.value(), cc)) {
                accessibleObject.setAccessible(true);
            }
        }
        return accessibleObject;
    }

    private static <E> boolean contains(E[] array, E element) {
        for (E e : array)
            if (Objects.equals(e, element))
                return true;
        return false;
    }
}
