package com.sd.lib.kmp.coroutines

import kotlin.coroutines.cancellation.CancellationException

inline fun <R> fRunCatchingEscape(
  escape: (Throwable) -> Boolean = { it is CancellationException },
  block: () -> R,
): Result<R> {
  return try {
    Result.success(block())
  } catch (e: Throwable) {
    if (escape(e)) throw e
    Result.failure(e)
  }
}