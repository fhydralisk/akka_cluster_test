package com.nopqzip

import akka.actor.{ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
import akka.cluster.pubsub._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

class Watcher extends Actor with ActorLogging {
  import DistributedPubSubMediator.Publish
  // activate the extension
  val mediator = DistributedPubSub(context.system).mediator
  override def preStart = {
    log.info ("Watcher start")
    import context.dispatcher
    context.system.scheduler.scheduleOnce(10 seconds) {
      log.info("Publishing")
      mediator ! Publish("watchee", StMsg("TEST"))
    }
  }
  
  override def postStop = {
    log.info("Watcher Stop")
  }
  
  def receive = {
    case StMsg(text) if text equals "hello" ⇒
      sender() ! StMsg("hi")
      val watchee = sender()
      import context.dispatcher
      context.system.scheduler.scheduleOnce(15 seconds) {
        log.info("15s left to watch")
      }
      context.system.scheduler.scheduleOnce(30 seconds) {
        log.info("watching watchee")
        context watch watchee
      }
      
    case StMsg(text) ⇒
      log.info("Receive " + text + " from " + sender.toString())
      // sender() ! StMsg("ok")
    
    case Terminated(a) ⇒
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
