package com.nido.a05sinput;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Short synthesized ticks for the Anki remote; built in memory, no audio files. */
final class AnkiSounds {
    private AudioTrack flip;
    private AudioTrack good;
    private AudioTrack again;
    private AudioTrack hard;
    private AudioTrack easy;

    AnkiSounds() {
        try {
            flip = tone(1400, 12, 280.0);
            good = tone(720, 16, 180.0);
            again = tone(280, 24, 120.0);
            hard = tone(460, 20, 150.0);
            easy = tone(1800, 10, 320.0);
        } catch (Throwable ignored) {
            // Audio is a nicety; the remote works without it.
        }
    }

    void play(AnkiAction action) {
        switch (action) {
            case GOOD: play(good); break;
            case AGAIN: play(again); break;
            case HARD: play(hard); break;
            case EASY: play(easy); break;
            default: play(flip); break;
        }
    }

    void release() {
        for (AudioTrack track : new AudioTrack[]{flip, good, again, hard, easy}) {
            try {
                if (track != null) track.release();
            } catch (Throwable ignored) {
            }
        }
        flip = good = again = hard = easy = null;
    }

    private static void play(AudioTrack track) {
        if (track == null) return;
        try {
            track.stop();
            track.reloadStaticData();
            track.play();
        } catch (Throwable ignored) {
        }
    }

    private static AudioTrack tone(int frequency, int durationMs, double decay) {
        int sampleRate = 44100;
        int samples = (sampleRate * durationMs) / 1000;
        short[] buffer = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = (double) i / sampleRate;
            buffer[i] = (short) (Math.sin(2.0 * Math.PI * frequency * t) * Math.exp(-t * decay) * 28000);
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(buffer, 0, samples);
        return track;
    }
}
