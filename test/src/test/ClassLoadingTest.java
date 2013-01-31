package test;

/**
 */
public class ClassLoadingTest {
    public static class X {
        static {
            System.out.println("Class X initializing...");
            System.out.println(Thread.currentThread());
        }

        static void test() {}
    }

    public static void main(String[] args) throws ClassNotFoundException {
        Class<?> classx = Class.forName(ClassLoadingTest.class.getName() + "$X", false, ClassLoadingTest.class.getClassLoader());
        System.out.println("Got class: " + classx.getName());
        System.out.println(classx.equals(X.class));
        System.out.println(classx.hashCode());
        System.out.println(classx.getDeclaredMethods());
        System.out.println(Thread.currentThread());
        Class.forName(classx.getName(), true, classx.getClassLoader());
    }
}
