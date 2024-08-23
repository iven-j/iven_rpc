# 手写RPC笔记整理

### day 1

#### 背景知识

##### 什么是RPC？

一个RPC**最最最简单**的过程是客户端**调用**服务端的的一个方法, 服务端返回执行方法的返回值给客户端。

![image-20240626172453087](C:\Users\jiangyinwen1\AppData\Roaming\Typora\typora-user-images\image-20240626172453087.png)

##### 什么是动态代理？

java动态代理机制中有两个重要的类和**接口InvocationHandler**（接口）和**Proxy（类）**，这一个类Proxy和接口InvocationHandler是我们实现动态代理的核心；

正常写接口都是先实现接口，然后编写实现类，通过创建实例，转型为接口并调用，

如果采用动态代理的方式，就是依旧先实现接口，但是不编写实现类，而是通过而是直接通过JDK提供的一个`Proxy.newProxyInstance()`创建了一个`Hello`接口对象。

这种没有实现类但是在运行期动态创建了一个接口对象的方式，我们称为动态代码。JDK提供的动态创建接口对象的方式，就叫动态代理。



####  part 1 实现一个基本的RPC调用

![image-20240702155538068](C:\Users\jiangyinwen1\AppData\Roaming\Typora\typora-user-images\image-20240702155538068.png)



##### 注解类集合

##### @AllArgsConstructor

@AllArgsConstructor 是 Lombok 提供的一个注解，用于自动生成一个包含所有类属性的构造方法。这个构造方法会将所有类属性作为参数，用于初始化对象的实例。通过使用 @AllArgsConstructor 注解，我们可以省去手动编写构造方法的繁琐工作，提高代码的可读性和可维护性。


使用示例：

```java
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class User {
    private String username;
    private int age;
    private String email;
    
    // 省略 getter 和 setter 方法
}

// 使用示例
public class Main {
    public static void main(String[] args) {
        // 自动生成的构造方法
        User user = new User("john_doe", 30, "john@example.com");
        
        // 输出对象属性
        System.out.println(user.getUsername()); // 输出：john_doe
        System.out.println(user.getAge());      // 输出：30
        System.out.println(user.getEmail());    // 输出：john@example.com
    }
}

```

##### 手写一个工作线程

```java
//继承runnable 重写run方法
@AllArgsConstructor
public class WorkThread implements Runnable{
    private Socket socket;
    private ServiceProvider serviceProvide;
    @Override
    public void run() {
        try {
            ObjectOutputStream oos=new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois=new ObjectInputStream(socket.getInputStream());
            //读取客户端传过来的request
            RpcRequest rpcRequest = (RpcRequest) ois.readObject();
            //反射调用服务方法获取返回值
            RpcResponse rpcResponse=getResponse(rpcRequest);
            //向客户端写入response
            oos.writeObject(rpcResponse);
            oos.flush();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    private RpcResponse getResponse(RpcRequest rpcRequest){
        //得到服务名
        String interfaceName=rpcRequest.getInterfaceName();
        //得到服务端相应服务实现类
        Object service = serviceProvide.getService(interfaceName);
        //反射调用方法
        Method method=null;
        try {
            method= service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamsType());
            Object invoke=method.invoke(service,rpcRequest.getParams());
            return RpcResponse.sussess(invoke);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            System.out.println("方法执行错误");
            return RpcResponse.fail();
        }
    }
}

```

####  part 2 引入netty框架

在之前的部分，对于客户端与服务端之间的传输，使用socket传输，效率底下，采用netty高性能网络框架进行优化。

netty和传统socket编程相比有哪些优势

- io传输由BIO ->NIO模式；底层 池化技术复用资源
- 可以自主编写 编码/解码器，序列化器等等，可拓展性和灵活性高
- 支持TCP,UDP多种传输协议；支持堵塞返回和异步返回

##### 如何解决沾包问题的？

NettyClientInitializer类，配置netty对**消息的处理机制**

- 指定编码器（将消息转为字节数组），解码器（将字节数组转为消息）

- 指定消息格式，消息长度，解决**沾包问题**
  - 什么是沾包问题？
  - netty默认底层通过TCP 进行传输，TCP**是面向流的协议**，接收方在接收到数据时无法直接得知一条消息的具体字节数，不知道数据的界限。由于TCP的流量控制机制，发生沾包或拆包，会导致接收的一个包可能会有多条消息或者不足一条消息，从而会出现接收方少读或者多读导致消息不能读完全的情况发生
  - 在发送消息时，先告诉接收方消息的长度，让接收方读取指定长度的字节，就能避免这个问题
- 指定对接收的消息的处理handler

注：这里的addLast没有先后顺序，netty通过加入的类实现的**接口**来自动识别类实现的是什么功能



执行流程：

客户端调用RpcClient.sendRequest方法 --->NettyClientInitializer-->Encoder编码 --->发送

服务端RpcServer接收--->NettyServerInitializer-->Decoder解码--->NettyRPCServerHandler ---->getResponse调用---> 返回结果

客户端接收--->NettyServerInitializer-->Decoder解码--->NettyRPCServerHandler处理结果并返回给上层

##### **1.netty传输位于网络结构模型中的哪一层？**

1. 传输层
2. Netty支持TCP和UDP等传输层协议，通过对这些协议的封装和抽象，Netty能够处理传输层的数据传输任务，如建立连接、数据传输和连接关闭等。
3. Netty的EventLoop和EventLoopGroup等组件基于Java NIO的多路复用器（Selector），实现了高效的IO事件处理机制，这在一定程度上与传输层的数据传输和事件处理机制相呼应。
4. 应用层
5. Netty提供了丰富的协议支持，如HTTP、WebSocket、SSL、Protobuf等，这些协议主要工作在应用层。Netty通过编解码器等组件，能够方便地在应用层对数据进行编解码，从而实现与应用层协议的交互。
6. Netty的ChannelPipeline和ChannelHandler等组件构成了一个灵活的事件处理链，允许开发者在应用层自定义各种事件处理逻辑，如身份验证、消息加密、业务逻辑处理等。

