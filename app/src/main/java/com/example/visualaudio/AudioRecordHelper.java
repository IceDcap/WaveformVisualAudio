package com.example.visualaudio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import com.example.visualaudio.soundfile.CheapSoundFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import ca.uol.aig.fftpack.RealDoubleFFT;

/**
 * Created by icedcap on 14/05/2017.
 */

public class AudioRecordHelper {
    private static final String LOG_TAG = "AudioRecordHelper";
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int EOF = -1;
    private RandomAccessFile mRandomAccessFile;
    private static final int SAMPLE_RATE = 44100;
    private boolean mShouldContinue;
    private int mRecordBufferSize;
    private AudioTrack mAudioTrack;
    private ShortBuffer mSamples; // the samples to play
    private int mNumSamples; // number of samples to play
    private File mFile;
    private String mFilename;
    private CheapSoundFile mCheapSoundFile;
    private MediaPlayer mWaveMediaPlayer;
    private ThreadPoolExecutor mThreadPoolExecutor;
    private int mWaveSize;

    final WaveHeader waveHeader = new WaveHeader();

    private OnAudioRecordListener mOnAudioRecordListener;
    private CheapSoundFile.ProgressListener mProgressListener;

    private long mOffset;

    public void setOnAudioRecordListener(OnAudioRecordListener onAudioRecordListener) {
        mOnAudioRecordListener = onAudioRecordListener;
    }

    public AudioRecordHelper(File file) {
        try {
            mFile = file;
            mFilename = mFile.getAbsolutePath();
            mRandomAccessFile = new RandomAccessFile(mFile, "rw");
            mThreadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public int getWaveSize() {
        return mWaveSize;
    }

    public void start(long time) {
        mShouldContinue = true;
        if (time > 0) {
            try {
                CheapSoundFile cheapSoundFile = CheapSoundFile.create(mFile.getAbsolutePath(), new CheapSoundFile.ProgressListener() {
                    @Override
                    public boolean reportProgress(double fractionComplete) {
                        return false;
                    }
                });
                final long filePointerSeek = cheapSoundFile.getAvgBitrateKbps() * time;
                recordAudio(filePointerSeek);

            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            recordAudio(0);
        }
    }


    public void stop() {
        mShouldContinue = false;
    }

    public void play(final long seek) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                playWavFile(mFile, 1, seek);
            }
        }).start();

    }

    public void stopMediaPlayer() {
        if (mWaveMediaPlayer != null && mWaveMediaPlayer.isPlaying()) {
            mWaveMediaPlayer.stop();
            if (mOnAudioRecordListener != null) {
                mOnAudioRecordListener.onMediaPlayerStop();
            }
        }
    }


    private void recordAudio(final long offset) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mOffset = offset;

                if (mOffset < 44) {
                    final short numChannels = 1;
                    final short bitsPerSample = 16;
                    waveHeader.setFormat(WaveHeader.FORMAT_PCM);
                    waveHeader.setNumChannels(numChannels);
                    waveHeader.setSampleRate(44100);
                    waveHeader.setBitsPerSample(bitsPerSample);
                    mOffset = 44;
                }


                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                // buffer size
                mRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (mRecordBufferSize == AudioRecord.ERROR || mRecordBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    mRecordBufferSize = SAMPLE_RATE * 2;
                }

                // audio buffer (AudioRecord receive the short type data)
                final int blockSize = mRecordBufferSize / 2;
                final short[] audioBuffer = new short[blockSize];
