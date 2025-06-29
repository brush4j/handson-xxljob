从一个最简单的例子讲起，比如说，现在我为自己设计了一个购物软件，每一个用户在这个软件上购买了产品，都会有对应的入账记录。

同时我还为自己设计了一个定时任务程序，每天晚上的10点25分，定时任务程序就会为我把今天的每笔营业额都加起来，得到今天总的营业额，然后我就可以拿着这个营业额给老婆汇报了。

## 高可用的大背景

就这样，我把这个定时任务程序部署在一台服务器上了。一开始似乎还没出什么差错，一切都很正常。但是我使用的部署程序的服务器质量不太行，用的时间长了就会偶尔罢工。 忙活了一天竟然连总的营业额也看不到，次数多了我老婆难免怀疑我在搞鬼。

为了打消老婆的疑虑，最终我决定在另一个服务器上再部署一个功能一模一样的定时任务程序。这样，每天就有两个定时任务程序到点执行，如果有一个罢工了，另一个正常运行，我和我老婆照样能得到一天的营业总额。

同时身为程序员的我动起了歪心思。我决定今后每天计算总的营业额的时候，在自己设计的定时任务中添加一点业务逻辑，当单笔营业额的收入超过50元，我就将这笔营业额减去10元给自己用，然后更新数据库的数据。

这样每天我都能攒一点自己的小金库，如果老婆想亲自对账，反正数据库中的数据也变了，我应该会安然无恙的。

## 引入并发问题

可是，现实总是残酷的。想一想，现在我在两个服务器上部署了两个逻辑相同的定时任务程序。这样一来，定时任务程序每天肯定会执行两次。如果第一个定时任务执行了，在我的超过50的单比营业额上减去了10快，如果这单比营业额是50多还好，减10块就剩40多了，达不到第二次减10块的标准。可如果单比营业额超过了60，这个营业额就会被两次定时任务程序减去20。一下子少了这么多钱，我也挺害怕我媳妇查出真相后狠狠地抽我啊。

为了不让这种情况发生，我决定认真发挥我程序员的本领，将定时任务程序的逻辑改造一下。虽然在两个服务实例上分别部署了定时任务程序，在经过我的重构后，我希望这两个定时任务程序在特定的时间只能触发一个，另一个暂不执行。如果其中一个定时任务程序无法正常执行，另一个还可以兜底。下面的代码是我最开始的定时任务程序(注意，下面都是一些伪代码，大家明白意思即可)。

```java
public class ScheduledTask{
  
     public static void main(String[] args) throws InterruptedException {
       while (true) {
            System.out.println("执行定时任务！");
            Thread.sleep(睡到下一天晚上);
        }
    }
}
```

接下来是经过我重构后的定时任务程序。

```java
public class ScheduledTask{
  
     public static void main(String[] args) throws InterruptedException {
       while (true) {
           //抢夺分布式锁，抢到则返回1，失败则返回0
           int lock = getDistributedLock();
            if (lock == 1) {
                //走到这里说明抢锁成功，直接执行定时任务即可
                System.out.println("执行定时任务！");
                //释放分布式锁
                freeDistributedLock();
            }
           //抢到锁或没抢到锁，都要睡到第二天晚上
            Thread.sleep(睡到下一天晚上);
        }
    }
}
```

可以看到，在上面重构过后的代码块中，我只是简单使用了一个分布式锁，这样，获得分布式锁的定时任务就可以执行，没有得到分布式锁的定时任务就执行不了。

还有我注意到程序中的 Thread.sleep(睡到下一天晚上) 这行代码，写得太不漂亮了，明明有更高级的组件供我使用，而我却写出了这样一行代码。既然我的程序是每天晚上执行，我就打算使用cron表达式来为我的程序工作。请看下面经过重构后的代码。

