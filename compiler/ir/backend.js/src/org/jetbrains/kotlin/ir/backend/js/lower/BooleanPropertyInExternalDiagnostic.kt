/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.config.RuntimeDiagnostic

class BooleanPropertyInExternalDiagnostic(
    private val context: JsIrBackendContext
) : BodyLoweringPass {

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        val booleanInExternalsDiagnostic = context.booleanInExternalsDiagnostic ?: return
        irBody.transformChildrenVoid(ExternalBooleanPropertyProcessor(booleanInExternalsDiagnostic))
    }

    private inner class ExternalBooleanPropertyProcessor(
        private val booleanInExternalsDiagnostic: RuntimeDiagnostic
    ) : IrElementTransformerVoid() {

        override fun visitCall(expression: IrCall): IrExpression {
            expression.transformChildrenVoid(this)

            val symbol = expression.symbol
            val callee = symbol.owner
            val property = callee.correspondingPropertySymbol?.owner ?: return expression

            if (!property.isEffectivelyExternal()) return expression

            if (callee == property.getter) {
                if (callee.returnType != context.irBuiltIns.booleanType) return expression

                val function = booleanInExternalsDiagnostic.diagnosticMethod()

                return context.createIrBuilder(symbol).irBlock {
                    val tmp = createTmpVariable(expression)
                    val call = JsIrBuilder.buildCall(
                        target = function,
                        type = function.owner.returnType
                    ).apply {
                        putValueArgument(0, irGet(tmp))
                    }
                    +JsIrBuilder.buildComposite(
                        type = callee.returnType,
                        statements = mutableListOf<IrStatement>(call).apply { add(irGet(tmp)) }
                    )
                }
            }

            return expression
        }
    }

    private fun RuntimeDiagnostic.diagnosticMethod() = when (this) {
        RuntimeDiagnostic.LOG -> context.intrinsics.jsBooleanInExternalLog
        RuntimeDiagnostic.EXCEPTION -> context.intrinsics.jsBooleanInExternalException
    }
}
