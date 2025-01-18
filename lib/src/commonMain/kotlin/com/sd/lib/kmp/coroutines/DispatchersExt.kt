package com.sd.lib.kmp.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

val Dispatchers.preferMainImmediate: MainCoroutineDispatcher
  get() = runCatching { Main.immediate }.getOrElse { Main }