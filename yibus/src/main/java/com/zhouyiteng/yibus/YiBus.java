package com.zhouyiteng.yibus;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by zhouyiteng on 16/7/2.
 * 参考Eventbus 2.4代码实现
 */
public class YiBus {
    public static final String TAG = "YiBus";

    private static YiBus defaultInstace;

    //缓存订阅者对象的onEvent方法
    private static Map<String, List<Method>> methodCache = new HashMap<>();

    private static Map<Class<?>, List<Class<?>>> eventTypeCache = new HashMap<>();

    //按EventType类型保存订阅者对象
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subcriptionsByEventType;

    //保存每个订阅者对象所订阅的事件类型
    private final Map<Object, List<Class<?>>> typesBySubscriber;

    private final ThreadLocal<BooleanWrapper> currentThreadIsPosting = new ThreadLocal<BooleanWrapper>(){
        @Override
        protected BooleanWrapper initialValue() {
            return new BooleanWrapper();
        }
    };

    private final ThreadLocal<List<Object>> currentThreadEventQueue = new ThreadLocal<List<Object>>() {
        @Override
        protected List<Object> initialValue() {
            return new ArrayList<Object>();
        }
    };

    private YiBus() {
        subcriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        mainThreadPoster = new PostViaHandler(Looper.getMainLooper());
    }

    private String defaultMethodName = "onEvent";

    private PostViaHandler mainThreadPoster;

    public static synchronized YiBus getDefault() {
        if (null == defaultInstace) {
            defaultInstace = new YiBus();
        }
        return defaultInstace;
    }

    public void register(Object subscriber, ThreadMode inPostThread) {
        register(subscriber,defaultMethodName,ThreadMode.InPostThread);
    }

    public void register(Object subscriber ,String methodName, ThreadMode threadMode) {
        //查找注册对象的methodName 方法
        List<Method> subscriberMethods = findSubscriberMethods(subscriber.getClass(), methodName);

        for (Method method : subscriberMethods) {
            Class<?> eventType = method.getParameterTypes()[0];
            subscribe(subscriber,method,eventType,threadMode);
        }
    }

    public void register(Object subscriber, Class<?> eventType, Class<?>... moreEventTypes){
        register(subscriber, defaultMethodName, eventType, moreEventTypes);
    }

    public void register(Object subscriber, String defaultMethodName, Class<?> eventType, Class<?>[] moreEventTypes) {
        Class<?> subscriberClass = subscriber.getClass();
        Method method = findSubscriberMethods(subscriberClass,defaultMethodName,eventType);

        subscribe(subscriber,method,eventType,ThreadMode.InPostThread);

        for (Class<?> anotherEventType : moreEventTypes) {
            method = findSubscriberMethods(subscriberClass,defaultMethodName,anotherEventType);
            subscribe(subscriber,method,anotherEventType,ThreadMode.InPostThread);
        }

    }

    public void registerForMainThread(Object subcriber,String methodName,ThreadMode threadMode) {
        List<Method> subcriberMethods = findSubscriberMethods(subcriber.getClass(),methodName);
        for (Method method : subcriberMethods) {
            Class<?> eventType = method.getParameterTypes()[0];
            subscribe(subcriber,method,eventType,ThreadMode.InMainThread);
        }

    }
    /**
     *
     * Android2.3 getMethod方法执行速度慢,使用getDeclaredMethod
     *
     */
    private Method findSubscriberMethods(Class<?> subscriberClass, String defaultMethodName, Class<?> eventType) {
        Class<?> clazz = subscriberClass;
        while (clazz != null) {
            try {
                return subscriberClass.getDeclaredMethod(defaultMethodName,eventType);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            clazz = clazz.getSuperclass();
        }
        throw (new RuntimeException("订阅对象没有指定的方法!"));
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

    private void subscribe(Object subscriber, Method method, Class<?> eventType, ThreadMode threadMode) {
        CopyOnWriteArrayList<Subscription> subscriptions = subcriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subcriptionsByEventType.put(eventType,subscriptions);
        } else {
            for (Subscription subscription : subscriptions) {
                if (subscription.subscriber == subscriber) {
                    Log.e(TAG,"已经注册");
                }
            }
        }

        method.setAccessible(true);
        Subscription subscription = new Subscription(subscriber,method,ThreadMode.InMainThread);
        subscriptions.add(subscription);

        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber,subscribedEvents);
        }

        subscribedEvents.add(eventType);
    }

