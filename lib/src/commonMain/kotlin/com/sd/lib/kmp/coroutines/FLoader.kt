package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

interface FLoader {
  /** 状态流 */
  val stateFlow: Flow<State>

  /** 加载状态流 */
  val loadingFlow: Flow<Boolean>

  /** 状态 */
  val state: State

  /** 是否正在加载中 */
  val isLoading: Boolean

  /**
   * 开始加载，如果上一次加载还未完成，再次调用此方法，会取消上一次加载，
   * [onLoad]在主线程触发，它的异常会被捕获，除了[CancellationException]
   *
   * 注意：[onLoad]中不允许嵌套调用[load]，否则会抛异常
   *
   * @param notifyLoading 是否通知加载状态
   * @param onLoad 加载回调
   */
  suspend fun <T> load(
    notifyLoading: Boolean = true,
    onLoad: suspend () -> T,
  ): Result<T>

  /**
   * 如果正在加载中，会抛出[CancellationException]异常，取消当前调用[tryLoad]的协程
   */
  suspend fun <T> tryLoad(
    notifyLoading: Boolean = true,
    onLoad: suspend () -> T,
  ): Result<T>

  /** 取消加载，并等待取消完成 */
  suspend fun cancel()

  data class State(
    /** 是否正在加载中 */
    val isLoading: Boolean = false,

    /** 最后一次的加载结果 */
    val result: Result<Unit>? = null,
  )
}

fun FLoader(): FLoader = LoaderImpl()

//-------------------- impl --------------------

private class LoaderImpl : FLoader {
  private val _mutator = FMutator()
  private val _stateFlow = MutableStateFlow(FLoader.State())

  override val stateFlow: Flow<FLoader.State>
    get() = _stateFlow.asStateFlow()

  override val loadingFlow: Flow<Boolean>
    get() = _stateFlow.map { it.isLoading }.distinctUntilChanged()

  override val state: FLoader.State
    get() = _stateFlow.value

  override val isLoading: Boolean
    get() = state.isLoading

  override suspend fun <T> load(
    notifyLoading: Boolean,
    onLoad: suspend () -> T,
  ): Result<T> {
    return _mutator.mutate {
      withContext(Dispatchers.preferMainImmediate) {
        doLoad(
          notifyLoading = notifyLoading,
          onLoad = onLoad,
        )
      }
    }
  }

  override suspend fun <T> tryLoad(
    notifyLoading: Boolean,
    onLoad: suspend () -> T,
  ): Result<T> {
    if (_mutator.isMutating()) throw CancellationException()
    return load(
      notifyLoading = notifyLoading,
      onLoad = onLoad,
    )
  }

  override suspend fun cancel() {
    _mutator.cancelMutate()
  }

  private suspend fun <T> FMutator.MutateScope.doLoad(
    notifyLoading: Boolean,
    onLoad: suspend () -> T,
  ): Result<T> {
    return try {
      if (notifyLoading) {
        _stateFlow.update { it.copy(isLoading = true) }
      }
      onLoad().let { data ->
        Result.success(data).also {
          ensureMutateActive()
          _stateFlow.update { it.copy(result = Result.success(Unit)) }
        }
      }
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      Result.failure<T>(e).also {
        ensureMutateActive()
        _stateFlow.update { it.copy(result = Result.failure(e)) }
      }
    } finally {
      if (notifyLoading) {
        _stateFlow.update { it.copy(isLoading = false) }
      }
    }
  }
}