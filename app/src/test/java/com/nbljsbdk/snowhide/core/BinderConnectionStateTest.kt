package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.engine.BinderConnectionEvent
import com.nbljsbdk.snowhide.core.engine.BinderConnectionState
import com.nbljsbdk.snowhide.core.engine.reduceBinderConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class BinderConnectionStateTest {

    @Test
    fun timeoutRejectsLateCallbackAndNewGenerationCanReconnect() {
        var state: BinderConnectionState = BinderConnectionState.Disconnected
        state = reduceBinderConnectionState(state, BinderConnectionEvent.Begin(1))
        state = reduceBinderConnectionState(state, BinderConnectionEvent.TimedOut(1))
        assertEquals(BinderConnectionState.TimedOut(1), state)

        state = reduceBinderConnectionState(state, BinderConnectionEvent.Connected(1))
        assertEquals(BinderConnectionState.TimedOut(1), state)

        state = reduceBinderConnectionState(state, BinderConnectionEvent.Begin(2))
        state = reduceBinderConnectionState(state, BinderConnectionEvent.Connected(2))
        assertEquals(BinderConnectionState.Connected(2), state)
        state = reduceBinderConnectionState(state, BinderConnectionEvent.Disconnected(1))
        assertEquals(BinderConnectionState.Connected(2), state)
    }

    @Test
    fun cancellationAndFailureAreTerminalForCurrentGeneration() {
        val binding = reduceBinderConnectionState(
            BinderConnectionState.Disconnected,
            BinderConnectionEvent.Begin(7),
        )
        val cancelled = reduceBinderConnectionState(binding, BinderConnectionEvent.Cancelled(7))
        assertEquals(BinderConnectionState.Cancelled(7), cancelled)
        assertEquals(
            cancelled,
            reduceBinderConnectionState(cancelled, BinderConnectionEvent.Failed(7, "late")),
        )

        val failed = reduceBinderConnectionState(binding, BinderConnectionEvent.Failed(7, "no service"))
        assertEquals(BinderConnectionState.Failed(7, "no service"), failed)
    }
}
