package com.chinaway.android.myapplication

object RtmpFactory {
    private const val SERVER_URL =
        "rtmp://192.168.1.5/live/stream"

    var url = SERVER_URL

    var port = "1935"

    fun createSender(w: Int, h: Int): RtmpSource {
        return RtmpSender(url)
    }
}