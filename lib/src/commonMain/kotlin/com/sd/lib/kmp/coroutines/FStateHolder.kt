package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

open class FStateHolder<T>(initialState: T) {
  private val _stateFlow = MutableStateFlow(initialState)

  /** 状态 */
  val state: T get() = _stateFlow.value

  /** 状态流 */
  val stateFlow: StateFlow<T> = _stateFlow.asStateFlow()

  /** 更新状态 */
  fun updateState(function: (T) -> T) = _stateFlow.update(function)
}