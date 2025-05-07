package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

open class FStateViewModel<T>(
  initialState: T,
  private val viewModelScope: CoroutineScope = FGlobalScope,
) {
  private val _stateFlow = MutableStateFlow(initialState)
  private val _effectFlow = MutableSharedFlow<Any>()

  /** 状态 */
  val state: T get() = _stateFlow.value

  /** 状态流 */
  val stateFlow: StateFlow<T> = _stateFlow.asStateFlow()

  /** 副作用流 */
  val effectFlow: Flow<Any> = _effectFlow.asSharedFlow()

  /** 更新状态 */
  fun updateState(function: (T) -> T) {
    _stateFlow.update(function)
  }

  /** 发送副作用 */
  fun sendEffect(effect: Any) {
    viewModelScope.launch {
      _effectFlow.emit(effect)
    }
  }

  /** 发送副作用 */
  fun sendEffect(getEffect: suspend () -> Any) {
    viewModelScope.launch {
      _effectFlow.emit(getEffect())
    }
  }

  /**
   * 启动协程，如果[block]触发了，则[onFinish]一定会触发
   */
  fun vmLaunch(
    onFinish: () -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
  ): Job {
    return viewModelScope.launch {
      try {
        block()
      } catch (e: Throwable) {
        if (e is CancellationException) throw e
        _effectFlow.emit(e)
      } finally {
        onFinish()
      }
    }
  }
}