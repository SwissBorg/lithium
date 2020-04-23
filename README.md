[![Build Status](https://travis-ci.org/SwissBorg/lithium.svg?branch=master)](https://travis-ci.org/SwissBorg/lithium)
[![Maven Central](https://img.shields.io/maven-central/v/com.swissborg/lithium_2.12)](https://oss.sonatype.org/content/repositories/releases/com/swissborg/lithium_2.12/)

# Lithium - A Split-Brain Resolver for Akka-Cluster

When a cluster member becomes unreachable the leader cannot perform its 
duties anymore. Members cannot change state, singletons cannot be moved
to a different member. In such situations the cluster administrator has
to manually down members so the leader can continue its duties. This 
library provides a few strategies that will automatically down members 
without needing any intervention.

## Getting started

```scala
libraryDependencies += "com.swissborg" %% "lithium" % "0.11.2"
```

## Stable-After
The strategy is run only after the cluster has been stable for 
a configured amount of time. The stability is affected by members changing
state and failure detections. However, the stability is not affected by
members becoming "joining" or "weakly-up" as they are not counted in decisions.
The reason for this is that a node can move to those states during network 
partitions and as result potentially not be seen by all the partitions.

The `stable-after` duration should be chosen longer than the time it takes
to gossip cluster membership changes. Additionally, since a partition cannot
communicate together, the duration should be large enough so that persistent
actor have the time to stop in one partition before being instantiated somewhere 
on the surviving partition.

## Down-when-unstable

The `down-all-when-unstable` flag when not set to `off` will down the partition 
if the cluster has been unstable for longer than `stable-after + 3/4 * stable-after`.
This stops the situation where a persistent actor is started in the surviving 
partition before being stopped in its original partition because the `stable-after`
timeout in the original is never hit. The duration can be overriden and should be
chosen as less than `2 * stable-after`. It can also be disabled by setting it
to `off` but this is not recommended.

```hocon
akka.cluster {
  downing-provider-class = "com.swissborg.lithium.DowningProviderImpl"
}

com.swissborg.lithium {
  # The name of the strategy to use for split-brain resolution.
  # Available: static-quorum, keep-majority, keep-referee, keep-oldest.
  active-strategy = null

  # Duration during which the cluster must be stable before taking
  # action on the network-partition. The duration must chose large
  # enough to allow for membership events to be gossiped and persistent
  # actor to be migrated.
  stable-after = 30s
  
  # Down the partition if it has been unstable for too long. If the cluster
  # wait too long before downing itself persistent actors might already be
  # restarted on another partition, leading to two instances of the same
  # persistent actor.
  # It is by default derived from 'stable-after' to be 'stable-after' + 3/4 'stable-after'.
  # If overriden, it must be less than 2 * 'stable-after'. To disable the downing, set 
  # it to 'off', however this is not recommended.
  #down-all-when-unstable = on
}
```

## Strategies


### Static-Quorum
Keeps the partition that contains at least `quorum-size` nodes and downs
all the other partitions.

The `quorum-size` should be chosen as strictly more than half the nodes
in the cluster, only counting nodes with the configured `role`.

In a cluster with 5 nodes, in the case of a split of two partitions. One with 3 nodes
and the other with 2 nodes, the 3 node partition will survive. With only 3 nodes still
in the cluster there is the risk that the cluster will downed while fixing the next failure.
Hence, new nodes should be added to the cluster after resolutions.

The cluster can down itself in a few cases:
 * When the cluster grows too large it will down itself to avoid creating a split-brain when multiple partitions form a quorum. 
 * When multiple partitions occur and none of them forms a quorum. 
 * When a large amount of nodes crash and not enough nodes remain to form a quorum.

This strategy is useful when the size of the cluster is fixed or the number of nodes
with the given role is fixed.

By design this strategy cannot lead to a split-brain.

#### Configuration
```hocon
com.swissborg.lithium {
  active-strategy = "static-quorum"
  static-quorum {
    # Minimum number of nodes in the surviving partition.
    quorum-size = null
    
    # Only take in account nodes with this role.
    role = ""
  }
}
```

### Keep-Majority
Keeps the side of partition that contains the majority of nodes and downs
the other partitions. In the case where they have the same size the one 
containing the member with the lowest address is kept.

The cluster can down itself in a few cases:
 * When multiple partitions occur and none of them forms a majority.
 * When a majority of nodes crash, the remaining nodes do not form a majority and down themselves. 

This strategy is useful when the size of the cluster is dynamic

This strategy can on rare occasions lead to a split-brain when a network partition
occurs during a membership dissemination and a partition ends up seeing certain nodes
as Up while others do not.

#### Configuration
```hocon
com.swissborg.lithium {
  active-strategy = "keep-majority"
  keep-majority {
    # Only take in account nodes with this role.
    role = ""
  }
}
```

### Keep-Referee
Keeps the partition that can contains the specified member and downs the other
partitions.

The cluster can down itself in a few cases:
 * When the referee crashes.
 * When the number of nodes is below `down-all-if-less-than-nodes`. 

This strategy is useful when the cluster has a node without which it 
cannot run.

By design this strategy cannot lead to a split-brain.

#### Configuration
```hocon
com.swissborg.lithium {
  active-strategy = "keep-referee"
  keep-referee {
    # Address of the member in the format "akka://system@host:port"
    referee = null
    
    # Minimum number of nodes in the surviving partition.
    down-all-if-less-than-nodes = 1
  }
}
```

### Keep-Oldest
Keeps the partition that can contains the oldest member in the cluster and downs 
the other partitions.

By enabling `down-if-alone` the other partitions will down the oldest node if
it is cut-off from the rest. This can prevent the scenario where you have a 100
node cluster, the oldest node become unreachable. 99 nodes will be downed and 
leave the cluster heavily crippled.

When `down-if-alone` is enabled, multiple partitions occur, and the oldest node is alone 
the cluster will down itself as a partition cannot know if the unreachable nodes are in
a single partition or form multiple ones. Hence, it then assumes that there is only one
other partition and downs itself. On the other side, the oldest node (who is alone) knows
it is alone and will down itself.

This strategy can on rare occasions lead to a split-brain when a network partition
occurs during a membership dissemination and a partition ends up seeing the oldest 
nodes as Removed and selecting the second-oldest as the oldest.

#### Configuration

```hocon
com.swissborg.lithium {
  active-strategy = "keep-oldest"
  keep-oldest {
    # Down the oldest member when alone.
    down-if-alone = no
    
    # Only take in account nodes with this role.
    role = ""
  }
}
```

## Indirectly-Connected Members
An indirectly-connected member is one that has been detected or has detected 
another member as unreachable but that is still connected to via some other nodes.

These nodes will always be downed in combination with the ones downed by the 
configured strategy.

Indirectly connected nodes are downed as they sit on the intersection of two 
partitions. As result both partitions will assume it is in theirs leading to
all sorts of trouble. By downing them the partitions become clean partitions
that do not overlap.

# License
Lithium is Open Source and available under the Apache 2 License.
