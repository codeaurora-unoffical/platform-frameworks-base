/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_BOOTANIMATION_H
#define ANDROID_BOOTANIMATION_H

#include <stdint.h>
#include <sys/types.h>

#include <androidfw/AssetManager.h>
#include <utils/Thread.h>

#include <EGL/egl.h>
#include <GLES/gl.h>
#include <GLES2/gl2.h>

#include <linux/msm_ion.h>
#include <linux/ion.h>
#include <linux/videodev2.h>

#define  EARLYCAMERA_PAUSE_FILE "/sys/class/earlycamera/earlycamera/earlycamera_lk_notify_display_pause"
#define  EARLYCAMERA_TAKE_OVER_DISPLAY_FILE "/dev/earlycamera"
#define  VIDIOC_MSM_EARLYCAMERA_INIT_BUF        0x1002
#define  VIDIOC_MSM_EARLYCAMERA_REQUEST_BUF     0x1003
#define  VIDIOC_MSM_EARLYCAMERA_RELEASE_BUF     0x1004
#define  VIDIOC_MSM_EARLYCAMERA_GET_SHOW_CAMERA 0x1009
#define  VIDIOC_MSM_EARLYCAMERA_QBUF            0x1010
#define  VIDIOC_MSM_EARLYCAMERA_DQBUF           0x1011
#define  EARLYCAMERA_BUFFER_NUM 4

class SkBitmap;

namespace android {

class Surface;
class SurfaceComposerClient;
class SurfaceControl;

// ---------------------------------------------------------------------------

class BootAnimation : public Thread, public IBinder::DeathRecipient
{
public:
                BootAnimation();
    virtual     ~BootAnimation();

    sp<SurfaceComposerClient> session() const;

private:
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    bool                updateIsTimeAccurate();

    class TimeCheckThread : public Thread {
    public:
        TimeCheckThread(BootAnimation* bootAnimation);
        virtual ~TimeCheckThread();
    private:
        virtual status_t    readyToRun();
        virtual bool        threadLoop();
        bool                doThreadLoop();
        void                addTimeDirWatch();

        int mInotifyFd;
        int mSystemWd;
        int mTimeWd;
        BootAnimation* mBootAnimation;
    };

    struct Texture {
        GLint   w;
        GLint   h;
        GLuint  name;
    };

    struct Font {
        FileMap* map;
        Texture texture;
        int char_width;
        int char_height;
    };

    struct Animation {
        struct Frame {
            String8 name;
            FileMap* map;
            int trimX;
            int trimY;
            int trimWidth;
            int trimHeight;
            mutable GLuint tid;
            bool operator < (const Frame& rhs) const {
                return name < rhs.name;
            }
        };
        struct Part {
            int count;  // The number of times this part should repeat, 0 for infinite
            int pause;  // The number of frames to pause for at the end of this part
            int clockPosX;  // The x position of the clock, in pixels. Positive values offset from
                            // the left of the screen, negative values offset from the right.
            int clockPosY;  // The y position of the clock, in pixels. Positive values offset from
                            // the bottom of the screen, negative values offset from the top.
                            // If either of the above are INT_MIN the clock is disabled, if INT_MAX
                            // the clock is centred on that axis.
            String8 path;
            String8 trimData;
            SortedVector<Frame> frames;
            bool playUntilComplete;
            float backgroundColor[3];
            uint8_t* audioData;
            int audioLength;
            Animation* animation;
        };
        int fps;
        int width;
        int height;
        Vector<Part> parts;
        String8 audioConf;
        String8 fileName;
        ZipFileRO* zip;
        Font clockFont;
    };

    /**
     *IMG_OEM: bootanimation file from oem/media
     *IMG_SYS: bootanimation file from system/media
     *IMG_ENC: encrypted bootanimation file from system/media
     */
    enum ImageID { IMG_OEM = 0, IMG_SYS = 1, IMG_ENC = 2 };
    const char *getAnimationFileName(ImageID image);
    status_t initTexture(Texture* texture, AssetManager& asset, const char* name);
    status_t initTexture(FileMap* map, int* width, int* height);
    status_t initFont(Font* font, const char* fallback);
    bool android();
    bool movie();
    void drawText(const char* str, const Font& font, bool bold, int* x, int* y);
    void drawClock(const Font& font, const int xPos, const int yPos);
    bool validClock(const Animation::Part& part);
    Animation* loadAnimation(const String8&);
    bool playAnimation(const Animation&);
    void releaseAnimation(Animation*) const;
    bool parseAnimationDesc(Animation&);
    bool preloadZip(Animation &animation);
    bool playSoundsAllowed() const;

    void checkExit();

    sp<SurfaceComposerClient>       mSession;
    AssetManager mAssets;
    Texture     mAndroid[2];
    int         mWidth;
    int         mHeight;
    bool        mUseNpotTextures = false;
    EGLDisplay  mDisplay;
    EGLDisplay  mContext;
    EGLDisplay  mSurface;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;
    bool        mClockEnabled;
    bool        mTimeIsAccurate;
    bool        mTimeFormat12Hour;
    bool        mSystemBoot;
    String8     mZipFileName;
    SortedVector<String8> mLoadedFiles;
    sp<TimeCheckThread> mTimeCheckThread;

    struct EarlyCameraBufferCfg{
        int fd;
        int offset;
        int num_planes;
        int pingpong_flag;
        int num_bufs;
        int idx;
    };

    struct EarlyCameraBuffer {
        EarlyCameraBufferCfg cfg;
        unsigned char* data;
        size_t size;
        struct ion_fd_data ionInfo;
    };
    int notifyLKEarlyCameraPauseDisplay();
    int openEarlyCameraDevice();
    int earlyCameraFrameInit(int w,int h);
    int earlyCameraFrameDeinit();
    bool showCamera();
    bool camera();
    GLuint loadShader(int iShaderType, const char* source);
    GLuint createProgram();
    void initCameraProgram();
    void destroyCameraProgram();
    void drawFrame();
    void checkGlError(const char* op);
    void buildTexture(const unsigned char* y, const unsigned char* uv, int width, int height);
    GLuint mProgram;
    EGLConfig   mConfig;
    GLuint mPositionHandle;
    GLuint mCoordHandle;
    GLuint mYHandle;
    GLuint mUVHandle;
    GLuint mYTexture;
    GLuint mUVTexture;
    int mVideoWidth;
    int mVideoHeight;
    int mEarlycameraFd;
    EarlyCameraBuffer mEarlyCameraBufs[EARLYCAMERA_BUFFER_NUM];
    int mIonFd;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_BOOTANIMATION_H
