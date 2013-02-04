package test;

import java.lang.reflect.Field;

/**
 */
public class ClassLoadingTest {
    public static class X {
        static int x = 42;
        static {
            System.out.println("Class X initializing...");
            System.out.println(Thread.currentThread());
        }

        static void test() {}
    }

    public static void main(String[] args) throws Exception {
        Class<?> classx = Class.forName(ClassLoadingTest.class.getName() + "$X", false, ClassLoadingTest.class.getClassLoader());
        System.out.println("Got class: " + classx.getName());
        System.out.println(classx.equals(X.class));
        System.out.println(classx.hashCode());
        Field xFld = X.class.getDeclaredField("x");
        System.out.println(xFld);
        System.out.println(Thread.currentThread());
        System.out.println(xFld.get(null));
    }
}
