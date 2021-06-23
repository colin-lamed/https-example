# Scala Http4s Example

An example project of *Haskell style MTL* REST service using Scala Http4s/cats/fs2/doobie.

Run server
```bash
sbt run
```

Test endpoints:
```bash
curl -i "http://localhost:8080/status"
curl -i "http://localhost:8080/v1/repos" -H "Authorization: Bearer xxx"
curl -i "http://localhost:8080/v1/homes/1" -H "Authorization: Bearer xxx"
```


## Resources

- https://http4s.org/v1.0/
- https://typelevel.org/cats-effect
- https://typelevel.org/cats-mtl/
- http://fs2.io/
- http://tpolecat.github.io/doobie/
