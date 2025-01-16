package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(InternalCoroutinesApi::class)
class FContinuations<T> {
  private val _lock = SynchronizedObject()
  private val _holder = mutableListOf<CancellableContinuation<T>>()

  suspend fun await(): T {
    return suspendCancellableCoroutine { cont ->
      synchronized(_lock) {
        _holder.add(cont)
      }
      cont.invokeOnCancellation {
        synchronized(_lock) {
          _holder.remove(cont)
        }
      }
    }
  }

  fun resumeAll(value: T) {
    foreach {
      it.resume(value)
    }
  }

  fun resumeAllWithException(exception: Throwable) {
    foreach {
      it.resumeWithException(exception)
    }
  }

  fun cancelAll(cause: Throwable? = null) {
    foreach {
      it.cancel(cause)
    }
  }

  private inline fun foreach(block: (CancellableContinuation<T>) -> Unit) {
    synchronized(_lock) {
      _holder.toTypedArray().also {
        _holder.clear()
      }
    }.forEach(block)
  }
}