//
                final RealDoubleFFT fftTrans = new RealDoubleFFT(blockSize);
                final double[] toTrans = new double[blockSize];


                AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, mRecordBufferSize);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(LOG_TAG, "Audio Record can not initialized!");
                    return;
                }

                audioRecord.startRecording();
                final Timer timer = new Timer("waveform-record");

                if (mOnAudioRecordListener != null) {
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            mOnAudioRecordListener.onWaveSize(mWaveSize);

                        }
                    }, 0, 25);
                }

                Log.v(LOG_TAG, "Start recording...");

                try {
                    mRandomAccessFile.seek(mOffset);
                } catch (IOException e) {
                    e.printStackTrace();
                }


                long bytesRead = 0;
                long shortsRead = 0;
                int index = 0;
                while (mShouldContinue) {
                    final int numberOfShort = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    shortsRead += numberOfShort;

                    mThreadPoolExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < blockSize && i < numberOfShort; i++) {
                                toTrans[i] = (double) audioBuffer[i] / Short.MAX_VALUE;
                            }
                            fftTrans.ft(toTrans);
                            setCurrentWaveSize(toTrans, blockSize);
                        }
                    });

                    // write to storage
                    byte[] b = short2byte(audioBuffer);
                    bytesRead += b.length;
                    try {
                        mRandomAccessFile.write(b, 0, mRecordBufferSize);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                final long endPoint = System.currentTimeMillis();
                audioRecord.stop();
                try {
                    mOffset = mRandomAccessFile.getFilePointer();
                    int byteCount = (int) (mRandomAccessFile.getFilePointer() - 44);
                    waveHeader.setNumBytes(byteCount);
                    mRandomAccessFile.seek(0);
                    mRandomAccessFile.write(waveHeader.getHeader(), 0, 44);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                timer.cancel();
                audioRecord.release();
                Log.v(LOG_TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
            }
        }).start();
    }

    private void playWavFile(File file, final float volume, final long seek) {
        if (mWaveMediaPlayer == null) {
            mWaveMediaPlayer = new MediaPlayer();
        }

        final Timer timer = new Timer("waveform-play");
        try {
            mWaveMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (volume >= 0 && volume <= 1) {
                        mp.setVolume(volume, volume);
                    }
                    mp.seekTo((int) seek);
                    mp.start();
                    if (mOnAudioRecordListener != null) {
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                mOnAudioRecordListener.onUpdateWaveFramePos();
                            }
                        }, 0, 25);
                    }
                }
            });

            mWaveMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(LOG_TAG, "Media Player onError");
                    timer.cancel();
                    return false;
                }
            });

            mWaveMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    mWaveMediaPlayer = null;
                    timer.cancel();
                    if (mOnAudioRecordListener != null) {
                        mOnAudioRecordListener.onMediaPlayerStop();
                        mOnAudioRecordListener.onMediaPlayerComplete();
                    }
                }
            });


            FileDescriptor fd = null;
            FileInputStream fis = new FileInputStream(file);
            fd = fis.getFD();
            if (fd != null) {
                mWaveMediaPlayer.setDataSource(fd);
                mWaveMediaPlayer.prepare();
//                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playPCMAudio(File file, float volume) {
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioTrack.ERROR || bufferSize == AudioTrack.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }

        if (mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize, AudioTrack.MODE_STREAM);
            if (volume >= 0 && volume <= 1) {
                mAudioTrack.setStereoVolume(volume, volume);
            }
        }

        mAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
                Log.v(LOG_TAG, "Audio file end reached");
                track.release();


            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {

            }
        });
        mAudioTrack.setPositionNotificationPeriod(SAMPLE_RATE / 30); // 30 times per second
        mAudioTrack.setNotificationMarkerPosition(mNumSamples);

        mAudioTrack.play();

        Log.v(LOG_TAG, "Audio file started");

        short[] buffer = new short[bufferSize];
        try {
            short[] samples = getSamples(file);
            mSamples = ShortBuffer.wrap(samples);
            mNumSamples = samples.length;
        } catch (IOException e) {
            e.printStackTrace();
        }
        mSamples.rewind();
        int limit = mNumSamples;
        int totalWritten = 0;
        while (mSamples.position() < limit && mShouldContinue) {
            int numSamplesLeft = limit - mSamples.position();
            int samplesToWrite;
            if (numSamplesLeft >= buffer.length) {
                mSamples.get(buffer);
                samplesToWrite = buffer.length;
            } else {
                for (int i = numSamplesLeft; i < buffer.length; i++) {
                    buffer[i] = 0;
                }
                mSamples.get(buffer, 0, numSamplesLeft);
                samplesToWrite = numSamplesLeft;
            }
            totalWritten += samplesToWrite;
            mAudioTrack.write(buffer, 0, samplesToWrite);
        }

        if (!mShouldContinue || mSamples.position() >= limit) {
            mShouldContinue = false;
            if (mAudioTrack != null) {
                mAudioTrack.release();
            }
            mAudioTrack = null;
        }

        Log.v(LOG_TAG, "Audio streaming finished. Samples written: " + totalWritten);
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

    private void setCurrentWaveSize(double[] toTrans, int bufferSize) {
        int i = 0;
        double res = 0;
        while (i < bufferSize / 6) {
            res += Math.abs(toTrans[i] * 10);
            i++;
        }
        mWaveSize = (int) (res / (bufferSize / 6));
    }


    private short[] getSamples(File file) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file), 8 * 1024);
        if (file.getAbsolutePath().endsWith("wav")) {
            is.skip(44);
        }
        byte[] data;
        try {
            data = toByteArray(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }

        ShortBuffer sb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] samples = new short[sb.limit()];
        sb.get(samples);
        return samples;
    }

    private byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count = 0;
        int n;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return output.toByteArray();
    }


    public interface OnAudioRecordListener {
        void onWaveSize(int size);

        void onUpdateWaveFramePos();

        void onMediaPlayerStart();

        void onMediaPlayerStop();

        void onMediaPlayerComplete();
    }
}
