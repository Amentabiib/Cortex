#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "CortexNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_vocab* g_vocab = nullptr;

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

    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_batch = 256;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
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

    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string userPrompt(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    std::string formattedPrompt =
        "<|im_start|>system\nYou are Cortex, a helpful Android system assistant.<|im_end|>\n"
        "<|im_start|>user\n" + userPrompt + "<|im_end|>\n"
        "<|im_start|>assistant\n";

    const int n_prompt_max = 400;
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(g_vocab, formattedPrompt.c_str(), (int32_t)formattedPrompt.length(),
                                   tokens.data(), n_prompt_max, true, true);
    if (n_tokens < 0) {
        return env->NewStringUTF("Error: tokenize failed");
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(g_ctx, batch) != 0) {
        return env->NewStringUTF("Error: decode failed");
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string result;
    const int max_new_tokens = 100;

    for (int i = 0; i < max_new_tokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        char buf[256];
        int len = llama_token_to_piece(g_vocab, new_token, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);

    return env->NewStringUTF(result.c_str());
}
