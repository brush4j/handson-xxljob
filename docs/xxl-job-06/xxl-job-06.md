接下来，我会为大家着重讲解执行器符合作为服务器，来接收调度中心发送过来的消息，然后根据消息的内容执行相应的定时任务

讲到这里，大家有没有感觉很熟悉？执行器的服务端和我们刚刚学习过的Netty的ChannelPipeline处理消息的流程多像呀。

以Netty构建的客户端或者是服务端，不就可以在接收到一条消息后出发消息的入站事件，然后会将这条消息传递到ChannelPipeline链表上进行流水化处理吗？ 在被回调的channelRead方法中可以进行业务的处理。

那回到我自己的构建的执行器中，我不就可以使用Netty构建一个服务器吗？既然调度中心是通过http发送的消息，那我的Netty就构建成http的服务器，所谓http服务器，其实就是使用对应的http协议的编解码器来处理接收或发送的消息。

然后用户可以自己定义一个入站的消息处理器EmbedHttpServerHandler，在该处理器中实现channelRead方法，定义自己的业务逻辑，在该方法内根据解码后得到定时任务的方法名称，去执行相应的定时任务。

所以，接下来，我就可以根据这个思路继续编写我的执行器程序了。

## 创建执行器端内嵌的Netty服务器
创建执行器端服务器的流程对XxlJobSpringExecutor和XxlJobSimpleExecutor这两种不同的执行器来说肯定都是一样的，所以，启动执行器端服务器的流程，就可以定义在父类XxlJobExecutor中了。接下来，请大家看我重构之后的代码。

XxlJobExecutor：
```java
    public void start() throws Exception {
        //初始化admin链接路径存储集合
        //如果是在集群情况下，可能会有多个调度中心，所以，执行器要把自己分别注册到这些调度中心上
        //这里的方法就是根据用户配置的调度中心的地址，把用来远程注册的客户端初始化好
        initAdminBizList(adminAddresses, accessToken);

        //启动执行器内部内嵌的服务器，该服务器是用Netty构建的，但构建的是http服务器，仍然是用http来传输消息的
        //在该方法中，会进一步把执行器注册到调度中心上
        initEmbedServer(address, ip, port, appname, accessToken);
    }
```

EmbedServer：其中执行器的任务注册到调度中心，这个工作其实是伴随着下面执行器内嵌服务器的启动，在其启动过程中就一起执行了`startRegistry(appname, address);`。但是这里我就不再详细讲解
```java
public class EmbedServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbedServer.class);


    //启动执行器的内嵌服务器，其中address参数就是执行器的IP地址，要封装到RegistryParam对象中
    //发送给调度中心的，port为Netty构建的服务器要使用的端口号
    //appname为执行器配置文件中用户设定的执行器的唯一标识，也是要封装到RegistryParam对象中
    //发送给调度中心的
    public void start(final String address, final int port, final String appname) {

        //下面都是netty的知识，学过手写netty的应该都清楚，就不再一一解释了
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    //心跳检测
                                    .addLast(new IdleStateHandler(0, 0, 30 * 3, TimeUnit.SECONDS))
                                    //http的编解码器，该处理器既是出站处理器，也是入站处理器
                                    .addLast(new HttpServerCodec())
                                    //这个处理器从名字上就能看出来，是聚合消息的，当传递的http消息过大时，会被拆分开，这里添加这个处理器
                                    //就是把拆分的消息再次聚合起来，形成一个整体再向后传递
                                    //该处理器是个入站处理器
                                    .addLast(new HttpObjectAggregator(5 * 1024 * 1024))
                                    //添加入站处理器，在该处理器中执行定时任务
                                    //现在，EmbedHttpServerHandler这个消息处理器，就要实现最核心的功能了
                                    .addLast(new EmbedHttpServerHandler());
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            //绑定端口号
            ChannelFuture future = bootstrap.bind(port).sync();
            //注册执行器到调度中心
            startRegistry(appname, address);
            //等待关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.info(">>>>>>>>>>> xxl-job remoting server stop.");
        }finally {
            try {
                //优雅释放资源
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}
```

