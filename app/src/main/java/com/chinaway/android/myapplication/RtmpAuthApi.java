package com.chinaway.android.myapplication;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.cry.cry.mediaprojectioncode.sender.Sender;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

public class RtmpAuthApi implements RtmpSource {

    private volatile boolean mIsStopRequested;

    public RtmpAuthApi(String url, int w, int h) {
        Sender.getInstance().open(url, w, h);
    }

    @Override
    public void push(@Nullable MediaCodec mediaCodec, boolean start, @Nullable MediaCodec.BufferInfo bufferInfo) {
        final int TIMEOUT_USEC = 10000;
        long firstInputTimeNsec = -1;
        boolean outputDone = false;
        while (!outputDone) {
//            if (VERBOSE) Log.d(TAG, "loop");
            if (mIsStopRequested) {
                return;
            }

            int decoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
//                if (VERBOSE) Log.d(TAG, "no output from decoder available");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not important for us, since we're using Surface
//                if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mediaCodec.getOutputFormat();
                Sender.getInstance().rtmpSendFormat(newFormat);
            } else if (decoderStatus < 0) {
                throw new RuntimeException(
                        "unexpected result from decoder.dequeueOutputBuffer: " +
                                decoderStatus);
            } else { // decoderStatus >= 0
                if (firstInputTimeNsec != 0) {
                    // Log the delay from the first buffer of input to the first buffer
                    // of output.
                    long nowNsec = System.nanoTime();
                    firstInputTimeNsec = 0;
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true;
                }

                boolean doRender = (bufferInfo.size != 0);

                if (doRender) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(decoderStatus);
                    Sender.getInstance().rtmpSend(bufferInfo, outputBuffer);
                }
                mediaCodec.releaseOutputBuffer(decoderStatus, doRender);
            }
        }
    }

    @Override
    public void release() {
        mIsStopRequested = true;
        Sender.getInstance().close();
    }
}
