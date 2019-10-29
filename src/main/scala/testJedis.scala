import redis.clients.jedis.{HostAndPort, Jedis, JedisCluster, JedisPool, ScanParams}

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

    println("\n===== INPUT: hostStr, keyOrPattern, hgetAllKey =====")
    println(hostStr)
    println(keyOrPattern)
    println(hgetAllKey)

    val HostAndPortSet = hostStr.split(",").toSet.map(RedisHostParser)

    withCluster(HostAndPortSet, { cli =>
      val scan = new ScanParams().`match`(keyOrPattern).count(50000)
      var result = cli.scan(ScanParams.SCAN_POINTER_START, scan)
      var keys = result.getResult.asScala.toList
      while (!result.getStringCursor.equals(ScanParams.SCAN_POINTER_START)) {
        result = cli.scan(result.getStringCursor, scan)
        keys ++= result.getResult.asScala.toList
      }
      println("\n===== JedisCluster scan result =====")
      println(keys)

      val hgetResult = cli.hgetAll(hgetAllKey)
      println("\n===== JedisCluster hgetAll result (control group, ensure the key exists)  =====")
      println(hgetResult)
      println("")
    })

    val tryJedis = TryJedis(hostStr, keyOrPattern)
    val keys = tryJedis.main()
    println("===== keys: scan by entry =====")
    println(keys)
    println("")
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

case class TryJedis(hostStr: String, keyOrPattern: String) {
  def main(): List[String]= {
    val jedisCluster = getJedisCluster(hostStr)
    val keys = getClusterKeys(jedisCluster, keyOrPattern)
    keys
  }

  def getJedisCluster(hostStr: String): JedisCluster = {
    val hosts = hostStr.split(",").toSet.map { i: String =>
      RedisHostParser(i).clusterNode
    }
    new JedisCluster(hosts.asJava)
  }

  def getClusterKeys(jedisCluster: JedisCluster, keyOrPattern: String): List[String] = {
    // val clusterNodes: scala.collection.mutable.Map[String, JedisPool] = jedisCluster.getClusterNodes.asScala
    val clusterNodes = jedisCluster.getClusterNodes
    var keys: List[String] = List()

    for (entry <- clusterNodes.entrySet().asScala) {
      val jedis: Jedis = entry.getValue.getResource
      if (!jedis.info("replication").contains("role:slave")) {
        keys ++= getScan(jedis, keyOrPattern)
      }
    }
    keys
  }

  def getScan(jedis: Jedis, str: String): List[String] = {
    val scan = new ScanParams().`match`(str).count(5000)
    var result = jedis.scan(ScanParams.SCAN_POINTER_START, scan)
    var keys = result.getResult.asScala.toList
    while (!result.getStringCursor.equals(ScanParams.SCAN_POINTER_START)) {
      result = jedis.scan(result.getStringCursor, scan)
      keys ++= result.getResult.asScala.toList
    }
    keys
  }
}