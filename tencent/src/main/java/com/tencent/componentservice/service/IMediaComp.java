package com.tencent.componentservice.service;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface IMediaComp {

    @Retention(RetentionPolicy.SOURCE)
    public @interface MediaType {
        public static final String MEDIA_TYPE_BT = "media_type_bt";
        public static final String MEDIA_TYPE_ONLINE = "media_type_online";
        public static final String MEDIA_TYPE_RADIO = "media_type_radio";
        public static final String MEDIA_TYPE_USB = "media_type_usb";
        public static final String MEDIA_TYPE_YUTING = "media_type_yunting";
    }

    int addFavorite();

    String getMediaCompName();

    MediaInfo getMediaInfo();

    @Deprecated
    default boolean isFavorite() {
        return false;
    }

    @Deprecated
    default boolean isPlay() {
        return false;
    }

    int jumpToMedia();

    int next();

    int pause();

    int play();

    int removeFavorite();

    public static class MediaInfo {
        public String coverUrl;
        public boolean isFavorite;
        public boolean isPlay;
        public boolean isSupportFavorite;
        public boolean likeEnable;
        public String media_comp;
        public String single;
        public String songName;

        public String toString() {
            return "MediaInfo{media_comp='" + this.media_comp + "', single='" + this.single + "', songName='" + this.songName + "', coverUrl='" + this.coverUrl + "', isSupportFavorite=" + this.isSupportFavorite + ", isFavorite=" + this.isFavorite + ", isPlay=" + this.isPlay + ", likeEnable=" + this.likeEnable + '}';
        }
    }
}
