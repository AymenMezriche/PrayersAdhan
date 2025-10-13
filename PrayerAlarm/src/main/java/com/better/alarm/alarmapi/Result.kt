package com.better.alarm.alarmapi

class Result<T> private constructor(
    val data: T? = null,
    val error: Throwable? = null
) {
    val isSuccess: Boolean get() = error == null
    val isError: Boolean get() = error != null

    companion object {
        @JvmStatic
        fun <T> success(data: T): Result<T> = Result(data, null)

        @JvmStatic
        fun <T> error(e: Throwable): Result<T> = Result(null, e)
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        data?.let(action)
        return this
    }

    inline fun onError(action: (Throwable) -> Unit): Result<T> {
        error?.let(action)
        return this
    }
}

