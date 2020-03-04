package com.chinaway.android.myapplication

import android.media.MediaCodec
import android.util.Log
import me.lake.librestreaming.core.VideoSenderThread
import me.lake.librestreaming.model.RESConfig
import me.lake.librestreaming.model.RESCoreParameters
import me.lake.librestreaming.rtmp.RESFlvDataCollecter
import me.lake.librestreaming.rtmp.RESRtmpSender

class RtmpLiveApi(val url: String, val w: Int, val h: Int) : RtmpSource {

    private val mSender: RESRtmpSender
    private val mCollecter: RESFlvDataCollecter
    private var mWorker: VideoSenderThread? = null

    init {
        mSender = RESRtmpSender().apply {
            prepare(RESCoreParameters().apply {
                rtmpAddr = url
                videoWidth = w
                videoHeight = h
                senderQueueLength = 200
                filterMode = RESConfig.FilterMode.HARD
                videoFPS = 15
                videoBufferQueueNum = 5
                mediacodecAVCFrameRate = 15
                mediacdoecAVCBitRate = 2000000
                mediacodecAVCIFrameInterval = 2
                renderingMode = RESConfig.RenderingMode.NativeWindow
            })
        }
        mCollecter = RESFlvDataCollecter { flvData, type ->
            {
                mSender.feed(flvData, type)
                Log.i("yymm", "Feed=" + flvData + ", type=" + type)
            }
        }
    }

    override fun push(mediaCodec: MediaCodec?, start: Boolean, bufferInfo: MediaCodec.BufferInfo?) {
        if (null == mWorker) {
            mSender.start(url)
            mWorker = VideoSenderThread("VideoSenderThread", mediaCodec, mCollecter).apply {
                start()
            }
        }
    }

    override fun release() {
        mWorker?.apply {
            quit()
            mWorker = null
        }
        mSender.stop()
    }
}