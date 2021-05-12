package com.github.timaliberdov.gremlin.sparql.cli

import com.github.rvesse.airline.annotations.Command
import com.github.rvesse.airline.annotations.Option
import com.github.rvesse.airline.annotations.OptionType
import com.github.timaliberdov.gremlin.sparql.Endpoint

@Command(name = "start", description = "Start SPARQL endpoint")
class StartCommand : CliCommand {
    @Option(type = OptionType.COMMAND, name = ["-p", "--port"], description = "port of the SPARQL endpoint")
    private val port = 9999

    // todo: remote-graph.properties file path
    // todo: remote-objects.yaml file path

    override fun run() = Endpoint(port).start()
}
