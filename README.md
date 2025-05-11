# handson-xxljob
手写xxljob框架采用多分支开发，每个分支都是可运行的程度：
- xxljob-01：论述调度中心的必要性，给出样例程序`JobScheduleHelper`
- xxljob-02：调度中心引入任务执行触发器`XxlJobTrigger`实现HTTP请求（回调）目标执行器任务，同时引入快慢双线程池避免短耗时的目标任务被阻塞
- xxljob-03：调度中心引入时间轮数据结构，和时间轮线程，精准调度任务
- xxljob-04：本节初始化了`xxljob`脚本初步搭建出admin管理界面默认账密Admin/123456，同时在调度中心测引入了`MySQL`分布式锁，以应对调度中心集群部署场景下调度的唯一性
- xxljob-05：本节给出了执行器测初始化过程，SmartInitializingSingleton收集所有的@XxlJob注解标记的Method方便后期通过反射执行。另外还给出了执行器作为客户端向调度中心注册自身xxl_job_registry信息。（注意真正的任务信息xxl_job_info是后期用户手动录入的）
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
