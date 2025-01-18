package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

object FEvent {
  @PublishedApi
  internal val map = mutableMapOf<KClass<*>, MutableSharedFlow<*>>()

  /** 发送事件 */
  suspend fun emit(event: Any) {
    withContext(Dispatchers.Main) {
      emitInternal(event)
    }
  }

  internal suspend fun emitInternal(event: Any) {
    val flow = map[event::class] as? MutableSharedFlow<Any>
    flow?.emit(event)
  }

  /** 收集事件 */
  suspend inline fun <reified T : Any> collect(crossinline block: suspend (T) -> Unit) {
    withContext(Dispatchers.preferMainImmediate) {
      val key = T::class
      val flow = map.getOrPut(key) { MutableSharedFlow<T>() } as MutableSharedFlow<T>
      try {
        flow.collect { block(it) }
      } finally {
        if (flow.subscriptionCount.value == 0) {
          map.remove(key)
        }
      }
    }
  }
}

/** 发送事件 */
fun FEvent.post(event: Any) {
  @OptIn(DelicateCoroutinesApi::class)
  GlobalScope.launch(Dispatchers.Main) {
    emitInternal(event)
  }
}

/** 获取事件流 */
inline fun <reified T : Any> FEvent.flowOf(): Flow<T> = channelFlow { collect<T> { send(it) } }