##### **2.讲一讲netty在你项目中的作用和执行流程？**

**作用**：引用高性能网络框架netty，实现了高效的信息传输；抽象了Java NIO底层的复杂性，提供了简单易用的API，简化了网络编程；提供各种组件方便网络数据的处理

**执行流程：**

1. 客户端发起请求
2. 客户端根据服务地址通过Netty客户端API创建一个客户端Channel，并连接到服务端的指定端口。
3. 客户端将RPC调用信息（如方法名、参数等）封装成请求消息，并通过Netty的编码器（Encoder）将请求消息序列化成字节流。
4. 客户端将序列化后的字节流通过网络发送给服务端。
5. 服务端接收请求并处理
6. 服务端通过Netty服务端API监听指定端口，等待客户端的连接请求。
7. 当接收到客户端的连接请求时，服务端通过Netty的解码器（Decoder）将接收到的字节流反序列化成请求消息。
8. 服务端根据请求消息中的方法名和参数等信息，通过反射调用本地服务实现，并将执行结果封装成响应消息。
9. 服务端通过Netty的编码器将响应消息序列化成字节流，并通过网络发送给客户端。
10. 客户端接收响应
11. 客户端接收到服务端的响应字节流后，通过Netty的解码器将字节流反序列化成响应消息。
12. 客户端根据响应消息中的结果信息，进行相应的业务处理。

##### **3.为什么会出现沾包问题？如何解决的？**

​	netty默认底层通过TCP 进行传输，TCP**是面向流的协议**，接收方在接收到数据时无法直接得知一条消息的具体字节数，不知道数据的界限。由于TCP的流量控制机制，发生沾包或拆包，会导致接收的一个包可能会有多条消息或者不足一条消息，从而会出现接收方少读或者多读导致消息不能读完全的情况发生

​	在发送消息时，先告诉接收方消息的长度，让接收方读取指定长度的字节，就能避免这个问题；项目中通过自定义的消息传输协议来实现对沾包问题的解决。

##### **4.你听过过哪些序列化方式？觉得哪种数据序列化方式最好？**

###### **Java对象序列化**

**优点**：

​	**兼容性高**，可以方便地在Java应用内部进行对象持久化和传输。

**缺点**：

​	序列化后的数据较大，速度相对较慢；不支持跨语言，仅适用于Java环境。

###### **JSON**

**优点**：

​	**可读性好**：JSON数据以文本形式存在，易于人类阅读和编写，方便调试和日志记录。**跨语言支持**：几乎所有主流编程语言都提供了JSON的解析和生成库，使得JSON成为跨语言数据交换的理想选择。

**缺点**：

​	**效率较低**：相对于二进制序列化格式（如Protobuf和Hessian），JSON的解析和序列化效率较低，特别是在处理大型数据结构时。

###### **Protobuf**

**优点**：

​	**高效**：Protobuf使用二进制编码，相比JSON和XML等文本格式，序列化后的数据更小，解析速度更快。

​	**向前向后兼容**：Protobuf支持数据结构的向前和向后兼容，可以在不破坏旧程序的情况下更新数据结构。

**缺点**：

​	**可读性差**：Protobuf序列化后的数据是二进制格式，不易于人类直接阅读。

​	**需要定义文件**：使用Protobuf需要先定义数据结构（.proto文件），然后生成序列化/反序列化的代码。

###### **Hessian**

**优点**：

​	**高效**：Hessian是一个轻量级的remoting on http工具，提供了RMI的功能，采用二进制RPC协议，序列化效率高。

​	**简单易用**：Hessian协议简单，实现起来相对容易。

**缺点**：

​	**可读性差**：Hessian序列化后的数据也是二进制格式，不易于人类直接阅读。

​	**安全性不足**：Hessian传输没有加密处理，对于安全性要求高的应用可能不适用。

​	**生态系统支持**：相对于JSON和Protobuf，Hessian的生态系统支持可能较少。



对于Rpc框架来说，使用Protobuf或者Hessian这种序列化后为二进制格式的数据，在消息传输上相比于Json，会更加高效

####  part 3 使用zookeeper作为注册中心

```
我们在调用服务时，对目标的ip地址和端口port都是写死的，默认本机地址和9999端口号
在实际场景下，服务的地址和端口会被记录到【注册中心】中。服务端上线时，在注册中心注册自己的服务与对应的地址，而客户端调用服务时，去注册中心根据服务名找到对应的服务端地址。
这里我们使用zookeeper作为注册中心。
zookeepepr是一个经典的分布式数据一致性解决方案，致力于为分布式应用提供一个高性能、高可用,且具有严格顺序访问控制能力的分布式协调存储服务。

1.高性能
zookeeper将全量数据存储在内存中，并直接服务于客户端的所有非事务请求，尤其用于以读为主的应用场景

2.高可用
zookeeper一般以集群的方式对外提供服务，一般3~5台机器就可以组成一个可用的 Zookeeper集群了，每台机器都会在内存中维护当前的服务器状态，井且每台机器之间都相互保持着通信。只要集群中超过一半的机器都能够正常工作，那么整个集群就能够正常对外服务

3.严格顺序访问
对于来自客户端的每个更新请求，Zookeeper都会分配一个全局唯一的递增编号，这个编号反应了所有事务操作的先后顺序
```



##### 关于zookeeper

笑死，叫zookeeper的原因，是用来管理hadoop(大象),Hive(蜜蜂)

