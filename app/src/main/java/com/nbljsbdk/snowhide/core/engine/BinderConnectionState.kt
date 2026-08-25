package com.nbljsbdk.snowhide.core.engine

/**
 * UserService Binder 连接的纯状态模型。
 *
 * generation 用于屏蔽超时或断开后迟到的旧回调。
 */
sealed interface BinderConnectionState {
    data object Disconnected : BinderConnectionState

    data class Binding(val generation: Long) : BinderConnectionState

    data class Connected(val generation: Long) : BinderConnectionState

    data class Failed(val generation: Long, val message: String) : BinderConnectionState

    data class TimedOut(val generation: Long) : BinderConnectionState

    data class Cancelled(val generation: Long) : BinderConnectionState
}

sealed interface BinderConnectionEvent {
    data class Begin(val generation: Long) : BinderConnectionEvent

    data class Connected(val generation: Long) : BinderConnectionEvent

    data class Failed(val generation: Long, val message: String) : BinderConnectionEvent

    data class TimedOut(val generation: Long) : BinderConnectionEvent

    data class Cancelled(val generation: Long) : BinderConnectionEvent

    data class Disconnected(val generation: Long) : BinderConnectionEvent
}

/**
 * 只接受当前 generation 的事件；返回新状态，不产生 Android 副作用。
 */
fun reduceBinderConnectionState(
    state: BinderConnectionState,
    event: BinderConnectionEvent,
): BinderConnectionState {
    return when (event) {
        is BinderConnectionEvent.Begin -> BinderConnectionState.Binding(event.generation)
        is BinderConnectionEvent.Connected ->
            if (state is BinderConnectionState.Binding && state.generation == event.generation) {
                BinderConnectionState.Connected(event.generation)
            } else state

        is BinderConnectionEvent.Failed ->
            if (state is BinderConnectionState.Binding && state.generation == event.generation) {
                BinderConnectionState.Failed(event.generation, event.message)
            } else state

        is BinderConnectionEvent.TimedOut ->
            if (state is BinderConnectionState.Binding && state.generation == event.generation) {
                BinderConnectionState.TimedOut(event.generation)
            } else state

        is BinderConnectionEvent.Cancelled ->
            if (state is BinderConnectionState.Binding && state.generation == event.generation) {
                BinderConnectionState.Cancelled(event.generation)
            } else state

        is BinderConnectionEvent.Disconnected ->
            if (
                (state is BinderConnectionState.Binding && state.generation == event.generation) ||
                    (state is BinderConnectionState.Connected && state.generation == event.generation)
            ) {
                BinderConnectionState.Disconnected
            } else state
    }
}
