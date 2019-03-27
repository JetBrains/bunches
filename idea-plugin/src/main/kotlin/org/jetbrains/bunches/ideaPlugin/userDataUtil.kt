package org.jetbrains.bunches.ideaPlugin

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import kotlin.reflect.KProperty

class NotNullableUserDataProperty<in R : UserDataHolder, T : Any>(private val key: Key<T>, private val defaultValue: T) {
    operator fun getValue(thisRef: R, desc: KProperty<*>) = thisRef.getUserData(key) ?: defaultValue

    operator fun setValue(thisRef: R, desc: KProperty<*>, value: T) {
        thisRef.putUserData(key, if (value != defaultValue) value else null)
    }
}