package com.slimvpn.app;

import android.content.Context;

import org.amnezia.awg.backend.Backend;
import org.amnezia.awg.backend.GoBackend;
import org.amnezia.awg.backend.Tunnel;
import org.amnezia.awg.config.Config;

/** Единая точка управления туннелем: экран и фоновый сторож используют один и тот же Backend. */
public final class VpnController {

    private static Backend backend;
    private static Tunnel tunnel;
    private static boolean connected = false;
    private static boolean ownedByWatcher = false;   // поднят автоматически по YouTube

    private VpnController() { }

    public static synchronized Backend backend(Context ctx) {
        if (backend == null) backend = new GoBackend(ctx.getApplicationContext());
        return backend;
    }

    public static synchronized boolean isConnected() { return connected; }

    public static synchronized void setOwnedByWatcher(boolean v) { ownedByWatcher = v; }

    public static synchronized boolean isOwnedByWatcher() { return ownedByWatcher; }

    public interface Callback { void done(boolean ok, String error); }

    public static synchronized void connect(final Context ctx, final Config cfg, final Callback cb) {
        if (connected) { if (cb != null) cb.done(true, null); return; }
        final Backend b = backend(ctx);
        new Thread(() -> {
            String err = null;
            try {
                if (tunnel == null) {
                    tunnel = new Tunnel() {
                        @Override public String getName() { return "slimvpn"; }
                        @Override public void onStateChange(State state) {
                            synchronized (VpnController.class) { connected = state == State.UP; }
                        }
                    };
                }
                b.setState(tunnel, Tunnel.State.UP, cfg);
                synchronized (VpnController.class) { connected = true; }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            }
            final String e = err;
            if (cb != null) cb.done(e == null, e);
        }, "vpn-connect").start();
    }

    public static synchronized void disconnect(final Callback cb) {
        final Tunnel t = tunnel;
        if (t == null) { connected = false; if (cb != null) cb.done(true, null); return; }
        new Thread(() -> {
            String err = null;
            try { backend.setState(t, Tunnel.State.DOWN, null); }
            catch (Throwable th) { err = String.valueOf(th.getMessage()); }
            synchronized (VpnController.class) { connected = false; ownedByWatcher = false; }
            final String e = err;
            if (cb != null) cb.done(e == null, e);
        }, "vpn-disconnect").start();
    }
}