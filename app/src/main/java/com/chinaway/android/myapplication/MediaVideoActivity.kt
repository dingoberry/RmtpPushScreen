package com.chinaway.android.myapplication

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.*
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.layout.*
import java.io.IOException
import kotlin.experimental.and


class MediaVideoActivity : AppCompatActivity() {

    private companion object {
        const val RECORD_WIDTH = 480
        const val RECORD_HEIGHT = 720
        const val MIME_TYPE = "video/avc"

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

    private var mMpm: MediaProjectionManager? = null
    private var mC: MediaCodec? = null
    private var mBi: BufferInfo? = null

    private var mHandle: Handler? = null
    private var mHt: HandlerThread? = null

    private var mRs: RtmpSender? = null
    private var mStartTime: Long? = null

    private var mStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout);

        mRs = RtmpSender()

        mHandle = Handler(
            HandlerThread("ec").also {
                it.start()
                mHt = it
            }.looper
        )

        mMpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (22 == requestCode) {
            data.takeIf { it != null }?.apply data@{
                makeSurface()?.apply {
                    mMpm?.getMediaProjection(resultCode, this@data)?.createVirtualDisplay(
                        "-Ds",
                        RECORD_WIDTH,
                        RECORD_HEIGHT,
                        1,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                        this,
                        null,
                        null
                    )
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        mStart = false
        stop()
    }

    private fun stop() {
        mHandle?.post {
            mC?.apply {
                signalEndOfInputStream()
                stop()
                mHt?.quit()
            }
        }

        mRs?.release()
        mStartTime = null
    }

    private fun makeSurface(): Surface? {
        mBi = BufferInfo()
        val f = MediaFormat.createVideoFormat(MIME_TYPE, RECORD_WIDTH, RECORD_HEIGHT).also {
            it.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            it.setInteger(MediaFormat.KEY_BIT_RATE, 800000)
            it.setInteger(MediaFormat.KEY_FRAME_RATE, 15) // 30fps
            it.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5) // 5 seconds
        }

        var result: Surface? = null
        try {
            mC = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                result = createInputSurface()
                start()
            }

            mHandle?.post {
                extract()
            }
        } catch (e: IOException) {
            Log.e("yymm", "eee", e)
        }
        return result
    }

    private fun extract() {
        mC?.apply ec@{
            Log.i("yymm", "extract")
            while (mStart) {
                mBi?.apply {
                    val status = this@ec.dequeueOutputBuffer(this ?: return@extract, 10000)
                    when {
                        status in setOf(INFO_TRY_AGAIN_LATER, INFO_OUTPUT_BUFFERS_CHANGED) -> {
                        }
                        status == INFO_OUTPUT_FORMAT_CHANGED -> {
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
                                mRs?.write(
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
                                return@extract
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
                                        mRs?.write(
                                            bytes,
                                            bytes.size,
                                            FLV_RTMP_PACKET_TYPE_VIDEO,
                                            (presentationTimeUs / 1000 - mStartTime as Long).toInt()
                                        )
                                    }

                                })
                        }
                    }
                } ?: return@extract
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

    fun toggleAction(v: View) {
        mMpm?.apply {
            mStart = !mStart
            if (mStart) {
                startActivityForResult(this.createScreenCaptureIntent(), 22)
                actionBtn.text = "Stop"
            } else {
                stop()
                actionBtn.text = "Start"
                Toast.makeText(this@MediaVideoActivity, "Stop", Toast.LENGTH_SHORT).show()
            }
        }
    }
}