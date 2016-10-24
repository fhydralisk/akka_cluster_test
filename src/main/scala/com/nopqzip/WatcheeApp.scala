package com.nopqzip

import akka.actor.{ActorSystem, ActorRef, Actor, Cancellable, Props}
import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration._

class Watchee extends Actor {
  private var scheduler: Cancellable = null 
  
  override def preStart = {
    val config = ConfigFactory.load()
    val host = config.getString("watch.watcher.tcp.hostname")
    val port = config.getInt("watch.watcher.tcp.port")
    println ("Watchee start")
    import context.dispatcher
    scheduler = context.system.scheduler.schedule(1 seconds, 1 seconds) {
      val actorSelection = context.actorSelection(s"akka.tcp://ClusterSystem@$host:$port/user/watcher")
      println(actorSelection)
      // implicit val timeout = Timeout(5.seconds)
      actorSelection ! StMsg("hello")
    }
  }
  
  override def postStop = {
    println ("Watchee stop")
  }
  
  def receive = {
    case StMsg(text) if text equals "hi" => 
      println("hi, watcher")
      val watcher = sender()
      if (scheduler != null) { 
        scheduler.cancel() 
        scheduler = null
      }
      
      // deal with tests
      val terminate = WatcheeApp.terminate
      val killActor = WatcheeApp.killActor
      val sendMsg = WatcheeApp.sendMsg
      import context.dispatcher
      if (WatcheeApp.terminate > 0) {
        context.system.scheduler.scheduleOnce (terminate.seconds) {
          println("terminating actor system")
          context.system.terminate().onSuccess {
            case result =>
              println(result)
              println("actor system down")
          }
        }
      }
      
      if (killActor > 0) {
        val inform = if (killActor > 15) killActor - 15 else 0
        context.system.scheduler.scheduleOnce(inform.seconds) {
          val t = killActor - inform
          println(s"$t seconds left to kill actor")
        }
        context.system.scheduler.scheduleOnce(killActor.seconds) {
          println("Stopping actor " + self.toString())
          context.system.stop(self)
        }
      }
      
      if (sendMsg > 0) {
        val inform = if (sendMsg > 15) sendMsg - 15 else 0
        context.system.scheduler.scheduleOnce(inform.seconds) {
          val t = sendMsg - inform
          println(s"$t seconds left to send custom message")
        }
        context.system.scheduler.scheduleOnce(sendMsg.seconds) {
          println("sending custom message to watcher" + watcher.toString())
          watcher ! StMsg("Test")
        }
      }

    case _ => println("unhandle2")
  }
  
}

object WatcheeApp {
  val conf = ConfigFactory.load()
  val terminate = conf.getInt("watch.watchee.testing.terminate")
  val killActor = conf.getInt("watch.watchee.testing.killactor")
  val sendMsg = conf.getInt("watch.watchee.testing.sendcustom")
  var watchee : Some[Any] = Some()
  def main(args: Array[String]) : Unit = {
    val conf = ConfigFactory.load()
    val port = conf.getInt("watch.watchee.tcp.port")
    val host = conf.getString("watch.watchee.tcp.hostname")
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString(s"akka.remote.netty.tcp.hostname=$host")).
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [watchee]")).
      withFallback(conf)
    val system = ActorSystem.create("ClusterSystem", config)
    watchee = Some(system.actorOf(Props[Watchee], name="watchee"))    
  }
}