###### 1.**配置管理**

在多个应用程序(或服务器)中，假如存在一些相同的配置信息，在对该配置信息进行修改时，我们需要一个一个进行修改，这样会大大增加维护的成本，不方便管理。这时如果使用一个专门放配置中心的组件，将相同的配置信息放在配置中心，需要的时候直接拉取，这样可以大大节约维护的成本, 而zookeeper即可实现配置中心的功能

###### 2.分布式锁

在多个用户访问同一台主机上的应用程序数据时，我们可以通过加锁解决并发操作的问题，但是如果有多台主机相同的应用程序要访问同一数据时，这个时候我们在一台主机上加锁是不能解决另一台主机的并发问题的，换句话说自己的锁只对自己有效并不影响别的 ，这个时候就需要分布式锁解决这类问题，我个人理解是有一把总锁对所有主机都有效。zookeeper可以实现分布式锁的功能

###### 3.集群管理

zookeeper作为注册中心，管理服务提供方的ip地址端口号url信息，并在服务消费方请求需要时发送给服务消费方



##### **1.zookeeper在项目中的角色？你为什么使用zookeeper**

1. zookeeper作为项目的注册中心，实现着服务注册，服务发现和维护服务状态的功能；
2. zookeeper具有高可用性，一致性，有着丰富的api，应用广阔，很多大数据框架都有他的身影。

##### **2.注册中心的意义？**

①服务注册与发现：注册中心实现了微服务架构中各个微服务的服务注册与发现，这是其最基础也是最重要的功能。通过注册中心，各个微服务可以将自己的地址信息（如IP地址、端口号等）注册到中心，同时也能够从中发现其他微服务的地址信息。

②动态性：在微服务架构中，服务的数量和位置可能会频繁变化。注册中心能够动态地处理这些变化，确保服务消费者能够实时获取到最新的服务提供者信息。

③增强微服务之间的去中心化在单体项目中，模块之间的依赖关系是通过内部的直接引用来实现的。而在微服务架构中，注册中心的存在使得微服务之间的依赖关系不再是直接的函数引用，而是通过注册中心来间接调用。这种方式增强了微服务之间的去中心化，提高了系统的灵活性和可扩展性。

④提升系统的可用性和容错性注册中心通常具有高可用性的设计，能够确保在部分节点故障时仍然能够正常工作。这使得整个微服务架构在面临故障时能够更加稳定地运行。

### day2 实现编解码与本地缓存



#### part1 netty实现编码器，解码器以及自定义序列化器

##### 使用自定义编解码器的优点

使用自定义编/解码器的好处？

- 将编码解码的过程进行封装，代码变得简洁易读，维护更加方便
- 在内部实现消息头的加工，解决沾包问题
- 消息头中加入messageType消息类型，对消息的读取机制有了进一步的拓展

###### 编解码器

编码器encoder的消息组成：

code[消息类型]—type[序列化方式]—length[序列化数组长度]—bytes[序列化数组]

对应decoder读顺序也是

先读code消息类型（short），然后读type(序列化方式)，之后读长度以及序列化数组，再将序列化数组反序列化，把字节流又转变为java对象

###### 序列化

**json序列化**

分为序列化和反序列化两部分

前者 ： 直接调用JSONObject.toJSONBytes，把对象转化为json格式的字节数组

后者/； 先调用JSON.parseObject转化为对应的消息类型，之后对转换后的request中的params属性逐个进行类型判断，果类型不匹配，使用`JSONObject.toJavaObject`方法将参数从`JSONObject`对象转换为正确的类型。

object序列化

#### part 2 实现本地缓存与自动更新



##### 1 实现本地缓存

以前的版本中，调用方每次调用服务，都要去注册中心zookeeper中查找地址，性能是不是很差呢？

我们可以在客户端建立一个本地缓存，缓存服务地址信息，作为优化的方案

![image-20240718150951008](C:\Users\jiangyinwen1\AppData\Roaming\Typora\typora-user-images\image-20240718150951008.png)

###### 首先是建立一个cache

几个必要的服务：addServcieToCache  replaceServiceAddress  getServcieFromCache  delete

cache用一个hashmap来构建

```
private static Map<String, List<String>> cache=new HashMap<>();
```



```
public class serviceCache {
    //key: serviceName 服务名
    //value： addressList 服务提供者列表
    private static Map<String, List<String>> cache=new HashMap<>();

    //添加服务
    public void addServcieToCache(String serviceName,String address){
        if(cache.containsKey(serviceName)){
            List<String> addressList = cache.get(serviceName);
            addressList.add(address);
            System.out.println("将name为"+serviceName+"和地址为"+address+"的服务添加到本地缓存中");
        }else {
            List<String> addressList=new ArrayList<>();
            addressList.add(address);
            cache.put(serviceName,addressList);
        }
    }
    //修改服务地址
    public void replaceServiceAddress(String serviceName,String oldAddress,String newAddress){
        if(cache.containsKey(serviceName)){
            List<String> addressList=cache.get(serviceName);
            addressList.remove(oldAddress);
            addressList.add(newAddress);
        }else {
            System.out.println("修改失败，服务不存在");
        }
    }
    //从缓存中取服务地址
    public  List<String> getServcieFromCache(String serviceName){
        if(!cache.containsKey(serviceName)) {
            return null;
        }
        List<String> a=cache.get(serviceName);
        return a;
    }
    //从缓存中删除服务地址
    public void delete(String serviceName,String address){
        List<String> addressList = cache.get(serviceName);
        addressList.remove(address);
        System.out.println("将name为"+serviceName+"和地址为"+address+"的服务从本地缓存中删除");
    }
}
```



##### 2.动态缓存更新实现



如果一个服务在注册中心中新增了一个地址，但是调用方始终能在本地缓存中读到这个服务

