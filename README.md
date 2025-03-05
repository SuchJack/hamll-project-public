# 部署配置

## 项目模块

todo



## 配置详情

4C 8G 40G

主机名：localhost.localdomain

当前主机：192.168.137.128

管理账户：root admin

静态地址：192.168.137.128



## 部署服务：hmall

> 要确保服务都要一个docker网络内；网络：hm-net

### mysql数据库-3306

载入初始化配置

开放端口：3306

root 123

```bash
docker run -d \
  --name mysql \
  -p 3306:3306 \
  -e TZ=Asia/Shanghai \
  -e MYSQL_ROOT_PASSWORD=123 \
  -v /root/mysql/data:/var/lib/mysql \
  -v /root/mysql/conf:/etc/mysql/conf.d \
  -v /root/mysql/init:/docker-entrypoint-initdb.d \
  --network hm-net\
  mysql
```



### Nacos注册中心-8848

载入初始化配置

开放端口：8848、9848、9849

后台地址：http://192.168.137.128:8848/nacos/

```bash
docker run -d \
--name nacos \
--env-file ./nacos/custom.env \
-p 8848:8848 \
-p 9848:9848 \
-p 9849:9849 \
--restart=always \
nacos/nacos-server:v2.1.0-slim
```

指令缺少指定网络，需要手动加入网络



### sentinel服务保护-8090

导入jar包、dockerfile

开放端口：8090

后台地址：http://192.168.137.128:8090/

部署指令

```bash
java -Dserver.port=8090 -Dcsp.sentinel.dashboard.server=localhost:8090 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard.jar
```



### seata分布式事务-7099

载入配置、导TC数据库、TA数据表（TA导进trade、item、cart数据库）

开放端口：8099、7099

后台地址：http://192.168.137.128:7099/

```bash
docker run --name seata \
-p 8099:8099 \
-p 7099:7099 \
-e SEATA_IP=110.40.64.81 \
-v ./seata:/seata-server/resources \
--privileged=true \
--network hm-net \
-d \
seataio/seata-server:1.5.2
```



### RabbitMQ消息队列-15672

开发端口：15672、5672

后台地址：http://192.168.137.128:15672/

```bash
docker run \
 -e RABBITMQ_DEFAULT_USER=itheima \
 -e RABBITMQ_DEFAULT_PASS=123321 \
 -v mq-plugins:/plugins \
 --name mq \
 --hostname mq \
 -p 15672:15672 \
 -p 5672:5672 \
 --network hm-net\
 -d \
 rabbitmq:3.8-management
```

需要在后台创建用户：hmall；密码：123才能启动项目

## 部署项目

nginx部署失败





