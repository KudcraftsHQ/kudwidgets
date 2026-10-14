package com.kudcrafts.widgets

import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Cached
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.FetchResult
import com.kudcrafts.widgets.core.after
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    private val min = 60_000L
    private val now = 1_000_000_000L

    @Test fun signedOutRefusedAndExpiredAllAskToSignIn() {
        val cached = Cached("numbers", now)
        for (s in listOf(AuthStatus.SignedOut, AuthStatus.Refused, AuthStatus.Expired)) {
            assertEquals(Display.SignIn(s), Display.resolve(s, cached, now))
        }
    }

    @Test fun firstRunIsLoadingThenFailedIfTheFirstFetchFails() {
        assertEquals(Display.Loading, Display.resolve<String>(AuthStatus.SignedIn, null, now))
        assertEquals(Display.Loading, Display.resolve(AuthStatus.SignedIn, Cached<String>(null, null), now))
        val failed = null.after<String>(FetchResult.Error("HTTP 500"), now)
        assertEquals(Display.Failed("HTTP 500"), Display.resolve(AuthStatus.SignedIn, failed, now))
    }

    @Test fun freshThenStaleAfterThirtyMinutes() {
        val c = Cached("n", now - 30 * min)
        val fresh = Display.resolve(AuthStatus.SignedIn, c, now) as Display.Data
        assertEquals(30L, fresh.ageMinutes)
        assertTrue(!fresh.stale)
        val stale = Display.resolve(AuthStatus.SignedIn, c, now + min) as Display.Data
        assertTrue(stale.stale)
        assertNull(stale.error)
    }

    @Test fun errorKeepsTheLastNumbersWithTheirAge() {
        val ok = null.after<String>(FetchResult.Ok("n"), now - 40 * min)
        val failed = ok.after(FetchResult.Error("HTTP 502"), now - min)
        val d = Display.resolve(AuthStatus.SignedIn, failed, now) as Display.Data
        assertEquals("n", d.data)
        assertEquals(40L, d.ageMinutes)
        assertEquals("HTTP 502", d.error)
        // A later success clears the error.
        val again = failed.after(FetchResult.Ok("m"), now)
        assertEquals(Cached("m", now, null, null), again)
    }

    @Test fun refusedLeavesTheCacheAlone() {
        val ok = Cached("n", now)
        assertEquals(ok, ok.after(FetchResult.Refused, now + min))
        assertEquals(FetchResult.Refused, FetchResult.forStatus(401))
        assertEquals(FetchResult.Refused, FetchResult.forStatus(403))
        assertEquals(FetchResult.Error("HTTP 500"), FetchResult.forStatus(500))
        assertNull(FetchResult.forStatus(200))
    }

    @Test fun keyExpiryIsCheckedBeforeAnyRequest() {
        assertEquals(AuthStatus.SignedOut, Display.authFor(hasKey = false, refused = false, expiresAt = null, now = now))
        assertEquals(AuthStatus.SignedIn, Display.authFor(true, false, now + 1, now))
        assertEquals(AuthStatus.Expired, Display.authFor(true, false, now, now))
        assertEquals(AuthStatus.SignedIn, Display.authFor(true, false, null, now))
        assertEquals(AuthStatus.Refused, Display.authFor(true, true, now + 1, now))
    }
}
