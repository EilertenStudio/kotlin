/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.assembleCirTree
import org.jetbrains.kotlin.commonizer.utils.progress

internal class EnqueuedCommonizerTarget(
    val target: SharedCommonizerTarget,
    val producer: Context.() -> CirTreeRoot?
) {
    internal interface Context {
        val parameters: CommonizerParameters
        fun requireCirTree(target: CommonizerTarget): CirTreeRoot?
    }

    internal abstract class AbstractContext(final override val parameters: CommonizerParameters) : Context
}

internal fun enqueueTarget(target: SharedCommonizerTarget): EnqueuedCommonizerTarget = EnqueuedCommonizerTarget(target) {
    val inputTargets = selectInputTargets(parameters.outputTargets.withAllLeaves(), target)
    val inputs = EagerTargetDependent(inputTargets, ::requireCirTree)

    val mergedTree = parameters.logger.progress(target, "Commonized declarations from $inputTargets") {
        commonize(parameters, target, inputs) ?: return@EnqueuedCommonizerTarget null
    }

    parameters.logger.progress(target, "Serialized target") {
        serializeTarget(parameters, mergedTree, target)
    }

    mergedTree.assembleCirTree()
}



// Worst prototype implementation I have ever produced.
//  literally does not do the "job" at all.
//   still produces some output that "can be used"
private fun selectInputTargets(targets: Set<CommonizerTarget>, target: SharedCommonizerTarget): Set<CommonizerTarget> {
    val allTargetLeaves = target.allLeaves()

    val subsets = targets.filter { it != target }
        .filter { allTargetLeaves.containsAll(it.allLeaves()) }
        .sortedByDescending { it.allLeaves().size }

    return subsets.fold(setOf()) { acc, subset ->
        if (acc.allLeaves().containsAll(subset.allLeaves())) return@fold acc
        val next = acc + subset
        if (next.allLeaves().size == allTargetLeaves.size) return next
        if (next.allLeaves().size < allTargetLeaves.size) next else acc
    }
}