```java
public class ScheduledTask{

     //定义一个cron表达式，在这里我把执行时间改为了每天的晚上22点
     public String scheduleCron = 0 0 22 * * ?;
  
     public static void main(String[] args) throws InterruptedException {
       while (true) {
           //抢夺分布式锁，抢到则返回1，失败则返回0
           int lock = getDistributedLock();
            if (lock == 1) {
                //走到这里说明抢锁成功，直接执行定时任务即可
                System.out.println("执行定时任务！");
                //释放分布式锁
                freeDistributedLock();
            }
            //这里就是根据配置的cron表达式，得到了以当前时间为起点，定时任务下一次执行的时间
            Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
            //这里就是用定时任务下一次执行的时间减去当前的时间，得到了线程要睡的时间
           long time = nextTime.getTime() - System.currentTimeMillis();
           //抢到锁或没抢到锁，都要睡到第二天晚上
           //睡完之后，再进入下一次循环，重新抢夺分布式锁
            Thread.sleep(time);
        }
    }
}
```

本来我以为定时任务程序可以就此完美无缺地执行下去了，但很快，我就在自己的程序中意识到了两个缺陷。 大家请看：

- 现在的程序是在一个循环中进行的，进入循环后首先判断是不是抢到了分布式锁，如果抢到了分布式锁就执行定时任务，然后再释放锁，最后计算下一次任务的执行时间，得到线程沉睡的时间差，线程直接睡到下一天晚上的10点，继续下一次循环。
- 如果没抢到分布式锁，就直接计算定时任务下一次的执行时间，然后线程睡过去。

在下面这行代码中，new Date()得到的是程序的当前时间，会根据我配置的cron表达式计算以当前时间为起点，定时任务下一次的执行时间，并且是每个定时任务程序计算自己的下一次执行时间。在这里2号定时任务程序就直接计算出了下一次要执行的时间，得到了一个时间差，然后线程睡过去了。而1号定时任务执行程序执行完了任务，可能定时任务有些耗时，或者访问数据库的时候阻塞了，但是我为程序配置的cron表达式是唯一的，所以，不管怎么执行，1号定时任务程序最终也会计算出和2号定时任务程序相同的下一次定时任务的执行时间。

```java
 Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
```

可以说，在这个计算过程中，cron表达式帮了我大忙。但是，问题也出在cron表达式上，我们都知道，cron表达式是和服务器各自的时间是有关系的，如果两个服务器的时间本身就不一致(当然，可以有各种各样的手段来同步服务器的时间，但相比使用各种同步手段，还要在定时任务业务逻辑中处理分布式锁的问题，远不如搞一个独立的调度中心来的简单)，cron表达式即使配置的一样，最后执行两个定时任务程序执行定时任务的时间也不会相同。执行时间都不同，那分布式锁还有个屁用啊。

其次，还有一个更为严重的问题，现在我的购物软件只有一个计算当天营业总额的定时任务，一天才执行一次。可是保不齐以后会有一些执行得特别频繁的定时任务，可能1秒或者2秒就要执行一次。这时候再来分析我的程序，显然，分布式锁的获取和释放就成了最大的问题。因为很可能1号定时任务程序得到分布式锁了，但是任务执行突然阻塞了，那1号定时任务程序就会一直持有分布式锁，单线程无法处理高频次且耗时的定时任务，2号定时任务程序得不到分布式锁，执行不了定时任务，就成了一个摆设。

## 引出调度中心

上面两个问题，第一个问题更严重，如果两台服务器各自的时间根本就不相同，或者我们可以说，服务器与服务器的时间可能总会有一些细微的差别，那我们程序这样设计显然就非常失败了。如果能有一个方法，让两个定时任务共用同一个执行时间就好了。或者说，让两个定时任务执行程序的服务器共用一个时间就好了。就像之前两个定时任务访问同一个数据库，处理同一份数据，只要数据有改动，两个定时任务程序都能感知到。

