package com.badoo.mvicore.feature

import com.badoo.mvicore.element.Store
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.disposables.Disposable

interface Feature<Wish : Any, State : Any, News : Any> : Store<Wish, State>, Disposable {

    val news: ObservableSource<News>

    /**
     * If [FeatureThreadStrategy.Async] is used, these will be emitted on the feature thread,
     * 
     */
    val backgroundStates: Observable<State>

    /**
     * Depending on the [FeatureThreadStrategy] this may behave the same [news].
     */
    val backgroundNews: Observable<News>
}
