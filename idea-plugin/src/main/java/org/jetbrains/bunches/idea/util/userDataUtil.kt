package org.jetbrains.bunches.idea.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import kotlin.reflect.KProperty

class NotNullableUserDataProperty<in R : UserDataHolder, T : Any>(
    private val key: Key<T>,
    private val defaultValue: T
) {
    operator fun getValue(thisRef: R, desc: KProperty<*>) = thisRef.getUserData(key) ?: defaultValue

    operator fun setValue(thisRef: R, desc: KProperty<*>, value: T) {
        thisRef.putUserData(key, if (value != defaultValue) value else null)
    }
}

fun AnActionEvent.getVirtualFile(): VirtualFile? {
    val files = this.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
    return files.singleOrNull { it.isValid }
}