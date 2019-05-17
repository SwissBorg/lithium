# Split-brain resolver for Akka clusters

When a cluster member becomes unreachable the leader cannot perform its 
duties anymore. Members cannot change state, singletons cannot be moved
to a different member. In such situations the cluster administrator has
to manually down members so the leader can continue its duties. This 
library provides a few strategies that can automatically down members 
without user intervention.

## Stable after
Split-brain resolution is only run after the cluster has been stable for 
a configured amount of time. The stability is affected by members changing
state and failure detections. However, the stability is not affected by
members becoming "joining" or "weakly-up" as they are not counted in decisions.
The reason for this is that a node can move to those states during network 
partitions and as result potentially not seen by all the partitions.

The `stable-after` duration should be chosen longer than the time it takes
to gossip cluster membership changes. Moreover, it should be long enough 
so that persistent actors are stopped before they are started on another 
node.

```hocon
akka.cluster {
  downing-provider-class = "akka.cluster.sbr.DowningProviderImpl"
}

com.swissborg.sbr {
  # The name of the strategy to use for split-brain resolution.
  # Available: static-quorum, keep-majority, keep-referee, keep-oldest.
  active-strategy = off

  # Duration during which the cluster must be stable before taking
  # action on the network-partition. The duration must be chosen as
  # longer than the time it takes for singletons and shards to be 
  # moved to surviving partitions.
  stable-after = 30s
}
```

## Strategies


### Static quorum
Keeps the side of a partition that has at least the specified number of reachable members.

This strategy is useful when the size of the cluster is fixed or the number of nodes
with the given role is fixed. 

The `quorum-size` should be chosen as at least more than half the number nodes in
the cluster. As a consequence, if the cluster grows and breaks this assumption two
partition might see itself as a quorum and down each other. Leading to a split-brain.

In a cluster with 5 nodes, in the case of a split of two partitions. One with 3 nodes
and the other with 2 nodes, the 3 node partition will survive. With only 3 nodes still
in the cluster there is the risk that the cluster will downed while fixing the next failure.
Hence, new nodes should be added to the cluster after resolutions.

The strategy could also lead to the downing of the cluster when multiple partitions occur
and none of them is a quorum. The same also happens when a large amount of nodes crash
and not enough nodes remain.

#### Configuration
```hocon
com.swissborg.sbr {
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
If the partitions are the same size the one containing the member with the
lowest address is kept.

This strategy is useful when the size of the cluster or the number of nodes with 
the given role is not known in advance.

Similarly to the `static-quorum` strategy, if none of partitions are in majority
they will down themselves and down the entire cluster.

#### Configuration
```hocon
com.swissborg.sbr {
  active-strategy = "keep-majority"
  keep-majority {
    # Take the decision based only on members with this role.
    role = ""
  }
}
```

### Keep referee
Keeps the partition that can contains the specified member.

This strategy is useful when there is a node containing critical resources.
However, this means that in case of a crash of the referee, the entire cluster
will be downed. On the other hand, it is not possible to end up with a split-brain.

The cluster will be downed if the partition containing the referee has 
strictly less nodes than the configure `down-all-if-less-than-nodes`.

#### Configuration
```hocon
com.swissborg.sbr {
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
Keeps the partition that can contains the oldest member in the cluster.

This strategy is useful as the oldest member contains the active Cluster Singleton. 
Minimizing the down time of singletons.

Similarly to the referee, if the partition containing the oldest node is very
small compared to the other partitions, the cluster size will drastically 
decreased.

By enabling `down-if-alone`, the other partitions will down the oldest node if
it is cut-off from the rest. Otherwise, all the other nodes will down themselves.

#### Configuration

```hocon
com.swissborg.sbr {
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