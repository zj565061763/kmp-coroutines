package com.sd.lib.kmp.coroutines

import com.sd.lib.kmp.mutator.Mutator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
   * [onLoad]的异常会被捕获，除了[CancellationException]
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

  /** 取消加载，并等待取消完成 */
  suspend fun cancel()

  data class State(
    /** 是否正在加载中 */
    val isLoading: Boolean = false,

    /** 最后一次的加载结果 */
    val result: Result<Unit>? = null,
  )
}

/** 如果正在加载中，则会挂起直到加载结束 */
suspend fun FLoader.awaitIdle() {
  if (isLoading) {
    loadingFlow.first { !it }
  }
}

fun FLoader(): FLoader = LoaderImpl()

//-------------------- impl --------------------

private class LoaderImpl : FLoader {
  private val _mutator = Mutator()
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
      doLoad(
        notifyLoading = notifyLoading,
        onLoad = onLoad,
      )
    }
  }

  override suspend fun cancel() {
    _mutator.cancelMutate()
  }

  private suspend fun <T> Mutator.MutateScope.doLoad(
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