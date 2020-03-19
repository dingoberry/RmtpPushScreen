package com.chinaway.android.myapplication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.layout.*
import java.lang.ref.WeakReference


class MediaVideoActivity : AppCompatActivity(), LifecycleOwner {

    private var mAction: IPushAction

    init {
//        mAction = CustomPushAction(lifecycle, this)

        mAction = EasyPushAction(WeakReference(this), Consumer {
            updateBtnText(it)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout);

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

        rtmpPort.setText(RtmpFactory.port)
        rtmpPort.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                RtmpFactory.port = s.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!mAction.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun toggleAction(v: View) {
        mAction.toggle()?.apply {
            updateBtnText(this)
        }
    }

    private fun updateBtnText(stat: Boolean) {
        if (stat) {
            actionBtn.text = "Stop"
        } else {
            actionBtn.text = "Start"
        }
    }
}