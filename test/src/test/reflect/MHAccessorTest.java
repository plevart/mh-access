/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.reflect;

import reflect.MHAccessors;
import sun.reflect.MethodAccessor;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public class MHAccessorTest {

    public int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) throws Exception {
        MHAccessorTest t = new MHAccessorTest();

        Method addM = MHAccessorTest.class.getMethod("add", int.class, int.class);

        try {
            System.out.println(addM.invoke(t, 1, 2d));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("-------------------");

        MethodAccessor addA = MHAccessors.newMethodAccessor(addM);

        try {
            System.out.println(addA.invoke(t, new Object[]{1, 2d}));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