那么 新增的变化就永远无法感知到....

那么问题来了，Server端新上一个服务地址，Client端的本地缓存该怎么才能感知到呢



###### **通过在注册中心注册Watcher，监听注册中心的变化，实现本地缓存的动态更新**



##### 事件监听机制

**watcher概念**

- zookeeper`提供了数据的`发布/订阅`功能，多个订阅者可同时监听某一特定主题对象，当该主题对象的自身状态发生变化时例如节点内容改变、节点下的子节点列表改变等，会实时、主动通知所有订阅者
- zookeeper`采用了 `Watcher`机制实现数据的发布订阅功能。该机制在被订阅对象发生变化时会异步通知客户端，因此客户端不必在 `Watcher`注册后轮询阻塞，从而减轻了客户端压力
- watcher`机制事件上与观察者模式类似，也可看作是一种观察者模式在分布式场景下的实现方式

##### watcher架构

`watcher`实现由三个部分组成

- zookeeper`服务端
- zookeeper`客户端
- 客户端的`ZKWatchManager对象`

客户端**首先将 `Watcher`注册到服务端**，同时将 `Watcher`对象**保存到客户端的`watch`管理器中**。当`Zookeeper`服务端监听的数据状态发生变化时，服务端会**主动通知客户端**，接着客户端的 `Watch`管理器会**触发相关 `Watcher`**来回调相应处理逻辑，从而完成整体的数据 `发布/订阅`流程

![image.png](https://article-images.zsxq.com/FvwfZcafPpvm8EZ1Lg0wiAeA9dDS)

```
public class watchZK {
    // curator 提供的zookeeper客户端
    private CuratorFramework client;
    //本地缓存
    serviceCache cache;

    public watchZK(CuratorFramework client,serviceCache  cache){
        this.client=client;
        this.cache=cache;
    }

    /**
     * 监听当前节点和子节点的 更新，创建，删除
     * @param path
     */
    public void watchToUpdate(String path) throws InterruptedException {
        CuratorCache curatorCache = CuratorCache.build(client, "/");
        curatorCache.listenable().addListener(new CuratorCacheListener() {
            @Override
            public void event(Type type, ChildData childData, ChildData childData1) {
                // 第一个参数：事件类型（枚举）
                // 第二个参数：节点更新前的状态、数据
                // 第三个参数：节点更新后的状态、数据
                // 创建节点时：节点刚被创建，不存在 更新前节点 ，所以第二个参数为 null
                // 删除节点时：节点被删除，不存在 更新后节点 ，所以第三个参数为 null
                // 节点创建时没有赋予值 create /curator/app1 只创建节点，在这种情况下，更新前节点的 data 为 null，获取不到更新前节点的数据
                switch (type.name()) {
                    case "NODE_CREATED": // 监听器第一次执行时节点存在也会触发次事件
                        String[] pathList= pasrePath(childData1);
                        if(pathList.length<=2) break;
                        else {
                            String serviceName=pathList[1];
                            String address=pathList[2];
                            //将新注册的服务加入到本地缓存中
                            cache.addServcieToCache(serviceName,address);
                        }
                        break;
                    case "NODE_CHANGED": // 节点更新
                        if (childData.getData() != null) {
                            System.out.println("修改前的数据: " + new String(childData.getData()));
                        } else {
                            System.out.println("节点第一次赋值!");
                        }
                        String[] oldPathList=pasrePath(childData);
                        String[] newPathList=pasrePath(childData1);
                        cache.replaceServiceAddress(oldPathList[1],oldPathList[2],newPathList[2]);
                        System.out.println("修改后的数据: " + new String(childData1.getData()));
                        break;
                    case "NODE_DELETED": // 节点删除
                        String[] pathList_d= pasrePath(childData);
                        if(pathList_d.length<=2) break;
                        else {
                            String serviceName=pathList_d[1];
                            String address=pathList_d[2];
                            //将新注册的服务加入到本地缓存中
                            cache.delete(serviceName,address);
                        }
                        break;
                    default:
                        break;
                }
            }
        });
        //开启监听
        curatorCache.start();
    }
    //解析节点对应地址
    public String[] pasrePath(ChildData childData){
        //获取更新的节点的路径
        String path=new String(childData.getPath());
        //按照格式 ，读取
        return path.split("/");
    }
}
```

### day3 负载均衡与白名单

#### 1.负载均衡

手写实现轮询，随机以及一致性哈希

##### 一致性哈希的总结与实现

###### 1.环形hash空间

根据常用的Hash，是将key哈希到一个长为2^32的桶中，即0～2^32-1的数字空间，最后通过首尾相连，我们可以想象成一个闭合的圆。把数据通过一定的Hash算法处理后，映射到环上

![img](https://i-blog.csdnimg.cn/blog_migrate/9896479d3f507abeaae3d6be38d2b4f8.png)

将机器信息通过hash算法映射到环上，一般情况下是对机器的信息通过计算hash，然后以顺时针方向计算，将对象信息存储在相应的位置。

![img](https://i-blog.csdnimg.cn/blog_migrate/5485c3cfd8c4c6caa1b06bf780ad3ca4.png)

###### 虚拟节点

上面是Hash算法的特性，但是Hash算法缺少一个平衡性。

　　Hash算法的平衡行就是为了尽可能使分配到每个数据桶里面的节点是均衡的，一个简单的例子：我们有3个分布式服务器，在大量客户端访问时，通过Hash算法，使得他们能在每个服务器均匀的访问。所以这里引入了“虚拟节点”节点，从而保证数据节点均衡。

　　虚拟节点”就是真实节点的复制品，一个真实的节点对应多个“虚拟节点”，这样使得我们的节点能尽可能的在环形Hash空间均匀分布，这样我们再根据虚拟节点找到真实节点，从而保证每个真实节点上分配到的请求是均衡的
![img](https://i-blog.csdnimg.cn/blog_migrate/3f23deb6b9925e700c5adba39082db49.png)





实现的话主要是一个初始化，一个关于节点的增删改查。关于初始化的逻辑是

**类级别的常量和变量：**

- `VIRTUAL_NUM`：定义了每个真实节点对应的虚拟节点数量。
- `shards`：一个有序的映射（SortedMap），用于存储虚拟节点和它们的哈希值。
- `realNodes`：一个列表，用于存储所有的真实节点。
- `servers`：一个静态数组，用于模拟初始的服务器列表。

**关于init方法**

   - 这个方法接收一个服务器列表`serviceList`作为参数。
   - 对于列表中的每个服务器，它将服务器添加到真实节点列表中，并为每个服务器创建`VIRTUAL_NUM`个虚拟节点，每个虚拟节点由服务器名称和一个虚拟节点标识符组成。
   - 计算每个虚拟节点的哈希值，并将它们添加到`shards`映射中。



 **`getServer`方法：**

就是先对serverlist进行初始化，转变为虚拟节点，用一个哈希表存储，key是对应hash值，value是server名字

之后对node求哈希值， 使用哈希值获取哈希环中的子集，即大于或等于当前哈希值的所有键值对

如果 不为空，则取第一个，再根据这个key值获取value（即server名字）

   - 这个方法接收一个节点标识符`node`和一个服务器列表`serviceList`作为参数。
   - 它首先初始化服务器列表（如果尚未初始化）。
   - 计算传入节点的哈希值，并使用该哈希值来获取哈希环中的子集。
   - 如果子集为空，则取哈希环中的最后一个键；否则，取子集中的第一个键。
   - 根据找到的键从`shards`映射中获取对应的虚拟节点，并返回虚拟节点的真实节点部分。

```


