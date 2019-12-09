package com.github.yoep.popcorn.activities.impl;

import com.github.yoep.popcorn.activities.Activity;
import com.github.yoep.popcorn.activities.ActivityListener;
import com.github.yoep.popcorn.activities.ActivityManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityManagerImpl implements ActivityManager {
    private final List<ListenerHolder> listeners = new ArrayList<>();
    private final TaskExecutor taskExecutor;

    @Override
    public <T extends Activity> void register(Class<T> activity, ActivityListener<T> listener) {
        Assert.notNull(activity, "activity cannot be null");
        Assert.notNull(listener, "listener cannot be null");
        synchronized (listeners) {
            listeners.add(new ListenerHolder<>(activity, listener));
        }
    }

    @Override
    public void register(Activity activity) {
        Assert.notNull(activity, "activity cannot be null");
        taskExecutor.execute(() -> {
            synchronized (listeners) {
                listeners.stream()
                        .filter(e -> e.getActivity().isAssignableFrom(activity.getClass()))
                        .map(ListenerHolder::getListener)
                        .forEach(e -> e.onActive(activity));
            }
        });
    }

    /**
     * Holds information about an activity listener.
     *
     * @param <T> The activity class this listener listens on.
     */
    @Getter
    @AllArgsConstructor
    private static class ListenerHolder<T extends Activity> {
        private final Class<T> activity;
        private final ActivityListener<T> listener;
    }
}
