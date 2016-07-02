package com.zhouyiteng.yibus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void a() {
        Object object = new Object();
        System.out.println(object.getClass().getName());
    }

    @Test
    public void b() {
        List<Object> list = new ArrayList<>();
        Object a,b,c,d;
        a = new Object();
        b = new Object();
        c = new Object();
        d = new Object();
        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);

        System.out.println(list.size());

        list.remove(a);

        System.out.println(list.size());
    }
}