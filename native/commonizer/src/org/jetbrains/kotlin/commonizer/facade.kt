/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.ResultsConsumer.Status
import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.deserializeCirTree
import org.jetbrains.kotlin.commonizer.utils.progress
import org.jetbrains.kotlin.storage.NullableLazyValue
import java.util.Comparator.comparing

fun runCommonization(parameters: CommonizerParameters) {
    if (!parameters.containsCommonModuleNames()) {
        parameters.resultsConsumer.allConsumed(parameters, Status.NOTHING_TO_DO)
        return
    }
    val pendingCommonizerTargets = parameters.outputTargets.map { target -> PendingCommonizerTarget(parameters, target) }
    val pendingCommonizerJobs = buildCommonizerJobQueue(parameters, pendingCommonizerTargets)
    pendingCommonizerJobs.forEach(PendingCommonizationJob::invoke)
    parameters.resultsConsumer.allConsumed(parameters, Status.DONE)
}

internal fun buildCommonizerJobQueue(
    parameters: CommonizerParameters,
    queue: List<PendingCommonizerTarget>
): List<PendingCommonizationJob> {
    // All targets that can just be de-serialized and do not need any commonization to happen first
    val providedTargets: TargetDependent<NullableLazyValue<CirTreeRoot>> =
        EagerTargetDependent(parameters.targetProviders.targets) { target ->
            parameters.storageManager.createNullableLazyValue {
                parameters.logger.progress(target, "Deserialized declarations") {
                    deserializeCirTree(parameters, target)
                }
            }
        }

    // Targets that are enqueued for commonization and pending
    val pendingTargets: MutableMap<CommonizerTarget, NullableLazyValue<CirTreeRoot>> = mutableMapOf()

    fun enqueueTargetIfNecessary(target: PendingCommonizerTarget): PendingCommonizationJob? {
        if (pendingTargets.containsKey(target.outputTarget)) return null

        // Ensure dependencies are enqueued first
        target.inputTargets.forEach { inputTarget ->
            queue.filter { it.outputTarget == inputTarget }.forEach { enqueueTargetIfNecessary(it) }
        }

        // Enqueue this target
        val inputDeclarations: TargetDependent<NullableLazyValue<CirTreeRoot>> = EagerTargetDependent(target.inputTargets) { inputTarget ->
            pendingTargets[inputTarget] ?: providedTargets.getOrNull(inputTarget)
            ?: throw IllegalStateException("Missing inputTarget $inputTarget")
        }

        val context = PendingCommonizerTarget.Context(parameters, inputDeclarations)
        val lazyDeclarations = parameters.storageManager.createNullableLazyValue { target.producer.invoke(context) }
        pendingTargets[target.outputTarget] = lazyDeclarations
        return PendingCommonizationJob(target.outputTarget, lazyDeclarations)
    }

    // Enqueue all targets
    return queue
        .sortedWith(PendingCommonizerTargetComparator)
        .mapNotNull { enqueuedCommonizerTarget -> enqueueTargetIfNecessary(enqueuedCommonizerTarget) }
}

// TODO NOW: Test
internal val PendingCommonizerTargetComparator = comparing<PendingCommonizerTarget, Int> { it.inputTargets.size }
    .then(comparing { it.outputTarget.allLeaves().size })

internal class PendingCommonizationJob(
    val outputTarget: SharedCommonizerTarget,
    private val declarations: NullableLazyValue<CirTreeRoot>
) {
    operator fun invoke() = declarations.invoke()
}