# handson-xxljob
手写xxljob框架采用多分支开发，每个分支都是可运行的程度：
- xxljob-01：论述调度中心的必要性，给出样例程序`JobScheduleHelper`
- xxljob-02：调度中心引入任务执行触发器`XxlJobTrigger`实现`HTTP`请求（回调）目标执行器任务，同时引入快慢双线程池避免短耗时的目标任务被阻塞
- xxljob-03：调度中心引入时间轮数据结构，和时间轮线程，精准调度任务
- xxljob-04：本节初始化了`xxljob`脚本初步搭建出admin管理界面默认账密`Admin/123456`，同时在调度中心测引入了`MySQL`分布式锁，以应对调度中心集群部署场景下调度的唯一性
- xxljob-05：本节给出了执行器测初始化过程，`SmartInitializingSingleton`收集所有的`@XxlJob`注解标记的`Method`方便后期通过反射执行。另外还给出了执行器作为客户端向调度中心注册自身`xxl_job_registry`信息。（注意真正的任务信息`xxl_job_info`是后期用户手动录入的）
- xxljob-06：本节给出了执行器作为服务端的程序，内嵌了Netty用于接收调度中心的Trigger。同时引入了业务线程池消费channelRead0，避免了Netty单线程IO阻塞，职责分明。最终还为每个定时任务创建独立的线程和阻塞队列，解决了长耗时但高频的任务耗尽业务线程池资源的问题
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