public class ConsistencyHashBalance implements LoadBalance {
    // 虚拟节点的个数
    private static final int VIRTUAL_NUM = 5;
​
    // 虚拟节点分配，key是hash值，value是虚拟节点服务器名称
    private static SortedMap<Integer, String> shards = new TreeMap<Integer, String>();
​
    // 真实节点列表
    private static List<String> realNodes = new LinkedList<String>();
​
    //模拟初始服务器
    private static String[] servers =null;
​
    private static void init(List<String> serviceList) {
        for (String server :serviceList) {
            realNodes.add(server);
            System.out.println("真实节点[" + server + "] 被添加");
            for (int i = 0; i < VIRTUAL_NUM; i++) {
                String virtualNode = server + "&&VN" + i;
                int hash = getHash(virtualNode);
                shards.put(hash, virtualNode);
                System.out.println("虚拟节点[" + virtualNode + "] hash:" + hash + "，被添加");
            }
        }
    }
    /**
     * 获取被分配的节点名
     *
     * @param node
     * @return
     */
    public static String getServer(String node,List<String> serviceList) {
        init(serviceList);
        int hash = getHash(node);
        Integer key = null;
        SortedMap<Integer, String> subMap = shards.tailMap(hash);
        if (subMap.isEmpty()) {
            key = shards.lastKey();
        } else {
            key = subMap.firstKey();
        }
        String virtualNode = shards.get(key);
        return virtualNode.substring(0, virtualNode.indexOf("&&"));
    }
​
    /**
     * 添加节点
     *
     * @param node
     */
    public  void addNode(String node) {
        if (!realNodes.contains(node)) {
            realNodes.add(node);
            System.out.println("真实节点[" + node + "] 上线添加");
            for (int i = 0; i < VIRTUAL_NUM; i++) {
                String virtualNode = node + "&&VN" + i;
                int hash = getHash(virtualNode);
                shards.put(hash, virtualNode);
                System.out.println("虚拟节点[" + virtualNode + "] hash:" + hash + "，被添加");
            }
        }
    }
​
    /**
     * 删除节点
     *
     * @param node
     */
    public  void delNode(String node) {
        if (realNodes.contains(node)) {
            realNodes.remove(node);
            System.out.println("真实节点[" + node + "] 下线移除");
            for (int i = 0; i < VIRTUAL_NUM; i++) {
                String virtualNode = node + "&&VN" + i;
                int hash = getHash(virtualNode);
                shards.remove(hash);
                System.out.println("虚拟节点[" + virtualNode + "] hash:" + hash + "，被移除");
            }
        }
    }
​
    /**
     * FNV1_32_HASH算法
     */
    private static int getHash(String str) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < str.length(); i++)
            hash = (hash ^ str.charAt(i)) * p;
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        // 如果算出来的值为负数则取其绝对值
        if (hash < 0)
            hash = Math.abs(hash);
        return hash;
    }
​
    @Override
    public String balance(List<String> addressList) {
        String random= UUID.randomUUID().toString();
        return getServer(random,addressList);
    }
