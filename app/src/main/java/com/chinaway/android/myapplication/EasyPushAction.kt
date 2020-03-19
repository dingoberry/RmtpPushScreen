package com.chinaway.android.myapplication

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import io.reactivex.Single
import io.reactivex.functions.Consumer
import io.reactivex.subjects.PublishSubject
import org.easydarwin.push.MediaStream
import org.easydarwin.util.AbstractSubscriber
import org.reactivestreams.Publisher
import java.lang.ref.WeakReference

class EasyPushAction(val mRef: WeakReference<MediaVideoActivity>, val callback: Consumer<Boolean>) :
    IPushAction {

    private val REQUEST_MEDIA_PROJECTION = 1001

    private var mMedia: MediaStream? = null


    private fun getMediaStream(): Single<MediaStream>? {
        mRef.get()?.apply {
            val single = RxHelper.single(MediaStream.getBindedMediaStream(this, this), mMedia)
            return if (mMedia == null) {
                single.doOnSuccess { ms -> mMedia = ms }
            } else {
                return single
            }
        }
        return null
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun onCreate() {
        mRef.get()?.apply {
            Log.i("yymm", "startService")
            startService(Intent(this, MediaStream::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            mRef.get()?.apply {
                getMediaStream()?.subscribe(object : Consumer<MediaStream> {
                    override fun accept(t: MediaStream?) {
                        t?.apply {
                            pushScreen(
                                resultCode,
                                data,
                                "192.168.1.5",
                                RtmpFactory.port,
                                "live"
                            )
                        }
                    }

                })
            }
            return true
        }
        return false
    }

    override fun toggle(): Boolean? {
        mRef.get()?.apply ref@{
            Log.i("yymm", "toggle")
            getMediaStream()?.subscribe(object : Consumer<MediaStream> {
                override fun accept(t: MediaStream?) {
                    Log.i("yymm", "accept")
                    t?.apply {
                        if (t.isScreenPushing) {
                            t.stopPushScreen()
                            callback.accept(false)
                        } else {
                            if (Build.VERSION_CODES.LOLLIPOP > Build.VERSION.SDK_INT) {
                                Toast.makeText(this@ref, "NotSupply", Toast.LENGTH_SHORT).show()
                                return
                            }

                            val mMpMngr =
                                this@ref.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            startActivityForResult(
                                mMpMngr.createScreenCaptureIntent(),
                                REQUEST_MEDIA_PROJECTION
                            )
                            callback.accept(true)
                        }
                    }
                }

            })
        }
        return null
    }


    object RxHelper {
        internal var IGNORE_ERROR = false

        fun <T> single(t: Publisher<T>, defaultValueIfNotNull: T?): Single<T> {
            if (defaultValueIfNotNull != null) return Single.just(defaultValueIfNotNull)
            val sub = PublishSubject.create<T>()
            t.subscribe(object : AbstractSubscriber<T>() {
                override fun onNext(t: T) {
                    super.onNext(t)
                    sub.onNext(t)
                }

                override fun onError(t: Throwable) {
                    if (IGNORE_ERROR) {
                        super.onError(t)
                        sub.onComplete()
                    } else {
                        sub.onError(t)
                    }
                }

                override fun onComplete() {
                    super.onComplete()
                    sub.onComplete()
                }
            })
            return sub.firstOrError()
        }
    }

}