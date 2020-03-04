package com.chinaway.android.myapplication

object RtmpFactory {
    private const val SERVER_URL =
        "rtmp://192.168.1.5/live/stream?sign=1592814912-4ddf8467ce37be7f933bb8553c4a5ca6"

    var url = SERVER_URL

    fun createSender(w: Int, h: Int): RtmpSource {
        return RtmpSender(url)
    }
}