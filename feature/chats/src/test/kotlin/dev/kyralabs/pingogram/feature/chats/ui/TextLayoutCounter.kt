package dev.kyralabs.pingogram.feature.chats.ui

import android.text.StaticLayout
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.util.reflector.Accessor
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector

/**
 * Counts text layouts (`docs/PERFORMANCE.md` §4): Compose lays out each text with a `StaticLayout`, so a test with
 * this shadow sees in [texts] which texts were laid out again, and how often, since the last [reset]. Its test calls
 * [reset] before and after each test: Android keeps a few builders for reuse, and one made by a test without this
 * shadow cannot be counted (nor one made with it be used without it).
 */
@Implements(StaticLayout.Builder::class)
class TextLayoutCounter {
    @RealObject private lateinit var real: StaticLayout.Builder

    @Implementation
    fun build(): StaticLayout {
        val text = reflector(BuilderReflector::class.java, real).getText()?.toString().orEmpty()
        texts[text] = (texts[text] ?: 0) + 1
        return reflector(BuilderReflector::class.java, real).build()
    }

    @ForType(StaticLayout.Builder::class)
    interface BuilderReflector {
        @Direct
        fun build(): StaticLayout

        @Accessor("mText")
        fun getText(): CharSequence?
    }

    companion object {
        /** Each text laid out since the last [reset], with how many times. */
        val texts = mutableMapOf<String, Int>()

        /** Forgets what was counted and the builders kept for reuse. */
        fun reset() {
            texts.clear()
            val pool =
                StaticLayout.Builder::class.java
                    .getDeclaredField(
                        "sPool",
                    ).apply { isAccessible = true }
                    .get(null)
            val acquire = pool.javaClass.getMethod("acquire")
            while (acquire.invoke(pool) != null) continue
        }
    }
}