在上面的代码块中，我为Netty服务端添加了四个消息处理器，
- 第一个处理器的作用是心跳检测，这也是Netty的知识点，就不再细说了。
- 之后的HttpServerCodec是为http服务的
- HttpObjectAggregator都是为http服务的
- 最后添加的那个EmbedHttpServerHandler处理器，才是我的服务端真正需要的消息处理器，也就是我的业务处理器，当接收到调度中心发来的消息后，定时任务该如何执行，这个核心逻辑就要在EmbedHttpServerHandler处理器中的channelRead方法中实现了。

接下来，我就要为自己的程序具体编写业务处理器EmbedHttpServerHandler的逻辑了。

## 编写业务处理器EmbedHttpServerHandler
但是在我开始编写该处理器的逻辑之前，我想请大家稍微思考一下，刚才我跟大家说，在EmbedHttpServerHandler处理器的channelRead方法中，被调度中心调度的定时任务就要开始执行了，这是不是就意味着在该方法内定时任务会被执行。

如果有很多定时任务，调度中心发送过来的消息都会传递到该处理器内，被channelRead方法处理，这就意味着该执行器部署的很多定时任务都会在该方法内执行。

如果大家对Netty足够精通的话，肯定知道channelRead方法其实还是归Netty中的单线程执行器来调度的，而单线程执行器所做的工作非常做，其中最重要的就是处理Selector上接收到的IO事件。

本来单线程执行器的任务就比较多，处理完一次IO事件后，就会紧接着处理下一个IO事件，因为调度中心会一直向执行器发送消息呀。

但是现在单线程执行器在接收到消息后，不仅要把消息传递到EmbedHttpServerHandler处理器的channelRead方法内，还要在该方法内执行对应的定时任务，这么做对单线程执行器显然是一种拖累，就让单线程执行器迅速处理IO事件就行了。

**因此，这种情况下，我们一般会创建一个业务线程池供EmbedHttpServerHandler处理器使用，定时任务也由业务线程池来执行，以此达到和单线程执行器明确分工的效果。**

所以，EmbedServer类还应该小小地重构一下，添加一个线程池，然后将这个线程池交给EmbedHttpServerHandler处理器使用。下面就是我重构好的EmbedServer类。
```java
public class EmbedServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbedServer.class);


    //启动执行器的内嵌服务器，其中address参数就是执行器的IP地址，要封装到RegistryParam对象中
    //发送给调度中心的，port为Netty构建的服务器要使用的端口号
    //appname为执行器配置文件中用户设定的执行器的唯一标识，也是要封装到RegistryParam对象中
    //发送给调度中心的
    public void start(final String address, final int port, final String appname) {
        
        //下面都是netty的知识，学过手写netty的应该都清楚，就不再一一解释了
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
    	//这就是我先创建好的业务线程池
        ThreadPoolExecutor bizThreadPool = new ThreadPoolExecutor(
                        0,
                        200,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(2000),
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                return new Thread(r, "xxl-job, EmbedServer bizThreadPool-" + r.hashCode());
                            }
                        },
                        new RejectedExecutionHandler() {
                            @Override
                            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                                throw new RuntimeException("xxl-job, EmbedServer bizThreadPool is EXHAUSTED!");
                            }
                        });

        
        try {
        	ServerBootstrap bootstrap = new ServerBootstrap();
        	bootstrap.group(bossGroup, workerGroup)
        	        .channel(NioServerSocketChannel.class)
        	        .childHandler(new ChannelInitializer<SocketChannel>() {
        	            @Override
        	            public void initChannel(SocketChannel channel) throws Exception {
        	                channel.pipeline()
        	                        //心跳检测
        	                        .addLast(new IdleStateHandler(0, 0, 30 * 3, TimeUnit.SECONDS))
        	                        //http的编解码器，该处理器既是出站处理器，也是入站处理器
        	                        .addLast(new HttpServerCodec())
        	                        //这个处理器从名字上就能看出来，是聚合消息的，当传递的http消息过大时，会被拆分开，这里添加这个处理器
        	                        //就是把拆分的消息再次聚合起来，形成一个整体再向后传递
        	                        //该处理器是个入站处理器
        	                        .addLast(new HttpObjectAggregator(5 * 1024 * 1024))
        	                        //添加入站处理器，在该处理器中执行定时任务
        	                        //把业务线程池交给EmbedHttpServerHandler处理器使用
        	                        .addLast(new EmbedHttpServerHandler(bizThreadPool));
        	            }
        	        })
        	        .childOption(ChannelOption.SO_KEEPALIVE, true);
        	//绑定端口号
        	ChannelFuture future = bootstrap.bind(port).sync();
        	//注册执行器到调度中心
        	startRegistry(appname, address);
        	//等待关闭
        	future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.info(">>>>>>>>>>> xxl-job remoting server stop.");
        }finally {
            try {
                //优雅释放资源
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}
```

