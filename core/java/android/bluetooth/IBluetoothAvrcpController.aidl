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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAvrcpInfo;

/**
 * APIs for Bluetooth AVRCP controller service
 *
 * @hide
 */
interface IBluetoothAvrcpController {
    List<BluetoothDevice> getConnectedDevices();
    List<BluetoothDevice> getDevicesMatchingConnectionStates(in int[] states);
    int getConnectionState(in BluetoothDevice device);
    void sendPassThroughCmd(in BluetoothDevice device, int keyCode, int keyState);
    void sendGroupNavigationCmd(in BluetoothDevice device, int keyCode, int keyState);
    void getMetaData(in int[] attributeIds);
    void getPlayStatus(in int[] playStatusIds);
    void getPlayerApplicationSetting();
    void setPlayerApplicationSetting(in int attributeId, in int attribVal);
    BluetoothAvrcpInfo getSupportedPlayerAppSetting(in BluetoothDevice device);
    int getSupportedFeatures(in BluetoothDevice device);
}
