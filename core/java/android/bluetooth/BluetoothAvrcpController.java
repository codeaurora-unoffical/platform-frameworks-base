/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Copyright (C) 2014 The Android Open Source Project
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

package android.bluetooth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth AVRCP Controller
 * profile.
 *
 *<p>BluetoothAvrcpController is a proxy object for controlling the Bluetooth AVRCP
 * Service via IPC. Use {@link BluetoothAdapter#getProfileProxy} to get
 * the BluetoothAvrcpController proxy object.
 *
 * {@hide}
 */
public final class BluetoothAvrcpController implements BluetoothProfile {
    private static final String TAG = "BluetoothAvrcpController";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;

    /**
     * Intent used to broadcast the change in connection state of the AVRCP Controller
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     *   <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     *   <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile.</li>
     *   <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission to
     * receive.
     */
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "android.bluetooth.acrcp-controller.profile.action.CONNECTION_STATE_CHANGED";

    /* KeyCodes for Pass Through Commands */
    public static final int AVRC_ID_PLAY = 0x44;
    public static final int AVRC_ID_PAUSE = 0x46;
    public static final int AVRC_ID_VOL_UP = 0x41;
    public static final int AVRC_ID_VOL_DOWN = 0x42;
    public static final int AVRC_ID_STOP = 0x45;
    public static final int AVRC_ID_FF = 0x49;
    public static final int AVRC_ID_REWIND = 0x48;
    public static final int AVRC_ID_FORWARD = 0x4B;
    public static final int AVRC_ID_BACKWARD = 0x4C;
    /* Key State Variables */
    public static final int KEY_STATE_PRESSED = 0;
    public static final int KEY_STATE_RELEASED = 1;
    /* Group Navigation Key Codes */
    public static final int AVRC_ID_NEXT_GRP = 0x00;
    public static final int AVRC_ID_PREV_GRP = 0x01;


    private Context mContext;
    private ServiceListener mServiceListener;
    private IBluetoothAvrcpController mService;
    private BluetoothAdapter mAdapter;

    final private IBluetoothStateChangeCallback mBluetoothStateChangeCallback =
            new IBluetoothStateChangeCallback.Stub() {
                public void onBluetoothStateChange(boolean up) {
                    if (DBG) Log.d(TAG, "onBluetoothStateChange: up=" + up);
                    if (!up) {
                        if (VDBG) Log.d(TAG,"Unbinding service...");
                        synchronized (mConnection) {
                            try {
                                mService = null;
                                mContext.unbindService(mConnection);
                            } catch (Exception re) {
                                Log.e(TAG,"",re);
                            }
                        }
                    } else {
                        synchronized (mConnection) {
                            try {
                                if (mService == null) {
                                    if (VDBG) Log.d(TAG,"Binding service...");
                                    doBind();
                                }
                            } catch (Exception re) {
                                Log.e(TAG,"",re);
                            }
                        }
                    }
                }
        };

    /**
     * Create a BluetoothAvrcpController proxy object for interacting with the local
     * Bluetooth AVRCP service.
     *
     */
    /*package*/ BluetoothAvrcpController(Context context, ServiceListener l) {
        mContext = context;
        mServiceListener = l;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.registerStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }

        doBind();
    }

    boolean doBind() {
        Intent intent = new Intent(IBluetoothAvrcpController.class.getName());
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, mConnection, 0,
                android.os.Process.myUserHandle())) {
            Log.e(TAG, "Could not bind to Bluetooth AVRCP Controller Service with " + intent);
            return false;
        }
        return true;
    }

    /*package*/ void close() {
        mServiceListener = null;
        IBluetoothManager mgr = mAdapter.getBluetoothManager();
        if (mgr != null) {
            try {
                mgr.unregisterStateChangeCallback(mBluetoothStateChangeCallback);
            } catch (Exception e) {
                Log.e(TAG,"",e);
            }
        }

        synchronized (mConnection) {
            if (mService != null) {
                try {
                    mService = null;
                    mContext.unbindService(mConnection);
                } catch (Exception re) {
                    Log.e(TAG,"",re);
                }
            }
        }
    }

    public void finalize() {
        close();
    }

    /**
     * {@inheritDoc}
     */
    public List<BluetoothDevice> getConnectedDevices() {
        if (VDBG) log("getConnectedDevices()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getConnectedDevices();
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (VDBG) log("getDevicesMatchingStates()");
        if (mService != null && isEnabled()) {
            try {
                return mService.getDevicesMatchingConnectionStates(states);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    public int getConnectionState(BluetoothDevice device) {
        if (VDBG) log("getState(" + device + ")");
        if (mService != null && isEnabled()
            && isValidDevice(device)) {
            try {
                return mService.getConnectionState(device);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public void sendGroupNavigationCmd(BluetoothDevice device, int keyCode, int keyState) {
        if (DBG) Log.d(TAG, "sendGroupNavigationCmd dev = " + device + " key " + keyCode +
                                                                   " State = " + keyState);
        if (mService != null && isEnabled()) {
            try {
                mService.sendGroupNavigationCmd(device, keyCode, keyState);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in sendGroupNavigationCmd()", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public void sendPassThroughCmd(BluetoothDevice device, int keyCode, int keyState) {
        if (DBG) Log.d(TAG, "sendPassThroughCmd dev = " + device + " key " + keyCode + " State = " + keyState);
        if (mService != null && isEnabled()) {
            try {
                mService.sendPassThroughCmd(device, keyCode, keyState);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in sendPassThroughCmd()", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public void getMetaData(int[] attributeIds) {
        if (DBG) Log.d(TAG, "getMetaData num requested Ids = " + attributeIds.length);
        if (mService != null && isEnabled()) {
            try {
                mService.getMetaData(attributeIds);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getMetaData", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public void getPlayStatus(int[] playStatusIds) {
        if (DBG) Log.d(TAG, "getPlayStatus num requested Ids  = "+ playStatusIds.length);
        if (mService != null && isEnabled()) {
            try {
                mService.getPlayStatus(playStatusIds);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getPlayStatus()", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public void getPlayerApplicationSetting() {
        if (DBG) Log.d(TAG, "getPlayerApplicationSetting");
        if (mService != null && isEnabled()) {
            try {
                mService.getPlayerApplicationSetting();
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getPlayerApplicationSetting()", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public void setPlayerApplicationSetting(int attributeId, int attributeVal) {
        if (DBG) Log.d(TAG, "setPlayerApplicationSetting attribId = " + attributeId + " attribVal = " + attributeVal);
        if (mService != null && isEnabled()) {
            try {
                mService.setPlayerApplicationSetting(attributeId, attributeVal);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in setPlayerApplicationSetting()", e);
                return;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
    }

    public BluetoothAvrcpInfo getSupportedPlayerAppSetting(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "getSupportedPlayerAppSetting dev = " + device);
        if (mService != null && isEnabled()) {
            try {
                return mService.getSupportedPlayerAppSetting(device);
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getSupportedPlayerAppSetting()", e);
                return null;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return null;
    }

    public int getSupportedFeatures(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "getSupportedFeatures dev = " + device);
        if (mService != null && isEnabled()) {
            try {
                return mService.getSupportedFeatures(device);
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getSupportedFeatures()", e);
                return 0;
            }
        }
        if (mService == null) Log.w(TAG, "Proxy not attached to service");
        return 0;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "Proxy object connected");
            mService = IBluetoothAvrcpController.Stub.asInterface(service);

            if (mServiceListener != null) {
                mServiceListener.onServiceConnected(BluetoothProfile.AVRCP_CONTROLLER,
                        BluetoothAvrcpController.this);
            }
        }
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) Log.d(TAG, "Proxy object disconnected");
            mService = null;
            if (mServiceListener != null) {
                mServiceListener.onServiceDisconnected(BluetoothProfile.AVRCP_CONTROLLER);
            }
        }
    };

    private boolean isEnabled() {
       if (mAdapter.getState() == BluetoothAdapter.STATE_ON) return true;
       return false;
    }

    private boolean isValidDevice(BluetoothDevice device) {
       if (device == null) return false;

       if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) return true;
       return false;
    }

    private static void log(String msg) {
      Log.d(TAG, msg);
    }
}
