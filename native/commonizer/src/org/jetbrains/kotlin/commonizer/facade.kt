/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.ResultsConsumer.Status
import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.deserializeCirTree
import org.jetbrains.kotlin.commonizer.utils.progress
import java.util.Comparator.comparing

fun runCommonization(parameters: CommonizerParameters) {
    if (!parameters.containsCommonModuleNames()) {
        parameters.resultsConsumer.allConsumed(parameters, Status.NOTHING_TO_DO)
        return
    }
    runCommonization(parameters, parameters.outputTargets.map(::enqueueTarget))
    parameters.resultsConsumer.allConsumed(parameters, Status.DONE)
}

internal fun runCommonization(parameters: CommonizerParameters, queue: List<EnqueuedCommonizerTarget>) {
    val enqueuedTargets = queue.associate { enqueuedTarget -> enqueuedTarget.target as CommonizerTarget to enqueuedTarget.producer }

    val providedTargets = parameters.targetProviders.targets.associateWith { target ->
        parameters.storageManager.createNullableLazyValue {
            parameters.logger.progress(target, "Deserialized declarations") {
                deserializeCirTree(parameters, target)
            }
        }
    }

    val context = object : EnqueuedCommonizerTarget.AbstractContext(parameters) {
        private val lazyEnqueuedTargets = enqueuedTargets
            .mapValues { (_, producer) -> parameters.storageManager.createNullableLazyValue { producer.invoke(this) } }

        override fun requireCirTree(target: CommonizerTarget): CirTreeRoot? {
            return if (target in providedTargets) providedTargets.getValue(target).invoke()
            else lazyEnqueuedTargets.getValue(target).invoke()
        }

        fun createTasks(): List<() -> Unit> {
            return lazyEnqueuedTargets.keys
                .sortedWith(comparing<CommonizerTarget?, Int?> { it.allLeaves().size }.then(comparing { it.konanTargets.size }))
                .map(lazyEnqueuedTargets::getValue)
                .map { { it.invoke() } }
        }
    }

    runCommonization(context.createTasks())
}

private fun runCommonization(tasks: List<Function0<*>>) = tasks.forEach { it.invoke() }
