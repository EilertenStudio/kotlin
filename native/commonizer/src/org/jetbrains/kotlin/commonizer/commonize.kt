/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import org.jetbrains.kotlin.commonizer.core.CommonizationVisitor
import org.jetbrains.kotlin.commonizer.mergedtree.CirCommonizedClassifierNodes
import org.jetbrains.kotlin.commonizer.mergedtree.CirKnownClassifiers
import org.jetbrains.kotlin.commonizer.mergedtree.CirRootNode
import org.jetbrains.kotlin.commonizer.transformer.Checked.Companion.invoke
import org.jetbrains.kotlin.commonizer.transformer.InlineTypeAliasCirNodeTransformer
import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.mergeCirTree
import org.jetbrains.kotlin.commonizer.utils.progress


internal fun commonizeTarget(
    parameters: CommonizerParameters,
    target: CommonizerTarget,
    inputDeclarations: TargetDependent<CirTreeRoot?>
): CirRootNode? {
    parameters.logger.progress(target, "Commonized declarations from ${inputDeclarations.targets}") {
        val availableTrees = inputDeclarations.filterNonNull()
        /* Nothing to merge */
        if (availableTrees.size == 0) return null

        val classifiers = CirKnownClassifiers(
            commonizedNodes = CirCommonizedClassifierNodes.default(),
            commonDependencies = parameters.dependencyClassifiers(target)
        )

        val mergedTree = mergeCirTree(parameters.storageManager, classifiers, availableTrees)
        InlineTypeAliasCirNodeTransformer(parameters.storageManager, classifiers).invoke(mergedTree)
        mergedTree.accept(CommonizationVisitor(classifiers, mergedTree), Unit)

        return mergedTree
    }
}
