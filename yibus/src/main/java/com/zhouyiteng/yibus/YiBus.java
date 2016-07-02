package com.zhouyiteng.yibus;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zhouyiteng on 16/7/2.
 * 参考Eventbus 2.4代码实现
 */
public class YiBus {
    public static final String TAG = "YiBus";

    private static YiBus defaultInstace;

    private static Map<String, List<Method>> methodCache = new HashMap<>();

    private final Map<Class<?>, List<Subscription>> subcriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final ThreadLocal<List<Subscription>> postQueue = new ThreadLocal<List<Subscription>>(){
        protected List<Subscription> initialValue() {
            return new ArrayList<>();
        }
    };

    private YiBus() {
        subcriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
    }

    private String defaultMethodName = "onEvent";

    public static synchronized YiBus getDefault() {
        if (null == defaultInstace) {
            defaultInstace = new YiBus();
        }
        return defaultInstace;
    }

    public void register(Object subscriber) {
        register(subscriber,defaultMethodName);
    }

    public void register(Object subscriber ,String methodName) {
        //查找注册对象的methodName 方法
        List<Method> subscriberMethods = findSubscriberMethods(subscriber.getClass(), methodName);

        for (Method method : subscriberMethods) {
            Class<?> eventType = method.getParameterTypes()[0];
            subscribe(subscriber,method,eventType);
        }
    }

    //当前子类重载父类的methodName方法,返回所有类的methodName方法
    private List<Method> findSubscriberMethods(final Class<?> subscriberClass, String methodName) {
        String key = subscriberClass.getName() + '.' + methodName;
        List<Method> subscriberMethods;
        synchronized (methodCache) {
            subscriberMethods = methodCache.get(key);
        }
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        subscriberMethods = new ArrayList<>();
        Class<?> aClass = subscriberClass;

        //递归查询当前类的methodName方法,如果子类没有,查看父类是否有methodName方法
        while (aClass != null) {
            String name = aClass.getName();

            //判断是否是系统Class
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
                break;
            }

            Method[] methods = aClass.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 1) {
                        subscriberMethods.add(method);
                    }
                }
            }
            aClass = aClass.getSuperclass();
        }
        //如果当前类及其父类没有methodName方法,抛出异常
        if (subscriberMethods.isEmpty()) {
            throw new RuntimeException("类:"+aClass+"中没有指定的方法:"+methodName);
        } else {
            //将当前注册的方法放入到缓存中
            synchronized (methodCache) {
                methodCache.put(key, subscriberMethods);
            }
            return subscriberMethods;
        }
    }

    private void subscribe(Object subscriber, Method method, Class<?> eventType) {
        //返回当前eventType的对应的object对象list
        List<Subscription> subscriptions = subcriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            //subcriptionsByEventType 当前map中没有该EventType订阅类型的对象
            // 则为第一次添加订阅该eventType类型的对象的arraylist,初始化arraylist
            subscriptions = new ArrayList<>();
            subcriptionsByEventType.put(eventType,subscriptions);
        } else {
            //当前有监听该EventType类的对象,查看list当中是否subscriber已经注册,如果已经注册,异常提示
            for (Subscription subscription : subscriptions) {
                if (subscription.subscriber == subscriber) {
                    throw new RuntimeException("该类:"+subscriber.getClass()+"的对象已经订阅event");
                }
            }
        }

        method.setAccessible(true);
        Subscription subscription = new Subscription(subscriber, method);
        //添加监听该EventType类型的对象和方法到subcriptionsByEventType map中
        subscriptions.add(subscription);

        //记录该对象的所有注册的监听事件,在unregister的时候查询该对象注册的所有EventType 类型
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);
    }

    public synchronized void unregister(Object subscriber) {
        //查找当前对象注册的eventtype类型
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null && !subscribedTypes.isEmpty()) {
           for (Class<?> eventType : subscribedTypes) {
                unsubscribedByEventType(subscriber,eventType);
           }
            subscribedTypes.clear();
        } else {
            Log.w(TAG,"该类对象没有注册任何事件");
        }

    }

    private void unsubscribedByEventType(Object subscriber, Class<?> eventType) {
        //获取当前注册该EventType的所有Subscription对象
        List<Subscription> subscriptions = subcriptionsByEventType.get(eventType);

        //查找当前subscriber对象并删除
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                if (subscription.equals(subscriber)) {
                    subscriptions.remove(subscription);
                }
            }
        }
    }

    public void post(Object event) {
        List<Subscription> subscriptions = postQueue.get();

        if (!subscriptions.isEmpty()) {
            Log.e(TAG,"之前的post请求还未发送完毕。清空之前的请求队列");
            subscriptions.clear();
        }

        Class<? extends Object> clazz = event.getClass();

        synchronized (this) {
            List<Subscription> list = subcriptionsByEventType.get(clazz);
            if (list != null && !list.isEmpty()) {
                subscriptions.addAll(list);
            }
        }

        if (subscriptions.isEmpty()) {
            Log.w(TAG,"当前没有对象注册该对象");
        } else {
            for (Subscription subscription : subscriptions) {
                postToSubscribtion(subscription, event);
            }
            subscriptions.clear();
        }
    }

    private void postToSubscribtion(Subscription subscription, Object event) {
        try {
            subscription.method.invoke(subscription.subscriber,event);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    static class Subscription {
        final Object subscriber;
        final Method method;

        Subscription(Object subscriber,Method method) {
            this.subscriber = subscriber;
            this.method = method;
        }

        /**
         * 判断Subscription 对象是否相等
         * @param obj
         * @return
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Subscription) {
                return subscriber == ((Subscription) obj).subscriber && method == ((Subscription) obj).method;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return subscriber.hashCode() + method.hashCode();
        }
    }
}