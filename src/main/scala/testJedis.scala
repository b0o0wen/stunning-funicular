import redis.clients.jedis.{HostAndPort, JedisCluster, JedisPool, ScanParams}
import scala.collection.JavaConverters._

/**
 *
 */
object testJedis {
  @transient private var cluster: JedisCluster = _

  def main(args: Array[String]): Unit = {
    val hostStr = args(1)
    val keyOrPattern = args(2)
    val hgetAllKey = args(3)

    println("\n===== hostStr, keyOrPattern, hgetAllKey =====")
    println(hostStr)
    println(keyOrPattern)
    println(hgetAllKey)

    var nodes: scala.collection.mutable.Map[String, JedisPool] = scala.collection.mutable.Map()
    val cliHost = RedisHostParser(hostStr)
    withCluster(Set(cliHost), { cli =>
      nodes = cli.getClusterNodes.asScala
    })
    println("===== cluster nodes =====")
    println(nodes)

    val HostAndPortSet: Set[RedisHostParser] = nodes.keys.toSet.map {
      key: String =>
        val addr = "c@" + key.toString + "/0"
        RedisHostParser(addr)
    }

    withCluster(HostAndPortSet, { cli =>
      val scan = new ScanParams().`match`(keyOrPattern).count(50000)
      var result = cli.scan(ScanParams.SCAN_POINTER_START, scan)
      var keys = result.getResult.asScala.toList
      while (!result.getStringCursor.equals(ScanParams.SCAN_POINTER_START)) {
        result = cli.scan(result.getStringCursor, scan)
        keys ++= result.getResult.asScala.toList
      }
      println("===== scan result =====")
      println(keys)

      val hgetResult = cli.hgetAll(hgetAllKey)
      println("===== hget result =====")
      println(hgetResult)
      println("")
    })
  }

  def withCluster[T](addrSet: Set[RedisHostParser], body: JedisCluster => T): T = {
    if (cluster == null) {
      synchronized {
        if (cluster == null) {
          cluster = new JedisCluster(addrSet.map(addr => addr.clusterNode).asJava)
        }
      }
    }
    body(cluster)
  }
}

case class RedisHostParser(addr: String) extends Serializable {
  private val Array(host, port_db) = addr.split(":")
  private val Array(port, _) = port_db.split("/")

  val isCluster: Boolean = host.split("@").head.equals("c")
  val clusterNode = new HostAndPort(host.split("@").last, port.toInt)
}