EmbedServer经过重构之后，就目前的功能来说，似乎已经很完整了，现在最核心的逻辑就来到了EmbedHttpServerHandler这个消息处理器中了。所以，接下来，就应该实现这个处理器中的channelRead方法，让业务线程池真正去执行被调度中心调度的定时任务。

这一块的逻辑就没什么好分析了，因为逻辑已经到了最底层了，我就直接为大家实现EmbedHttpServerHandler这个处理器即可。下面，请看我实现的EmbedHttpServerHandler处理器。
```java
public static class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        //bizThreadPool会赋值给该属性
        private ThreadPoolExecutor bizThreadPool;

        //构造方法
        public EmbedHttpServerHandler(ThreadPoolExecutor bizThreadPool) {
            this.bizThreadPool = bizThreadPool;
        }


   	    //入站方法，在该方法中，进行定时任务的调用
        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            //获得发送过来的消息，这里要强调一下，这个requestData，其实就是
            //调度中心发送过来的TriggerParam对象，该对象中可是封装着要执行的定时任务
            //的方法名称
            String requestData = msg.content().toString(CharsetUtil.UTF_8);
            //发送请求的方法
            HttpMethod httpMethod = msg.method();
            //在判断http连接是否还存活，也就是是否还活跃的意思
            boolean keepAlive = HttpUtil.isKeepAlive(msg);
            //上面Netty的单线程执行器为我们解析了消息，下面的工作就该交给用户定义的工作线程来执行吧
            //否则会拖累Netty的单线程执行器处理IO事件的效率
            bizThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    //在下面的这个方法中调度中心触发的定时任务，得到返回结果
                    //现在，process方法就成了最核心的方法了
                    Object responseObj = process(httpMethod,requestData);
                    //序列化
                    String responseJson = GsonTool.toJson(responseObj);
                    //把消息回复给调度中心，注意，这里的回复消息的动作，是业务线程发起的
                    //但是学完手写netty的各位都知道，真正发送消息还是由单线程执行器来完成的
                    writeResponse(ctx, keepAlive, responseJson);
                }
            });
        }


    //该方法暂时先不实现
    private Object process(HttpMethod httpMethod, String requestData) {
        
    }
}
```
在上面的代码块中，我实现的是channelRead0方法，但是学过Netty的诸位，一定也明白这里只是运用了模版方法设计模式，实际上先调用的是channelRead方法，然后在该方法内调用到channelRead0方法。

但是在channelRead0方法中，我并没有真的开始让执行器这边去执行定时任务，而是先获得了通过http协议发送过来的消息，并且对http连接的状态做了一些判断，然后又调用了process方法，打算把真正执行定时任务的核心逻辑定义在这个方法中。

注意，process方法已经是bizThreadPool业务线程池在执行了，这说明现在定时任务已经交给业务线程池来执行了。

可以从requestData中获得要执行的定时任务的方法名称，然后直接让线程池执行定时任务就行了。

接下来，就看我自己实现的process方法。从jobHandlerRepository中获取对应的MethodJobHandler来反射执行目标任务
```java
public static class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    
    //其他方法和属性就省略了


    //process方法
	//在该方法中要执行定时任务了
    private Object process(HttpMethod httpMethod, String uri, String requestData) {
        //判断是不是post方法，因为调度中心发送消息时就是使用的post请求发送的
        if (HttpMethod.POST != httpMethod) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
        }
        try {
            //把requestData转化成触发器参数对象，也就是TriggerParam对象
            TriggerParam triggerParam = GsonTool.fromJson(requestData, TriggerParam.class);
            String jobName = triggerParam.getExecutorHandler();
            //得到了定时任务的方法名称，就可以从上一章定义的Map中获得对应的MethodJobHandler对象
            //然后就可以使用反射执行定时任务了，在这里就可以直接调用XxlJobExecutor
            //中的loadJobHandler方法获得定时任务名称对应的MethodJobHandler对象
            MethodJobHandler jobHandler = XxlJobExecutor.loadJobHandler(jobName);
            //通过反射执行定时任务
            jobHandler.execute();
            //返回给调度中心一个成功的响应结果
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<String>(ReturnT.FAIL_CODE, "request error:" + ThrowableUtil.toString(e));
        }
    }
}
```
## 线程池很快被耗尽！！！
到此为止，我的分布式定时任务调度框架的执行器模块似乎就终于完全实现了。定时任务也执行了，并且返回给调度中心一个结果，而且我还考虑到了Netty的单线程执行器的性能问题，为了不使它的工作受影响，我还特意创建了一个业务线程池专门执行调度中心调度的定时任务。业务线程池的最大线程数定义为200，我相信，这么多可以复用的线程足够处理巨量的定时任务了。

