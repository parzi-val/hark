package com.hark.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hark.HarkApp
import com.hark.di.AppContainer

/** Build a ViewModel from the app's manual [AppContainer], no DI framework required. */
@Composable
inline fun <reified VM : ViewModel> harkViewModel(
    key: String? = null,
    noinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as HarkApp
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(app.container) } },
    )
}
