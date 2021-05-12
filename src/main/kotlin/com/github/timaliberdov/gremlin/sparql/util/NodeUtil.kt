package com.github.timaliberdov.gremlin.sparql.util

import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.impl.LiteralLabelFactory
import org.apache.jena.iri.IRIFactory

object NodeUtil {
    private val factory: IRIFactory = IRIFactory()

    init {
        factory.useSpecificationURI(true)
    }

    fun mapToNode(obj: Any): Node =
        when (obj) {
            is String -> {
                val iri = factory.create(obj)
                if (iri.isAbsolute && !iri.hasViolation(false)) NodeFactory.createURI(obj)
                else NodeFactory.createLiteral(LiteralLabelFactory.createTypedLiteral(obj))
            }
            is Number -> NodeFactory.createLiteral(LiteralLabelFactory.createTypedLiteral(obj))
            is Boolean -> NodeFactory.createLiteral(LiteralLabelFactory.createTypedLiteral(obj))
            is Char -> NodeFactory.createLiteral(LiteralLabelFactory.createTypedLiteral(obj))
            else -> throw NotImplementedError()
        }

}
