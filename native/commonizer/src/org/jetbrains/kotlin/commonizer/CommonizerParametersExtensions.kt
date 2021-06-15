/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import kotlinx.metadata.klib.ChunkedKlibModuleFragmentWriteStrategy
import org.jetbrains.kotlin.commonizer.ResultsConsumer.ModuleResult.Commonized
import org.jetbrains.kotlin.commonizer.core.CommonizationVisitor
import org.jetbrains.kotlin.commonizer.mergedtree.CirCommonizedClassifierNodes
import org.jetbrains.kotlin.commonizer.mergedtree.CirKnownClassifiers
import org.jetbrains.kotlin.commonizer.mergedtree.CirNode.Companion.indexOfCommon
import org.jetbrains.kotlin.commonizer.mergedtree.CirRootNode
import org.jetbrains.kotlin.commonizer.metadata.CirTreeSerializer
import org.jetbrains.kotlin.commonizer.transformer.Checked.Companion.invoke
import org.jetbrains.kotlin.commonizer.transformer.InlineTypeAliasCirNodeTransformer
import org.jetbrains.kotlin.commonizer.tree.CirTreeRoot
import org.jetbrains.kotlin.commonizer.tree.deserializeCirTree
import org.jetbrains.kotlin.commonizer.tree.mergeCirTree
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.storage.StorageManager

private val KLIB_FRAGMENT_WRITE_STRATEGY = ChunkedKlibModuleFragmentWriteStrategy()

internal fun CommonizerParameters.deserialize(target: CommonizerTarget): CirTreeRoot? {
    val targetProvider = targetProviders[target] ?: return null
    return deserializeCirTree(this, targetProvider)
}

internal fun CommonizerParameters.commonize(
    target: CommonizerTarget, cirTrees: TargetDependent<CirTreeRoot?>, storageManager: StorageManager
): CirRootNode? {
    val availableTrees = cirTrees.filterNonNull()
    /* Nothing to merge */
    if (availableTrees.size == 0) return null

    val classifiers = CirKnownClassifiers(
        commonizedNodes = CirCommonizedClassifierNodes.default(),
        commonDependencies = dependencyClassifiers(target)
    )

    val mergedTree = mergeCirTree(storageManager, classifiers, availableTrees)
    InlineTypeAliasCirNodeTransformer(storageManager, classifiers).invoke(mergedTree)
    mergedTree.accept(CommonizationVisitor(classifiers, mergedTree), Unit)

    return mergedTree
}

internal fun CommonizerParameters.serialize(mergedTree: CirRootNode, commonTarget: CommonizerTarget) {
    CirTreeSerializer.serializeSingleTarget(mergedTree, mergedTree.indexOfCommon, statsCollector) { metadataModule ->
        val libraryName = metadataModule.name
        val serializedMetadata = with(metadataModule.write(KLIB_FRAGMENT_WRITE_STRATEGY)) {
            SerializedMetadata(header, fragments, fragmentNames)
        }
        val manifestData = manifestProvider[commonTarget].buildManifest(libraryName)
        resultsConsumer.consume(this, commonTarget, Commonized(libraryName, serializedMetadata, manifestData))
    }
    resultsConsumer.targetConsumed(this, commonTarget)
}


internal fun CommonizerParameters.fork(): CommonizerParameters = with(logger?.fork())

