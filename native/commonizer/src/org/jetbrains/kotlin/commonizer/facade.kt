/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.ResultsConsumer.Status
import org.jetbrains.kotlin.commonizer.mergedtree.CirRootNode
import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.assembleCirTree
import org.jetbrains.kotlin.commonizer.tree.deserializeCirTree
import org.jetbrains.kotlin.commonizer.utils.progress
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager

fun runCommonization(parameters: CommonizerParameters) {
    if (!parameters.containsCommonModuleNames()) {
        parameters.resultsConsumer.allConsumed(parameters, Status.NOTHING_TO_DO)
        return
    }

    val queue = createQueue(parameters)
    val context = CommonizationContextImpl(parameters, queue)
    context.toExecutable().invoke()

    parameters.resultsConsumer.allConsumed(parameters, Status.DONE)
}

private fun createQueue(parameters: CommonizerParameters): List<EnqueuedTarget> {
    return parameters.outputTargets
        .map { target -> enqueueTarget(target) }
        .sortedBy { enqueuedTarget -> enqueuedTarget.target.targets.size }
}


interface CommonizationContext {
    val storageManager: StorageManager
    val parameters: CommonizerParameters
    fun serializeTarget(mergedTree: CirRootNode, target: SharedCommonizerTarget)
    fun getCirTree(target: CommonizerTarget): CirTreeRoot?
}

private class CommonizationContextImpl(
    override val parameters: CommonizerParameters, private val queue: List<EnqueuedTarget>
) : CommonizationContext {

    override val storageManager: StorageManager = LockBasedStorageManager("Commonizer")

    private val enqueuedTargets: Map<CommonizerTarget, Lazy<CirTreeRoot?>> = queue.associate { enqueuedTarget ->
        enqueuedTarget.target to lazy(LazyThreadSafetyMode.NONE) { enqueuedTarget(this) }
    }

    private val providedTargets: Map<CommonizerTarget, Lazy<CirTreeRoot?>> = parameters.targetProviders.targets.associateWith { target ->
        lazy {
            parameters.logger.progress(target, "Deserialized declarations") {
                deserializeCirTree(parameters, target)
            }
        }
    }

    override fun getCirTree(target: CommonizerTarget): CirTreeRoot? {
        return if (target in parameters.targetProviders.targets) return providedTargets.getValue(target).value
        else enqueuedTargets.getValue(target).value
    }

    override fun serializeTarget(mergedTree: CirRootNode, target: SharedCommonizerTarget) {
        serialize(parameters, mergedTree, target)
    }

    fun toExecutable(): () -> Unit = {
        queue.forEach { enqueuedTargets.getValue(it.target).value }
    }
}

private class EnqueuedTarget(
    val target: SharedCommonizerTarget,
    private val producer: CommonizationContext.() -> CirTreeRoot?
) {
    operator fun invoke(declarations: CommonizationContext): CirTreeRoot? = declarations.producer()
}

private fun enqueueTarget(target: SharedCommonizerTarget): EnqueuedTarget = EnqueuedTarget(target) {
    val inputTargets = selectInputTargets(parameters.outputTargets.withAllLeaves(), target)
    val inputs = EagerTargetDependent(inputTargets, ::getCirTree)

    val mergedTree = parameters.logger.progress(target, "Commonized declarations from $inputTargets") {
        commonize(parameters, storageManager, target, inputs) ?: return@EnqueuedTarget null
    }

    parameters.logger.progress(target, "Serialized target") {
        serializeTarget(mergedTree, target)
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