​
}
```



###### FNV1_32_HASH

FNV哈希算法全名为Fowler-Noll-Vo算法，

特点和用途：FNV能快速hash大量数据并保持较小的冲突率，它的高度分散使它适用于hash一些**非常相近的字符串**，比如URL，hostname，文件名，text，IP地址等

#### 2.超时重试与白名单

**为什么需要超时重试？**

一次 RPC 调用，去调用远程的一个服务，比如用户的登录操作，会先对用户的用户名以及密码进行验证，验证成功之后会获取用户的基本信息。当通过远程的用户服务来获取用户基本信息的时候，恰好网络出现了问题，比如网络突然抖了一下，导致请求失败了，而这个请求希望它能够尽可能地执行成功，那这时要怎么做呢？

当调用端发起的请求失败时，RPC 框架自身可以进行重试，再重新发送请求，通过这种方式保证系统的容错率



采用 Guava Retry  2.0.0版本

通过很多方法来设置重试机制：

retryIfException()：对所有异常进行重试 retryIfRuntimeException()：设置对指定异常进行重试 retryIfExceptionOfType()：对所有 RuntimeException 进行重试 retryIfResult()：对不符合预期的返回结果进行重试

还有五个以 withXxx 开头的方法，用来对重试策略/等待策略/阻塞策略/单次任务执行时间限制/自定义监听器进行设置，以实现更加强大的异常处理：

withRetryListener()：设置重试监听器，用来执行额外的处理工作 withWaitStrategy()：重试等待策略 withStopStrategy()：停止重试策略 withAttemptTimeLimiter：设置任务单次执行的时间限制，如果超时则抛出异常 withBlockStrategy()：设置任务阻塞策略，即可以设置当前重试完成，下次重试开始前的这段时间做什么事情

#### 重试机制存在什么问题？

如果这个服务业务逻辑不是幂等的，比如插入数据操作，那触发重试的话会不会引发问题呢？

会的。

在使用 RPC 框架的时候，要确保被调用的服务的业务逻辑是幂等的，这样才能考虑根据事件情况开启 RPC 框架的异常重试功能

所以，我们可以**设置一个白名单**，服务端在注册节点时，将幂等性的服务注册在白名单中，客户端在请求服务前，先去白名单中查看该服务是否为幂等服务，如果是的话使用重试框架进行调用

白名单可以存放在zookeeper中（充当配置中心的角色）

##### 改动方面

###### 客户端

1.建立guavaRetry类

2.在serviceCenter接口中添加checkRetry方法

3.在ZKServiceCenter中实现方法



###### 服务端

1.ZKServiceRegister中在register方法中添加逻辑：如果是可重试的服务，添加到zookeeper 的分支中

2.在客户端最上层的ClientProxy 调用服务时，添加重试机制和白名单的验证

![image-20240722154840270](C:\Users\jiangyinwen1\AppData\Roaming\Typora\typora-user-images\image-20240722154840270.png)

### day4 服务限流，降级 ，熔断

RPC 是解决分布式系统通信问题的一大利器，而分布式系统的一大特点就是高并发，所以说 RPC 也会面临**高并发**的场景。在这样的情况下，提供服务的每个服务节点就都可能由于访问量过大而引起一系列的问题，比如业务处理耗时过长、CPU 飘高、频繁 Full GC 以及服务进程直接宕机等等。但是在生产环境中，要保证服务的稳定性和高可用性，这时就需要业务进行自我保护，从而保证在高访问量、高并发的场景下，应用系统依然稳定，服务依然高可用。 我们可以将 RPC 框架拆开来分析，RPC 调用包括服务端和调用端，下面分别说明一下**服务端与调用端**分别是如何进行自我保护的。

#### 服务限流，降级

采用令牌桶算法作为本项目的限流算法

**令牌桶算法简介** 

令牌桶是指一个限流容器，容器有最大容量，每秒或每100ms产生一个令牌（具体取决于机器每秒处理的请求数），当容量中令牌数量达到最大容量时，令牌数量也不会改变了，只有当有请求过来时，使得令牌数量减少（只有获取到令牌的请求才会执行业务逻辑），才会不断生成令牌，所以令牌桶算法是一种弹性的限流算法

**令牌桶算法限流范围：** 

假设令牌桶最大容量为n，每秒产生r个令牌

平均速率：则随着时间推延，处理请求的平均速率越来越趋近于每秒处理r个请求，说明令牌桶算法可以控制平均速率

瞬时速率：如果在一瞬间有很多请求进来，此时来不及产生令牌，则在一瞬间最多只有n个请求能获取到令牌执行业务逻辑，所以令牌桶算法也可以控制瞬时速率

在这里提下漏桶，漏桶由于出水量固定，所以无法应对突然的流量爆发访问，也就是没有保证瞬时速率的功能，但是可以保证平均速率

#### 服务熔断







### 项目常见面试题整理

#### 网络传输层面

##### **1.netty传输位于网络结构模型中的哪一层？**

1. 传输层
2. Netty支持TCP和UDP等传输层协议，通过对这些协议的封装和抽象，Netty能够处理传输层的数据传输任务，如建立连接、数据传输和连接关闭等。
3. Netty的EventLoop和EventLoopGroup等组件基于Java NIO的多路复用器（Selector），实现了高效的IO事件处理机制，这在一定程度上与传输层的数据传输和事件处理机制相呼应。
4. 应用层
5. Netty提供了丰富的协议支持，如HTTP、WebSocket、SSL、Protobuf等，这些协议主要工作在应用层。Netty通过编解码器等组件，能够方便地在应用层对数据进行编解码，从而实现与应用层协议的交互。
6. Netty的ChannelPipeline和ChannelHandler等组件构成了一个灵活的事件处理链，允许开发者在应用层自定义各种事件处理逻辑，如身份验证、消息加密、业务逻辑处理等。



##### **2.讲一讲netty在你项目中的作用和执行流程？**

**作用**：引用高性能网络框架netty，实现了高效的信息传输；抽象了Java NIO底层的复杂性，提供了简单易用的API，简化了网络编程；提供各种组件方便网络数据的处理

**执行流程：**

1. 客户端发起请求
2. 客户端根据服务地址通过Netty客户端API创建一个客户端Channel，并连接到服务端的指定端口。
3. 客户端将RPC调用信息（如方法名、参数等）封装成请求消息，并通过Netty的编码器（Encoder）将请求消息序列化成字节流。
4. 客户端将序列化后的字节流通过网络发送给服务端。
5. 服务端接收请求并处理
6. 服务端通过Netty服务端API监听指定端口，等待客户端的连接请求。
7. 当接收到客户端的连接请求时，服务端通过Netty的解码器（Decoder）将接收到的字节流反序列化成请求消息。
8. 服务端根据请求消息中的方法名和参数等信息，通过反射调用本地服务实现，并将执行结果封装成响应消息。
9. 服务端通过Netty的编码器将响应消息序列化成字节流，并通过网络发送给客户端。
10. 客户端接收响应
11. 客户端接收到服务端的响应字节流后，通过Netty的解码器将字节流反序列化成响应消息。
12. 客户端根据响应消息中的结果信息，进行相应的业务处理。

##### **3.为什么会出现沾包问题？如何解决的？**

​	netty默认底层通过TCP 进行传输，TCP**是面向流的协议**，接收方在接收到数据时无法直接得知一条消息的具体字节数，不知道数据的界限。由于TCP的流量控制机制，发生沾包或拆包，会导致接收的一个包可能会有多条消息或者不足一条消息，从而会出现接收方少读或者多读导致消息不能读完全的情况发生

​	在发送消息时，先告诉接收方消息的长度，让接收方读取指定长度的字节，就能避免这个问题；项目中通过自定义的消息传输协议来实现对沾包问题的解决。

##### **4.你听过过哪些序列化方式？觉得哪种数据序列化方式最好？**

**Java对象序列化**

**优点**：

​	**兼容性高**，可以方便地在Java应用内部进行对象持久化和传输。

**缺点**：

​	序列化后的数据较大，速度相对较慢；不支持跨语言，仅适用于Java环境。

**JSON**

**优点**：

​	**可读性好**：JSON数据以文本形式存在，易于人类阅读和编写，方便调试和日志记录。**跨语言支持**：几乎所有主流编程语言都提供了JSON的解析和生成库，使得JSON成为跨语言数据交换的理想选择。

**缺点**：

​	**效率较低**：相对于二进制序列化格式（如Protobuf和Hessian），JSON的解析和序列化效率较低，特别是在处理大型数据结构时。

**Protobuf**

**优点**：

​	**高效**：Protobuf使用二进制编码，相比JSON和XML等文本格式，序列化后的数据更小，解析速度更快。

​	**向前向后兼容**：Protobuf支持数据结构的向前和向后兼容，可以在不破坏旧程序的情况下更新数据结构。

**缺点**：

​	**可读性差**：Protobuf序列化后的数据是二进制格式，不易于人类直接阅读。

​	**需要定义文件**：使用Protobuf需要先定义数据结构（.proto文件），然后生成序列化/反序列化的代码。

**Hessian**

**优点**：

​	**高效**：Hessian是一个轻量级的remoting on http工具，提供了RMI的功能，采用二进制RPC协议，序列化效率高。

​	**简单易用**：Hessian协议简单，实现起来相对容易。

**缺点**：

​	**可读性差**：Hessian序列化后的数据也是二进制格式，不易于人类直接阅读。

​	**安全性不足**：Hessian传输没有加密处理，对于安全性要求高的应用可能不适用。

​	**生态系统支持**：相对于JSON和Protobuf，Hessian的生态系统支持可能较少。



对于Rpc框架来说，使用Protobuf或者Hessian这种序列化后为二进制格式的数据，在消息传输上相比于Json，会更加高效



##### **5.netty的常见八股**

BIO,NIO；netty的构成，组件，执行流程

[【硬核】肝了一月的Netty知识点-CSDN博客](https://blog.csdn.net/qq_35190492/article/details/113174359?spm=1001.2014.3001.5506)







#### **注册中心层面**



##### **1.zookeeper在项目中的角色？你为什么使用zookeeper**

1. zookeeper作为项目的注册中心，实现着服务注册，服务发现和维护服务状态的功能；
2. zookeeper具有高可用性，一致性，有着丰富的api，应用广阔，很多大数据框架都有他的身影。

##### **2.注册中心的意义？**

①服务注册与发现：注册中心实现了微服务架构中各个微服务的服务注册与发现，这是其最基础也是最重要的功能。通过注册中心，各个微服务可以将自己的地址信息（如IP地址、端口号等）注册到中心，同时也能够从中发现其他微服务的地址信息。

②动态性：在微服务架构中，服务的数量和位置可能会频繁变化。注册中心能够动态地处理这些变化，确保服务消费者能够实时获取到最新的服务提供者信息。

③增强微服务之间的去中心化在单体项目中，模块之间的依赖关系是通过内部的直接引用来实现的。而在微服务架构中，注册中心的存在使得微服务之间的依赖关系不再是直接的函数引用，而是通过注册中心来间接调用。这种方式增强了微服务之间的去中心化，提高了系统的灵活性和可扩展性。

④提升系统的可用性和容错性注册中心通常具有高可用性的设计，能够确保在部分节点故障时仍然能够正常工作。这使得整个微服务架构在面临故障时能够更加稳定地运行。



##### **3.zookeeper的常见八股**

这里了解zookeeper的结构 和特点即可，面试一般不会问的太深入

[https://blog.csdn.net/xiaojiejie_baby/article/details/136485414?ops_request_misc=&request_id=&biz_id=102&utm_term=zookeeper%E9%9D%A2%E8%AF%95%E9%A2%98&utm_medium=distribute.pc_search_result.none-task-blog-2~all~sobaiduweb~default-2-136485414.nonecase&spm=1018.2226.3001.4187](https://blog.csdn.net/xiaojiejie_baby/article/details/136485414?ops_request_misc=&request_id=&biz_id=102&utm_term=zookeeper面试题&utm_medium=distribute.pc_search_result.none-task-blog-2~all~sobaiduweb~default-2-136485414.nonecase&spm=1018.2226.3001.4187)







#### **算法层面**



##### **1.三种负载均衡算法的比较？**

轮询法（Round Robin）

1. **原理**：轮询法将所有请求按顺序轮流分配给后端服务器，依次循环。
2. **优点**
3. 简单易实现。
4. 无状态，不保存任何信息，因此实现成本低。
5. **缺点**
6. 当后端服务器性能差异大时，无法根据服务器的负载情况进行动态调整，可能导致某些服务器负载过大或过小。
7. 如果服务器配置不一样，不适合使用轮询法。

随机法（Random）

1. **原理**：随机法将请求随机分配到各个服务器。
2. **优点**
3. 分配较为均匀，避免了轮询法可能出现的连续请求分配给同一台服务器的问题。
4. 使用简单，不需要复杂的配置。
5. **缺点**
6. 随机性可能导致某些服务器被频繁访问，而另一些服务器则相对较少，这取决于随机数的生成情况。
7. 如果服务器配置不同，随机法可能导致负载不均衡，影响整体性能。

一致性哈希法（Consistent Hashing）

1. **原理**：一致性哈希法将输入（如客户端IP地址）通过哈希函数映射到一个固定大小的环形空间（哈希环）上，每个服务器也映射到这个哈希环上。客户端的请求会根据哈希值在哈希环上顺时针查找，遇到的第一个服务器就是该请求的目标服务器。
2. **优点**
3. 当服务器数量发生变化时，只有少数键需要被重新映射到新的服务器上，这大大减少了缓存失效的数量，提高了系统的可用性。
4. 具有良好的可扩展性，可以动态地添加或删除服务器。
5. **缺点**
6. 在哈希环偏斜的情况下，大部分的缓存对象很有可能会缓存到一台服务器上，导致缓存分布极度不均匀。
7. 实现较为复杂，需要引入虚拟节点等技术来解决哈希偏斜问题。

##### **2.讲一讲一致性哈希算法？**

一致性哈希算法的原理和优化，可以参考文章

https://blog.csdn.net/zhanglu0223/article/details/100579254?spm=1001.2014.3001.5506



##### **3.限流算法有哪些？**

常见的限流算法有4种：计数器法，滑动窗口算法，漏桶算法和令牌桶算法

对于上面4种算法的详细介绍和优缺点比较可以参考

https://javaguide.cn/high-availability/limit-request.html





##### **4.令牌桶算法如何实现的？**

**令牌桶算法简介**	令牌桶是指一个限流容器，容器有最大容量，每秒或每100ms产生一个令牌（具体取决于机器每秒处理的请求数），当容量中令牌数量达到最大容量时，令牌数量也不会改变了，只有当有请求过来时，使得令牌数量减少（只有获取到令牌的请求才会执行业务逻辑），才会不断生成令牌

**令牌桶算法限流范围：**假设令牌桶最大容量为n，每秒产生r个令牌

平均速率：则随着时间推延，处理请求的平均速率越来越趋近于每秒处理r个请求，说明令牌桶算法可以控制平均速率

瞬时速率：如果在一瞬间有很多请求进来，此时来不及产生令牌，则在一瞬间最多只有n个请求能获取到令牌执行业务逻辑，所以令牌桶算法也可以控制瞬时速率





#### **各种场景题**



这方面一般是围绕着降级熔断重试 等等问题来回答，主要考察项目是否是自己做出来的，是否有对项目有过思考



##### **1.本地缓存怎么做的？能保证缓存和服务的一致性吗？**

在客户端设计一个缓存层，每次调用服务时从缓存层中获取地址，避免直接调用注册中心，优化速度和资源



可以。这里使用了zookeeper的监听机制，在服务节点上注册Watcher，当注册中心的服务地址发生改动时，Watcher会异步通知客户端的缓存层修改对应的地址，从而实现两者的一致性





##### **2.某个服务多个节点承压能力不一，怎么办？**

​	前面学习过一致性哈希算法 就会知道，在一致性哈希算法中，使用虚拟节点对真实节点进行映射，并且能通过设置虚拟节点的个数 来控制该节点接收到请求的概率。

​	所以在服务器负载能力不一致的情况下，我们可以在服务端将服务器的负载能力写入到注册中心中，客户端在进行负载均衡时会在注册中心中获取各服务器的能力，并设置对应的虚拟节点的数量，来控制流量的分发。

​	这里可以拓展一下自适应负载均衡的实现



##### **3.网络抖动导致某个节点被下线了，过一会网络好了，考虑过这个问题吗？**

当调用端发起的请求失败时，RPC 框架自身可以进行重试，再重新发送请求，通过这种方式保证系统的容错率；

项目使用Google Guava这款性能强大且轻量的框架来实现失败重试的功能；



##### **4.每个服务都进行重试吗？**

如果这个服务业务逻辑不是幂等的，比如插入数据操作，那触发重试的话会不会引发问题呢？

会的。

在使用 RPC 框架的时候，要确保被调用的服务的业务逻辑是幂等的，这样才能考虑根据事件情况开启 RPC 框架的异常重试功能

所以，我们可以**设置一个白名单**，服务端在注册节点时，将幂等性的服务注册在白名单中，客户端在请求服务前，先去白名单中查看该服务是否为幂等服务，如果是的话使用重试框架进行调用

白名单存放在zookeeper中（充当配置中心的角色）





##### **5.如果下游有一个服务的所有服务器都宕机了，该怎么做避免失败请求的大量堆积**

​	项目在客户端调用的链路头部设置了熔断器，当检测到失败次数超过阈值时，熔断器会变为关闭状态，阻止后续的请求；在一定时间后，熔断器变为半开状态，并根据之后请求的成功情况来决定是否阻止或放行请求



##### **熔断器具体实现？**







 

#### 关于反射