一切看起来似乎都很完美，但危机总是在你最放松的时候来临。所以，请大家再仔细想想，我让业务线程池直接去执行定时任务真的没有问题吗？
- 假设现在业务线程池的最大线程数为200，而我在执行器这一端部署的定时任务也是200个，这样调度中心就会得到我这200个定时任务的信息，并且根据规定好的执行时间来调度这些任务。考虑一种最极端的情况，就算这200个定时任务要同时被调用，那现在的200个最大线程，每个线程执行一个定时任务也绰绰有余了。但是，请大家再考虑另外一种情况，如果这200个定时任务中有一个任务执行起来特别耗时呢？或者说忽然出问题了，执行这个任务的时候会访问数据库，然后让线程阻塞住了。这也是很有可能的对吧，没关系，就算那个线程一直阻塞者，我们还剩下199个线程可以用，执行定时任务还不是跟玩一样。但如果现在有10个定时任务让线程阻塞了呢？那还剩下180个线程，是的，的确是这样，没错。但是，现在请大家再考虑一种情况，调度中心维护的定时任务并不会只执行一次呀，如果这10个会造成线程阻塞的定时任务每5秒就执行一次呢？其他的190个任务并不会造成线程阻塞，这么一来，这10个会阻塞的线程的定时任务一开始会占用10个线程，当这10个任务还没有执行完，5秒过去了，现在要开始第二次执行这10个定时任务了，于是又会占用10个线程，如果次数多了，很有可能出现一种情况，就是线程池中大面积的线程都会被这10个执行耗时的定时任务占用，从而拖累其他任务的执行。如果情况更加极端的话，定时任务在一定时间内被调用多次，很可能所有线程都被这10个定时任务阻塞了，那执行器还怎么工作呀？这里我想跟大家强调一点，在任何框架中，多线程的引入或者线程池的引入，都是为了解决性能问题的。如果不是为了提高性能，提高工作效率，何必冒着出现并发问题的风险使用多线程呢？但在我目前完成的执行器中，直接把定时任务提交给线程池执行，显然对工作性能来说是一种拖累。
- 并且，还应该再考虑一种情况，那就是某个定时任务被调度后，这一次执行遇到一些状况，比较耗时，在还没执行完的时候该任务就被第二次调度了，这样业务线程池就要分配新的线程来执行该定时任务。但是，这一次却突然不耗时，反而是第二次被调度的定时任务先于第一次被调度的定时任务执行完，然后第一次被调度的定时任务才执行完。如果这个定时任务这两次调度对某些数据做了改变，本来应该按顺序修改，现在任务执行完成的顺序颠倒了，很可能就会出现数据错乱的并发问题。

可见，我的代码编写得很有问题。那么，该如何解决这个问题呢？

请大家想一想，一个定时任务只要被调度中心来维护了，记录在数据库中，这个定时任务就肯定拥有一个唯一的主键ID，并且对于同一个执行器来说，这个定时任务还拥有一个唯一的方法名称。

既然定时任务执行起来有可能会十分耗时，也有可能会阻塞线程，那么，我就只让一个线程一直执行这一个定时任务不就好了？这样一来，阻塞也只阻塞这一个线程。

举个简单例子，假如现在有5个定时任务，编号为1到5，调度中心调度了这些定时任务之后，在执行器这一端会创建5个新的线程，编号也为1到5，五个线程和五个定时任务一一对应。

