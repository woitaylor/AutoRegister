package com.billy.android.register

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * 日志工具类
 */
object Logger {
    private val logger: Logger = Logging.getLogger("auto-register")

    fun i(message: String) {
        logger.lifecycle(message)
    }

    fun d(message: String) {
        logger.debug(message)
    }

    fun w(message: String) {
        logger.warn(message)
    }

    fun e(message: String) {
        logger.error(message)
    }

    fun e(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }
} 