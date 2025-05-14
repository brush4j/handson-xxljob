其实SpringBoot所做的就是在执行器侧把用户定义的每一个定时任务包装成bean对象，然后把这些对象创建出来交给容器来管理了。

如果不使用SpringBoot，那就自己定义一个执行器，当执行器启动的时候，把用户定义的定时任务用对象包装起来不就好了？

然后把这些对象放到一个容器中，比如就放到集合中。整体的逻辑十分简单，所以，接下来我就先为执行器引入一个新的类，就是XxlJobSimpleExecutor类
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/8/2
 * @Description:不依赖SpringBoot的执行器
 */
public class XxlJobSimpleExecutor extends XxlJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSimpleExecutor.class);

	//存放定时任务对象的集合
    private List<Object> xxlJobBeanList = new ArrayList<>();
    
    public List<Object> getXxlJobBeanList() {
        return xxlJobBeanList;
    }
    public void setXxlJobBeanList(List<Object> xxlJobBeanList) {
        this.xxlJobBeanList = xxlJobBeanList;
    }


    @Override
    public void start() {

        initJobHandlerMethodRepository(xxlJobBeanList);

        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }


    private void initJobHandlerMethodRepository(List<Object> xxlJobBeanList) {
        if (xxlJobBeanList==null || xxlJobBeanList.size()==0) {
            return;
        }
        for (Object bean: xxlJobBeanList) {
            Method[] methods = bean.getClass().getDeclaredMethods();
            if (methods.length == 0) {
                continue;
            }
            for (Method executeMethod : methods) {
                XxlJob xxlJob = executeMethod.getAnnotation(XxlJob.class);
                registJobHandler(xxlJob, bean, executeMethod);
            }
        }
    }
}
```

在执行器启动的时候创建上面这个类的对象，然后调用该类的setXxlJobBeanList方法，就可以把用户创建的定时任务对象存放到该类的集合中。请看下面的代码块。

```java
public class FrameLessXxlJobConfig {
    private static Logger logger = LoggerFactory.getLogger(FrameLessXxlJobConfig.class);


    private static FrameLessXxlJobConfig instance = new FrameLessXxlJobConfig();
    public static FrameLessXxlJobConfig getInstance() {
        return instance;
    }


    //定义一个XxlJobSimpleExecutor对象
    private XxlJobSimpleExecutor xxlJobExecutor = null;

    //在这个方法中把定时任务对象放到XxlJobSimpleExecutor内部的集合中
    public void initXxlJobExecutor() {

        // load executor prop
        Properties xxlJobProp = loadProperties("xxl-job-executor.properties");

        // init executor
        xxlJobExecutor = new XxlJobSimpleExecutor();
        xxlJobExecutor.setAdminAddresses(xxlJobProp.getProperty("xxl.job.admin.addresses"));
        xxlJobExecutor.setAccessToken(xxlJobProp.getProperty("xxl.job.accessToken"));
        xxlJobExecutor.setAppname(xxlJobProp.getProperty("xxl.job.executor.appname"));
        xxlJobExecutor.setAddress(xxlJobProp.getProperty("xxl.job.executor.address"));
        xxlJobExecutor.setIp(xxlJobProp.getProperty("xxl.job.executor.ip"));
        xxlJobExecutor.setPort(Integer.valueOf(xxlJobProp.getProperty("xxl.job.executor.port")));
        xxlJobExecutor.setLogPath(xxlJobProp.getProperty("xxl.job.executor.logpath"));
        xxlJobExecutor.setLogRetentionDays(Integer.valueOf(xxlJobProp.getProperty("xxl.job.executor.logretentiondays")));

        //new SampleXxlJob就是创建了一个定时任务对象，然后把这个对象设置到集合中了
        xxlJobExecutor.setXxlJobBeanList(Arrays.asList(new SampleXxlJob()));

        // start executor
        try {
            xxlJobExecutor.start();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * destroy
     */
    public void destroyXxlJobExecutor() {
        if (xxlJobExecutor != null) {
            xxlJobExecutor.destroy();
        }
    }


    public static Properties loadProperties(String propertyFileName) {
        InputStreamReader in = null;
        try {
            ClassLoader loder = Thread.currentThread().getContextClassLoader();

            in = new InputStreamReader(loder.getResourceAsStream(propertyFileName), "UTF-8");;
            if (in != null) {
                Properties prop = new Properties();
                prop.load(in);
                return prop;
            }
        } catch (IOException e) {
            logger.error("load {} error!", propertyFileName);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("close {} error!", propertyFileName);
                }
            }
        }
        return null;
    }

}
```

在该版本的代码中，有两个测试类模块，第一个测试类模块提供的就是不依赖SpringBoot的执行器。

FrameLessXxlJobConfig已经被放在了执行器启动类中创建
```java
/**
 * @author xuxueli 2018-10-31 19:05:43
 */
public class FramelessApplication {
    private static Logger logger = LoggerFactory.getLogger(FramelessApplication.class);

    public static void main(String[] args) {

        try {
            // start
            FrameLessXxlJobConfig.getInstance().initXxlJobExecutor();

            // Blocks until interrupted
            while (true) {
                try {
                    TimeUnit.HOURS.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            // destroy
            FrameLessXxlJobConfig.getInstance().destroyXxlJobExecutor();
        }

    }

}

```


```shell
beat at:0
beat at:1
beat at:2
beat at:3
beat at:4
```