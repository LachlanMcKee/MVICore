package com.badoo.mvicore.feature

import com.badoo.mvicore.element.StoreKtx
import kotlinx.coroutines.flow.Flow

interface FeatureKtx<Wish : Any, State : Any, News : Any> : StoreKtx<Wish, State> {
    val news: Flow<News>
    fun cancel()
}