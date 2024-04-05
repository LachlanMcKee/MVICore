package com.badoo.mvicore.element

import kotlinx.coroutines.flow.Flow

interface StoreKtx<Wish : Any, State : Any> : (Wish) -> Unit, Flow<State> {

    val state: State
}