想到这里，我豁然开朗，既然存储到数据库就可以被多个程序共同使用，那我为什么不把定时任务的执行时间也存储到数据库呢？这样一来，当1号定时任务程序抢到了分布式锁，就开始执行定时任务，然后计算定时任务的下一次执行时间；而没抢到分布式锁的2号定时任务程序就什么也不做，反正数据库中记录了定时任务的下一次执行时间，2号定时任务程序下一次循环的时候依然可以根据数据库中记录的定时任务的时间判断定时任务是否该执行了。讲到这里大家应该也清楚了，实际上根本就没有多个定时任务，定时任务只有一个在调度中心那边放着，关键是交给谁去执行。说句装逼的话，这就是调度中心出现的指导思想。好了，既然已经分析出解决问题的方法了，该怎么在代码中实现呢？换句话说，定时任务该以怎样的形式存储在数据库中？难道只存储一个下一次的执行时间？别开玩笑了。

只存储定时任务的下一次执行时间当然是不行的，刚才我们已经明确强调过了，定时任务只有一个，关键是交给哪个定时任务程序来执行。定时任务本身肯定是不能直接存储到数据库中的，你不可能把代码存到数据库中吧，但既然定时任务只有一个，我们就不妨把定时任务的名称存到数据库中，也就是定时任务方法的名称。这样，数据库中存储定时任务的方法名称，也存储定时任务下一次的执行时间。当然，为了存储这两个数据，我们还要为这两个数据创建一个对象，用来封装这两个数据。这个我已经创建好了，就在下面的代码块中。

```java
public class XxlJobInfo {

    //定时任务的方法名称
	private String executorHandler;

    //定时任务的下一次执行时间
    private long triggerNextTime;

    public String getExecutorHandler() {
		return executorHandler;
	}

	public void setExecutorHandler(String executorHandler) {
		this.executorHandler = executorHandler;
	}


    public long getTriggerNextTime() {
		return triggerNextTime;
	}

	public void setTriggerNextTime(long triggerNextTime) {
		this.triggerNextTime = triggerNextTime;
	}

}
```

数据库中就存储executorHandler和triggerNextTime这两个字段。下面，我再为大家定义一下操纵数据库中数据的方法。

```java
@Mapper
public interface XxlJobInfoDao {

    //这个方法就是根据定时任务的名字，获得定时任务的具体信息
    XxlJobInfo loadByName(String name);

    //更新数据库中定时任务数据的方法
    int save(XxlJobInfo info);
  
}
```

接着，就是我已经重构好的定时任务本身。这一次，我给定时任务定义了一个selfishHeart的名字，翻译过来就是自私的心。

```java
public void selfishHeart() {
        while (true) {
            //从数据库中查询定时任务的最新信息
            XxlJobInfo jobInfo = loadByName(selfishHeart);
            //获得当前时间
            long time = System.currentTimeMillis();
            //用当前时间和定时任务的执行时间做对比，如果当前时间大于定时任务的执行时间
            //说明定时任务应该执行了
            if (time >= jobInfo.getTriggerNextTime) {
                //抢夺分布式锁
                int lock = getDistributedLock();
                if (lock == 1) {
                    //抢到锁就直接执行定时任务
                    System.out.println("执行定时任务！");
                    //根据cron表达式计算下一次定时任务的执行时间
                    Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                    //创建定时任务信息对象
                    XxlJobInfo job = new XxlJobInfo();
                    //设置定时任务名字
                    job.setExecutorHandler(selfishHeart);
                    //设置定时任务的下一次执行时间
                    job.setTriggerNextTime(nextTime.getTime());
                    //更新数据库信息，这里就把定时任务的id省略了，大家知道怎么回事就行
                    save(job);
                    //释放分布式锁
                    freeDistributedLock();
                } else {
                    //没抢到锁的定时任务程序会执行到这里，其实本来可以直接就循环了，
                    //但是担心抢到锁的定时任务程序还没执行完任务，数据库中下一次的执行时间没有更新，所以睡30秒
                    //再进入下一轮循环，一般来说，30秒足够定时任务程序执行完定时任务了
                    //这样再进入下一轮循环的时候，从数据库中得到的定时任务下一次的执行时间一定是大于当前时间的了
                    Thread.sleep(30000);
                }
            } else {
                //走到这里说明定时任务还不到执行的时间，直接让定时任务程序睡觉就行
                //其实根据我们定义的定时任务的逻辑，睡30秒显然太少了，睡几个小时都少，不同的定时任务可能睡的时间也不同
                //从这里也可以看出来，如果让定时任务程序自身维持定时任务的下一次执行时间，编码的逻辑会和定时任务本身耦合十分严重
                //在这里睡完之后，就会进入下一次循环，得到数据库中存储的定时任务下一次的执行时间
                //如果最新的执行时间大于当前时间，说明还不到执行的时候，只有小于或等于当前时间，说明当前时间已经来到或者超过了
                //定时任务下一次的执行时间，定时任务也就可以立刻执行了
                Thread.sleep(30000);
            }
        }
    }
```

