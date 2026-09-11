package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BlockPath", appName)
  }

  @Test
  fun `user profile repository updates name and avatar correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = UserProfileRepository(context)

    repo.updateProfile("PathMaster", "🛡️")
    val profile = repo.userProfile.value
    assertEquals("PathMaster", profile.name)
    assertEquals("🛡️", profile.avatar)
  }

  @Test
  fun `user profile repository calculates win rate and streaks correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = UserProfileRepository(context)
    repo.resetStats()

    repo.recordWallPlaced()
    repo.recordWallPlaced()
    repo.recordGameFinished(won = true)
    repo.recordGameFinished(won = true)
    repo.recordGameFinished(won = false)

    val profile = repo.userProfile.value
    assertEquals(2, profile.wins)
    assertEquals(1, profile.losses)
    assertEquals(3, profile.gamesPlayed)
    assertEquals(66, profile.winRate)
    assertEquals(0, profile.winStreak)
    assertEquals(2, profile.bestStreak)
    assertEquals(2, profile.wallsPlaced)
  }
}
