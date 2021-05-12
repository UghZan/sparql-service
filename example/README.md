Run `docker-compose up --build -d` from example/docker directory

Connect to gremlin console: `docker exec -it docker_janusgraph_1 ./bin/gremlin.sh`

Add some test data. E.g.:<br>
`:remote connect tinkerpop.server conf/custom-remote.yaml`<br>
`:> graph = g.getGraph()`<br>
`:> graph.io(graphml()).readGraph('sample-data/air-routes.graphml')`<br>
<br>
Note that air-routes.graphml is taken from [here](https://kelvinlawrence.net/book/Gremlin-Graph-Guide.html),
but modified a bit: for one of two Dallas airports country code is written as URI to test mapping. 

Run Main.kt with `start` argument.<br>
Send example request. See sparql-test.http
