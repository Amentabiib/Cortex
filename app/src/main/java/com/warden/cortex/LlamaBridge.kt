package com.warden.cortex

object LlamaBridge {
    init {
        System.loadLibrary("cortex_native")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun generate(prompt: String): String
}
