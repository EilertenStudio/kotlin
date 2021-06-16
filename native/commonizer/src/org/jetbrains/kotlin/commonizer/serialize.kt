/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer

import kotlinx.metadata.klib.ChunkedKlibModuleFragmentWriteStrategy
import org.jetbrains.kotlin.commonizer.mergedtree.CirNode.Companion.indexOfCommon
import org.jetbrains.kotlin.commonizer.mergedtree.CirRootNode
import org.jetbrains.kotlin.commonizer.metadata.CirTreeSerializer
import org.jetbrains.kotlin.commonizer.utils.progress
import org.jetbrains.kotlin.library.SerializedMetadata

private val KLIB_FRAGMENT_WRITE_STRATEGY = ChunkedKlibModuleFragmentWriteStrategy()

internal fun serializeTarget(
    parameters: CommonizerParameters,
    mergedTree: CirRootNode,
    target: SharedCommonizerTarget
): Unit = parameters.logger.progress(target, "Serialized target") {
    CirTreeSerializer.serializeSingleTarget(mergedTree, mergedTree.indexOfCommon, parameters.statsCollector) { metadataModule ->
        val libraryName = metadataModule.name
        val serializedMetadata = with(metadataModule.write(KLIB_FRAGMENT_WRITE_STRATEGY)) {
            SerializedMetadata(header, fragments, fragmentNames)
        }
        val manifestData = parameters.manifestProvider[target].buildManifest(libraryName)
        parameters.resultsConsumer.consume(
            parameters, target,
            ResultsConsumer.ModuleResult.Commonized(libraryName, serializedMetadata, manifestData)
        )
    }
    parameters.resultsConsumer.targetConsumed(parameters, target)
}