以上就是我重构好的定时任务，确实利用上数据库了，两个定时任务共用同一个执行时间了，而且每一行代码的逻辑在代码块中标注得十分清楚，所以就不再重复解释了。

但重构后还有很多问题需要解决。当然，我想先请大家别去关系一个定时任务具体怎么存放到数据库中，从数据库中查询到的信息，怎么去调用这个定时任务，这些大家先不用考虑，等前置功能都实现了，最后我们再解决定时任务执行的问题。现在，请大家只看上面重构后的定时任务，难道不觉得有什么重大缺陷吗？下面，请让我来自我反省一下。

- 第一，重构后的定时任务代码显得比较杂乱，if嵌套的有些多。
- 第二，定时任务的线程一直在while循环中执行，即便我的定时任务一天才执行一次，这个线程也不能停下来歇一歇，睡30秒可不叫休息。如果让你每天24小时工作，你愿意每隔30秒睡一下，还睡够12个小时，再连续工作十二个小时呢？肯定是后者。如果有一个办法，能够让定时任务需要执行的时候再启动线程就好了，定时任务不执行的时候，线程直接终止即可。这样还能节省CPU资源。
- 第三，大家应该也注意到了，我定义的定时任务的名字是selfishHeart，但是在该定时任务中，不仅需要处理分布式锁的抢夺和释放，还要执行真正的定时任务，还要计算定时任务的下一次执行时间，这些逻辑杂糅到一起，严重污染了定时任务本身的逻辑。换句话说，定时任务不干净了。
- 第四，整个定时任务，没有丝毫考虑到容错机制。比如说，当定时任务执行完了，程序开始计算定时任务下一次的执行时间，这时候执行定时任务的服务器突然宕机了，定时任务确实是执行了，但是下一次的执行时间还未计算。这样上一次没抢到锁的定时任务程序，在下一次循环中就会再次执行该定时任务，因为数据库中定时任务的下一次执行时间并未更新，这样一来，如果定时任务涉及到数据库中数据的非幂等性改变，数据不就出问题了吗？
- 再换一种潜在的风险，当获取分布式锁的定时任务程序执行到定时任务的时候，执行失败了，但是用户并不知道，接下来，程序依然计算定时任务的下一次执行时间，并且更新了数据库中定时任务的下一次执行时间。这样一来，没抢到分布式锁的程序再次进入循环，即便抢到了分布式锁也无法执行定时任务，因为定时任务下次的执行时间已经更新到一天后了。显然，这么做就少执行了一次定时任务，计算不了当天的总营业额，搞不好我老婆以为我把钱独吞了。我可不想被狠狠地抽耳光啊！
- 最后一点，也是最严重的缺陷，之前困扰我的那个问题仍然没有解决呀，现在虽然两个定时任务共用同一个执行时间，但是还是使用各自的服务器时间呀，判断时间的时候，也是使用各自服务器的时间和数据库中的信息进行判断的。最重要的问题我是一点也没解决呀。搞了半天，我好像都在做无用功！

## 拿掉分布式锁

我希望能再次重构自己的程序，解决以上列出的问题。**重构的麻烦之处不在于代码怎样编写，而在于重构的思路怎样明确。比如，针对上面的问题，我首先想到的就是，有没有一种方法，能将定时任务的业务逻辑和其他逻辑分隔开，并且分割得一清二楚！换句话说，我打算就让定时任务只执行定时任务的逻辑，其他的逻辑都放到一边。那业务逻辑交给谁来执行呢？想也该想到了，执行任务的只能是线程，所以，自然是交给另一个线程来执行。那么，接下来，就请大家看看我再次重构好的代码。**

