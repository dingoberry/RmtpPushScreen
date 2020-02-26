//
// Created by WangTyler on 2020-02-25.
//
#include <jni.h>
#include <string>
#include <android/log.h>
#include <rtmp.h>

#define LOGD(...) __android_log_print(ANDROID_LOG_WARN,"yymmNative",__VA_ARGS__)

JNIEXPORT jlong
JNICALL
open(JNIEnv *env, jobject, jstring url) {
    RTMP *rtmp = RTMP_Alloc();
    if (nullptr == rtmp) {
        LOGD("RTMP_Alloc=NULL");
        return NULL;
    }
    RTMP_Init(rtmp);

    const char *_url = env->GetStringUTFChars(url, JNI_FALSE);

    if (!RTMP_SetupURL(rtmp, const_cast<char *>(_url))) {
        RTMP_Free(rtmp);
        LOGD("RTMP_SetupURL=ret");
        return NULL;
    }

    RTMP_EnableWrite(rtmp);

    if (!RTMP_Connect(rtmp, nullptr)) {
        RTMP_Free(rtmp);
        LOGD("RTMP_Connect=ret");
        return NULL;
    }

    if (!RTMP_ConnectStream(rtmp, 0)) {
        RTMP_Close(rtmp);
        RTMP_Free(rtmp);
        LOGD("RTMP_ConnectStream=ret");
        return NULL;
    }
    env->ReleaseStringUTFChars(url, _url);

    __android_log_write(ANDROID_LOG_WARN, "yymmNative", _url);
    return reinterpret_cast<jlong>(rtmp);
}

JNIEXPORT void
JNICALL
close(JNIEnv *env, jobject, jlong rtmpPointer) {
    RTMP *rtmp = (RTMP *) rtmpPointer;
    RTMP_Close(rtmp);
    RTMP_Free(rtmp);
}

JNIEXPORT jint
JNICALL
write(JNIEnv *env, jobject, jlong rtmpPointer, jbyteArray _data, jint size, jint type, jint ts) {
    jbyte *buffer = env->GetByteArrayElements(_data, JNI_FALSE);

    auto *pkt = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(pkt, size);
    RTMPPacket_Reset(pkt);
    switch (type) {
        case RTMP_PACKET_TYPE_INFO:
            pkt->m_nChannel = 0x03;
            break;
        case RTMP_PACKET_TYPE_VIDEO:
            pkt->m_nChannel = 0x04;
            break;
        case RTMP_PACKET_TYPE_AUDIO:
            pkt->m_nChannel = 0x05;
            break;
        default:
            pkt->m_nChannel = -1;
            break;
    }

    RTMP *rtmp = reinterpret_cast<RTMP *>(rtmpPointer);
    pkt->m_nInfoField2 = rtmp->m_stream_id;

    memcpy(pkt->m_body, buffer, size);
    pkt->m_headerType = RTMP_PACKET_SIZE_LARGE;
    pkt->m_hasAbsTimestamp = false;
    pkt->m_nTimeStamp = ts;
    pkt->m_packetType = type;
    pkt->m_nBodySize = size;
    LOGD("Send pkg");
    jint result = RTMP_SendPacket(rtmp, pkt, 0);

    RTMPPacket_Free(pkt);
    free(pkt);
    env->ReleaseByteArrayElements(_data, buffer, 0);

    if (!result) {
        LOGD("end write error %d", result);
        return result;
    } else {
        LOGD("end write success");
        return 0;
    }
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *) {
    JNINativeMethod methodsMapping[] = {
            {"open",        "(Ljava/lang/String;)J", (void *) open},
            {"closeNative", "(J)V",                  (void *) close},
            {"writeNative", "(J[BIII)I",             (void *) write}
    };
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass((char *) "com/chinaway/android/myapplication/RtmpSender");
    if (nullptr == cls) {
        return JNI_ERR;
    }
    if (env->RegisterNatives(cls, methodsMapping,
                             sizeof(methodsMapping) / sizeof(JNINativeMethod)) < 0) {
        return JNI_FALSE;
    }
    // 返回jni的版本
    return JNI_VERSION_1_4;
}