如果后续1号定时任务再次被调用了，那就仍然让1号线程来执行该定时任务，如果1号定时任务执行比较耗时，还没被1号线程执行完，1号定时任务就再次被调度中心调度了，那么就可以在1号线程内部创建一个任务队列，把后续被调度的1号任务信息暂时存储在任务队列中，等待1号线程陆续调度。

这样一来，线程大面积阻塞和并发问题不就都解决了吗？现在对我的程序而言，最关键的问题就是如何让一个线程和一个定时任务一一对应。

其实很简单，上面已经为大家分析了，既然每个定时任务都有一个唯一的主键ID，那么就让调度中心把定时任务信息发送过来的时候，把定时任务的主键ID也发送过来。这样，就可以在执行器这一端定义一个Map，这个Map存储的就是定时任务的主键ID和其对应的要执行它的线程。

请看下面几个代码块，是我为自己的程序新添加的内容。首先是TriggerParam要重构一下，把定时任务的主键ID添加进来。
```java
public class TriggerParam implements Serializable{
    private static final long serialVersionUID = 42L;

    //定时任务主键id
    private int jobId;

    //定时任务方法的名字
    private String executorHandler;

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getExecutorHandler() {
        return executorHandler;
    }

    public void setExecutorHandler(String executorHandler) {
        this.executorHandler = executorHandler;
    }

}
```

接下来就是我最新定义的用来和定时任务ID一一对应，然后执行定时任务的线程。
```java
public class JobThread extends Thread{
    //定时任务的ID
	private int jobId;
    
    private MethodJobHandler handler;
    
	//存放触发器参数的一个队列，也就是我们上面刚刚解释过的，有可能一个任务比较耗时，3秒还没执行完，但调度周期是2秒，
	//那么之后每2秒要执行的这个定时任务可能就会放在JobThread对象中的队列中等待执行，其实存放的就是触发器参数，触发器参数中有待执行的定时任务的名称
	private LinkedBlockingQueue<TriggerParam> triggerQueue;

    //线程终止的标记
	private volatile boolean toStop = false;


    //构造方法
    public JobThread(int jobId, MethodJobHandler handler) {
		this.jobId = jobId;
		this.handler = handler;
		//初始化队列
		this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
		//设置工作线程名称
		this.setName("xxl-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
	}

    
    //把触发器参数放进队列中的方法
	public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
		//在这里放进队列中
		triggerQueue.add(triggerParam);
		//返回成功结果
        return ReturnT.SUCCESS;
	}


 	//当前线程启动之后会执行的run方法，这个方法简化到极致了
    //在我手写的第一版本代码中，逻辑要复杂得多
    @Override
	public void run() {
        while(!toStop){
            //先声明一个触发器参数变量
            TriggerParam triggerParam = null;
            //从触发器参数队列中取出一个触发器参数对象
			//这里是限时的阻塞获取，如果超过3秒没获取到，就不阻塞了
			triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
            //如果触发参数不为null，说明调度中心调度了定时任务，执行器这边就要执行
            //因为所有被调度的定时任务的信息，都会先放进定时任务内部的任务队列中
            //然后让线程从队列中判断有没有任务信息，下面大家就会看到具体的逻辑了
            if (triggerParam!=null) {
			    //通过反射执行了定时任务，终于在这里执行了
			    handler.execute();
            }
        }
    }
    
}
```

然后就是要定义的那个缓存定时任务ID与对应执行线程的Map。
```java
private static ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<Integer, JobThread>();
```

现在一切都已经就绪了，就等着确定jobThreadRepository这个属性究竟该定义在哪个类中，然后就可以启动我的执行器程序了。这里我就不再给大家卖关子了，直接就挑明了吧。其实这个jobThreadRepository和之前我们定义的另个缓存定时任务方法名称和MethodJobHandler对象的Map一样，都定义在XxlJobExecutor这个类中。下面，我就为大家展示一下XxlJobExecutor类中的部分代码。
```java
public class XxlJobExecutor  {

    //其他方法和属性暂时省略


    //缓存JobThread的Map，而每一个定时任务对应着一个ID，也就对应着一个执行这个定时任务的线程
    //这个Map中，key就是定时任务的ID，value就是执行它的线程
    private static ConcurrentMap<Integer, JobThread> jobThreadRepository = new ConcurrentHashMap<Integer, JobThread>();

    //把定时任务对应的JobThread缓存到jobThreadRepository这个Map中
    public static JobThread registJobThread(int jobId, MethodJobHandler handler){
        //根据定时任务ID和封装定时任务方法的IJobHandler对象创建JobThread对象
        JobThread newJobThread = new JobThread(jobId, handler);
        //创建之后就启动线程
        newJobThread.start();
        //将该线程缓存到Map中
        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);
        return newJobThread;
    }


    //根据定时任务ID，获取对应的JobThread对象
    public static JobThread loadJobThread(int jobId){
        return jobThreadRepository.get(jobId);
    }
}
```

