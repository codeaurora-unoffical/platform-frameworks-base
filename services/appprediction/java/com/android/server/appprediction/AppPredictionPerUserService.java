/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.appprediction;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppGlobals;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionSessionId;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.IPredictionCallback;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ParceledListSlice;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.appprediction.AppPredictionService;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.server.infra.AbstractPerUserSystemService;

import java.util.ArrayList;

/**
 * Per-user instance of {@link AppPredictionManagerService}.
 */
public class AppPredictionPerUserService extends
        AbstractPerUserSystemService<AppPredictionPerUserService, AppPredictionManagerService>
             implements RemoteAppPredictionService.RemoteAppPredictionServiceCallbacks {

    private static final String TAG = AppPredictionPerUserService.class.getSimpleName();

    @Nullable
    @GuardedBy("mLock")
    private RemoteAppPredictionService mRemoteService;

    /**
     * When {@code true}, remote service died but service state is kept so it's restored after
     * the system re-binds to it.
     */
    @GuardedBy("mLock")
    private boolean mZombie;

    @GuardedBy("mLock")
    private final ArrayMap<AppPredictionSessionId, AppPredictionSessionInfo> mSessionInfos =
            new ArrayMap<>();

    protected AppPredictionPerUserService(AppPredictionManagerService master,
            Object lock, int userId) {
        super(master, lock, userId);
    }

    @Override // from PerUserSystemService
    protected ServiceInfo newServiceInfoLocked(@NonNull ComponentName serviceComponent)
            throws NameNotFoundException {

        ServiceInfo si;
        try {
            si = AppGlobals.getPackageManager().getServiceInfo(serviceComponent,
                    PackageManager.GET_META_DATA, mUserId);
        } catch (RemoteException e) {
            throw new NameNotFoundException("Could not get service for " + serviceComponent);
        }
        // TODO(b/111701043): must check that either the service is from a system component,
        // or it matches a service set by shell cmd (so it can be used on CTS tests and when
        // OEMs are implementing the real service and also verify the proper permissions
        return si;
    }

    @GuardedBy("mLock")
    @Override // from PerUserSystemService
    protected boolean updateLocked(boolean disabled) {
        final boolean enabledChanged = super.updateLocked(disabled);
        if (enabledChanged) {
            if (!isEnabledLocked()) {
                // Clear the remote service for the next call
                mRemoteService = null;
            }
        }
        return enabledChanged;
    }

    /**
     * Notifies the service of a new prediction session.
     */
    @GuardedBy("mLock")
    public void onCreatePredictionSessionLocked(@NonNull AppPredictionContext context,
            @NonNull AppPredictionSessionId sessionId) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.onCreatePredictionSession(context, sessionId);

            mSessionInfos.put(sessionId, new AppPredictionSessionInfo(sessionId, context, () -> {
                synchronized (mLock) {
                    AppPredictionSessionInfo sessionInfo = mSessionInfos.get(sessionId);
                    if (sessionInfo != null) {
                        sessionInfo.removeAllCallbacksLocked();
                        mSessionInfos.remove(sessionId);
                    }
                }
            }));
        }
    }

    /**
     * Records an app target event to the service.
     */
    @GuardedBy("mLock")
    public void notifyAppTargetEventLocked(@NonNull AppPredictionSessionId sessionId,
            @NonNull AppTargetEvent event) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.notifyAppTargetEvent(sessionId, event);
        }
    }

    /**
     * Records when a launch location is shown.
     */
    @GuardedBy("mLock")
    public void notifyLaunchLocationShownLocked(@NonNull AppPredictionSessionId sessionId,
            @NonNull String launchLocation, @NonNull ParceledListSlice targetIds) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.notifyLaunchLocationShown(sessionId, launchLocation, targetIds);
        }
    }

    /**
     * Requests the service to sort a list of apps or shortcuts.
     */
    @GuardedBy("mLock")
    public void sortAppTargetsLocked(@NonNull AppPredictionSessionId sessionId,
            @NonNull ParceledListSlice targets, @NonNull IPredictionCallback callback) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.sortAppTargets(sessionId, targets, callback);
        }
    }

    /**
     * Registers a callback for continuous updates of predicted apps or shortcuts.
     */
    @GuardedBy("mLock")
    public void registerPredictionUpdatesLocked(@NonNull AppPredictionSessionId sessionId,
            @NonNull IPredictionCallback callback) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.registerPredictionUpdates(sessionId, callback);

            AppPredictionSessionInfo sessionInfo = mSessionInfos.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.addCallbackLocked(callback);
            }
        }
    }

    /**
     * Unregisters a callback for continuous updates of predicted apps or shortcuts.
     */
    @GuardedBy("mLock")
    public void unregisterPredictionUpdatesLocked(@NonNull AppPredictionSessionId sessionId,
            @NonNull IPredictionCallback callback) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.unregisterPredictionUpdates(sessionId, callback);

            AppPredictionSessionInfo sessionInfo = mSessionInfos.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.removeCallbackLocked(callback);
            }
        }
    }

    /**
     * Requests a new set of predicted apps or shortcuts.
     */
    @GuardedBy("mLock")
    public void requestPredictionUpdateLocked(@NonNull AppPredictionSessionId sessionId) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.requestPredictionUpdate(sessionId);
        }
    }

    /**
     * Notifies the service of the end of an existing prediction session.
     */
    @GuardedBy("mLock")
    public void onDestroyPredictionSessionLocked(@NonNull AppPredictionSessionId sessionId) {
        final RemoteAppPredictionService service = getRemoteServiceLocked();
        if (service != null) {
            service.onDestroyPredictionSession(sessionId);

            AppPredictionSessionInfo sessionInfo = mSessionInfos.get(sessionId);
            if (sessionInfo != null) {
                sessionInfo.removeAllCallbacksLocked();
                mSessionInfos.remove(sessionId);
            }
        }
    }

    @Override
    public void onFailureOrTimeout(boolean timedOut) {
        if (isDebug()) {
            Slog.d(TAG, "onFailureOrTimeout(): timed out=" + timedOut);
        }
        // Do nothing, we are just proxying to the prediction service
    }

    @Override
    public void onConnectedStateChanged(boolean connected) {
        if (isDebug()) {
            Slog.d(TAG, "onConnectedStateChanged(): connected=" + connected);
        }
        if (connected) {
            synchronized (mLock) {
                if (mZombie) {
                    // Sanity check - shouldn't happen
                    if (mRemoteService == null) {
                        Slog.w(TAG, "Cannot resurrect sessions because remote service is null");
                        return;
                    }
                    mZombie = false;
                    resurrectSessionsLocked();
                }
            }
        }
    }

    @Override
    public void onServiceDied(RemoteAppPredictionService service) {
        if (isDebug()) {
            Slog.w(TAG, "onServiceDied(): service=" + service);
        }
        synchronized (mLock) {
            mZombie = true;
        }
        // Do nothing, eventually the system will bind to the remote service again...
    }

    /**
     * Called after the remote service connected, it's used to restore state from a 'zombie'
     * service (i.e., after it died).
     */
    private void resurrectSessionsLocked() {
        final int numSessions = mSessionInfos.size();
        if (isDebug()) {
            Slog.d(TAG, "Resurrecting remote service (" + mRemoteService + ") on "
                    + numSessions + " sessions.");
        }

        for (AppPredictionSessionInfo sessionInfo : mSessionInfos.values()) {
            sessionInfo.resurrectSessionLocked(this);
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private RemoteAppPredictionService getRemoteServiceLocked() {
        if (mRemoteService == null) {
            final String serviceName = getComponentNameLocked();
            if (serviceName == null) {
                if (mMaster.verbose) {
                    Slog.v(TAG, "getRemoteServiceLocked(): not set");
                }
                return null;
            }
            ComponentName serviceComponent = ComponentName.unflattenFromString(serviceName);

            mRemoteService = new RemoteAppPredictionService(getContext(),
                    AppPredictionService.SERVICE_INTERFACE, serviceComponent, mUserId, this,
                    mMaster.isBindInstantServiceAllowed(), mMaster.verbose);
        }

        return mRemoteService;
    }

    private static final class AppPredictionSessionInfo {
        private final AppPredictionSessionId mSessionId;
        private final AppPredictionContext mContext;
        private final ArrayList<IPredictionCallback> mCallbacks = new ArrayList<>();
        private final IBinder.DeathRecipient mBinderDeathHandler;

        AppPredictionSessionInfo(AppPredictionSessionId id, AppPredictionContext context,
                IBinder.DeathRecipient binderDeathHandler) {
            mSessionId = id;
            mContext = context;
            mBinderDeathHandler = binderDeathHandler;
        }

        void addCallbackLocked(IPredictionCallback callback) {
            if (mBinderDeathHandler != null) {
                try {
                    callback.asBinder().linkToDeath(mBinderDeathHandler, 0);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to link to death: " + e);
                }
            }
            mCallbacks.add(callback);
        }

        void removeCallbackLocked(IPredictionCallback callback) {
            if (mBinderDeathHandler != null) {
                callback.asBinder().unlinkToDeath(mBinderDeathHandler, 0);
            }
            mCallbacks.remove(callback);
        }

        void removeAllCallbacksLocked() {
            if (mBinderDeathHandler != null) {
                for (IPredictionCallback callback : mCallbacks) {
                    callback.asBinder().unlinkToDeath(mBinderDeathHandler, 0);
                }
            }
            mCallbacks.clear();
        }

        void resurrectSessionLocked(AppPredictionPerUserService service) {
            if (service.isDebug()) {
                Slog.d(TAG, "Resurrecting remote service (" + service.getRemoteServiceLocked()
                        + ") for session Id=" + mSessionId + " and "
                        + mCallbacks.size() + " callbacks.");
            }
            service.onCreatePredictionSessionLocked(mContext, mSessionId);
            for (IPredictionCallback callback : mCallbacks) {
                service.registerPredictionUpdatesLocked(mSessionId, callback);
            }
        }
    }
}
