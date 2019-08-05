package org.jetbrains.bunches

import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
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

