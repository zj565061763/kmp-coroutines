package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FKeyedSyncable<T> {
  private val _holder = mutableMapOf<String, FSyncable<T>>()
  private val _loadingState = FKeyedState { false }

  suspend fun sync(key: String, block: suspend () -> T): Result<T> {
    return withContext(Dispatchers.preferMainImmediate) {
      doSync(key, block)
    }
  }

  fun syncingFlow(key: String): Flow<Boolean> {
    return _loadingState.flowOf(key)
  }

  private suspend fun doSync(key: String, block: suspend () -> T): Result<T> {
    val cache = _holder[key]
    if (cache != null) return cache.sync()
    return newSyncable(key, block).let { syncable ->
      _holder[key] = syncable
      try {
        syncable.sync()
      } finally {
        _holder.remove(key)
      }
    }
  }

  private fun newSyncable(
    key: String,
    block: suspend () -> T,
  ): FSyncable<T> {
    return FSyncable {
      _loadingState.update(key, state = true)
      try {
        block()
      } finally {
        _loadingState.updateAndRelease(key, state = false)
      }
    }
  }
}

suspend fun <T> FKeyedSyncable<T>.syncOrThrow(
  key: String,
  block: suspend () -> T,
): T = sync(key, block).getOrThrow()