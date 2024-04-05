package com.badoo.mvicore.android.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withCreated
import androidx.lifecycle.withResumed
import androidx.lifecycle.withStarted
import com.badoo.binder.Binder
import com.badoo.binder.BinderKtx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun Lifecycle.createDestroy(f: Binder.() -> Unit) {
    Binder(CreateDestroyBinderLifecycle(this)).apply(f)
}

fun Lifecycle.startStop(f: Binder.() -> Unit) {
    Binder(StartStopBinderLifecycle(this)).apply(f)
}

fun Lifecycle.resumePause(f: Binder.() -> Unit) {
    Binder(ResumePauseBinderLifecycle(this)).apply(f)
}

fun Lifecycle.createDestroyKtx(func: BinderKtx.BindScope.() -> Unit) = runBlocking {
    withCreated {
        BinderKtx().bindScope(this, func)
    }
}

fun Lifecycle.startStopKtx(func: BinderKtx.() -> Unit) = runBlocking {
    withStarted {
        launch {
            func(BinderKtx())
        }
    }
}

fun Lifecycle.resumePauseKtx(func: BinderKtx.() -> Unit) = runBlocking {
    withResumed {
        launch {
            func(BinderKtx())
        }
    }
}

fun fooBar(lifecycle: Lifecycle) {
    lifecycle.createDestroyKtx {
        bind(flowOf("") to {})

        onUi {
            bind(flowOf("") to {})
        }
    }
}

fun BinderKtx.BindScope.onUi(func: BinderKtx.BindScope.() -> Unit) {
    bindScope(CoroutineScope(Dispatchers.Main), func)
}
