package com.deehem.splitz

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.deehem.splitz.data.AppDatabase
import com.deehem.splitz.data.SplitShortcut
import com.deehem.splitz.utils.ShortcutUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Splitz", appName)

    val shortDesc = context.getString(R.string.app_short_description)
    assertTrue(shortDesc.contains("Accessibility Service"))

    val fullDesc = context.getString(R.string.app_full_description)
    assertTrue(fullDesc.contains("Accessibility Service"))
  }

  @Test
  fun `shortcut id format is consistent`() {
    val shortcut = SplitShortcut(
      id = 42L,
      name = "Chrome & Contacts",
      topPackage = "com.android.chrome",
      bottomPackage = "com.android.contacts"
    )
    val shortcutId = ShortcutUtils.getShortcutId(shortcut)
    assertEquals("split_shortcut_42", shortcutId)
  }

  @Test
  fun `shortcut dao insert and delete works properly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.splitShortcutDao()

    val shortcut = SplitShortcut(
      name = "Test Pair",
      topPackage = "com.android.chrome",
      bottomPackage = "com.android.contacts",
      folder = "Work"
    )
    val insertedId = dao.insertShortcut(shortcut)
    assertTrue(insertedId > 0)

    val retrieved = dao.getShortcutById(insertedId)
    assertNotNull(retrieved)
    assertEquals("Test Pair", retrieved?.name)
    assertEquals("Work", retrieved?.folder)

    // Delete shortcut
    dao.deleteShortcut(retrieved!!)
    val afterDelete = dao.getShortcutById(insertedId)
    assertEquals(null, afterDelete)
  }

  @Test
  fun `disable shortcut runs without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shortcut = SplitShortcut(
      id = 10L,
      name = "Test Pair",
      topPackage = "com.android.chrome",
      bottomPackage = "com.android.contacts"
    )
    // Verify calling disableShortcut executes safely
    ShortcutUtils.disableShortcut(context, shortcut)
  }
}
