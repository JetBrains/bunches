package org.jetbrains.bunches

import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.bunches.git.ChangeType
import org.jetbrains.bunches.git.CommitInfo
import org.jetbrains.bunches.git.FileChange
import org.jetbrains.bunches.hooks.PreRebaseCheckResult
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.map
import java.io.File

fun <T> Assertion.Builder<List<T>>.equalsBy(other: List<T>, comparator: (T, T) -> Any) =
    compose("corresponding elements of the lists pass the comparator") {
        expectThat(it.size).isEqualTo(other.size)
        it.zip(other).forEach { (first, second) -> comparator(first, second) }
    } then {
        if (allPassed) {
            pass()
        } else {
            fail()
        }
    }

fun <T, V> Assertion.Builder<List<T>>.hasModifiedFieldsFrom(other: List<T>, getter: (T) -> V, modifier: (T) -> V) =
    compose("modified fields from second list are equal to first list values from getter") {
        expectThat(it.size).isEqualTo(other.size)
        it.zip(other).forEach { (first, second) -> expectThat(getter(first)).isEqualTo(modifier(second)) }
    } then {
        if (allPassed) {
            pass()
        } else {
            fail()
        }
    }

fun <T, V> Assertion.Builder<T>.hasEqualFieldWith(other: T, getter: (T) -> V) =
    assert("values from getter are equal") {
        expectThat(getter(it)).isEqualTo(getter(other))
    }

fun <T, V> Assertion.Builder<T>.hasModifiedFieldWith(other: T, getter: (T) -> V, modifier: (T) -> V) =
    assert("the getter field is equal to the value obtained from the second object using the modifier") {
        expectThat(getter(it)).isEqualTo(modifier(other))
    }

fun Assertion.Builder<File>.isExists() = assert("file exists") {
    it.exists()
}

fun Assertion.Builder<File>.isContentEqualsTo(file: File) =
    assert("given files have equal text") {
        expectThat(it).isContentEqualsTo(file.readText())
    }

fun Assertion.Builder<File>.isContentEqualsTo(text: String) =
    assert("given text equals to files content") {
        expectThat(it.readText()).isEqualTo(text)
    }

internal fun Assertion.Builder<PreRebaseCheckResult>.containsExactly(
    isOk: Boolean,
    added: List<String>,
    deleted: List<String>
) = assert("pre-rebase check result contains exactly given files in added and deleted lists") {
    expectThat(it.isOk()).isEqualTo(isOk)
    if (isOk) {
        expectThat(it.addedFiles).containsExactlyInAnyOrder(added)
        expectThat(it.deletedFiles).containsExactlyInAnyOrder(deleted)
    }
}

internal fun Assertion.Builder<CommitInfo>.containsExactlyActions(directory: File, actions: List<FileChange>) =
    assert("commit info file actions match given file changes for files in the directory") {
        expectThat(it).get { fileActions }.map { action ->
            val type = if (action.changeType == DiffEntry.ChangeType.ADD) ChangeType.ADD else ChangeType.MODIFY
            expectThat(action.newPath).isNotNull()
            FileChange(type, File(directory, action.newPath ?: return@map null).absoluteFile)
        }.containsExactlyInAnyOrder(actions)
    }