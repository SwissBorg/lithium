# Split-brain resolver for Akka clusters

When a cluster member becomes unreachable the leader cannot perform its 
duties anymore. Members cannot change state, singletons cannot be moved
to a different member. In such situations the cluster administrator has
to manually down members so the leader can continue its duties. This 
library provides a few strategies that can automatically down members 
without user intervention.

## Strategies

### Static quorum
Keeps the side of a partition that has at least the specified number of reachable members.

#### Configuration
```
akka.cluster {
    split-brain-resolver {
        downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
        active-strategy = "static-quorum"
        static-quorum {
            quorum-size = # ???
            role = # ???
        }
        stable-after = # ???
    }
}
```

### Keep majority
Keeps the side of partition that has a majority of reachable members.

#### Configuration
```
akka.cluster {
    split-brain-resolver {
        downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
        active-strategy = "keep-majority"
        static-quorum {
            role = # ???
        }
        stable-after = # ???
    }
}
```

### Keep referee
Keeps the partition that can reach the specified member.

#### Configuration
```
akka.cluster {
    split-brain-resolver {
        downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
        active-strategy = "keep-referee"
        static-quorum {
            address = # "akka.trttl.gremlin.tcp://ThreeNodeSpec@localhost:9991"
            down-all-if-less-than-nodes = # ???
        }
        stable-after = # ???
    }
}
```

### Keep oldest
Keeps the partition that can reach the oldest member in the cluster.

#### Configuration

```
akka.cluster {
    split-brain-resolver {
        downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
        active-strategy = "keep-oldest"
        static-quorum {
            down-all-if-alone = # no ???
            role = ""
        }
        stable-after = # ???
    }
}
```

## Indirectly connected members
An indirectly connected member is a cluster member that has a mix of nodes
that can reach and not reach it. Such members are downed in combination 
with the ones downed by the configured strategy.