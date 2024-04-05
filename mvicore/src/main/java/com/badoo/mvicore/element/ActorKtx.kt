package com.badoo.mvicore.element

import kotlinx.coroutines.flow.Flow

typealias ActorKtx<State, Action, Effect> =
            (state: State, action: Action) -> Flow<Effect>
