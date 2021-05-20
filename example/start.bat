cd docker
docker compose up --build -d
docker exec -it docker_janusgraph_1 ./bin/gremlin.sh