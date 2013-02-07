package si.pele.friendly;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import sun.reflect.ReflectionFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
final class FriendlyProxyFactory<I> {

    @SuppressWarnings("unchecked")
    static <I> FriendlyProxyFactory<? extends I> getFactory(Class<I> intf) throws IllegalArgumentException {
        return (FriendlyProxyFactory<? extends I>) factoryCache.get(Objects.requireNonNull(intf));
    }

    private static final ClassValue<FriendlyProxyFactory<?>> factoryCache = new ClassValue<FriendlyProxyFactory<?>>() {
        @Override
        protected FriendlyProxyFactory<?> computeValue(Class<?> intf) {
            return new FriendlyProxyFactory<>(intf);
        }
    };

    private static final ReflectionFactory reflectionFactory =
        java.security.AccessController.doPrivileged
            (new sun.reflect.ReflectionFactory.GetReflectionFactoryAction());

    private final Class<? extends I> proxyClass;
    private final Method[] targetMethods;

    FriendlyProxyFactory(Class<I> intf) throws IllegalArgumentException {

        if (!intf.isInterface())
            throw new IllegalArgumentException(intf + " is not an interface.");

        // take just abstract instance methods (ignore default/static JDK8 methods)
        Method[] methods = intf.getMethods();
        int pubAbstrInstCount = 0;
        for (Method method : methods) {
            int mod = method.getModifiers();
            if (Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                pubAbstrInstCount++;
        }
        if (pubAbstrInstCount != methods.length) {
            Method[] filteredMethods = new Method[pubAbstrInstCount];
            int i = 0;
            for (Method method : methods) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                    filteredMethods[i++] = method;
            }
            methods = filteredMethods;
        }

        // collect method's exception types into an array of arrays
        @SuppressWarnings("unchecked")
        Class<?>[][] methodsExceptionTypes = new Class[methods.length][];

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
            methodsExceptionTypes[i] = exceptionTypes;
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

        proxyClass = spinProxyClass(intf, methods, methodsExceptionTypes);
//        try {
//            //noinspection unchecked
//            proxyClass = (Class<? extends I>) Class.forName(intf.getName() + "_FriendlyProxy", false, intf.getClassLoader());
//        }
//        catch (ClassNotFoundException e) {
//            throw new IllegalArgumentException(e.getMessage(), e);
//        }
    }

    Class<? extends I> getProxyClass() {
        return proxyClass;
    }

    Method[] getTargetMethods() {
        return targetMethods;
    }

    // proxy class spinning

    static final String PROXY_INSTANCE_FIELD_NAME = "INSTANCE";

    private static final String proxyClassNamePrefix = "$FriendlyProxy";
    private static final String mhFieldNamePrefix = "mh";
    private static final String MethodHandle_typeDescriptor = Type.getDescriptor(MethodHandle.class);
    private static final AtomicLong nextUniqueNumber = new AtomicLong();
    private static final int classFileVersion = 51;
    private static final String Friendly_className = Friendly.class.getName().replace('.', '/');

    private Class<? extends I> spinProxyClass(Class<I> intf, Method[] methods, Class<?>[][] methodsExceptionTypes) {

        String intfName = intf.getName().replace('.', '/');
        int lastSlash = intfName.lastIndexOf('/');
        String pkgPath = lastSlash >= 0 ? intfName.substring(0, lastSlash + 1) : "";
        if (Modifier.isPublic(intf.getModifiers())) pkgPath = "";
        String proxyClassName = pkgPath + proxyClassNamePrefix + nextUniqueNumber.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // generate proxy class
        {
            cw.visit(classFileVersion, Opcodes.ACC_SUPER, proxyClassName, null, "java/lang/Object", new String[]{intfName});

            // generate private static final fields with names: mh0, mh1, ... and type java.lang.invoke.MethodHandle
            for (int i = 0; i < targetMethods.length; i++) {
                FieldVisitor fv = cw.visitField(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    mhFieldNamePrefix + i,
                    MethodHandle_typeDescriptor,
                    null,
                    null
                );
                fv.visitEnd();
            }

            // generate private static final field INSTANCE to hold the singleton instance
            {
                FieldVisitor fv = cw.visitField(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    PROXY_INSTANCE_FIELD_NAME,
                    "L" + proxyClassName + ";",
                    null,
                    null
                );
                fv.visitEnd();
            }

            // generate static initializer
            {
                MethodVisitor clinit = cw.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "<clinit>",
                    MethodType.methodType(void.class).toMethodDescriptorString(),
                    null,
                    null
                );

                // initialize static mh0, mh1, ... fields
                for (int i = 0; i < targetMethods.length; i++) {
                    Method targetMethod = targetMethods[i];
                    // load target method's declaring class FQ name
                    clinit.visitLdcInsn(targetMethod.getDeclaringClass().getName());
                    // load method name
                    clinit.visitLdcInsn(targetMethod.getName());
                    // load method type descriptor
                    clinit.visitLdcInsn(
                        MethodType.methodType(
                            targetMethod.getReturnType(),
                            targetMethod.getParameterTypes()
                        ).toMethodDescriptorString()
                    );
                    // invoke the Friendly.findVirtual method
                    clinit.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        Friendly_className,
                        Friendly.FIND_VIRTUAL_METHOD_NAME,
                        Friendly.FIND_VIRTUAL_METHOD_TYPE.toMethodDescriptorString()
                    );
                    // put the result to mh0, mh1, ... field
                    clinit.visitFieldInsn(
                        Opcodes.PUTSTATIC,
                        proxyClassName,
                        mhFieldNamePrefix + i,
                        MethodHandle_typeDescriptor
                    );
                }

                // initialize static INSTANCE field
                {

                }

                clinit.visitEnd();
            }

            cw.visitEnd();
        }

        // TODO
        return null;
    }

    // access to private native method in j.l.r.Proxy

    private static final MethodHandle defineClass0MH = Friendly.method(
        Proxy.class, "defineClass0", ClassLoader.class, String.class, byte[].class, int.class, int.class
    );

    private static Class<?> defineClass0(
        ClassLoader loader, String name,
        byte[] b, int off, int len
    ) {
        try {
            return (Class<?>) defineClass0MH.invokeExact(loader, name, b, off, len);
        }
        catch (Throwable t) {
            throw MHThrows.unchecked(t);
        }
    }
}
