package com.chinaway.android.myapplication

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.layout.*
import me.lake.librestreaming.core.MediaCodecHelper
import me.lake.librestreaming.model.RESConfig
import me.lake.librestreaming.model.RESCoreParameters
import me.lake.librestreaming.tools.LogTools
import java.io.IOException


class MediaVideoActivity : AppCompatActivity() {

    private companion object {
        const val RECORD_WIDTH = 480
        const val RECORD_HEIGHT = 720
        const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    private var mMpm: MediaProjectionManager? = null
    private var mC: MediaCodec? = null
    private var mBi: BufferInfo? = null

    private var mHandle: Handler? = null
    private var mHt: HandlerThread? = null

    private var mRs: RtmpSource? = null

    private var mStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout);

        mRs = RtmpFactory.createSender(RECORD_WIDTH, RECORD_HEIGHT)
        rtmpUrl.setText(RtmpFactory.url)
        rtmpUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                RtmpFactory.url = s.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        mHandle = Handler(
            HandlerThread("ec").also {
                it.start()
                mHt = it
            }.looper
        )

        mMpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        LogTools.d("onActivityResult")
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
            mC = MediaCodecHelper.createHardVideoMediaCodec(RESCoreParameters().apply {
                rtmpAddr =
                    "rtmp://192.168.1.5/live/stream?sign=1592814912-4ddf8467ce37be7f933bb8553c4a5ca6"
                videoWidth = RECORD_WIDTH
                videoHeight = RECORD_HEIGHT
                senderQueueLength = 200
                filterMode = RESConfig.FilterMode.HARD
                videoFPS = 15
                videoBufferQueueNum = 5
                mediacodecAVCFrameRate = 15
                mediacdoecAVCBitRate = 2000000
                mediacodecAVCIFrameInterval = 2
                renderingMode = RESConfig.RenderingMode.NativeWindow
            }, f).apply {
                configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                result = createInputSurface()
                start()
            }

            mHandle?.post {
                LogTools.d("PUSH")
                mRs?.push(mC, mStart, mBi)
            }
        } catch (e: IOException) {
            Log.e("yymm", "eee", e)
        }
        return result
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