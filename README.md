# Split-brain resolver for Akka clusters

When a cluster member becomes unreachable the leader cannot perform its 
duties anymore. Members cannot change state, singletons cannot be moved
to a different member. In such situations the cluster administrator has
to manually down members so the leader can continue its duties. This 
library provides a few strategies that can automatically down members 
without user intervention.

## Strategies
```hocon
akka.cluster {
    downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
    split-brain-resolver {
        # The name of the strategy to use for split-brain resolution.
        # Available: static-quorum, keep-majority, keep-referee, keep-oldest.
        active-strategy = off
     
        # Duration during which the cluster must be stable before taking
        # action on the network-partition. The duration must be chosen as
        # longer than the time it takes for singletons and shards to be 
        # moved to surviving partitions.
        stable-after = 30s
    }
}
```


### Static quorum
Keeps the side of a partition that has at least the specified number of reachable members.

#### Configuration
```hocon
akka.cluster.split-brain-resolver {
    active-strategy = "static-quorum"
    static-quorum {
        # The minimum number of members a partition must have.
        quorum-size = undefined
        
        # Take the decision based only on members with this role.
        role = ""
    }
}
```

### Keep majority
Keeps the side of partition that has a majority of reachable members.

#### Configuration
```hocon
akka.cluster.split-brain-resolver {
    active-strategy = "keep-majority"
    keep-majority {
        # Take the decision based only on members with this role.
        role = ""
    }
}
```

### Keep referee
Keeps the partition that can reach the specified member.

#### Configuration
```hocon
akka.cluster.split-brain-resolver {
    active-strategy = "keep-referee"
    keep-referee {
        # Address of the member in the format "akka.tcp://system@host:port"
        address = ""
        
        # The minimum number of nodes the partition containing the 
        # referee must contain. Otherwise, the cluster is downed.
        down-all-if-less-than-nodes = 1
    }
}
```

### Keep oldest
Keeps the partition that can reach the oldest member in the cluster.

#### Configuration

```hocon
akka.cluster.split-brain-resolver {
    active-strategy = "keep-oldest"
    keep-oldest {
        # When enabled, downs the oldest member when alone.
        down-if-alone = on
        
        # Take the decision based only on members with this role.
        role = ""
    }
}
```

## Indirectly connected members
An indirectly connected member is a cluster member that has a mix of nodes
that can reach and not reach it. Such members are downed in combination 
with the ones downed by the configured strategy.