现在，一切都准备就绪了，我就可以再次重构一下EmbedHttpServerHandler类的process方法，因为定时任务就是在这个方法中执行的。然后就可以启动我的定时任务的执行器了。请大家看下面的代码。
```java
public static class EmbedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    
    //其他方法和属性就省略了


    //process方法
	//在该方法中要执行定时任务了
    private Object process(HttpMethod httpMethod, String uri, String requestData) {
        //判断是不是post方法，因为调度中心发送消息时就是使用的post请求发送的
        if (HttpMethod.POST != httpMethod) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
        }
        try {
            //把requestData转化成触发器参数对象，也就是TriggerParam对象
            TriggerParam triggerParam = GsonTool.fromJson(requestData, TriggerParam.class);
            
            //通过定时任务的ID从jobThreadRepository这个Map中获取一个具体的用来执行定时任务的线程
            JobThread jobThread = XxlJobExecutor.loadJobThread(triggerParam.getJobId());
            //判断执行器这一端有没有为这个定时任务创建对应线程
            if (jobThread!=null){
                //走到这里说明已经为该定时任务创建了对应线程
                //直接把要执行的定时任务信息放到该线程内部的队列中，让线程自己去执行即可
                //注意，这个时候得到的线程就是已经启动了的线程
                 ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
            }
            
            //走到这里说明该定时任务是第一次被调度中心调度，执行器这一端还没有为其创建对应的线程
            //所以接下来就要创建对应的工作线程，当然，首先应该得到该定时任务对应的MethodJobHandler对象
            String jobName = triggerParam.getExecutorHandler();
            //得到了定时任务的方法名称，就可以从上一章定义的Map中获得对应的MethodJobHandler对象
		    //在这里就可以直接调用XxlJobExecutor
            //中的loadJobHandler方法获得定时任务名称对应的MethodJobHandler对象
            MethodJobHandler jobHandler = XxlJobExecutor.loadJobHandler(jobName);
            //然后就可以创建JobThread对象了，并且把该对象和定时任务的ID，以键值对的形式缓存到Map中
            //在registJobThread方法内部，会创建出JobThread对象，然后启动该线程，最后把定时任务ID
            //和该线程缓存到jobThreadRepository这个Map中
            jobThread = XxlJobExecutor.registJobThread(triggerParam.getJobId(), jobHandler);
            //最后把定时任务信息放到线程内部的任务队列中，等待线程执行即可
            return jobThread.pushTriggerQueue(triggerParam);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<String>(ReturnT.FAIL_CODE, "request error:" + ThrowableUtil.toString(e));
        }
    }
}
```
到此为止，我的执行器这一端的代码终于就重构结束了，并且之前所有遇到的问题，可能出现的情况，都被我一一解决了。

最终执行器这一端执行定时任务的模式就成了这样：执行器内嵌的Netty服务器接收调度中心发送过来的要执行的定时任务的信息，然后出发消息的入站事件，会被解码后的消息传递到EmbedHttpServerHandler这个消息处理器中，被这个消息处理器的channelRead0方法处理。

为了避免单线程执行器的工作效率受到影响，在该方法中，会使用我实现定义好的业务线程池来执行定时任务，但并不是让业务线程池直接执行定时任务。

在业务线程池中，会给每一个要执行的定时任务创建对应的线程，这个线程就用来一直执行这个定时任务。等后续的调度中心再次调度相同的定时任务时，在执行器这一端仍然是相同的线程来执行这个定时任务。

总之，每个定时任务都会在执行器端对应一个唯一的线程。不管该定时任务被调度几次，总是与其对应的唯一线程来执行它。当然，这样也有弊端，那就是定时任务本次调用超时了或者阻塞了，后续对该定时任务的调度都会延时。

