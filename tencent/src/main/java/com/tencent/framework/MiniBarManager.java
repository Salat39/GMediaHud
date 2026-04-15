package com.tencent.framework;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.tencent.componentservice.service.IMediaComp;

import java.util.Map;


public class MiniBarManager {
    private static final String TAG = "MiniBarManager";
    private IMediaComp.MediaInfo curMediaInfo;
    private IMediaComp mCurFocusMediaComp;
    private String mCurFocusMediaCompName;
    private Map<String, IMediaComp> mMediaCompList;
    private Handler mainHandler;
    private MinibarUIListener minibarListener;

    public interface MinibarUIListener {
        default void clearMediaInfo() {
        }

        int hide();

        boolean minibarIsVisible();

        int refreshMediaInfo(IMediaComp.MediaInfo mediaInfo);

        int show();

        default void updateFavoriteStatus(String str, boolean z) {
        }

        default void updatePlayStatus(String str, boolean z) {
        }
    }

    public int notifyHide(String str) {
        return 0;
    }

    public int notifyShow(String str) {
        return 0;
    }

    private static class Holder {
        private static MiniBarManager INSTANCE = new MiniBarManager();

        private Holder() {
        }
    }

    public static MiniBarManager getInstance() {
        return Holder.INSTANCE;
    }

    private MiniBarManager() {
        this.mMediaCompList = new ArrayMap();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    private boolean checkHasCurFocusMediaComp() {
        if (this.mCurFocusMediaComp != null) {
            return true;
        }
        Log.e(TAG, "not set curFocusMediaComp");
        return false;
    }

    public void setMinibarListener(MinibarUIListener minibarUIListener) {
        this.minibarListener = minibarUIListener;
    }

    public int registerMediaComp(IMediaComp iMediaComp) {
        if (this.mMediaCompList.containsKey(iMediaComp.getMediaCompName())) {
            return 0;
        }
        this.mMediaCompList.put(iMediaComp.getMediaCompName(), iMediaComp);
        return 0;
    }

    public String getCurFocusMediaCompName() {
        return this.mCurFocusMediaCompName;
    }

    public void clearMediaInfo() {
        this.mCurFocusMediaCompName = null;
        this.mCurFocusMediaComp = null;
        this.curMediaInfo = null;
        MinibarUIListener minibarUIListener = this.minibarListener;
        if (minibarUIListener != null) {
            minibarUIListener.clearMediaInfo();
        }
    }

    public int refreshMediaInfo(final IMediaComp.MediaInfo mediaInfo) {
        this.mainHandler.post(new Runnable() { // from class: com.tencent.framework.-$$Lambda$MiniBarManager$yhcFALWXZvgHR5hlo5QsPi6q6Zg
            @Override // java.lang.Runnable
            public final void run() {
                MiniBarManager.this.lambda$refreshMediaInfo$0$MiniBarManager(mediaInfo);
            }
        });
        return 0;
    }

    public /* synthetic */ void lambda$refreshMediaInfo$0$MiniBarManager(IMediaComp.MediaInfo mediaInfo) {
        Log.i(TAG, "ready refreshMediaInfo:" + mediaInfo);
        if (mediaInfo == null) {
            return;
        }
        if (mediaInfo != null && !TextUtils.isEmpty(mediaInfo.media_comp) && (TextUtils.isEmpty(this.mCurFocusMediaCompName) || mediaInfo.isPlay)) {
            String str = mediaInfo.media_comp;
            this.mCurFocusMediaCompName = str;
            this.mCurFocusMediaComp = this.mMediaCompList.get(str);
        }
        if (TextUtils.isEmpty(mediaInfo.media_comp) || !mediaInfo.media_comp.equals(this.mCurFocusMediaCompName)) {
            Log.e(TAG, "curFocus comp is different from the set comp ,not allow refresh mediainfo!!!!");
            return;
        }
        this.curMediaInfo = mediaInfo;
        MinibarUIListener minibarUIListener = this.minibarListener;
        if (minibarUIListener != null) {
            minibarUIListener.refreshMediaInfo(mediaInfo);
        }
    }

    public boolean minibarIsVisible() {
        MinibarUIListener minibarUIListener = this.minibarListener;
        if (minibarUIListener != null) {
            return minibarUIListener.minibarIsVisible();
        }
        return false;
    }

    public void updatePlayStatus(final String str, final boolean z) {
        this.mainHandler.post(new Runnable() { // from class: com.tencent.framework.-$$Lambda$MiniBarManager$RfZSalmixpXuXqFHPnRTCi-Ze0U
            @Override // java.lang.Runnable
            public final void run() {
                MiniBarManager.this.lambda$updatePlayStatus$1$MiniBarManager(str, z);
            }
        });
    }

    public /* synthetic */ void lambda$updatePlayStatus$1$MiniBarManager(String str, boolean z) {
        MinibarUIListener minibarUIListener = this.minibarListener;
        if (minibarUIListener != null) {
            minibarUIListener.updatePlayStatus(str, z);
        }
    }

    public void updateFavoriteStatus(final String str, final boolean z) {
        this.mainHandler.post(new Runnable() { // from class: com.tencent.framework.-$$Lambda$MiniBarManager$aXWlHn--ohsyduAIHhCJW9hX-9s
            @Override // java.lang.Runnable
            public final void run() {
                MiniBarManager.this.lambda$updateFavoriteStatus$2$MiniBarManager(str, z);
            }
        });
    }

    public /* synthetic */ void lambda$updateFavoriteStatus$2$MiniBarManager(String str, boolean z) {
        MinibarUIListener minibarUIListener = this.minibarListener;
        if (minibarUIListener != null) {
            minibarUIListener.updateFavoriteStatus(str, z);
        }
    }

    public void play() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.play();
        }
    }

    public void pause() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.pause();
        }
    }

    public void next() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.next();
        }
    }

    public void addFavorite() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.addFavorite();
        }
    }

    public void removeFavorite() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.removeFavorite();
        }
    }

    public void jumpToMedia() {
        if (checkHasCurFocusMediaComp()) {
            this.mCurFocusMediaComp.jumpToMedia();
        }
    }

    public IMediaComp.MediaInfo getMediaInfo() {
        return this.curMediaInfo;
    }
}
