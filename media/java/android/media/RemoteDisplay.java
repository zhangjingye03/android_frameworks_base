/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import dalvik.system.CloseGuard;

import android.os.Handler;
import android.view.Surface;

/**
 * Listens for Wifi remote display connections managed by the media server.
 *
 * @hide
 */
public final class RemoteDisplay {
    /* these constants must be kept in sync with IRemoteDisplayClient.h */

    public static final int DISPLAY_FLAG_SECURE = 1 << 0;

    public static final int DISPLAY_ERROR_UNKOWN = 1;
    public static final int DISPLAY_ERROR_CONNECTION_DROPPED = 2;

    private final CloseGuard mGuard = CloseGuard.get();
    private final Listener mListener;
    private final Handler mHandler;

    private long mPtr;

    private native long nativeListen(String iface);
    private native void nativeDispose(long ptr);
    private native void nativePause(long ptr);
    private native void nativeResume(long ptr);
	
// Mediatek {
    private static boolean isDispose = false;
    private static final Object lock = new Object();
    private native void nativeSetWfdLevel(long ptr, int level);
    private native int  nativeGetWfdParam(long ptr, int paramType);
	//WFD Sink Support
    private native long nativeConnect(String iface, Surface surface);
    private native void nativeSuspendDisplay(long ptr, boolean suspend, Surface surface);
    private native void nativeSendUibcEvent(long ptr, String eventDesc);

    private RemoteDisplay(Listener listener, Handler handler) {
        mListener = listener;
        mHandler = handler;
    }
// }
    @Override
    protected void finalize() throws Throwable {
        try {
            dispose(true);
        } finally {
            super.finalize();
        }
    }

    /**
     * Starts listening for displays to be connected on the specified interface.
     *
     * @param iface The interface address and port in the form "x.x.x.x:y".
     * @param listener The listener to invoke when displays are connected or disconnected.
     * @param handler The handler on which to invoke the listener.
     */
    public static RemoteDisplay listen(String iface, Listener listener, Handler handler) {
        if (iface == null) {
            throw new IllegalArgumentException("iface must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        RemoteDisplay display = new RemoteDisplay(listener, handler);
        display.startListening(iface);
        return display;
    }

    /**
     * Disconnects the remote display and stops listening for new connections.
     */
    public void dispose() {
        synchronized (lock) {
            if (isDispose) {
                return;
            }
            isDispose = true;
        }
        dispose(false);
    }

    public void pause() {
        nativePause(mPtr);
    }

    public void resume() {
        nativeResume(mPtr);
    }

    private void dispose(boolean finalized) {
        if (mPtr != 0) {
            if (mGuard != null) {
                if (finalized) {
                    mGuard.warnIfOpen();
                } else {
                    mGuard.close();
                }
            }

            nativeDispose(mPtr);
            mPtr = 0;
        }
        synchronized (lock) {
            isDispose = false;
        }
    }

    private void startListening(String iface) {
        mPtr = nativeListen(iface);
        if (mPtr == 0) {
            throw new IllegalStateException("Could not start listening for "
                    + "remote display connection on \"" + iface + "\"");
        }
        mGuard.open("dispose");
    }
	
// Mediatek {
    /*
     *
     * @hide
     *
     */
    public void setWfdLevel(int level) {
        nativeSetWfdLevel(mPtr, level);
    }

    /*
    *
     * @hide
     *
     */
    public int getWfdParam(int paramType) {
        return nativeGetWfdParam(mPtr, paramType);
    }

    //WFD Sink Support

    /*
     *
     * @hide
     *
     */
    public static RemoteDisplay connect(String iface, Surface surface, Listener listener, Handler handler) {
        if (iface == null) {
            throw new IllegalArgumentException("iface must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        RemoteDisplay display = new RemoteDisplay(listener, handler);
        display.startConnecting(iface, surface);
        return display;
    }

    /*
     *
     * @hide
     *
     */
    private void startConnecting(String iface, Surface surface) {
        mPtr = nativeConnect(iface, surface);
        if (mPtr == 0) {
            throw new IllegalStateException("Could not start connecting for "
                    + "remote display connection on \"" + iface + "\"");
        }
        mGuard.open("dispose");
    }

    /*
     *
     * @hide
     *
     */
    public void suspendDisplay(boolean suspend, Surface surface) {
        if (suspend && surface != null) {
            throw new IllegalArgumentException("surface must be null when suspend display");
        }
        if (!suspend && surface == null) {
            throw new IllegalArgumentException("surface must not be null when resume display");
        }

        nativeSuspendDisplay(mPtr, suspend, surface);
    }

    /*
     *
     * @hide
     *
     */
    public void sendUibcEvent(String eventDesc) {
        nativeSendUibcEvent(mPtr, eventDesc);
    }
// }

    // Called from native.
    private void notifyDisplayConnected(final Surface surface,
            final int width, final int height, final int flags, final int session) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayConnected(surface, width, height, flags, session);
            }
        });
    }

    // Called from native.
    private void notifyDisplayDisconnected() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayDisconnected();
            }
        });
    }

    // Called from native.
    private void notifyDisplayError(final int error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayError(error);
            }
        });
    }
	
// Mediatek {
    private void  notifyDisplayKeyEvent(final int uniCode, final int flags) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayKeyEvent(uniCode, flags);
            }
        });
    }

    private void notifyDisplayGenericMsgEvent(final int event) {
         mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayGenericMsgEvent(event);
            }
        });
    }
// }

    /**
     * Listener invoked when the remote display connection changes state.
     */
    public interface Listener {
        void onDisplayConnected(Surface surface,
                int width, int height, int flags, int session);
        void onDisplayDisconnected();
        void onDisplayError(int error);
// Mediatek {
        void onDisplayKeyEvent(int uniCode, int flags);
        void onDisplayGenericMsgEvent(int event);
// }
    }
}
