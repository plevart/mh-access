package si.pele.friendly;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

/**
 */
final class FriendlyProxyFactory<I> {

//    private static final ReflectionFactory reflectionFactory =
//        java.security.AccessController.doPrivileged
//            (new sun.reflect.ReflectionFactory.GetReflectionFactoryAction());

    private final Class<? extends I> proxyClass;
    private final Method[] targetMethods;

    FriendlyProxyFactory(Class<I> intf) throws IllegalArgumentException {

        if (!intf.isInterface())
            throw new IllegalArgumentException(intf + " is not an interface.");

        // take just abstract instance methods (ignore default/static JDK8 methods)
        Method[] methods = intf.getMethods();
        int abstrInstCount = 0;
        for (Method method : methods) {
            int mod = method.getModifiers();
            if (Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                abstrInstCount++;
        }
        if (abstrInstCount != methods.length) {
            Method[] filteredMethods = new Method[abstrInstCount];
            int i = 0;
            for (Method method : methods) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod) && !Modifier.isStatic(mod))
                    filteredMethods[i++] = method;
            }
            methods = filteredMethods;
        }

        // collect methods' exception types into an array of arrays
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
            // validate assign-ability of declared checked exception types
            next_target_exc_type:
            for (Class<?> targetExceptionType : targetMethod.getExceptionTypes()) {
                // skip unchecked exception types
                if (RuntimeException.class.isAssignableFrom(targetExceptionType) ||
                    Error.class.isAssignableFrom(targetExceptionType))
                    continue next_target_exc_type;
                // checked target method exception type should be assign-able to at least one
                // of proxy method's exception types...
                for (Class<?> exceptionType : exceptionTypes) {
                    if (exceptionType.isAssignableFrom(targetExceptionType))
                        continue next_target_exc_type;
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
            cw.visit(classFileVersion, Opcodes.ACC_SUPER | Opcodes.ACC_FINAL, proxyClassName, null, "java/lang/Object", new String[]{intfName});

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
                    "()V",
                    null,
                    null
                );

                // initialize static mh0, mh1, ... fields
                for (int i = 0; i < targetMethods.length; i++) {
                    Method targetMethod = targetMethods[i];
                    // load target method's declaring class
                    clinit.visitLdcInsn(Type.getType(targetMethod.getDeclaringClass()));
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
                    // create new proxy instance
                    clinit.visitTypeInsn(Opcodes.NEW, "L" + proxyClassName + ";");
                    // duplicate reference to newly created instance
                    clinit.visitInsn(Opcodes.DUP);
                    // invoke no-arg constructor
                    clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, proxyClassName, "<init>", "()V");
                    // assign the instance to "INSTANCE" static field
                    clinit.visitFieldInsn(
                        Opcodes.PUTSTATIC,
                        proxyClassName,
                        PROXY_INSTANCE_FIELD_NAME,
                        "L" + proxyClassName + ";"
                    );
                }

                // return
                clinit.visitInsn(Opcodes.RETURN);

                // end of static initializer
                clinit.visitEnd();
            }

            // generate private no-arg constructor
            {
                MethodVisitor init = cw.visitMethod(
                    Opcodes.ACC_PRIVATE,
                    "<init>",
                    "()V",
                    null,
                    null
                );

                // invoke super (j.l.Object) constructor
                init.visitVarInsn(Opcodes.ALOAD, 0);
                init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");

                // return
                init.visitInsn(Opcodes.RETURN);

                // end of constructor
                init.visitEnd();
            }

            // generate proxy methods
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                jdk.internal.org.objectweb.asm.commons.Method m =
                    jdk.internal.org.objectweb.asm.commons.Method.getMethod(method);
                GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC, m, null, getExceptionTypes(method), cw);
                // TODO...
            }

            cw.visitEnd();
        }

        // TODO
        return null;
    }

    private Type[] getExceptionTypes(Method method) {
        Class<?>[] exceptionClasses = method.getExceptionTypes();
        Type[] exceptionTypes = new Type[exceptionClasses.length];
        for (int i = 0; i < exceptionClasses.length; i++) {
            exceptionTypes[i] = Type.getType(exceptionClasses[i]);
        }
        return exceptionTypes;
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