```java
public class Test {

    //这是定时任务本身
    public static void selfishHeart() {
        System.out.println("执行定时任务！");
    }

    //main方法开始执行程序了
    public static void main(String[] args) {
        while (true) {
            //从数据库中查询定时任务信息
            XxlJobInfo jobInfo = loadByName(selfishHeart);
            //得到当前时间
            long time = System.currentTimeMillis();
            //判断当前时间是否大于定时任务的执行时间了
            if (time >= jobInfo.getTriggerNextTime) {
                //如果大于就执行定时任务，但是去执行哪一个定时任务呢？这里就出现问题了
                //不知道要去执行哪个定时任务，所以先注释掉吧，下面就会讲到
                //System.out.println("创建一个新的线程，去执行定时任务!");
                //计算定时任务下一次的执行时间
                Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                //下面就是更新数据库中定时任务的操作
                XxlJobInfo job = new XxlJobInfo();
                job.setExecutorHandler(selfishHeart);
                job.setTriggerNextTime(nextTime.getTime());
                save(job);
            }
        }
    }
}
```

在上面的代码块中，我把定时任务程序还原成本来面目了，定时任务程序只执行定时任务本身的逻辑。而其他的从数据库中查询定时任务执行时间，更新定时任务下一次执行时间这些操作，都交给main函数的线程来执行了。如果查询到数据库中有定时任务可以执行了，那就直接开辟一个新的线程，去执行定时任务。这些逻辑应该都很简单了，如果有朋友对开辟新的线程去执行定时任务感到疑惑，不妨就继续让定时任务再main函数线程中执行，这样一来，逻辑不是又杂糅到一起了吗？

当然，上面代码块中最大的变动还是把分布式锁给取消了，为什么我要暂时取消分布式锁？原因很简单，我已经开始渐渐把调度定时任务的功能抽取成单独的功能模块了。所谓的调度定时任务，就是判断定时任务是否到了执行时间，然后通知定时任务执行，然后计算定时任务下一次的执行时间，这就是调度功能。既然是这样，现在的情形就有些微妙了。我来给大家仔细分析一下。到目前为止，我为自己的购物软件定义了一个积攒小金库的定时任务，定时任务只有一个，该定时任务的信息存储在数据库中，而执行定时任务的程序有两个，部署在两个服务器中。

之前呢，我让定时任务本身去主动触发自己的执行，不停地从数据库中获取自己的信息，看自己是不是可以执行了，然后再记录下一次执行的时间，这种编码方式简单粗暴，很容易出现各种各样的状况，不是程序的理想状态。

现在呢，我把定时任务和调度定时任务的功能做了一个分割，单独抽取出一个调度功能，这个功能去数据库中扫描要执行的定时任务信息，并且通知定时任务只执行，然后记录定时任务下一次的执行时间。如果打个比方的话，以前定时任务执行就像是学生回答老师的问题，争着抢着主动去回答，虽然每个人都很积极，但是可能会吵得乱七八糟。而现在仍然是学生回答老师的问题，只是老师来点名让某个学生来回答，没点到名的就不必回答了，并且，老师叫哪个学生回答问题依据的是自己的判断，换句话说，现在定时任务程序执行可能并不需要依赖自己服务器的时间了，而是依赖"老师"所属服务器的判断，这样，就有一个公共的时间标尺了，这样情况就好很多了。调度功能在整个程序中就扮演了老师的角色，只需要一个老师点名学生回答问题就行了(当然，调度中心也可以集群化，也是需要加分布式锁的，这个后面会讲到。这里就先使用一个调度中心)。数据库的信息也都由调度中心来维护了，这时候，程序是不是就整齐了很多，至少从功能上来说，都抽取成单独的模块了。

