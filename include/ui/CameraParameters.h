/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
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

#ifndef ANDROID_HARDWARE_CAMERA_PARAMETERS_H
#define ANDROID_HARDWARE_CAMERA_PARAMETERS_H

#include <utils/KeyedVector.h>
#include <utils/String8.h>

namespace android {

class CameraParameters
{
public:
    CameraParameters();
    CameraParameters(const String8 &params) { unflatten(params); }
    ~CameraParameters();

    enum {
        CAMERA_ORIENTATION_UNKNOWN = 0,
        CAMERA_ORIENTATION_PORTRAIT = 1,
        CAMERA_ORIENTATION_LANDSCAPE = 2,
    };

    String8 flatten() const;
    void unflatten(const String8 &params);

    void set(const char *key, const char *value);
    void set(const char *key, int value);
    const char *get(const char *key) const;
    int getInt(const char *key) const;

    /* preview-size=176x144 */
    void setPreviewSize(int width, int height);
    void getPreviewSize(int *width, int *height) const;

    /* preview-fps=15 */
    void setPreviewFrameRate(int fps);
    int getPreviewFrameRate() const;

    /* preview-format=rgb565|yuv422 */
    void setPreviewFormat(const char *format);
    const char *getPreviewFormat() const;

    /* picture-size=1024x768 */
    void setPictureSize(int width, int height);
    void getPictureSize(int *width, int *height) const;

    /* picture-format=yuv422|jpeg */
    void setPictureFormat(const char *format);
    const char *getPictureFormat() const;

    int getOrientation() const;
    void setOrientation(int orientation);
    /* Special Effect */
    const char *getEffect() const;

    /* ISO value */
    const char *getISOValue() const;

    /* Auto exposure value */
    const char *getAutoexposureValue() const;

    /* Lensshading value */
    const char *getLensshadingValue() const;

    /* Auto focus value */
    const char *getAutoFocusValue() const;

    /* White Balance Lighting Conditions */
    const char *getWBLighting() const;

    /* Anti Banding */
    const char *getAntiBanding() const;
    /* Main image quality */
    int getJpegMainimageQuality() const;
    /* Brightness control */
    int getBrightness() const;

    /* Digital Zoom control */
    int getZoomValue() const;

    /* Check if Camera is Enabled */
    int getCameraEnabledVal() const;

    /* get the getRecordLocation Value */
    int getRecordLocation() const;

    /* get the LatitudeRef Value */
    const char* getLatitudeRef() const;

    /* get the Latitude Value */
    const char* getLatitude() const;

    /* get the LongitudeRef Value */
    const char* getLongitudeRef() const;

    /* get the Longitude Value */
    const char* getLongitude() const;

    /* get the AltitudeRef Value */
    int getAltitudeRef() const;

    /* get the Altitude Value */
    const char* getAltitude() const;

    /* get the getDateTime Value */
    const char* getDateTime() const;

    /* Led Flash value */
    const char *getLedflashValue() const;

    /*  set/get maximum zoom value */
    void setMaxZoomValue( int );
    int getMaxZoomValue() const;

    void dump() const;
    status_t dump(int fd, const Vector<String16>& args) const;

private:
    DefaultKeyedVector<String8,String8>    mMap;
};


}; // namespace android

#endif
