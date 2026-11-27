package com.umicorp.autolotto.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class PrizeAfterTaxPreferenceTest {
    @Test
    fun `failed save leaves the current and reopened preference unchanged and allows a retry`() {
        val preferences = Preferences()
        val store = preferences.openStore()
        assertTrue(store.setShowPrizeAfterTax(true))

        preferences.failCommit = true
        assertFalse(store.setShowPrizeAfterTax(false))
        assertTrue(store.getShowPrizeAfterTax())
        assertTrue(preferences.openStore().getShowPrizeAfterTax())

        preferences.failCommit = false
        assertTrue(store.setShowPrizeAfterTax(false))
        assertFalse(store.getShowPrizeAfterTax())
        assertFalse(preferences.openStore().getShowPrizeAfterTax())
    }

    private class Preferences {
        private var storedValue: String? = null
        var failCommit = false

        fun openStore(): SecureStore {
            var currentValue = storedValue
            val prefs = proxy<SharedPreferences> { _, method, args ->
                when (method) {
                    "contains" -> true
                    "getString" -> currentValue ?: args[1]
                    "edit" -> {
                        var pending = currentValue
                        proxy<SharedPreferences.Editor> { editor, operation, values ->
                            when (operation) {
                                "putString" -> editor.also { pending = values[1] as String? }
                                "commit" -> {
                                    currentValue = pending
                                    if (!failCommit) storedValue = pending
                                    !failCommit
                                }
                                else -> error(operation)
                            }
                        }
                    }
                    else -> error(method)
                }
            }
            val context = object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this
                override fun deleteSharedPreferences(name: String): Boolean = true
            }
            return SecureStore(context, prefs)
        }
    }
}

private inline fun <reified T : Any> proxy(crossinline invoke: (Any, String, Array<out Any?>) -> Any?): T {
    val type = T::class.java
    val instance = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        invoke(proxy, method.name, args ?: emptyArray())
    }
    return requireNotNull(type.cast(instance))
}