并且，大家应该还注意到了，在上面的代码块中，调度功能是在一个while循环中执行的，这个while循环一直没有停歇，也就是说线程一直不sleep。这对CPU资源来说也是一种消耗呀。而且，让我痛心的是，这种消耗，几乎全用在了一个定时任务上面。因为在上面的代码块中，调度功能的线程一直在判断数据库中的selfishHeart这个定时任务方法是否可以执行了。显然，这也是一种很严重的浪费。既然，调度功能的线程都不休息了，为何不让它多干点活呢？换句话说，为什么不让调度功能的线程多判断一些定时任务，看这些定时任务是否能够执行。因为我现在的程序已经把定时任务的信息存储到数据库中了，交给调度功能来维护，虽然目前只有一个定时任务，但保不齐以后还会有更多的定时任务要在程序中执行呀，这些定时任务都可以交给调度功能模块来调度。所以，我再次对代码做了一点重构。

首先增添了一个查询数据库中所有定时任务的方法。

```java
@Mapper
public interface XxlJobInfoDao {

    //这个方法就是根据定时任务的名字，获得定时任务的具体信息
    XxlJobInfo loadByName(String name);

    //更新数据库中定时任务数据的方法
    int save(XxlJobInfo info);

    //查询所有定时任务信息的方法
    List findAll();
  
}
```

接着是调度功能的代码。

```java
public class TestJob {

    public static void main(String[] args) {
        while (true) {
            //从数据库中查询所有定时任务信息
            List<XxlJobInfo> jobInfoList = findAll();
            //得到当前时间
            long time = System.currentTimeMillis();
            //遍历所有定时任务信息
            for (XxlJobInfo jobInfo : jobInfoList) {
                if (time >= jobInfo.getTriggerNextTime) {
                    //如果大于就执行定时任务，但是去执行哪一个定时任务呢？这里就出现问题了
                	//不知道要去执行哪个定时任务，所以先注释掉吧，下面就会讲到
                	//System.out.println("创建一个新的线程，去执行定时任务!");
                    //计算定时任务下一次的执行时间
                    Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                    //下面就是更新数据库中定时任务的操作
                    XxlJobInfo job = new XxlJobInfo();
                    job.setExecutorHandler(selfishHeart);
                    job.setTriggerNextTime(nextTime.getTime());
                    save(job);
                }
            }
        }
    }
}
```

在上面的代码块中，调度模块的线程显然比之前干的活更多了，永不停歇的线程利用得更充分了一些。当然，在上面我们还可以看到，现在的情况是只要有一个定时任务要去执行了，就会开创一个新的线程，去执行定时任务。我们都知道，在程序中，线程的创建和销毁是很消耗资源的，频繁的创建线程究竟会对我们的程序带来怎样的影响呢？这就是后面的内容了，关于创建线程的合理化，以及如何去合理化，后面的章节我会为大家详细剖析。现在，我的程序还有一个十分严重的问题，大家肯定也都注意到了，那就是在目前的调度模块中，我不知道要把定时任务分配给哪个定时任务程序去执行。换句话说，老师不知道该叫哪个学生站起来回答问题。

## 注册执行器地址

在我的程序中，虽然设置的定时任务只有一个，但是这个定时任务交给两个定时任务程序来执行了，调度模块每次只通知一个定时任务程序去执行，另一个定时任务程序起到兜底的作用。所以，现在我部署了两个定时任务程序，那每次执行的时候，调度模块应该通知哪个定时任务程序去执行呢？再进一步分析一下，调度模块从数据库中查询到了多个要执行的定时任务，这些定时任务肯定都有相应的定时任务程序部署在不同的服务器上，那调度中心该具体通知哪些定时任务程序来执行定时任务呢？

所以，现在就让我来再次想一想，调度功能和定时任务程序的关系。首先有一点是可以明确的，执行定时任务的程序肯定是要部署在一个服务器上的，而调度功能模块是我后来单独抽取出来的，这两个模块的关系十分紧密。那么，现在有一个问题，就是调度功能的模块是不是要和定时任务执行程序部署在相同的服务器上呢？显然是不能的，如果调度模块和定时任务程序部署在同一个服务器上，如果服务器崩溃了，那么调度模块和定时任务程序都无法使用了。我心中的理想程序是，如果有一个定时任务程序崩溃了，那么剩下的那个定时任务还可以继续配合调度模块执行定时任务。所以，答案显而易见了，最理想的方法就是调度模块单独部署在一个服务器上，两个定时任务程序部署在另外两个服务器上。

