package com.chinaway.android.myapplication

import android.media.MediaCodec

interface RtmpSource {

    fun push(mediaCodec: MediaCodec?, start: Boolean, bufferInfo: MediaCodec.BufferInfo?)

    fun release()
}