//    private void subscribe(Object subscriber, Method method, Class<?> eventType) {
//        //返回当前eventType的对应的object对象list
//        CopyOnWriteArrayList<Subscription> subscriptions = subcriptionsByEventType.get(eventType);
//        if (subscriptions == null) {
//            //subcriptionsByEventType 当前map中没有该EventType订阅类型的对象
//            // 则为第一次添加订阅该eventType类型的对象的arraylist,初始化arraylist
//            subscriptions = new CopyOnWriteArrayList<>();
//            subcriptionsByEventType.put(eventType,subscriptions);
//        } else {
//            //当前有监听该EventType类的对象,查看list当中是否subscriber已经注册,如果已经注册,异常提示
//            for (Subscription subscription : subscriptions) {
//                if (subscription.subscriber == subscriber) {
//                    throw new RuntimeException("该类:"+subscriber.getClass()+"的对象已经订阅event");
//                }
//            }
//        }
//
//        method.setAccessible(true);
//        Subscription subscription = new Subscription(subscriber, method);
//        //添加监听该EventType类型的对象和方法到subcriptionsByEventType map中
//        subscriptions.add(subscription);
//
//        //记录该对象的所有注册的监听事件,在unregister的时候查询该对象注册的所有EventType 类型
//        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
//        if (subscribedEvents == null) {
//            subscribedEvents = new ArrayList<>();
//            typesBySubscriber.put(subscriber, subscribedEvents);
//        }
//        subscribedEvents.add(eventType);
//    }

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

    public synchronized void unregister(Object subscriber, Class<?>... eventTypes) {
        if (eventTypes.length == 0) {
            throw new RuntimeException("必须提供至少一个EventTytp 类型");
        }

        List<Class<?>> subscribedClasses = typesBySubscriber.get(subscriber);
        if (subscribedClasses != null && !subscribedClasses.isEmpty()) {
            for (Class<?> eventType : eventTypes) {
                unsubscribedByEventType(subscriber,eventType);
                subscribedClasses.remove(eventType);
            }
        }
    }

    private void unsubscribedByEventType(Object subscriber, Class<?> eventType) {
        //获取当前注册该EventType的所有Subscription对象
        List<Subscription> subscriptions = subcriptionsByEventType.get(eventType);

        //查找当前subscriber对象并删除
        int size = subscriptions.size();
        for (int i = 0; i < size; i++) {
            if (subscriptions.get(i).subscriber == subscriber) {
                subscriptions.remove(i);
                i--;
                size --;
            }
        }

//        if (subscriptions != null && !subscriptions.isEmpty()) {
//            for (Subscription subscription : subscriptions) {
//                if (subscription.subscriber == subscriber) {
//                    subscriptions.remove(subscription);
//                }
//            }
//        }
    }

    public void post(Object event) {
        List<Object> eventQueue = currentThreadEventQueue.get();
        eventQueue.add(event);

        BooleanWrapper isPosting = currentThreadIsPosting.get();
        if (isPosting.value) {
            return;
        } else {
            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0));
                }
            } finally {
                isPosting.value = false;
            }
        }

//        List<Subscription> subscriptions;
//        synchronized (this) {
//            int countPooled = postQueuePool.size();
//            if (countPooled == 0) {
//                subscriptions = new ArrayList<Subscription>();
//            } else {
//                subscriptions = postQueuePool.remove(countPooled - 1);
//                if (!subscriptions.isEmpty()) {
//                    Log.d(TAG,"还有Event事件未发送完毕");
//                    throw new RuntimeException("Post queue from pool was not Empty");
//                }
//            }
//        }
//            List<Subscription> list = subcriptionsByEventType.get(clazz);
//            if (list != null && !list.isEmpty()) {
//                subscriptions.addAll(list);
//            }
//
//            if (subscriptions.isEmpty()){
//
//            } else {
//                int size = subscriptions.size();
//                for (int i = 0; i < size; i++) {
//                    Subscription subscription = subscriptions.get(i);
//                    postToSubscribtion(subscription,event);
//                }
//                subscriptions.clear();
//            }
//
//            synchronized (this) {
//                postQueuePool.add(subscriptions);
//            }
    }

    private void postSingleEvent(Object event) {
        List<Class<?>> eventTypes = findEventTypes(event.getClass());
        int countTypes = eventTypes.size();
        for (int h = 0; h < countTypes; h++) {
            Class<?> clazz = eventTypes.get(h);
            CopyOnWriteArrayList<Subscription> subscriptions;
            synchronized (this) {
                subscriptions = subcriptionsByEventType.get(clazz);
            }
            if (subscriptions == null) {
                Log.e(TAG,"没有对象注册该事件:"+clazz.getName());
            } else {
                for (Subscription subscription : subscriptions) {
                    if (subscription.threadMode == ThreadMode.InPostThread) {
                        postToSubscribtion(subscription,event);
                    } else if (subscription.threadMode == ThreadMode.InMainThread) {
                        mainThreadPoster.enqueue(event,subscription);
                    } else {
                        Log.e(TAG,"不能识别的线程模式");
                    }
                }
            }
        }
    }

    private List<Class<?>> findEventTypes(Class<?> eventClass) {
        synchronized (eventTypeCache) {
            List<Class<?>> eventTypes = eventTypeCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypeCache.put(eventClass,eventTypes);
            }
            return eventTypes;
        }
    }

    private void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes,interfaceClass.getInterfaces());
            }
        }
    }

    static void postToSubscribtion(Subscription subscription, Object event) {
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
        final ThreadMode threadMode;

        Subscription(Object subscriber,Method method,ThreadMode threadMode) {
            this.subscriber = subscriber;
            this.method = method;
            this.threadMode = threadMode;
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

    final static class PostViaHandler extends Handler {
        PostViaHandler(Looper looper) {
            super(looper);
        }

        void enqueue(Object event, Subscription subscription) {
            PendingPost pendingPost = PendingPost.obtainPendingPost(event,subscription);
            Message msg = obtainMessage();
            msg.obj = pendingPost;
            if (!sendMessage(msg)) {
                Log.e(TAG,"发送消息失败");
            }
        }

        @Override
        public void handleMessage(Message msg) {
            PendingPost pendingPost = (PendingPost) msg.obj;
            Object event= pendingPost.event;
            Subscription subscription = pendingPost.subscription;
            PendingPost.releasePendingPost(pendingPost);
            postToSubscribtion(subscription,event);
        }
    }

    final static class BooleanWrapper {
        boolean value;
    }
}