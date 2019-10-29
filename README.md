# Reids cluster 下 scan的问题

## 准备测试数据
在redis集群下 写入一条，比如从 
10.1.1.171 上
```
$ redis-cli -c -p 30001
```
> HSET test:{mytestkey} f1 111

它
> Redirected to slot [7558] located at 10.1.2.109:30008


## 在redis cmd中

在10.1.1.171 机器上，再以 10.1.1.171 为cli 入口进入redis cmd
```
devops@ip-10-1-1-171:~$ redis-cli -c -p 30001 -h 10.1.1.171
```
而后
```
10.1.1.171:30001> scan 0 match test:{*} count 1000000
1) "0"
2) (empty list or set)
10.1.1.171:30001> scan 0 match test:{mytestkey} count 1000000
1) "0"
2) (empty list or set)
```
即scan不到数据
但是`HGETALL`拿得到
```
10.1.1.171:30001> HGETALL test:{mytestkey}
-> Redirected to slot [7558] located at 10.1.2.109:30008
1) "f1"
2) "111"
```



在10.1.1.171 机器上，再以 10.1.2.109 为cli 入口进入redis cmd
```
devops@ip-10-1-1-171:~$ redis-cli -c -p 30008 -h 10.1.2.109
```
进行同样的scan
```
10.1.2.109:30008> scan 0 match test:{*} count 1000000
1) "0"
2) 1) "test:{mytestkey}"
10.1.2.109:30008> scan 0 match test:{mytestkey} count 1000000
1) "0"
2) 1) "test:{mytestkey}"
```
均scan得到数据


即redis cluster cmd中`scan`仅`scan`当前slot中的数据。接下来检查jedis是否对此进行优化



## 使用jedis

__代码__: 没有分分支。都在master 分支中，除了update README，每个commit是一个版本。都是打包好, 然后scp到机器上测试
```
java -jar test_jedis.jar testJedis [1] [2] [3]
```
[1] 为集群 nodes，c@host:port/db * n
[2] 为scan match的参数 keyOrPattern
[3] 为hgetAll的key
其中 hgetAll 只是作为对比，看是否有这条数据

### 最初版本的测试

scp到171机器上测试
```
devops@ip-10-1-1-171:~$ java -jar test_jedis.jar testJedis c@10.1.1.171:30001/0 test:{mytestkey} test:{mytestkey}
===== hostStr, keyOrPattern, hgetAllKey =====
c@10.1.1.171:30001/0
test:{mytestkey}
test:{mytestkey}
===== scan result =====
List(test:{mytestkey})
===== hget result =====
{f1=111}
```

```
devops@ip-10-1-1-171:~$ java -jar test_jedis.jar testJedis c@10.1.1.171:30001/0 test:{*} test:{mytestkey}
===== hostStr, keyOrPattern, hgetAllKey =====
c@10.1.1.171:30001/0
test:{*}
test:{mytestkey}
===== scan result =====
List()
===== hget result =====
{f1=111}
```
以10.1.1.171作为入口，scan 具体的key拿得到数据，scan test:{*} 拿不到....


以10.1.2.109作为入口，结果一样
```
devops@ip-10-1-1-171:~$ java -jar test_jedis.jar testJedis c@10.1.2.109:30008/0 test:{mytestkey} test:{mytestkey}
===== hostStr, keyOrPattern, hgetAllKey =====
c@10.1.2.109:30008/0
test:{mytestkey}
test:{mytestkey}
===== scan result =====
List(test:{mytestkey})
===== hget result =====
{f1=111}
```

```devops@ip-10-1-1-171:~$ java -jar test_jedis.jar testJedis c@10.1.2.109:30008/0 test:{*} test:{mytestkey}
===== hostStr, keyOrPattern, hgetAllKey =====
c@10.1.2.109:30008/0
test:{*}
test:{mytestkey}
===== scan result =====
List()
===== hget result =====
{f1=111}
```
在机器10.1.2.109 上以 10.1.2.109 作为入口，结果一样.

但是！
```
devops@ip-10-1-1-171:~$ java -jar test_jedis.jar testJedis c@10.1.2.109:30008/0 sid:{*} test:{mytestkey}
```
拿得数据
```
===== scan result =====
List(sid:{ABAC2B46C770404D8033D797532ECA9E}, sid:{2715B1900D19470C8B2358BB70C1BF8E}, sid:{0041687F35654D47B7D01054FD53E4E5}, sid:{6BE5256EF4864DC28FDF6C0F9D5BDB2D},

......
```