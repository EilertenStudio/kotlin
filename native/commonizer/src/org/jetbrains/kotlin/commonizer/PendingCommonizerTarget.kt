/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.assembleCirTree
import org.jetbrains.kotlin.commonizer.utils.progress
import org.jetbrains.kotlin.storage.NullableLazyValue

internal class PendingCommonizerTarget(
    val inputTargets: Set<CommonizerTarget>,
    val outputTarget: SharedCommonizerTarget,
    val producer: Context.() -> CirTreeRoot?
) {
    internal data class Context(
        val parameters: CommonizerParameters,
        val inputDeclarations: TargetDependent<NullableLazyValue<CirTreeRoot>>
    )
}

internal fun PendingCommonizerTarget(parameters: CommonizerParameters, target: SharedCommonizerTarget): PendingCommonizerTarget =
    PendingCommonizerTarget(inputTargets = selectInputTargets(parameters, target), outputTarget = target) Producer@{
        val mergedTree = parameters.logger.progress(target, "Commonized declarations from ${inputDeclarations.targets}") {
            commonizeTarget(parameters, target, inputDeclarations.mapValue { it.invoke() }) ?: return@Producer null
        }

        parameters.logger.progress(target, "Serialized target") {
            serializeTarget(parameters, mergedTree, target)
        }

        mergedTree.assembleCirTree()
    }


internal fun selectInputTargets(parameters: CommonizerParameters, outputTarget: SharedCommonizerTarget): Set<CommonizerTarget> {
    return selectInputTargets(parameters.outputTargets + parameters.targetProviders.targets, outputTarget)
}

// Worst prototype implementation I have ever produced.
//  literally does not do the "job" at all.
//   still produces some output that "can be used"
internal fun selectInputTargets(targets: Set<CommonizerTarget>, outputTarget: SharedCommonizerTarget): Set<CommonizerTarget> {
    if (false) return outputTarget.allLeaves()

    val allTargetLeaves = outputTarget.allLeaves()

    val subsets = targets.filter { it != outputTarget }
        .filter { allTargetLeaves.containsAll(it.allLeaves()) }
        .sortedByDescending { it.allLeaves().size }

    return subsets.fold(setOf()) { acc, subset ->
        if (acc.allLeaves().containsAll(subset.allLeaves())) return@fold acc
        val next = acc + subset
        if (next.allLeaves().size == allTargetLeaves.size) return next
        if (next.allLeaves().size < allTargetLeaves.size) next else acc
    }
}