- 调度模块单独访问数据库，并且维护定时任务在数据库中的信息
- 而两个定时任务程序从创建的那一刻，就分别把自己的定时任务信息通过网络发送给调度模块，随着定时任务信息一起发送的，还有定时任务程序部署的服务器的ip地址。

这样一来，当调度模块从数据库中查询到了可以执行的定时任务，就会通过某种策略确定让哪个定时任务程序去执行，然后确定要执行定时任务的程序的ip地址，最后通过网络向该定时任务程序发送消息，通知该定时任务程序执行定时任务。并且，最开始困扰我的那个问题，也就终于得到解决了，定时任务执行时间的判断依据的是调度中心部署服务器的时间，而不是两个定时任务部署的服务器，这样，执行时间就终于有了一个统一的标尺。请大家看下面一幅简图。

![admin.png](admin.png)

**接着，我再用刚才明确的编码思路把我的程序重构一下。首先是定时任务本身。**

```java
 public static void selfishHeart() {
        System.out.println("执行定时任务！");
}
```

**接着是定时任务创建完毕后，要发送给调度模块的定时任务信息。**

```java
public class RegistryParam implements Serializable {
  
    private static final long serialVersionUID = 42L;
  
    //定时任务方法的名称
    private String registryKey;
    //定时任务程序部署的服务器的ip地址
    private String registryValue;

    public RegistryParam(){
    
    }
  
    public RegistryParam(String registryKey, String registryValue) {
        this.registryKey = registryKey;
        this.registryValue = registryValue;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

}
```

**接着是要和数据库打交道的XxlJobInfo类。**

```java
public class XxlJobInfo {

    //定时任务的方法名称
	private String executorHandler;

    //定时任务的下一次执行时间
    private long triggerNextTime;

    //定时任务部署的服务器ip地址的集合
    private List<String> registryList;

    public String getExecutorHandler() {
		return executorHandler;
	}

	public void setExecutorHandler(String executorHandler) {
		this.executorHandler = executorHandler;
	}


    public long getTriggerNextTime() {
		return triggerNextTime;
	}

	public void setTriggerNextTime(long triggerNextTime) {
		this.triggerNextTime = triggerNextTime;
	}

    public List<String> getRegistryList() {
        return registryList;
    }

     public void setRegistryList(List<String> registryList) {
        this.registryList = registryList;
    }

}
```

在这里，大家肯定会思考一个问题，那就是RegistryParam和XxlJobInfo的关系。我就简单明了地解释一下，RegistryParam是定时任务程序中要用到的对象，定时任务程序要把自己的信息通过网络发送给调度模块，就会把自己执行任务的方法和服务器的ip地址封装到RegistryParam对象中，该对象经过序列化后在网络中被传输到调度模块这一端。看到这里，大家应该有这样一个意识，那就是目前我的程序中部署了两个定时任务程序，这两个程序执行的都是相同的定时任务，那么它们发送给调度模块的RegistryParam对象中，registryKey肯定都是相同的，因为执行的都是同一个定时任务呀，不同的只有registryValue，因为这两个定时任务程序部署的服务器的ip地址肯定是不同的。但是，在调度中心维护的数据库中，相同的定时任务肯定只能存储一个，可是现在定时任务却有两个不同的要执行的地址，究竟把哪个地址存储在数据库中呢？当然是两个都要存储，为了解决这个问题，调度模块会在接收到两个定时任务程序发送过来的registryValue对象后，会判断它们的registryKey，也就是定时任务的方法名称是否一致，如果一致就把这两个对象中的registryValue，也就是远程的执行定时任务服务器的地址放进一个list集合中，封装到XxlJobInfo对象中，当然，registryKey也会封装到XxlJobInfo对象中，然后再把XxlJobInfo对象中的信息存储到数据库中。具体的细节还有很多很多，这是第二个代码版本对应的内容了，到时候我会为大家详细讲解。

**好了，最后就是经过我重构的调度模块功能。**

