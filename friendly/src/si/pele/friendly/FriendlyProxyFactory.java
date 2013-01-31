package si.pele.friendly;

import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
public final class FriendlyProxyFactory<I> {

    @SuppressWarnings("unchecked")
    public static <I> FriendlyProxyFactory<? extends I> getFactory(Class<I> intf) throws IllegalArgumentException {
        return (FriendlyProxyFactory<? extends I>) factoryCache.get(Objects.requireNonNull(intf));
    }

    private static final ClassValue<FriendlyProxyFactory<?>> factoryCache = new ClassValue<FriendlyProxyFactory<?>>() {
        @Override
        protected FriendlyProxyFactory<?> computeValue(Class<?> intf) {
            return new FriendlyProxyFactory<>(intf);
        }
    };

    private static final String proxyClassNamePrefix = "$FriendlyProxy";
    private static final AtomicLong nextUniqueNumber = new AtomicLong();
    private static final ReflectionFactory reflectionFactory =
        java.security.AccessController.doPrivileged
            (new sun.reflect.ReflectionFactory.GetReflectionFactoryAction());

    private final Constructor<? extends I> proxyConstructor;
    private final Method[] targetMethods;

    FriendlyProxyFactory(Class<I> intf) throws IllegalArgumentException {

        if (!intf.isInterface())
            throw new IllegalArgumentException(intf + " is not an interface.");

        // take just public abstract instance methods (ignore default/static JDK8 methods)
        Method[] methods = intf.getMethods();
        int pubAbstrInstCount = 0;
        for (Method method : methods) {
            int mod = method.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                pubAbstrInstCount++;
        }
        if (pubAbstrInstCount != methods.length) {
            Method[] filteredMethods = new Method[pubAbstrInstCount];
            int i = 0;
            for (Method method : methods) {
                int mod = method.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                    filteredMethods[i++] = method;
            }
            methods = filteredMethods;
        }

        // deduce target methods from interface methods
        targetMethods = new Method[methods.length];
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            String name = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 0)
                throw new IllegalArgumentException(
                    "Invalid proxy method: " + method + " (missing target parameter)"
                );
            Class<?> targetClass = paramTypes[0];
            Class<?>[] targetParamTypes = new Class<?>[paramTypes.length - 1];
            System.arraycopy(paramTypes, 1, targetParamTypes, 0, targetParamTypes.length);
            Method targetMethod;
            try {
                targetMethod = targetClass.getDeclaredMethod(name, targetParamTypes);
            }
            catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "Can't find target method for proxy method: " + method
                );
            }
            if (method.getReturnType() != targetMethod.getReturnType()) {
                throw new IllegalArgumentException(
                    "Return types of target method: " + targetMethod +
                    " and proxy method: " + method + " don't match"
                );
            }
            Class<?>[] exceptionTypes = method.getExceptionTypes();
            nextTargetExceptionType:
            for (Class<?> targetExceptionType : targetMethod.getExceptionTypes()) {
                if (RuntimeException.class.isAssignableFrom(targetExceptionType) ||
                    Error.class.isAssignableFrom(targetExceptionType))
                    continue nextTargetExceptionType;
                for (Class<?> exceptionType : exceptionTypes) {
                    if (exceptionType.isAssignableFrom(targetExceptionType))
                        continue nextTargetExceptionType;
                }
                throw new IllegalArgumentException(
                    "Target method: " + targetMethod + " declares checked exceptions" +
                    " that are not declared by proxy method: " + method
                );
            }
            // Ok, validated
            targetMethods[i] = targetMethod;
        }

        Class<? extends I> proxyClass;
        try {
            //noinspection unchecked
            proxyClass = (Class<? extends I>) Class.forName(intf.getName() + "_FriendlyProxy", false, intf.getClassLoader());
        }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        Class<?>[] paramTypes = new Class<?>[targetMethods.length];
        Arrays.fill(paramTypes, MethodHandle.class);
        try {
            this.proxyConstructor = proxyClass.getDeclaredConstructor(paramTypes);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public I createProxy(MethodHandle[] methodHandles) throws IllegalArgumentException {
        try {
            return proxyConstructor.newInstance(methodHandles);
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public Method[] getTargetMethods() {
        // must copy methods every time because we manipulate the "accessible" flag...
        Method[] methods = new Method[targetMethods.length];
        for (int i = 0; i < targetMethods.length; i++)
            methods[i] = reflectionFactory.copyMethod(targetMethods[i]);
        return methods;
    }

    // access to private native method in j.l.r.Proxy

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