其实这也称不上是弊端，至少解决了并发问题。再说，还可以设置某些超时策略，来处理这种情况。但这是后面课程要讲解的内容了，之后会详细讲解。

## 收尾工作
也许有的朋友会觉得的，既然都创建了业务线程池了，还要在业务线程池中继续创建很多新的线程，这么做不会给服务器造成很大的压力吗？线程总是频繁地切换，对工作性能也是会有影响的。

不过也可以换个角度思考问题，如果服务器本身部署的项目不是很多，那在一个项目中创建多个线程，岂不是可以充分压榨CPU的性能了吗？

当然，如果有办法限制创建线程的数量，或者说的直接一点，如果能及时终止一些线程，这样服务端的压力就会少很多吧？

现在，只要是执行定时任务，执行器端就要为其创建对应的线程，这个线程会在一个无尽的while循环中一直工作，那什么时候才可以终止呢？
```java
public class JobThread extends Thread{
    //定时任务的ID
	private int jobId;
    
    private MethodJobHandler handler;
    
	//存放触发器参数的一个队列，也就是我们上面刚刚解释过的，有可能一个任务比较耗时，3秒还没执行完，但调度周期是2秒，
	//那么之后每2秒要执行的这个定时任务可能就会放在JobThread对象中的队列中等待执行，其实存放的就是触发器参数，触发器参数中有待执行的定时任务的名称
	private LinkedBlockingQueue<TriggerParam> triggerQueue;

    //线程终止的标记
	private volatile boolean toStop = false;


    //构造方法
    public JobThread(int jobId, MethodJobHandler handler) {
		this.jobId = jobId;
		this.handler = handler;
		//初始化队列
		this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
		//设置工作线程名称
		this.setName("xxl-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
	}

    
    //把触发器参数放进队列中的方法
	public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
		//在这里放进队列中
		triggerQueue.add(triggerParam);
		//返回成功结果
        return ReturnT.SUCCESS;
	}


 	//当前线程启动之后会执行的run方法，这个方法简化到极致了
    //在我手写的第一版本代码中，逻辑要复杂得多
    @Override
	public void run() {
        while(!toStop){
            //先声明一个触发器参数变量
            TriggerParam triggerParam = null;
            //从触发器参数队列中取出一个触发器参数对象
			//这里是限时的阻塞获取，如果超过3秒没获取到，就不阻塞了
			triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
            //如果触发参数不为null，说明调度中心调度了定时任务，执行器这边就要执行
            //因为所有被调度的定时任务的信息，都会先放进定时任务内部的任务队列中
            //然后让线程从队列中判断有没有任务信息，下面大家就会看到具体的逻辑了
            if (triggerParam!=null) {
			    //通过反射执行了定时任务，终于在这里执行了
			    handler.execute();
            }
        }
    }
    
}
```

比如说有一个定时任务，每一天才调度一次，第一次执行的时候执行器这一端肯定会为其创建一个线程，执行完定时任务后，难道该线程也要一直在while循环中工作吗？下一次调用可是要等到一天之后呢。其实这时候，这个线程就可以终止了，等第二天再调度的时候，再次创建对应的线程即可。

