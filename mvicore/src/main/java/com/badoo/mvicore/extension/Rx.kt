package com.badoo.mvicore.extension

import io.reactivex.Observer
import io.reactivex.functions.Consumer

fun <T : Any> Observer<T>.asConsumer() = Consumer<T> { onNext(it) }
