/* DO NOT EDIT THIS FILE - it is machine generated */
#include <android/log.h>
#include <jni.h>
#include <stddef.h>
#include "log.h"
/* Header for class me_lake_librestreaming_rtmp_RtmpClient */

#ifndef _Included_me_lake_librestreaming_rtmp_RtmpClient
#define _Included_me_lake_librestreaming_rtmp_RtmpClient
#ifdef __cplusplus
extern "C" {
#endif

 JNIEXPORT jlong JNICALL Java_me_lake_librestreaming_rtmp_RtmpClient_open
 (JNIEnv * env, jobject thiz, jstring url_, jboolean isPublishMode);
/*
 * Class:     me_lake_librestreaming_rtmp_RtmpClient
 * Method:    read
 * Signature: ([CI)I
 */
 JNIEXPORT jint JNICALL Java_me_lake_librestreaming_rtmp_RtmpClient_read
 (JNIEnv * env, jobject thiz,jlong rtmp, jbyteArray data_, jint offset, jint size);

/*
 * Class:     me_lake_librestreaming_rtmp_RtmpClient
 * Method:    write
 * Signature: ([CI)I
 */
 JNIEXPORT jint JNICALL Java_me_lake_librestreaming_rtmp_RtmpClient_write
 (JNIEnv * env, jobject thiz,jlong rtmp, jbyteArray data, jint size, jint type, jint ts);

/*
 * Class:     me_lake_librestreaming_rtmp_RtmpClient
 * Method:    close
 * Signature: ()I
 */
 JNIEXPORT jint JNICALL Java_me_lake_librestreaming_rtmp_RtmpClient_close
 (JNIEnv * env,jlong rtmp, jobject thiz);



#ifdef __cplusplus
}
#endif
#endif
