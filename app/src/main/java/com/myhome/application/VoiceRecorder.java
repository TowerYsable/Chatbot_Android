package com.myhome.application;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.konovalov.vad.Vad;
import com.konovalov.vad.VadConfig;
import com.konovalov.vad.VadListener;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_IN_STEREO;

/**
 * Created by George Konovalov on 11/16/2019.
 */

public class VoiceRecorder {
    private static final int PCM_CHANNEL = CHANNEL_IN_MONO;
    private static final int PCM_ENCODING_BIT = AudioFormat.ENCODING_PCM_16BIT;
//    private static long detectedVoiceSamplesMillis;

    private long previousTimeMillis = System.currentTimeMillis();
    private boolean needResetDetectedSamples = true;
    public long detectedVoiceSamplesMillis = 0;
    private long detectedSilenceSamplesMillis = 0;
    private boolean isWork = false;

    private Vad vad;
    private AudioRecord audioRecordVad;
    private Listener callback;
    private Thread thread;

    private boolean isListening = false;

    private static final String TAG = VoiceRecorder.class.getSimpleName();
    private static long SpeechTime;
    private static long NoiseTime;
//#############

    public VoiceRecorder(Listener callback, VadConfig config) {
        this.callback = callback;
        this.vad = new Vad(config);
    }

    public void updateConfig(VadConfig config) {
        vad.setConfig(config);
    }

    public void start() {
        stop();
        audioRecordVad = createAudioRecord();
        if (audioRecordVad != null) {
            isListening = true;
            audioRecordVad.startRecording();

            thread = new Thread(new ProcessVoice());
            thread.start();
            vad.start();
        } else {
            Log.w(TAG, "Failed start Voice Recorder!");
        }
    }


    public void stop() {
        isListening = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (audioRecordVad != null) {
            try {
                audioRecordVad.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stop AudioRecord ", e);
            }
            audioRecordVad = null;
        }
        if (vad != null) {
            vad.stop();
        }
    }


    private AudioRecord createAudioRecord() {
        try {
            final int minBufSize = AudioRecord.getMinBufferSize(vad.getConfig().getSampleRate().getValue(), PCM_CHANNEL, PCM_ENCODING_BIT);

            if (minBufSize == AudioRecord.ERROR_BAD_VALUE) {
                return null;
            }

            final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, vad.getConfig().getSampleRate().getValue(), PCM_CHANNEL, PCM_ENCODING_BIT, minBufSize);

            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                return audioRecord;
            } else {
                audioRecord.release();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error can't create AudioRecord ", e);
        }

        return null;
    }

    private int getNumberOfChannels() {
        switch (PCM_CHANNEL) {
            case CHANNEL_IN_MONO:
                return 1;
            case CHANNEL_IN_STEREO:
                return 2;
        }
        return 1;
    }

    private class ProcessVoice implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            while (!Thread.interrupted() && isListening && audioRecordVad != null) {
                short[] buffer = new short[vad.getConfig().getFrameSize().getValue() * getNumberOfChannels() * 2];
                audioRecordVad.read(buffer, 0, buffer.length);

                detectSpeech(buffer);
            }
        }

//        private void detectSpeech(short[] buffer) {
        private void detectSpeech(short[] buffer) {
            long currentTimeMillis = System.currentTimeMillis();

            if (isWork) {
                detectedVoiceSamplesMillis += currentTimeMillis - previousTimeMillis;
                needResetDetectedSamples = true;
                if (detectedVoiceSamplesMillis > 500) {
                    previousTimeMillis = currentTimeMillis;
                    callback.onSpeechDetected();
                }
            } else {
                if (needResetDetectedSamples) {
                    needResetDetectedSamples = false;
                    detectedSilenceSamplesMillis = 0;
                    detectedVoiceSamplesMillis = 0;
                }
                detectedSilenceSamplesMillis += currentTimeMillis - previousTimeMillis;
                if (detectedSilenceSamplesMillis > 500) {
                    previousTimeMillis = currentTimeMillis;
                    callback.onNoiseDetected();
                }
            }
            SpeechTime = detectedVoiceSamplesMillis;
            NoiseTime = detectedSilenceSamplesMillis;
//            Log.e("de_time", String.valueOf(detectedVoiceSamplesMillis)+'\n'+String.valueOf(detectedSilenceSamplesMillis)+'\n'+VadTime);
            previousTimeMillis = currentTimeMillis;

            vad.addContinuousSpeechListener(buffer, new VadListener() {
                @Override
                public void onSpeechDetected() {
                    isWork = true;

                    callback.onSpeechDetected();
                }

                @Override
                public void onNoiseDetected() {
                    isWork = false;
                    callback.onNoiseDetected();
                }
            });
        }
    }


    public interface Listener {
        void onSpeechDetected();

        void onNoiseDetected();
    }


    public static long ReturnSpeechTime(){
        return SpeechTime;
    }
    public static long ReturnNoiseTime(){
        return NoiseTime;
    }



}
