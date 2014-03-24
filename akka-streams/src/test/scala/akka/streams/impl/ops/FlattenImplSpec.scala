package akka.streams.impl.ops

import org.scalatest.{ FreeSpec, ShouldMatchers }

import akka.streams.impl._
import akka.streams.Operation.{ FromProducerSource, FromIterableSource }

class FlattenImplSpec extends FreeSpec with ShouldMatchers with SyncOperationSpec {
  "Flatten should" - {
    "initially" - {
      "request one substream as soon as at least one result element was requested" in new UninitializedSetup {
        flatten.handleRequestMore(1) should be(UpstreamRequestMore(1))
      }
    }
    "while waiting for subscription" - {
      "support requestMore" in new UninitializedSetup {
        flatten.handleRequestMore(10) should be(UpstreamRequestMore(1))
        val SubscribeToProducer(CustomProducer, onSubscribe) = flatten.handleNext(CustomSource)
        flatten.handleRequestMore(3) should be(Continue)
        val subDownstream = onSubscribe(SubUpstream)
        // should now request all previously requested elements
        subDownstream.start() should be(RequestMoreFromSubstream(13))
      }
    }
    "while consuming substream" - {
      "request elements from substream and deliver results to downstream" in new UninitializedSetup {
        flatten.handleRequestMore(10) should be(UpstreamRequestMore(1))
        flatten.handleRequestMore(1) should be(Continue) // don't request more substreams while one is still pending
        val SubscribeToProducer(CustomProducer, onSubscribe) = flatten.handleNext(CustomSource)
        val subDownstream = onSubscribe(SubUpstream)
        subDownstream.start() should be(RequestMoreFromSubstream(11))
        subDownstream.handleNext(1.5f) should be(DownstreamNext(1.5f))
        flatten.handleRequestMore(5) should be(RequestMoreFromSubstream(5))
        subDownstream.handleNext(8.7f) should be(DownstreamNext(8.7f))
      }
      "go on with super stream when substream is depleted" in new UninitializedSetup {
        flatten.handleRequestMore(10) should be(UpstreamRequestMore(1))
        flatten.handleRequestMore(1) should be(Continue) // don't request more substreams while one is still pending
        val SubscribeToProducer(CustomProducer, onSubscribe) = flatten.handleNext(CustomSource)
        val subDownstream = onSubscribe(SubUpstream)
        subDownstream.start() should be(RequestMoreFromSubstream(11))
        subDownstream.handleComplete() should be(UpstreamRequestMore(1))
      }
      "eventually close to downstream when super stream closes and last substream is depleted" in new UninitializedSetup {
        flatten.handleRequestMore(10) should be(UpstreamRequestMore(1))
        flatten.handleRequestMore(1) should be(Continue) // don't request more substreams while one is still pending
        val SubscribeToProducer(CustomProducer, onSubscribe) = flatten.handleNext(CustomSource)
        val subDownstream = onSubscribe(SubUpstream)
        subDownstream.start() should be(RequestMoreFromSubstream(11))
        subDownstream.handleNext(1.5f) should be(DownstreamNext(1.5f))
        flatten.handleComplete() should be(Continue)
        subDownstream.handleNext(8.3f) should be(DownstreamNext(8.3f))
        subDownstream.handleComplete() should be(DownstreamComplete)
      }
      "deliver many elements from substream to output" in pending
      "cancel substream and main stream when cancelled itself" in pending
      "immediately report substream error" in pending
    }
  }

  class UninitializedSetup {
    val CustomProducer = namedProducer[Any]("customProducer")
    val CustomSource = FromProducerSource(CustomProducer)
    case class RequestMoreFromSubstream(n: Int) extends ExternalEffect {
      def run(): Unit = ???
    }
    case object CancelSubstream extends ExternalEffect {
      def run(): Unit = ???
    }
    object SubUpstream extends Upstream {
      val requestMore: Int ⇒ Effect = RequestMoreFromSubstream
      val cancel: Effect = CancelSubstream
    }
    val flatten = new FlattenImpl(upstream, downstream, TestContextEffects)
  }
}
