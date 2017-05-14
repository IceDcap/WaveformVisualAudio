package com.example.visualaudio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by icedcap on 14/05/2017.
 */

public class AudioRecordHelper {
    private static final String LOG_TAG = "AudioRecordHelper";
    private RandomAccessFile mRandomAccessFile;
    private static final int SAMPLE_RATE = 44100;
    private boolean mShouldContinue;
    private int mRecordBufferSize;
    private static long sWroteAccessFilePointer;
    private static long sRecordedDuration;
    private void recordAudio(final long offset) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                // buffer size
                mRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (mRecordBufferSize == AudioRecord.ERROR || mRecordBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    mRecordBufferSize = SAMPLE_RATE * 2;
                }

                short[] audioBuffer = new short[mRecordBufferSize / 2];

                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, mRecordBufferSize);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(LOG_TAG, "Audio Record can not initialized!");
                    return;
                }

                audioRecord.startRecording();
                final long startPoint = System.currentTimeMillis();

                Log.v(LOG_TAG, "Start recording...");

                try {
                    mRandomAccessFile.seek(offset);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                double[] toTrans = null;

                long bytesRead = 0;
                long shortsRead = 0;
                int index = 0;
                while (mShouldContinue) {
                    int numberOfShort = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    shortsRead += numberOfShort;


                    // write to storage
                    byte[] b = short2byte(audioBuffer);
                    bytesRead += b.length;
                    try {
                        mRandomAccessFile.write(b, 0, mRecordBufferSize);
                        sWroteAccessFilePointer = mRandomAccessFile.getFilePointer();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                final long endPoint = System.currentTimeMillis();
                audioRecord.stop();
                sRecordedDuration += endPoint - startPoint;
                audioRecord.release();
                Log.v(LOG_TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
            }
        }).start();
    }

    //Conversion of short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];

        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }
}
