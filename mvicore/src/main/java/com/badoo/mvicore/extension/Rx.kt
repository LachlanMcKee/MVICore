package com.badoo.mvicore.extension

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.functions.Consumer

fun <T : Any> Observer<T>.asConsumer() = Consumer<T> { onNext(it) }

internal fun <T> Observable<T>.subscribeOnNullable(scheduler: Scheduler?): Observable<T> =
    if (scheduler != null) subscribeOn(scheduler) else this
