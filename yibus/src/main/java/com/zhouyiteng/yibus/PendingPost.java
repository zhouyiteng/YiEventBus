package com.zhouyiteng.yibus;

import com.zhouyiteng.yibus.YiBus.Subscription;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhouyiteng on 16/7/7.
 */
public class PendingPost {
    private final static List<PendingPost> pendingPostPool = new ArrayList<>();

    Object event;
    Subscription subscription;

    private PendingPost(Object event,Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    static PendingPost obtainPendingPost(Object event, Subscription subscription) {
        synchronized (pendingPostPool) {
            int size = pendingPostPool.size();
            if (size > 0) {
                PendingPost pendingPost = pendingPostPool.remove(size - 1);
                pendingPost.event = event;
                pendingPost.subscription = subscription;
            }
        }
        return new PendingPost(event,subscription);
    }

    static void releasePendingPost(PendingPost pendingPost) {
        synchronized (pendingPostPool) {
            pendingPostPool.add(pendingPost);
        }
    }
}
