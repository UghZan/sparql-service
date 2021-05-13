package com.github.timaliberdov.gremlin.sparql

import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.http.javadsl.ServerBinding
import akka.http.javadsl.model.StatusCodes
import akka.http.javadsl.server.Directives.*
import akka.http.javadsl.server.Route
import akka.http.javadsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import com.github.timaliberdov.gremlin.sparql.util.NodeUtil
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QueryParseException
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.query.Syntax
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.ResultSetStream
import org.apache.jena.sparql.engine.binding.BindingHashMap
import org.apache.tinkerpop.gremlin.driver.Client
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.Result
import org.apache.tinkerpop.gremlin.process.remote.RemoteConnection
import org.apache.tinkerpop.gremlin.process.remote.traversal.AbstractRemoteTraverser
import org.apache.tinkerpop.gremlin.sparql.PrefixesProxy
import org.apache.tinkerpop.gremlin.sparql.process.traversal.dsl.sparql.SparqlTraversalSource
import java.time.Duration
import java.util.*
import kotlin.collections.HashMap

class Endpoint(private val port: Int) {
    private val system = ActorSystem.create("sparql-gremlin-endpoint")
    private val http = Http.get(system)
    private val materializer = ActorMaterializer.create(system)

    fun start() {
        val futureBinding = http.bindAndHandle(
            routes.flow(system, materializer),
            ConnectHttp.toHost("0.0.0.0", port),
            materializer
        )

        futureBinding.whenComplete { binding: ServerBinding?, exception ->
            if (binding != null) {
                system.log().info("Running on ${binding.localAddress()}")
                Runtime.getRuntime().addShutdownHook(Thread {
                    system.log().info("Shutting down")
                    binding.terminate(Duration.ofSeconds(3)).thenAccept { system.terminate() }
                })
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception)
                system.terminate()
            }
        }
    }

    private val routes: Route = path("sparql") {
            post {
                entity(Unmarshaller.entityToString(), this::execQuery)
            }
    }

    private fun execQuery(queryStr: String): Route {

        val scanner = Scanner(queryStr)
        var newQueryStr = ""
        var prefixes = HashMap<String, String>()
        while(scanner.hasNext())
        {
            var str = scanner.nextLine()
            if(str.startsWith("PREFIX", true))
            {
                val parts = str.split(" ")
                prefixes[parts[1]] = parts[2].replace("<|>".toRegex(), "")
            }
            else
            {
                for((key, value) in prefixes)
                    str.replace(key, value)

                str = str.replace("v:http://", "v:")
                str = str.replace("p:http://", "p:")
                str = str.replace("e:http://", "e:")
                val match = "(v:|p:|e:)[^\\s]+".toRegex().find(str)
                if(match != null)
                    str = str.substring(0, match.range.first-1) + str.substring(match.range.first, match.range.last).replace("/","_") + str.substring(match.range.last+1)
            }
        }


        val query = try {
            QueryFactory.create(PrefixesProxy.prependGremlinPrefixes(newQueryStr), Syntax.syntaxSPARQL)
        } catch (e: QueryParseException) {
            return complete(StatusCodes.BAD_REQUEST, "Malformed query: ${e.localizedMessage}")
        }

        if (!query.isSelectType) {
            return complete(StatusCodes.BAD_REQUEST, "Only Select queries are supported")
        } else if (query.isQueryResultStar) {
            return complete(StatusCodes.BAD_REQUEST, "Star shaped queries are not supported")
        }

        val propertiesConfiguration = PropertiesConfiguration("conf/remote-graph.properties") // todo: replace hard coded value with run parameter
        val src = SparqlTraversalSource(RemoteConnection.from(propertiesConfiguration))

        val cluster = Cluster.open("conf/remote-objects.yaml") // todo: replace hard coded value with run parameter
        val client = cluster.connect<Client>().alias("g") // todo: how to get alias from properties file?

        val graphTraversal = src.sparql<Any>(newQueryStr)
        val resultSet = client.submit(graphTraversal)
        val bindings: List<String> = query.resultVars

        return onComplete(resultSet.all().thenApply { process(bindings, it) }) { res ->
            res.fold(
                {
                    when (it) {
                        is NotImplementedError -> complete(StatusCodes.NOT_IMPLEMENTED, it.localizedMessage)
                        is IllegalArgumentException -> complete(StatusCodes.BAD_REQUEST, it.localizedMessage)
                        else -> complete(StatusCodes.INTERNAL_SERVER_ERROR, it.localizedMessage)
                    }
                },
                { complete(StatusCodes.OK, it) }
            )
        }
    }

    private fun process(bindings: List<String>, results: List<Result>): String {
        val bindingIter = when (bindings.size) {
            0 -> throw IllegalArgumentException("Bindings are empty!")
            1 -> {
                val varName = bindings.first()
                results.stream().map {
                    val bindingHashMap = BindingHashMap()
                    bindingHashMap.add(
                        Var.alloc(varName),
                        NodeUtil.mapToNode((it.`object` as AbstractRemoteTraverser<*>).get())
                    )
                    bindingHashMap
                }.iterator()
            }
            else -> {
                results.stream().map {
                    val bindingHashMap = BindingHashMap()
                    @Suppress("UNCHECKED_CAST")
                    val hashMap = (it.`object` as AbstractRemoteTraverser<HashMap<String, *>>).get()
                    bindings.forEach { varName ->
                        hashMap[varName]?.let { bindingHashMap.add(Var.alloc(varName), NodeUtil.mapToNode(it)) }
                    }
                    bindingHashMap
                }.iterator()
            }
        }

        val resultSet = ResultSetStream(bindings, null, bindingIter)
        return ResultSetFormatter.asXMLString(resultSet)
    }

}