```java
public class TestJob {

    public static void main(String[] args) {
        while (true) {
            //从数据库中查询所有定时任务信息
            List<XxlJobInfo> jobInfoList = findAll();
            //得到当前时间
            long time = System.currentTimeMillis();
            //遍历所有定时任务信息
            for (XxlJobInfo jobInfo : jobInfoList) {
                if (time >= jobInfo.getTriggerNextTime) {
                    //如果大于就执行定时任务，在这里就选用集合的第一个地址
                    String address = jobInfo.getRegistryList().get(0);
                    //注意，既然调度模块已经单独部署了，就没有再创建新的线程去执行定时任务
                    //而是远程通知定时任务程序执行定时任务，没被通知的定时任务程序就不必执行
                	System.out.println("通知address服务器，去执行定时任务!");
                    //计算定时任务下一次的执行时间
                    Date nextTime = new CronExpression(scheduleCron).getNextValidTimeAfter(new Date());
                    //下面就是更新数据库中定时任务的操作
                    XxlJobInfo job = new XxlJobInfo();
                    job.setExecutorHandler(selfishHeart);
                    job.setTriggerNextTime(nextTime.getTime());
                    save(job);
                }
            }
        }
    }
}
```

到此为止，程序已经很完美了，但是我仍然对自己所做的一切不太满意，因为这太像个小玩具了。别忘了，我的程序员之魂已经觉醒了，我打算把项目做大做强，然后开源。现在这种玩意怎么好意思给别人看呢？所以，我决定要做就做到最好，再下一番功夫，把这个程序重构得尽善尽美。因为程序本身还存在太多太多的缺陷了，别说其他的，就说说调度模块的名字吧，到现在为止，都没有一个正式的名字。仍然叫TestJob，还Job个毛啊！这就像你煞费苦心地追一个姑娘，花了很多时间，花了很多钱，你难道就不想尽快把她娶回家，给她一个正式的名份吗！所以，调度模块的名字是一定要明确一下的，比如就可以把TestJob这个类名换成JobScheduleHelper，这没什么不可以，反正是调度任务的好帮手嘛。

除此之外呢？要调度的定时任务特别多怎么办？调度的定时任务没有成功怎么办？最要紧的是，调度定时任务，再通知定时任务去执行，这个活都让一个线程干，你就不怕把它累死啊。当然，累死线程事小，影响用户体验就事大了，说白了还是考虑到执行任务的效率。让一个线程在调度定时任务的同时，从数据库扫描定时任务信息，然后去远程通知定时任务程序执行定时任务，接着再计算定时任务下一次的执行时间，这样的方案设计，能给程序带来更高更快的性能吗？

总之，目前程序存在的问题太多了，这一章肯定是讲不完了，下一章继续吧。

## 总结

这一章为大家详细剖析了调度中心的演变过程，我主要是从三个方面来剖析的：

- 第一，如果不单独抽取调度中心，在定时任务中，业务逻辑就会和其他的逻辑耦合得特别严重，这显然是不成熟的代码。
- 第二，执行定时任务的服务器时间上需要同步。其实第二点也没有特别重要，如果只是同步服务器时间，方法肯定是有的。
- 第三，最开始定时任务程序自己维护执行时间，总要在程序中多写一些逻辑，并且每个定时任务和每个定时任务的执行时间，消耗的时间都是不相同的。这样一来，在处理分布式锁超时的时候，可能会十分麻烦。

所以直接引入调度中心，一切就都交给调度中心来调度，定时任务程序就不需要分布式锁了，这就让程序简化太多了，定时任务只专注业务逻辑即可。当然，调度中心集群化也会为调度中心引入分布式锁，但调度中心说白了只起到一个调度定时任务的作用，又不执行定时任务，分布式锁引入了也就是决绝一个谁来调度定时任务的问题，这是很简单的。说到底，调度中心之所以存在，主要还是为了将业务逻辑和任务调度轻松解耦。当然，这只是我自己的观点，欢迎大家一起讨论。下一章，我就会为大家渐渐搭建起一个功能成熟的调度中心。
