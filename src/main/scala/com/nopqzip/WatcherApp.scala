package com.nopqzip

import akka.actor.{ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
import akka.cluster.pubsub._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class Watcher extends Actor with ActorLogging {
  import DistributedPubSubMediator.Publish
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator
  var isWatched : Boolean = false
  
  def findWatchee : Unit = {
    log.info("Publishing")
    mediator ! Publish("watchee", StMsg("can I watch you?"))
    import context.dispatcher
    if (!isWatched) context.system.scheduler.scheduleOnce(10 seconds)(findWatchee)
  }
  
  override def preStart = {
    log.info ("Watcher start")
    findWatchee
  }
  
  override def postStop = {
    log.info("Watcher Stop")
  }
  
  def receive = {
    case StMsg(text) if text equals "yes you can!" ⇒
      val watchee = sender()
      import context.dispatcher
      isWatched = true
      context.system.scheduler.scheduleOnce(15 seconds) {
        log.info("15s left to watch")
      }
      context.system.scheduler.scheduleOnce(30 seconds) {
        log.info("watching watchee")
        context watch watchee
      }
      
    case StMsg(text) ⇒
      log.info("Receive " + text + " from " + sender.toString())
    
    case Terminated(a) ⇒
      isWatched = false
      findWatchee
      log.info("Watchee Terminated, sender is " + sender.toString())
  }
}

object WatcherApp {
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watcher.tcp.port")
    val host = conf.getString("watch.watcher.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watcher]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    val watcher = system.actorOf(Props[Watcher], name="watcher")
  }
}
