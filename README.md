# handson-xxljob
手写xxljob框架采用多分支开发，每个分支都是可运行的程度：
- xxljob-01：论述调度中心的必要性，给出样例程序`JobScheduleHelper`
- xxljob-02：调度中心引入任务执行触发器`XxlJobTrigger`实现HTTP请求（回调）目标执行器任务，同时引入快慢双线程池避免短耗时的目标任务被阻塞
- xxljob-03：调度中心引入时间轮，和时间轮线程，精准调度任务
- xxljob-04：本节初始化了`xxljob`脚本，同时在调度中心测引入了`MySQL`分布式锁，以应对调度中心集群部署场景下调度的唯一性
- 更多分支，持续更新中

main分支涵盖以上所有分支功能，全量文档见：[docs](docs)
