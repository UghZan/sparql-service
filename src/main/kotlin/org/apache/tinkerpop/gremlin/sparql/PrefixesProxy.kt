package org.apache.tinkerpop.gremlin.sparql

object PrefixesProxy {
    fun prependGremlinPrefixes(script: String): String = Prefixes.prepend(script)
}
