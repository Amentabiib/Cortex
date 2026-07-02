#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "CortexNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_warden_cortex_LlamaBridge_loadModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path, model_params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (g_model == nullptr) {
        LOGI("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        LOGI("Failed to create context");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_warden_cortex_LlamaBridge_generate(JNIEnv *env, jobject, jstring prompt) {
    if (g_ctx == nullptr || g_model == nullptr) {
        return env->NewStringUTF("Error: model not loaded");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string result = "Model loaded. Generation logic pending.";
    env->ReleaseStringUTFChars(prompt, promptStr);

    return env->NewStringUTF(result.c_str());
}
