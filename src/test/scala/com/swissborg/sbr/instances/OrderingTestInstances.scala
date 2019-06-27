package com.swissborg.sbr.instances

import shapeless.tag.@@

object OrderingTestInstances extends OrderingTestInstances

trait OrderingTestInstances {
  implicit def taggedOrdering[A: Ordering, B]: Ordering[A @@ B] = Ordering[A].on(identity)
}
