package test;

import java.lang.invoke.MethodType;

/**
 */
public class MethodTypeTest {
    public static void main(String[] args) {
        System.out.println(MethodType.methodType(int.class).toMethodDescriptorString());
    }
}
