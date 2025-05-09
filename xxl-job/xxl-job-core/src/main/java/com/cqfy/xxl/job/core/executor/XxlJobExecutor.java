package com.cqfy.xxl.job.core.executor;

import com.cqfy.xxl.job.core.biz.AdminBiz;
import com.cqfy.xxl.job.core.biz.client.AdminBizClient;
import com.cqfy.xxl.job.core.handler.IJobHandler;
import com.cqfy.xxl.job.core.handler.annotation.XxlJob;
import com.cqfy.xxl.job.core.handler.impl.MethodJobHandler;
import com.cqfy.xxl.job.core.thread.ExecutorRegistryThread;
import com.cqfy.xxl.job.core.util.IpUtil;
import com.cqfy.xxl.job.core.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/8
 * @Description:执行器启动的入口类，其实是从子类中开始执行，但是子类会调用到父类的start方法，真正启动执行器组件
 */
public class XxlJobExecutor  {

    private static final Logger logger = LoggerFactory.getLogger(XxlJobExecutor.class);

    //下面这些成员变量都是定义在配置文件中的，而这里的属性，会在用户自己定义的XxlJobConfig配置类中被赋值成功
    //服务器的地址，也就是调度中心的地址，执行器要注册到调度中心那一端
    private String adminAddresses;
    //token令牌，这个令牌要和调度中心那一端的令牌配置成一样的，否则调度中心那端校验不通过会报错
    private String accessToken;
    //这个就是执行器的名称，注册执行器到调度中心的时候，使用的就是这个名称
    private String appname;
    //执行器的地址，这个地址在配置文件中为空，意味着使用默认地址
    //地址为：ip+port
    private String address;
    //执行器的ip地址
    private String ip;
    //端口号
    private int port;
    //执行器的日志收集地址
    private String logPath;
    //执行器日志的保留天数，一般为30天
    private int logRetentionDays;
    //下面这些方法都会在用户自己定义的XxlJobConfig类中被调用到
    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    public void setAppname(String appname) {
        this.appname = appname;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public void setIp(String ip) {
        this.ip = ip;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }
    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:执行器的组件终于启动了，这里我删去了几个组件，后续再迭代完整
     */
    public void start() throws Exception {
        //初始化admin链接路径存储集合
        //如果是在集群情况下，可能会有多个调度中心，所以，执行器要把自己分别注册到这些调度中心上
        //这里的方法就是根据用户配置的调度中心的地址，把用来远程注册的客户端AdminBizClient初始化好
        initAdminBizList(adminAddresses, accessToken);

        // fill ip port
        port = port>0?port: NetUtil.findAvailablePort(9999);
        ip = (ip!=null&&ip.trim().length()>0)?ip: IpUtil.getIp();

        // generate address
        if (address==null || address.trim().length()==0) {
            String ip_port_address = IpUtil.getIpPort(ip, port);   // registry-address：default use address to registry , otherwise use ip:port if address is null
            address = "http://{ip_port}/".replace("{ip_port}", ip_port_address);
        }
        //AdminBizClient初始化好之后，再开始向调度中心注册执行器, address为执行器的地址
        ExecutorRegistryThread.getInstance().start(appname,address);

        //启动执行器内部内嵌的服务器，该服务器是用Netty构建的，但构建的是http服务器，仍然是用http来传输消息的
        //在该方法中，会进一步把执行器注册到调度中心上
//        initEmbedServer(address, ip, port, appname, accessToken);//todo 下个版本用nettty做执行器端服务器
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:销毁执行器组件的方法
     */
    public void destroy(){
        //清空缓存jobHandler的Map
        jobHandlerRepository.clear();

    }



    //该成员变量是用来存放AdminBizClient对象的，而该对象是用来向调度中心发送注册信息的
    private static List<AdminBiz> adminBizList;


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:初始化客户端的方法，初始化的客户端是用来向调度中心发送消息的
     */
    private void initAdminBizList(String adminAddresses, String accessToken) throws Exception {
        if (adminAddresses!=null && adminAddresses.trim().length()>0) {
            //在这里判断可能有多个调度中心服务器，所以要展开遍历
            for (String address: adminAddresses.trim().split(",")) {
                if (address!=null && address.trim().length()>0) {
                    //根据服务器地址和令牌创建一个客户端
                    AdminBiz adminBiz = new AdminBizClient(address.trim(), accessToken);
                    //如果AdminBizClient对象为空，就初始化集合对象
                    if (adminBizList == null) {
                        adminBizList = new ArrayList<AdminBiz>();
                    }
                    //把创建好的客户端添加到集合中
                    adminBizList.add(adminBiz);
                }
            }
        }
    }


    public static List<AdminBiz> getAdminBizList(){
        return adminBizList;
    }



    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:存放IJobHandler对象的Map
     */
    private static ConcurrentMap<String, IJobHandler> jobHandlerRepository = new ConcurrentHashMap<String, IJobHandler>();


    public static IJobHandler loadJobHandler(String name){
        return jobHandlerRepository.get(name);
    }

    public static IJobHandler registJobHandler(String name, IJobHandler jobHandler){
        logger.info(">>>>>>>>>>> xxl-job register jobhandler success, name:{}, jobHandler:{}", name, jobHandler);
        return jobHandlerRepository.put(name, jobHandler);
    }


    /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:该方法就是用来将用户定义的bean中的每一个定时任务方法都注册到JobHandler的子类对象中的
     */
    protected void registJobHandler(XxlJob xxlJob, Object bean, Method executeMethod){
        //先判断注解是否为空，为空直接返回
        if (xxlJob == null) {
            return;
        }
        //获取注解的名称，这个名称就是用户定义的当前定时任务的名称
        String name = xxlJob.value();
        //得到bean的Class对象
        Class<?> clazz = bean.getClass();
        //获得定时任务方法的名称，其实定时任务的名称和注解名称也可以定义为相同的，这个没有限制
        String methodName = executeMethod.getName();
        //对定时任务的名称做判空处理
        if (name.trim().length() == 0) {
            throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
        }
        //从缓存JobHandler的Map中，根据定时任务的名字获取JobHandler
        if (loadJobHandler(name) != null) {
            //如果不为空，说明已经存在相同名字的定时任务了，也有了对应的JobHandler了，所以抛出异常
            throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
        }
        //设置方法可访问
        executeMethod.setAccessible(true);
        //下面声明了初始化方法和销毁方法，这里我要强调一下，为什么会有这个声明
        //因为用户毕竟定义的是IOC容器中的对象，而容器中的对象是可以由用户定义并实现初始化和销毁方法的
        //如果定时任务的注解中也写了初始化和销毁方法，就意味着这个定时任务在执行之前要先执行bean对象的初始化方法
        //在结束后要执行bean对象的销毁方法，所以这两个方法也要一起注册到JobHandler对象中
        Method initMethod = null;
        Method destroyMethod = null;
        //先判断@XxlJob注解中是否写了初始化名称
        if (xxlJob.init().trim().length() > 0) {
            try {
                //如果有则使用反射从bean对象中获得相应的初始化方法
                initMethod = clazz.getDeclaredMethod(xxlJob.init());
                //设置可访问，因为后续会根据反射调用的
                initMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        //判断有没有定义销毁的方法
        if (xxlJob.destroy().trim().length() > 0) {
            try {
                //如果有就使用反射获得
                destroyMethod = clazz.getDeclaredMethod(xxlJob.destroy());
                //设置可访问
                destroyMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
            }
        }
        //把得到的定时任务的方法对象，初始化方法对象，和销毁方法对象，以及定时任务的名字，包装一下
        //定时任务的方法对象，初始化方法对象，和销毁方法对象可以注册到MethodJobHandler中，以后调用时就由这个类的对象
        //调用，其实内部还是使用了反射。然后把定时任务的名字和MethodJobHandler对象以键值对的方式缓存在
        //jobHandlerRepository这个Map中
        registJobHandler(name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));
    }
}
