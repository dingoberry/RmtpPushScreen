#include <jni.h>
#include <string>

JNIEXPORT jstring
JNICALL
stringFromJNI(JNIEnv *env) {
    return env->NewStringUTF("HELLO FROM C++ OF NEW");
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *) {
    JNINativeMethod methodsMapping[] = {
            {"stringFromJNI", "()Ljava/lang/String;", (void *) stringFromJNI}
    };
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass((char *) "com/chinaway/android/myapplication/MainActivity");
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