#include <jni.h>
#include <stdint.h>
#include "lame.h"

JNIEXPORT jlong JNICALL Java_id_umarflab_murotalaudioeditor_Mp3Encoder_create(JNIEnv *env, jobject self, jint bitrate) {
    lame_t handle = lame_init();
    if (!handle) return 0;
    lame_set_in_samplerate(handle, 44100);
    lame_set_out_samplerate(handle, 44100);
    lame_set_num_channels(handle, 2);
    lame_set_brate(handle, bitrate / 1000);
    lame_set_quality(handle, 5);
    lame_set_mode(handle, JOINT_STEREO);
    lame_set_bWriteVbrTag(handle, 0);
    if (lame_init_params(handle) < 0) { lame_close(handle); return 0; }
    return (jlong)(intptr_t)handle;
}
JNIEXPORT jint JNICALL Java_id_umarflab_murotalaudioeditor_Mp3Encoder_encode(JNIEnv *env, jobject self, jlong ptr, jobject pcm, jint frames, jbyteArray output) {
    lame_t handle = (lame_t)(intptr_t)ptr;
    short *samples = (*env)->GetDirectBufferAddress(env, pcm);
    if (!handle || !samples || frames < 0 || (jlong)frames * 4 > (*env)->GetDirectBufferCapacity(env, pcm)) return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!bytes) return -1;
    int size = lame_encode_buffer_interleaved(handle, samples, frames, (unsigned char *)bytes, (*env)->GetArrayLength(env, output));
    (*env)->ReleaseByteArrayElements(env, output, bytes, 0);
    return size;
}
JNIEXPORT jint JNICALL Java_id_umarflab_murotalaudioeditor_Mp3Encoder_flush(JNIEnv *env, jobject self, jlong ptr, jbyteArray output) {
    if (!ptr) return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!bytes) return -1;
    int size = lame_encode_flush((lame_t)(intptr_t)ptr, (unsigned char *)bytes, (*env)->GetArrayLength(env, output));
    (*env)->ReleaseByteArrayElements(env, output, bytes, 0);
    return size;
}
JNIEXPORT void JNICALL Java_id_umarflab_murotalaudioeditor_Mp3Encoder_close(JNIEnv *env, jobject self, jlong ptr) {
    if (ptr) lame_close((lame_t)(intptr_t)ptr);
}
