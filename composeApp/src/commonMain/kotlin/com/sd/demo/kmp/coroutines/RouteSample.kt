package com.sd.demo.kmp.coroutines

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RouteSample(
  onClickBack: () -> Unit,
) {
  RouteScaffold(
    title = "RouteSample",
    onClickBack = onClickBack,
  ) {
    Text(text = "text")
  }
}