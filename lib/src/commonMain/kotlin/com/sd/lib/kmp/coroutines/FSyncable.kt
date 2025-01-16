package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

interface FSyncable<T> {
  /** 是否正在同步中状态流 */
  val syncingFlow: StateFlow<Boolean>

  /** 同步并等待结果 */
  suspend fun sync(): Result<T>

  /** 如果正在同步中，则会挂起直到同步结束 */
  suspend fun awaitIdle()
}

suspend fun <T> FSyncable<T>.syncOrThrow(): T {
  return sync().getOrThrow()
}

suspend fun <T> FSyncable<T>.syncOrThrowCancellation(): Result<T> {
  return sync().onFailure { e ->
    if (e is CancellationException) throw e
  }
}

/**
 * 调用[FSyncable.sync]时，如果[FSyncable]处于空闲状态，则当前协程会切换到主线程执行[onSync]，
 * 如果执行未完成时又有新协程调用[FSyncable.sync]，则新协程会挂起等待结果。
 *
 * 注意：[onSync]引发的所有异常都会被捕获，包括[CancellationException]，即[onSync]不会导致调用[FSyncable.sync]的协程被取消，
 * 如果调用[FSyncable.sync]的协程被取消，那一定是外部导致的。
 *
 * 这样子设计比较灵活，只要[FSyncable.sync]返回了[Result]，调用处就知道[onSync]发生的所有情况，包括取消情况，
 * 可以根据具体情况再做处理，例如知道[onSync]里面被取消了，可以选择继续往上抛出取消异常，或者处理其他逻辑。
 *
 * - 如果希望同步时抛出所有异常，可以使用方法[FSyncable.syncOrThrow]
 * - 如果希望同步时抛出取消异常，可以使用方法[FSyncable.syncOrThrowCancellation]
 */
fun <T> FSyncable(
  onSync: suspend () -> T,
): FSyncable<T> = SyncableImpl(onSync)

private class SyncableImpl<T>(
  private val onSync: suspend () -> T,
) : FSyncable<T> {
  private val _continuations = FContinuations<Result<T>>()
  private val _syncingFlow = MutableStateFlow(false)

  private var _syncing: Boolean
    get() = _syncingFlow.value
    set(value) {
      _syncingFlow.value = value
    }

  override val syncingFlow: StateFlow<Boolean>
    get() = _syncingFlow.asStateFlow()

  override suspend fun sync(): Result<T> {
    if (currentCoroutineContext()[SyncElement]?.tag === this@SyncableImpl) {
      throw ReSyncException("Can not call sync in the onSync block.")
    }
    return withContext(Dispatchers.preferMainImmediate) {
      if (_syncing) {
        _continuations.await()
      } else {
        doSync()
      }
    }.also {
      currentCoroutineContext().ensureActive()
    }
  }

  override suspend fun awaitIdle() {
    withContext(Dispatchers.preferMainImmediate) {
      if (_syncing) {
        _syncingFlow.first { !it }
      }
    }
  }

  private suspend fun doSync(): Result<T> {
    return try {
      _syncing = true
      withContext(SyncElement(tag = this@SyncableImpl)) {
        onSync()
      }.let { data ->
        Result.success(data).also { _continuations.resumeAll(it) }
      }
    } catch (e: Throwable) {
      if (e is ReSyncException) {
        _continuations.cancelAll()
        throw e
      } else {
        Result.failure<T>(e).also { _continuations.resumeAll(it) }
      }
    } finally {
      _syncing = false
    }
  }
}

private class SyncElement(
  val tag: FSyncable<*>,
) : AbstractCoroutineContextElement(SyncElement) {
  companion object Key : CoroutineContext.Key<SyncElement>
}

/** 嵌套同步异常 */
private class ReSyncException(message: String) : IllegalStateException(message)