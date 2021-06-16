/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.assembleCirTree
import org.jetbrains.kotlin.commonizer.tree.deserializeTarget
import org.jetbrains.kotlin.storage.NullableLazyValue

private typealias OutputCommonizerTarget = SharedCommonizerTarget
private typealias InputCommonizerTarget = CommonizerTarget

internal class CommonizerQueue(
    private val parameters: CommonizerParameters,
    private val inputTargetsSelector: InputTargetsSelector = DefaultInputTargetsSelector
) {

    /**
     * Targets that can just be deserialized and do not need to be commonized.
     * All leaf targets are expected to be provided.
     * Previously commonized targets can also be provide (TODO)
     */
    private val providedTargets: MutableMap<InputCommonizerTarget, NullableLazyValue<CirTreeRoot>> =
        parameters.targetProviders.targets.associateWithTo(mutableMapOf()) { target ->
            parameters.storageManager.createNullableLazyValue {
                deserializeTarget(parameters, target)
            }
        }

    /**
     * Targets that created using commonization.
     * The roots are lazy and will be removed as no further pending target requires it's input
     */
    private val commonizedTargets: MutableMap<OutputCommonizerTarget, NullableLazyValue<CirTreeRoot>> = mutableMapOf()

    /**
     * Represents dependency relationships between input and output targets.
     * Dependencies will be removed if the target was commonized.
     */
    private val targetDependencies: MutableMap<OutputCommonizerTarget, Set<InputCommonizerTarget>> = mutableMapOf()

    /**
     * Runs all tasks/targets in this queue
     */
    fun invokeAll() {
        parameters.outputTargets.forEach { outputTarget -> invokeTarget(outputTarget) }
        assert(providedTargets.isEmpty()) { "Expected 'providedTargets' to be empty. Found ${providedTargets.keys}" }
        assert(commonizedTargets.isEmpty()) { "Expected 'commonizedTargets' to be empty. Found ${commonizedTargets.keys}" }
        assert(targetDependencies.isEmpty()) { "Expected 'targetDependencies' to be empty. Found $targetDependencies" }
    }

    fun invokeTarget(outputTarget: OutputCommonizerTarget) {
        commonizedTargets[outputTarget]?.invoke()
    }

    private fun enqueue(outputTarget: OutputCommonizerTarget) {
        registerTargetDependencies(outputTarget)

        commonizedTargets[outputTarget] = parameters.storageManager.createNullableLazyValue {
            commonize(outputTarget)
        }
    }

    private fun commonize(target: SharedCommonizerTarget): CirTreeRoot? {
        val inputTargets = targetDependencies.getValue(target)

        val inputDeclarations = EagerTargetDependent(inputTargets) { inputTarget ->
            (providedTargets[inputTarget] ?: commonizedTargets[inputTarget]
            ?: throw IllegalStateException("Missing inputTarget $inputTarget")).invoke()
        }

        return commonizeTarget(parameters, inputDeclarations, target)
            .also { removeTargetDependencies(target) }
            ?.also { commonizedDeclarations -> serializeTarget(parameters, commonizedDeclarations, target) }
            ?.assembleCirTree()

    }

    private fun registerTargetDependencies(outputTarget: OutputCommonizerTarget) {
        targetDependencies[outputTarget] = inputTargetsSelector(parameters, outputTarget)
    }

    private fun removeTargetDependencies(target: OutputCommonizerTarget) {
        val inputTargets = targetDependencies.remove(target) ?: return
        val referencedInputTargets = targetDependencies.values.flatten().toSet()

        // Remove targets that are not required anymore
        inputTargets.forEach { inputTarget ->
            if (inputTarget !in referencedInputTargets) {
                providedTargets.remove(inputTarget)
                commonizedTargets.remove(inputTarget)
            }
        }
    }

    init {
        parameters.outputTargets.forEach(this::enqueue)
    }

}