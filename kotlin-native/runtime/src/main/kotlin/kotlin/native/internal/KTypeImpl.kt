/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.native.internal

import kotlin.reflect.*

internal class KTypeProjectionSpecialList(val varianceOrdinal: IntArray, val type: Array<KType?>) : List<KTypeProjection> {
    override val size: Int
        get() = varianceOrdinal.size

    override fun contains(element: KTypeProjection) = indexOf(element) != -1

    override fun containsAll(elements: Collection<KTypeProjection>): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    private fun varianceByOrdianal(ordinal : Int) = when (ordinal) {
        -1 -> null
        KVariance.IN.ordinal -> KVariance.IN
        KVariance.OUT.ordinal -> KVariance.OUT
        KVariance.INVARIANT.ordinal -> KVariance.INVARIANT
        else -> throw IllegalStateException()
    }

    override fun get(index: Int) : KTypeProjection {
        checkElementIndex(index)
        return KTypeProjection(varianceByOrdianal(varianceOrdinal[index]), type[index])
    }

    override fun indexOf(element: KTypeProjection): Int {
        for (i in 0 until size) {
            if (get(i) == element) return i
        }
        return -1
    }

    override fun lastIndexOf(element: KTypeProjection): Int {
        for (i in size - 1 downTo 0) {
            if (get(i) == element) return i
        }
        return -1
    }

    override fun isEmpty(): Boolean = size == 0

    private class Iterator(val list: KTypeProjectionSpecialList, var index: Int) : ListIterator<KTypeProjection> {
        override fun hasNext() = index < list.size

        override fun hasPrevious() = index > 0

        override fun next(): KTypeProjection {
            if (!hasNext()) throw NoSuchElementException()
            return list.get(index++)
        }

        override fun nextIndex() = index

        override fun previous() : KTypeProjection {
            if (!hasPrevious()) throw NoSuchElementException()
            return list.get(--index)
        }

        override fun previousIndex() = index - 1
    }

    override fun iterator() = listIterator()

    override fun listIterator() = listIterator(0)

    override fun listIterator(index: Int) : ListIterator<KTypeProjection> {
        checkPositionIndex(index)
        return Iterator(this, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<KTypeProjection> {
        checkRangeIndexes(fromIndex, toIndex)
        return List(toIndex - fromIndex) { get(fromIndex + it) }
    }

    private fun checkElementIndex(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }

    private fun checkPositionIndex(index: Int) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }

    private fun checkRangeIndexes(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex > size) {
            throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
        }
        if (fromIndex > toIndex) {
            throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
        }
    }
}

internal class KTypeImpl(
        override val classifier: KClassifier?,
        override val arguments: List<KTypeProjection>,
        override val isMarkedNullable: Boolean
) : KType {
    override fun equals(other: Any?) =
            other is KTypeImpl &&
                    this.classifier == other.classifier &&
                    this.arguments == other.arguments &&
                    this.isMarkedNullable == other.isMarkedNullable

    override fun hashCode(): Int {
        return (classifier?.hashCode() ?: 0) * 31 * 31 + this.arguments.hashCode() * 31 + if (isMarkedNullable) 1 else 0
    }

    override fun toString(): String {
        val classifierString = when (classifier) {
            is KClass<*> -> classifier.qualifiedName ?: classifier.simpleName
            is KTypeParameter -> classifier.name
            else -> null
        } ?: return "(non-denotable type)"

        return buildString {
            append(classifierString)

            if (arguments.isNotEmpty()) {
                append('<')

                arguments.forEachIndexed { index, argument ->
                    if (index > 0) append(", ")

                    append(argument)
                }

                append('>')
            }

            if (isMarkedNullable) append('?')
        }
    }
}

internal class KTypeImplForTypeParametersWithRecursiveBounds : KType {
    override val classifier: KClassifier?
        get() = error("Type parameters with recursive bounds are not yet supported in reflection")

    override val arguments: List<KTypeProjection> get() = emptyList()

    override val isMarkedNullable: Boolean
        get() = error("Type parameters with recursive bounds are not yet supported in reflection")

    override fun equals(other: Any?) =
            error("Type parameters with recursive bounds are not yet supported in reflection")

    override fun hashCode(): Int =
            error("Type parameters with recursive bounds are not yet supported in reflection")
}
