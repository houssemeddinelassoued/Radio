package com.statichiss.recordio;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import com.statichiss.R;
import com.statichiss.recordio.filehandling.M3uHandler;
import com.statichiss.recordio.filehandling.PlsHandler;
import com.statichiss.recordio.utils.StringUtils;

import java.io.IOException;

public class PlayerService extends Service {

    private static final String TAG = "com.statichiss.recordio.PlayerService";
    private RadioActivity caller;
    private boolean buffering = false;

    private final IBinder mBinder = new RadioServiceBinder();

    public boolean alreadyPlaying() {
        MediaPlayer mediaPlayer = ((RadioApplication) getApplicationContext()).getMediaPlayer();
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    @Override
    public void onCreate() {
        RadioApplication radioApplication = (RadioApplication) getApplicationContext();
        MediaPlayer mediaPlayer = radioApplication.getMediaPlayer();
        super.onCreate();
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        }
        radioApplication.setMediaPlayer(mediaPlayer);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void stopPlaying(RadioActivity view) {
        RadioApplication radioApplication = (RadioApplication) getApplicationContext();
        MediaPlayer mediaPlayer = radioApplication.getMediaPlayer();

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
        }

        view.updateUIForPlaying(false, "");
        NotificationHelper.cancelNotification(getApplicationContext(), NotificationHelper.NOTIFICATION_PLAYING_ID);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startPlaying(RadioActivity view, RadioDetails radioDetails) {

        caller = view;
        RadioApplication radioApplication = (RadioApplication) getApplicationContext();
        MediaPlayer mediaPlayer = radioApplication.getMediaPlayer();

        try {

            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mediaPlayer) {
                    if (mediaPlayer.isPlaying()) {    //should be false if error occurred
                        mediaPlayer.start();
                    }
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                    Log.e(TAG, "Error occurred");
                    caller.updateUIForPlaying(false, "Error playing");
                    return true;
                }
            });

            if (radioDetails.getPlaylistUrl().endsWith(".pls") || radioDetails.getPlaylistUrl().endsWith(".m3u")) {
                if (radioDetails.getPlaylistUrl().endsWith(".pls")) {
                    radioDetails = PlsHandler.parse(radioDetails);
                } else {
                    radioDetails = M3uHandler.parse(radioDetails);
                }
            } else {
                radioDetails.setStreamUrl(radioDetails.getPlaylistUrl());
            }

            // need to check we aren't buffering before proceeding
            if (buffering) {
                Log.d(TAG, "Buffering already, so resetting MediaPlayer before starting again");
                mediaPlayer.reset();
                buffering = false;
            }

            mediaPlayer.setDataSource(radioDetails.getStreamUrl());
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
            buffering = true;

            final RadioDetails radioDetailsToPlay = radioDetails;

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                    buffering = false;
                    StringBuilder status = new StringBuilder(getString(R.string.playing_string));
                    if (!StringUtils.IsNullOrEmpty(radioDetailsToPlay.getStationName())) {
                        status.append(" ")
                                .append(radioDetailsToPlay.getStationName());
                    }
                    caller.updateUIForPlaying(true, status.toString());

                    NotificationHelper.showNotification(getApplicationContext(), NotificationHelper.NOTIFICATION_PLAYING_ID, radioDetailsToPlay, status.toString(), status.toString());
                }
            });


        } catch (IOException ioe) {
            Log.e(TAG, "Error caught in play", ioe);
            ioe.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            mediaPlayer.reset();
        } finally {
            radioApplication.setMediaPlayer(mediaPlayer);
            radioApplication.setPlayingStation(radioDetails);
        }
    }

    public class RadioServiceBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }
}