所以，就应该设定一个新的变量，用来记录线程的空闲时间或者说是空闲次数，只要线程循环了30次，并且每次都什么也没干，就可以选择终止该线程了。就像下面代码块中写的这样。
```java
public class JobThread extends Thread{
    //定时任务的ID
	private int jobId;
    
    private MethodJobHandler handler;
    
	//存放触发器参数的一个队列，也就是我们上面刚刚解释过的，有可能一个任务比较耗时，3秒还没执行完，但调度周期是2秒，
	//那么之后每2秒要执行的这个定时任务可能就会放在JobThread对象中的队列中等待执行，其实存放的就是触发器参数，触发器参数中有待执行的定时任务的名称
	private LinkedBlockingQueue<TriggerParam> triggerQueue;

    //线程终止的标记
	private volatile boolean toStop = false;

    //该线程的空闲时间，默认为0
	private int idleTimes = 0;


    //构造方法
    public JobThread(int jobId, MethodJobHandler handler) {
		this.jobId = jobId;
		this.handler = handler;
		//初始化队列
		this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
		//设置工作线程名称
		this.setName("xxl-job, JobThread-"+jobId+"-"+System.currentTimeMillis());
	}

    
    //把触发器参数放进队列中的方法
	public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
		//在这里放进队列中
		triggerQueue.add(triggerParam);
		//返回成功结果
        return ReturnT.SUCCESS;
	}


 	//当前线程启动之后会执行的run方法，这个方法简化到极致了
    //在我手写的第一版本代码中，逻辑要复杂得多
    @Override
	public void run() {
        while(!toStop){
            //先给空闲次数加1
            idleTimes++;
            //先声明一个触发器参数变量
            TriggerParam triggerParam = null;
            //从触发器参数队列中取出一个触发器参数对象
			//这里是限时的阻塞获取，如果超过3秒没获取到，就不阻塞了
			triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
            //如果触发参数不为null，说明调度中心调度了定时任务，执行器这边就要执行
            //因为所有被调度的定时任务的信息，都会先放进定时任务内部的任务队列中
            //然后让线程从队列中判断有没有任务信息，下面大家就会看到具体的逻辑了
            if (triggerParam!=null) {
                //如果有定时任务要执行，空闲时间也可以置为0了
                //意味着可以重新开始计数
				idleTimes = 0;
			    //通过反射执行了定时任务，终于在这里执行了
			    handler.execute();
            }else {
				//走到这里说明触发器队列中没有数据，也就意味着没有要执行的定时任务
				//如果线程的空闲时间大于30次，这里指的是循环的次数，每循环一次空闲时间就自增1，
				//有定时任务被执行空闲时间就清零，不可能没任务线程空转，太浪费资源了
				if (idleTimes > 30) {
					//而且触发器队列也没有数据
					if(triggerQueue.size() == 0) {
						//这里就写伪代码了，手写的第一版代码中写得很详细
                        System.out.println("该线程要终止运行了！");
					}
				}
			}
        }
    }
    
}
```
因为源码中定义的空转次数为30次，所以我这里也就直接写成30次了，也许有的朋友会觉得30次会不会太少了。

如果一个定时任务每5秒调度一次(大于阻塞队列获取超时时间3秒)，可能下一次调度的时候，执行器这一端与其对应的线程早就被终止运行了，还要重新创建新的线程。

这里我想对大家说一句，不要用我们自己对时间的感受去代入到CPU，在我们看来1秒非常短暂，但是对CPU来说，1秒可能非常漫长，能做的事情太多太多了。所以，我相信xxl大神设置的这个值应该是很合理的。

不管怎么说吧，调度中心和执行器这两个模块的核心知识都已经讲解完了，这些知识足够大家体会XXL-JOB分布式定时任务框架的核心执行流程了，已经比较完整了。

到此为止相当于为大家搭建了一个完整的xxljob骨架，后续会向里面填充更多的内容，使它尽可能丰满起来。



## 测试方式
启动admin服务

启动sample服务

会发现执行器地址已经注册到执行器注册表`xxl_job_registry`中了
```sql
id;registry_group;registry_key;registry_value;update_time
1;EXECUTOR;xxl-job-executor-sample;http://:9999/;2025-05-09 14:07:27
5;EXECUTOR;xxl-job-executor-sample;http://10.77.182.251:9999/;2025-05-12 10:49:13

```

因为本节调度中心暂未处理执行器发送的心跳（xxljob源码是调度中心在registryMonitorThread线程中剔除dead的执行器并自动将xxl_job_registry中的地址拼接到xxl_job_group），所以无法自动的填充执行器组xxl_job_group表中的地址，

需要我们手动将执行器表`xxl_job_group`的地址字段补充为执行器注册表`xxl_job_registry`中的地址：http://你的测试执行器本地ip:9999/
```sql
id;app_name;title;address_type;address_list;update_time
1;xxl-job-executor-sample;通用执行器Sample;0;http://10.77.182.251:9999/;2025-05-08 08:51:10
2;xxl-job-executor-sample-ai;AI执行器Sample;0;\N;2025-05-08 08:51:10
```

在admin管理页面手动执行一次定时任务，触发的时候用的xxl_job_group中的数据

测试结果在执行器侧打印如下
```shell
第0次
第1次
第2次
第3次
第4次
下一次任务开始了！
```
