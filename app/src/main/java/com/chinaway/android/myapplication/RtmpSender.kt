package com.chinaway.android.myapplication

import android.util.Log

class RtmpSender() {
    private companion object {
        const val SERVER_URL =
            "rtmp://80869.livepush.myqcloud.com/live/lop?txSecret=b19386bdc0f44adfebc87f69bf5c54ae&txTime=5E5938FF"

        init {
            System.loadLibrary("rtmp-lib")
        }
    }

    private var mJniRtmpPt: Long? = null;

    init {
        mJniRtmpPt = open(SERVER_URL)

        Log.i("yymm", "JniRtmpPointer=" + mJniRtmpPt)
    }

    external fun open(url: String): Long

    private external fun closeNative(pointer: Long)

    private external fun writeNative(
        pointer: Long,
        data: ByteArray,
        size: Int,
        type: Int,
        ts: Int
    ): Int

    fun write(
        data: ByteArray,
        size: Int,
        type: Int,
        ts: Int
    ): Int {
        return mJniRtmpPt?.run {
            writeNative(this, data, size, type, ts)
        } ?: -1
    }

    fun release() {
        mJniRtmpPt?.apply {
            closeNative(this)
        }
        mJniRtmpPt = null
    }
}