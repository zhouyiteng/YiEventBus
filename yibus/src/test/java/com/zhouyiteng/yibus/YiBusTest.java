package com.zhouyiteng.yibus;

import android.app.Activity;

import junit.framework.TestCase;

/**
 * Created by zhouyiteng on 16/7/3.
 */
public class YiBusTest extends TestCase{
    private YiBus yiBus;
    private String lastStringEvent;
    private String countStringEvent;
    private int countIntEvent;
    private int lastIntEvent;

    protected void setUp() throws Exception {
        super.setUp();
        yiBus = YiBus.getDefault();
    }

    public void testRegisterForEventClassAndPost() throws InterruptedException {
        TestActvity actvity = new TestActvity();
        String event = "hello";

        long start = System.currentTimeMillis();

        yiBus.register(actvity,String.class);
        long time = System.currentTimeMillis() - start;
        System.out.println(time);
        yiBus.post(event);

        assertEquals(event,actvity.lastStringEvent);
    }

    public void testRegisterForAndPost() {
        TestActvity testActvity = new TestActvity();
        String event = "Hello";

        long start = System.currentTimeMillis();
        yiBus.register(testActvity);
        long time = System.currentTimeMillis() - start;
        System.out.println(time);
        yiBus.post(event);

        assertEquals(event,testActvity.lastStringEvent);
    }

    public void testPostwithoutRegister() {
        yiBus.post("hello");
    }

    public void testUnRegisterWithoutRegister() {
        yiBus.unregister(this);
        yiBus.unregister(this,String.class);
    }

    public void testRegisterTwice() {
        TestActvity testActvity = new TestActvity();
        yiBus.register(testActvity,String.class);
        yiBus.register(testActvity,String.class);
    }

    static class TestActvity extends Activity {
        public String lastStringEvent;

        public void onEvent(String event) {
            lastStringEvent = event;
        }
    }

}
