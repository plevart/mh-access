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
    // an all-mighty lookup
    private static final MethodHandles.Lookup lookup = AccessController.doPrivileged(
        new GetStaticFieldAction<MethodHandles.Lookup>(MethodHandles.Lookup.class, "IMPL_LOOKUP")
    );

    /**
     * Looks up a direct {@link MethodHandle} to a method. If the method is non-static, the receiver argument is
     * treated as an initial argument. If method is virtual, overriding is respected on every call.
     * The type of the method handle will be that of the method, with the receiver type prepended
     * (but only if it is non-static). Unless the method is annotated with the @{@link Friend} annotation specifying
     * the caller class in it's list, normal Java access checking is performed immediately on behalf of the caller class.
     * The returned method handle will have variable arity if and only if the method's variable arity modifier
     * bit ( 0x0080) is set.
     *
     * @param rcv            the class or interface in which the method is declared
     * @param methodName     the name of the method
     * @param parameterTypes the parameter types array
     * @return a method handle which can invoke the method
     * @throws IllegalArgumentException (wrapping {@link NoSuchMethodException}) if a matching method is not found
     * @throws FriendlyAccessException  (wrapping {@link IllegalAccessException}) if caller class does not have access
     *                                  to the method
     */
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

    /**
     * Looks up a direct {@link MethodHandle} to a constructor. The type of the method handle will be that of the
     * constructor, with the return type changed to the declaring class. The method handle will perform a
     * newInstance operation, creating a new instance of the constructor's class on the arguments passed to
     * the method handle. Unless the constructor is annotated with the @{@link Friend} annotation specifying
     * the caller class in it's list, normal Java access checking is performed immediately on behalf of the caller class.
     * The returned method handle will have variable arity if and only if the constructor's variable arity modifier
     * bit ( 0x0080) is set.
     *
     * @param rcv            the class in which the constructor is declared
     * @param parameterTypes the parameter types array
     * @return a method handle which can invoke the constructor
     * @throws IllegalArgumentException (wrapping {@link NoSuchMethodException}) if a matching constructor is not found
     * @throws FriendlyAccessException  (wrapping {@link IllegalAccessException}) if caller class does not have access
     *                                  to the constructor
     */
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

    /**
     * Looks up a direct {@link MethodHandle} giving read access to a field. The type of the method handle will have
     * a return type of the field's value type. If the field is static, the method handle will take no arguments.
     * Otherwise, its single argument will be the instance containing the field. Unless the field is annotated
     * with the @{@link Friend} annotation specifying the caller class in it's list, normal Java access checking
     * is performed immediately on behalf of the caller class.
     *
     * @param rcv       the class in which the field is declared
     * @param fieldName the name of the field
     * @return a method handle which can read the field's value
     * @throws IllegalArgumentException (wrapping {@link NoSuchFieldException}) if a matching field is not found
     * @throws FriendlyAccessException  (wrapping {@link IllegalAccessException}) if caller class does not have access
     *                                  to the field
     */
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

    /**
     * Looks up a direct {@link MethodHandle} giving write access to a field. The type of the method handle will
     * have a void return type. If the field is static, the method handle will take a single argument, of the field's
     * value type, the value to be stored. Otherwise, the two arguments will be the instance containing the field,
     * and the value to be stored. Unless the field is annotated with the @{@link Friend} annotation specifying
     * the caller class in it's list, normal Java access checking is performed immediately on behalf of the caller class.
     *
     * @param rcv       the class in which the field is declared
     * @param fieldName the name of the field
     * @return a method handle which can write the field's value
     * @throws IllegalArgumentException (wrapping {@link NoSuchFieldException}) if a matching field is not found
     * @throws FriendlyAccessException  (wrapping {@link IllegalAccessException}) if caller class does not have access
     *                                  to the field
     */
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

    /**
     * A friendly proxy factory method. Returns a singleton proxy object implementing given interface. The method
     * calls on the returned object are forwarded to target methods deduced from proxy interface methods using the
     * following rules:
     * <ul>
     * <li>each proxy interface method must have at least one parameter. The first parameter type is taken as the
     * target method's declaring class/interface. When called, the first parameter is used as a receiver of the
     * forwarded call.
     * </li>
     * <li>the rest of the proxy method parameters' types (if any) must exactly match the target method parameters'.
     * When called they are passed to target method unchanged.
     * </li>
     * </ul>
     *
     * @param intf
     * @param <I>
     * @return
     * @throws IllegalArgumentException
     * @throws FriendlyAccessException
     */
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
            @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
            I proxy = (I) PROXY_INSTANCE_CV.get(proxyClass);
            return proxy;
        }
        finally {
            // clear thread-local context
            PROXY_CLASS_BEING_INITIALIZED.remove();
        }
    }

    // public MethodHandle lookup methods that can only be accessed from friendly proxies' static initializer(s)

    /**
     * This method is public only as an implementation detail. Normal usage will always throw
     * {@link FriendlyAccessException}.<p>
     * A front-end for {@link MethodHandles.Lookup#findVirtual} method that adapts the {@code methodTypeDescriptor}
     * parameter from string representation to a {@link MethodType} before passing it to the lookup method.
     * The transformation uses the caller's class loader to perform the lookup of the types specified in the
     * string {@code methodTypeDescriptor}. This method allows access to arbitrary methods but only if invoked
     * from the static initializer of a proxy class generated by the {@link #proxy} method.
     *
     * @param refc                 the class or interface from which the method is accessed
     * @param name                 the name of the method
     * @param methodTypeDescriptor the type of the method, with the receiver argument omitted, expressed as a descriptor
     *                             as defined by the {@link MethodType#toMethodDescriptorString()}
     * @return the desired method handle
     * @throws IllegalArgumentException (wrapping {@link NoSuchMethodException}) if the method does not exist
     * @throws FriendlyAccessException  if not called from static initializer of a proxy class or
     *                                  (wrapping {@link IllegalAccessException}) if the method is static or
     *                                  if the method's variable arity modifier bit is set and asVarargsCollector fails
     */
    public static MethodHandle findVirtual(Class<?> refc, String name, String methodTypeDescriptor)
        throws IllegalArgumentException, FriendlyAccessException {
        Class<?> cc = Reflection.getCallerClass(2);
        checkProxyClassBeingInitialized(cc);
        ClassLoader ccl = cc.getClassLoader();
        try {
            MethodType methodType = MethodType.fromMethodDescriptorString(methodTypeDescriptor, ccl);
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

    // some common PrivilegedActions...

    static class GetDeclaredMethodAction implements PrivilegedAction<Method> {
        private final Class<?> clazz;
        private final String methodName;
        private final Class<?>[] parameterTypes;
        private final String noSuchMethodMessage;

        GetDeclaredMethodAction(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
            this(clazz, methodName, parameterTypes, null);
        }

        GetDeclaredMethodAction(Class<?> clazz, String methodName, Class<?>[] parameterTypes,
                                String noSuchMethodMessage) {
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
                throw new IllegalArgumentException(
                    noSuchMethodMessage == null
                    ? e.getMessage()
                    : noSuchMethodMessage,
                    e
                );
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

    static class GetStaticFieldAction<T> implements PrivilegedAction<T> {
        private final Class<?> clazz;
        private final String fieldName;

        GetStaticFieldAction(Class<?> clazz, String fieldName) {
            this.clazz = clazz;
            this.fieldName = fieldName;
        }

        @Override
        public T run() {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T) field.get(null);
            }
            catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            catch (IllegalAccessException e) {
                throw new FriendlyAccessException(e);
            }
        }
    }
}
