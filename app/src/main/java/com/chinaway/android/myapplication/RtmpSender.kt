package com.chinaway.android.myapplication

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.util.Log
import kotlin.experimental.and

class RtmpSender(val url: String) : RtmpSource {
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

        init {
            System.loadLibrary("rtmp-lib")
        }
    }

    private var mJniRtmpPt: Long? = null;
    private var mStartTime: Long? = null

    init {
        mJniRtmpPt = open(url)

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

    private fun write(
        data: ByteArray,
        size: Int,
        type: Int,
        ts: Int
    ): Int {
        return mJniRtmpPt?.run {
            writeNative(this, data, size, type, ts)
        } ?: -1
    }

    override fun release() {
        mJniRtmpPt?.apply {
            closeNative(this)
        }
        mJniRtmpPt = null
        mStartTime = null
    }

    override fun push(mediaCodec: MediaCodec?, start: Boolean, bufferInfo: BufferInfo?) {
        mediaCodec?.apply ec@{
            Log.i("yymm", "extract")
            while (start) {
                bufferInfo?.apply {
                    val status = this@ec.dequeueOutputBuffer(this, 10000)
                    when {
                        status in setOf(
                            MediaCodec.INFO_TRY_AGAIN_LATER,
                            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
                        ) -> {
                        }
                        status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            this@ec.getOutputFormat().apply {
                                Log.i("yymm", "FormMt=" + this.toString())
                                val spsBuf = getByteBuffer("csd-0").also {
                                    it.position(4)
                                }
                                val ppsBuf = getByteBuffer("csd-1").also {
                                    it.position(4)
                                }
                                val spsLen = spsBuf.remaining()
                                val ppsLen = ppsBuf.remaining()
                                val bufBytes = ByteArray(11 + spsLen + ppsLen)
                                spsBuf.get(bufBytes, 8, spsLen)
                                ppsBuf.get(bufBytes, 8 + spsLen + 3, ppsLen)

                                bufBytes[0] = 0x01
                                bufBytes[1] = bufBytes[9]
                                bufBytes[2] = bufBytes[10]
                                bufBytes[3] = bufBytes[11]
                                bufBytes[4] = 0xFF.toByte()
                                bufBytes[5] = 0xE1.toByte()
                                bufBytes[6] = ((spsLen shr 8) and 0xFF).toByte()
                                bufBytes[7] = (spsLen and 0xFF).toByte()
                                ((8 + spsLen).apply {
                                    bufBytes[this] = 0x01
                                } + 1).apply {
                                    bufBytes[this] = (ppsLen shr 8).and(0xFF).toByte()
                                    bufBytes[this + 1] = ppsLen.and(0xFF).toByte()
                                }

                                val finalBuf = ByteArray(FLV_VIDEO_TAG_LENGTH + bufBytes.size)
                                fillFlvVideoInfo(
                                    finalBuf,
                                    isAvcSqHeader = true,
                                    isIDR = true,
                                    len = bufBytes.size
                                )

                                System.arraycopy(
                                    bufBytes,
                                    0,
                                    finalBuf,
                                    FLV_VIDEO_TAG_LENGTH,
                                    bufBytes.size
                                )
                                write(
                                    finalBuf,
                                    finalBuf.size,
                                    FLV_RTMP_PACKET_TYPE_VIDEO,
                                    0
                                )
                            }
                        }
                        status < 0 -> throw  RuntimeException("unexpected status=" + status)
                        else -> {
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
                                        write(
                                            bytes,
                                            bytes.size,
                                            FLV_RTMP_PACKET_TYPE_VIDEO,
                                            (presentationTimeUs / 1000 - mStartTime as Long).toInt()
                                        )
                                    }

                                })
                        }
                    }
                } ?: return@push
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
}