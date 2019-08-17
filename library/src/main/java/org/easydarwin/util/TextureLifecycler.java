package org.easydarwin.util;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.view.TextureView;

import java.lang.ref.WeakReference;

/**
 * LifecycleOwner
 * 生命周期事件分发者。例如我们最熟悉的Activity/Fragment。它们在生命周期发生变化时发出相应的Event给LifecycleRegistry。
 * */
public class TextureLifecycler implements LifecycleOwner {
    WeakReference<TextureView> mRef;

    // LifecycleRegistry
    // 控制中心。它负责控制state的转换、接受分发event事件。其实个人觉得Lifecycle组件与EventBus很类似？ 但以下代码表现了他们的不同:
    private LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    public TextureLifecycler(TextureView view) {
        mRef = new WeakReference<>(view);
        mLifecycleRegistry.markState(Lifecycle.State.INITIALIZED);

        if (view.isAvailable()) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        }

        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

}
