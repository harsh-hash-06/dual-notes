package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read localized app name for English and Spanish`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Dual Notes", context.getString(R.string.app_name))

        val spanishConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
        }
        val spanishContext = context.createConfigurationContext(spanishConfig)
        assertEquals("Notas Duales", spanishContext.getString(R.string.app_name))
    }
}
