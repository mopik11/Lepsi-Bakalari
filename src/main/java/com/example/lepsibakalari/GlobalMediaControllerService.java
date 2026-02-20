package com.example.lepsibakalari;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.util.Log;
import android.media.MediaMetadata;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

import java.util.List;

public class GlobalMediaControllerService extends NotificationListenerService {

    private static final String TAG = "GlobalMediaCtrlSvc";
    public static final String ACTION_MEDIA_UPDATE = "com.example.lepsibakalari.ACTION_MEDIA_UPDATE";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_ARTIST = "extra_artist";
    public static final String EXTRA_PLAYING = "extra_playing";
    public static final String EXTRA_ALBUM_ART_URI = "extra_album_art_uri";
    public static final String EXTRA_ALBUM_ART_BYTES = "extra_album_art_bytes";
    private MediaSessionManager mediaSessionManager;
    private List<MediaController> controllers;
    private MediaController primaryController;
    private MediaController.Callback mediaCallback;

    private static GlobalMediaControllerService instance;

    public static GlobalMediaControllerService getInstance() {
        return instance;
    }

    public void onCreate() {
        super.onCreate();
        instance = this;
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            controllers = mediaSessionManager.getActiveSessions(new ComponentName(this, GlobalMediaControllerService.class));
            pickPrimaryController();
        } catch (SecurityException e) {
            Log.w(TAG, "Notification access not granted yet");
        }
    }

    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            controllers = mediaSessionManager.getActiveSessions(new ComponentName(this, GlobalMediaControllerService.class));
            pickPrimaryController();
        } catch (SecurityException e) {
            Log.w(TAG, "Notification access not granted in onListenerConnected");
        }
    }

    public void onActiveSessionsChanged(List<MediaController> controllers) {
        // NotificationListenerService on some SDKs doesn't expose a super implementation
        this.controllers = controllers;
        pickPrimaryController();
    }

    private void pickPrimaryController() {
        if (controllers == null || controllers.isEmpty()) {
            primaryController = null;
            return;
        }

        // Prefer a currently playing controller
        for (MediaController mc : controllers) {
            PlaybackState s = mc.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                primaryController = mc;
                return;
            }
        }

        // fallback to first
        MediaController newPrimary = controllers.get(0);

        if (primaryController != null && primaryController != newPrimary) {
            try {
                primaryController.unregisterCallback(mediaCallback);
            } catch (Exception ignored) {}
        }

        primaryController = newPrimary;

        // register callback to receive metadata/playback updates
        if (mediaCallback == null) {
            mediaCallback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    sendUpdate();
                }

                @Override
                public void onMetadataChanged(android.media.MediaMetadata metadata) {
                    sendUpdate();
                }
                @Override
                public void onQueueChanged(java.util.List<android.media.session.MediaSession.QueueItem> queue) {
                    sendUpdate();
                }

                @Override
                public void onSessionDestroyed() {
                    sendUpdate();
                }

                @Override
                public void onExtrasChanged(android.os.Bundle extras) {
                    sendUpdate();
                }

                @Override
                public void onSessionEvent(String event, android.os.Bundle extras) {
                    sendUpdate();
                }
            };
        }

        try {
            primaryController.registerCallback(mediaCallback);
        } catch (Exception ignored) {}

        // send initial update
        sendUpdate();
    }

    private void sendUpdate() {
        GlobalMediaControllerService svc = instance;
        if (svc == null) return;
        String title = null, artist = null;
        boolean playing = false;
        if (svc.primaryController != null) {
            android.media.MediaMetadata md = svc.primaryController.getMetadata();
            if (md != null) {
                CharSequence t = md.getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
                CharSequence a = md.getText(android.media.MediaMetadata.METADATA_KEY_ARTIST);
                title = t == null ? null : t.toString();
                artist = a == null ? null : a.toString();
            }
            PlaybackState s = svc.primaryController.getPlaybackState();
            playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        }

        android.content.Intent i = new android.content.Intent(ACTION_MEDIA_UPDATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_ARTIST, artist);
        i.putExtra(EXTRA_PLAYING, playing);
        try {
            if (svc.primaryController != null) {
                android.media.MediaMetadata md = svc.primaryController.getMetadata();
                if (md != null) {
                    // Try to include artwork bytes if available
                    Bitmap art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art == null) art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
                    if (art != null) {
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            art.compress(Bitmap.CompressFormat.PNG, 90, baos);
                            i.putExtra(EXTRA_ALBUM_ART_BYTES, baos.toByteArray());
                        } catch (Exception e) {
                            // ignore
                        }
                    } else {
                        // fallback: try to send a URI if available
                        String artUri = md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                        if (artUri == null) artUri = md.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI);
                        if (artUri != null) i.putExtra(EXTRA_ALBUM_ART_URI, artUri);
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            sendBroadcast(i);
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to send media update broadcast");
        }
    }

    // Public helper methods to control the primary controller
    public static void playPause() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        PlaybackState s = svc.primaryController.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
            svc.primaryController.getTransportControls().pause();
        } else {
            svc.primaryController.getTransportControls().play();
        }
    }

    public static void play() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        svc.primaryController.getTransportControls().play();
    }

    public static void pause() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        svc.primaryController.getTransportControls().pause();
    }

    public static void next() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        svc.primaryController.getTransportControls().skipToNext();
    }

    public static void previous() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        svc.primaryController.getTransportControls().skipToPrevious();
    }

    public static boolean isPlaying() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return false;
        PlaybackState s = svc.primaryController.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    public static String getCurrentTitle() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return null;
        android.media.MediaMetadata md = svc.primaryController.getMetadata();
        if (md == null) return null;
        CharSequence title = md.getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
        return title == null ? null : title.toString();
    }

    public static String getCurrentArtist() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return null;
        android.media.MediaMetadata md = svc.primaryController.getMetadata();
        if (md == null) return null;
        CharSequence art = md.getText(android.media.MediaMetadata.METADATA_KEY_ARTIST);
        return art == null ? null : art.toString();
    }

    public static String getActivePackage() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return null;
        return svc.primaryController.getPackageName();
    }

    public static String getCurrentAlbumArtUri() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return null;
        android.media.MediaMetadata md = svc.primaryController.getMetadata();
        if (md == null) return null;
        String uri = md.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri == null) uri = md.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI);
        return uri;
    }

    public static byte[] getCurrentAlbumArtBytes() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return null;
        android.media.MediaMetadata md = svc.primaryController.getMetadata();
        if (md == null) return null;
        Bitmap art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
        if (art == null) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            art.compress(Bitmap.CompressFormat.PNG, 90, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static long getPlaybackPosition() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return 0;
        PlaybackState s = svc.primaryController.getPlaybackState();
        if (s == null) return 0;
        // position may be updated; use state's position
        return s.getPosition();
    }

    public static long getPlaybackDuration() {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return 0;
        android.media.MediaMetadata md = svc.primaryController.getMetadata();
        if (md == null) return 0;
        try {
            long d = md.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
            return d;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void seekTo(long posMs) {
        GlobalMediaControllerService svc = instance;
        if (svc == null || svc.primaryController == null) return;
        try {
            svc.primaryController.getTransportControls().seekTo(posMs);
        } catch (Exception ignored) {}
    }
}
