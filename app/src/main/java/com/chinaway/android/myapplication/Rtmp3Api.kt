package com.chinaway.android.myapplication

import android.media.MediaCodec
import android.util.Log
import net.butterflytv.rtmp_client.RTMPMuxer
import kotlin.experimental.and

class Rtmp3Api(val url: String, val w: Int, val h: Int) : RtmpSource {

    private companion object {
        const val FLV_TAG_LENGTH = 11
        const val FLV_VIDEO_TAG_LENGTH = 5
        const val FLV_AUDIO_TAG_LENGTH = 2
        const val FLV_TAG_FOOTER_LENGTH = 4
        const val NALU_HEADER_LENGTH = 4

        const val FLV_RTMP_PACKET_TYPE_VIDEO = 9
        const val FLV_RTMP_PACKET_TYPE_AUDIO = 8
        const val FLV_RTMP_PACKET_TYPE_INFO = 18
        const val NALU_TYPE_IDR = 5
    }

    private var mPusher: RTMPMuxer?;
    private var mStartTime: Long? = null

    init {
        mPusher = RTMPMuxer().apply {
            open(url, w, h)
            write_flv_header(false, true)
        }
    }

    override fun push(mediaCodec: MediaCodec?, start: Boolean, bufferInfo: MediaCodec.BufferInfo?) {
        mediaCodec?.apply ec@{
            bufferInfo?.apply {
                val status = dequeueOutputBuffer(this, 10000)
                if (status > 0) {
                    if (this.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return@push
                    }

                    releaseOutputBuffer(
                        status,
                        null != this.size.takeIf { 0 != it }?.apply {
                            this@ec.getOutputBuffer(status)?.apply {
                                position(offset + 4)
                                limit(offset + size)

                                val len = remaining()
                                val offset = FLV_VIDEO_TAG_LENGTH + NALU_HEADER_LENGTH
                                val bytes = ByteArray(offset + len)
                                get(bytes, offset, len)
                                fillFlvVideoInfo(
                                    bytes, false,
                                    bytes[offset].and(0x1F).toInt() == 5, len
                                )
                                Log.i("yymm", "Out pos=" + bytes.size)

                                if (null == mStartTime) {
                                    mStartTime = presentationTimeUs / 1000
                                }

                                mPusher?.writeVideo(
                                    bytes,
                                    0,
                                    bytes.size,
                                    presentationTimeUs / 1000 - mStartTime as Long
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    private fun fillFlvVideoInfo(
        buffer: ByteArray,
        isAvcSqHeader: Boolean,
        isIDR: Boolean,
        len: Int,
        pos: Int = 0
    ) {
        buffer[pos] = if (isIDR) 0x17 else 0x27
        buffer[pos + 1] = if (isAvcSqHeader) 0x00 else {
            buffer[pos + 5] = (len shr 24).and(0xFF).toByte()
            buffer[pos + 5 + 1] = (len shr 16).and(0xFF).toByte()
            buffer[pos + 5 + 2] = (len shr 8).and(0xFF).toByte()
            buffer[pos + 5 + 3] = (len).and(0xFF).toByte()
            0x01
        }
        buffer[pos + 2] = 0x00
        buffer[pos + 3] = 0x00
        buffer[pos + 4] = 0x00
    }

    override fun release() {
        mPusher?.close().also {
            mPusher = null
        }
        mStartTime = null
    }
}