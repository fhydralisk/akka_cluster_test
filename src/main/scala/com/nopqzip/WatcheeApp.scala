package com.nopqzip

import akka.actor.{ActorSystem, ActorRef, Actor, Cancellable, Props, ActorLogging}
import com.typesafe.config.{Config, ConfigFactory}
import akka.cluster.pubsub._
import scala.concurrent.duration._

class Watchee extends Actor with ActorLogging {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck }
  val mediator = DistributedPubSub(context.system).mediator
  // subscribe to the topic named "content"
  mediator ! Subscribe("watchee", self)
  
  override def preStart = {
    log.info("Watchee start")
    scheduleSuicide()
  }
  
  override def postStop = {
    log.info ("Watchee stop")
  }
  
  def scheduleSuicide() = {
    val terminate = WatcheeApp.terminate
    val killActor = WatcheeApp.killActor
    import context.dispatcher
    if (WatcheeApp.terminate > 0) {
      context.system.scheduler.scheduleOnce (terminate.seconds) {
       log.info("terminating actor system")
       context.system.terminate().onSuccess {
        case result =>
          log.info(result.toString())
          log.info("actor system down")
       }
      }
    }
    
    if (killActor > 0) {
      val inform = if (killActor > 15) killActor - 15 else 0
      context.system.scheduler.scheduleOnce(inform.seconds) {
       val t = killActor - inform
       log.info(s"$t seconds left to kill actor")
      }
      context.system.scheduler.scheduleOnce(killActor.seconds) {
       log.info("Stopping actor " + self.toString())
       context.system.stop(self)
      }
    }
  }
  
  def scheduleSendmsg(watcher: ActorRef) = {
    import context.dispatcher
    val sendMsg = WatcheeApp.sendMsg
    if (sendMsg > 0) {
      val inform = if (sendMsg > 15) sendMsg - 15 else 0
      context.system.scheduler.scheduleOnce(inform.seconds) {
        val t = sendMsg - inform
        log.info(s"$t seconds left to send custom message")
      }
      context.system.scheduler.scheduleOnce(sendMsg.seconds) {
        log.info("sending custom message to watcher" + watcher.toString())
        watcher ! StMsg("Test")
      }
    }
  }
  
  override def receive = {
    case StMsg(text) if text eq "hi" ⇒ 
      log.info("hi, watcher")
    case SubscribeAck(Subscribe("watchee", None, `self`)) ⇒
      log.info("subscribing");
    case StMsg(text) if text eq "can I watch you?" ⇒
      sender ! StMsg("yes you can!")
      val watcher = sender()
      // deal with tests
      scheduleSendmsg(watcher)
    case StMsg(text) if text eq "TEST" ⇒
      log.info("TEST sender is" + sender().toString())
    case _ => log.info("unhandle2")
  }
}


object WatcheeApp {
  val conf = ConfigFactory.load()
  val terminate = conf.getInt("watch.watchee.testing.terminate")
  val killActor = conf.getInt("watch.watchee.testing.killactor")
  val sendMsg = conf.getInt("watch.watchee.testing.sendcustom")
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watchee.tcp.port")
    val host = conf.getString("watch.watchee.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watchee]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    system.actorOf(Props[Watchee], name="watchee")
  }
}
