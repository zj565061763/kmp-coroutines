package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TestCounter {
  private val _mutex = Mutex()
  private var _count = 0

  suspend fun incrementAndGet() {
    _mutex.withLock {
      _count++
    }
  }

  fun get(): Int {
    return _count
  }
}