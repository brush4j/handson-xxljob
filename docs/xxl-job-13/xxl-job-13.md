在线编码功能是在执行器这一端实现的，当然，操作的发起者是调度中心。

用户在web界面编写的源码也会保存到数据库中，就是通过下面这个类来保存的。请看下面的代码块。
```java
/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:如果定时任务是glue模式，需要在前端页面编写代码的化，这个类就是负责在线编辑定时任务的查找和保存。
 */
@Controller
@RequestMapping("/jobcode")
public class JobCodeController {

	@Resource
	private XxlJobInfoDao xxlJobInfoDao;
	@Resource
	private XxlJobLogGlueDao xxlJobLogGlueDao;


	@RequestMapping
	public String index(HttpServletRequest request, Model model, int jobId) {
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
		List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueDao.findByJobId(jobId);
		if (jobInfo == null) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
		}
		JobInfoController.validPermission(request, jobInfo.getJobGroup());
		model.addAttribute("GlueTypeEnum", GlueTypeEnum.values());
		model.addAttribute("jobInfo", jobInfo);
		model.addAttribute("jobLogGlues", jobLogGlues);
		return "jobcode/jobcode.index";
	}

	@RequestMapping("/save")
	@ResponseBody
	public ReturnT<String> save(Model model, int id, String glueSource, String glueRemark) {
		if (glueRemark==null) {
			return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_glue_remark")) );
		}
		if (glueRemark.length()<4 || glueRemark.length()>100) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_remark_limit"));
		}
		XxlJobInfo exists_jobInfo = xxlJobInfoDao.loadById(id);
		if (exists_jobInfo == null) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		exists_jobInfo.setGlueSource(glueSource);
		exists_jobInfo.setGlueRemark(glueRemark);
		exists_jobInfo.setGlueUpdatetime(new Date());
		exists_jobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(exists_jobInfo);
		XxlJobLogGlue xxlJobLogGlue = new XxlJobLogGlue();
		xxlJobLogGlue.setJobId(exists_jobInfo.getId());
		xxlJobLogGlue.setGlueType(exists_jobInfo.getGlueType());
		xxlJobLogGlue.setGlueSource(glueSource);
		xxlJobLogGlue.setGlueRemark(glueRemark);
		xxlJobLogGlue.setAddTime(new Date());
		xxlJobLogGlue.setUpdateTime(new Date());
		xxlJobLogGlueDao.save(xxlJobLogGlue);
		xxlJobLogGlueDao.removeOld(exists_jobInfo.getId(), 30);
		return ReturnT.SUCCESS;
	}

}
```
## 执行器侧执行前的解析工作
调度的话，还是和普通定时任务一样，正常调度即可。

那么，当执行器那一端开始执行在线编辑的定时任务时，会进行怎样的操作呢？这就不得不再为执行器引入两个新的类了。首先就是GlueFactory类，请看下面的代码块
```java
/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:运行模式工厂
 */
public class GlueFactory {

	private static GlueFactory glueFactory = new GlueFactory();

	public static GlueFactory getInstance(){
		return glueFactory;
	}

	public static void refreshInstance(int type){
		if (type == 0) {
			glueFactory = new GlueFactory();
		} else if (type == 1) {
			glueFactory = new SpringGlueFactory();
		}
	}

	private GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

	private ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

	//在该方法中创建IJobHandler对象
	public IJobHandler loadNewInstance(String codeSource) throws Exception{
		//对用户在线编辑的源码做判空校验
		if (codeSource!=null && codeSource.trim().length()>0) {
			//把源码转化为Class文件
			Class<?> clazz = getCodeSourceClass(codeSource);
			if (clazz != null) {
				//创建对象
				Object instance = clazz.newInstance();
				if (instance!=null) {
					//public class DemoGlueJobHandler extends IJobHandler {
					//
					//	@Override
					//	public void execute() throws Exception {
					//		XxlJobHelper.log("XXL-JOB, Hello World.");
					//	}
					//
					//}
					//上面是我从xxl-job复制过来的默认例子，可以看到，在新编写的类都要继承IJobHandler抽象类的
					//所以这里要判断一下是否属于这个对象
					if (instance instanceof IJobHandler) {
						//这里其实做的就是属性注入的工作
						this.injectService(instance);
						return (IJobHandler) instance;
					}
					else {
						throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, "
								+ "cannot convert from instance["+ instance.getClass() +"] to IJobHandler");
					}
				}
			}
		}
		throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, instance is null");
	}


	private Class<?> getCodeSourceClass(String codeSource){
		try {
			//可以看到，这里其实是用MD5把源码加密成字节
			byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
			String md5Str = new BigInteger(1, md5).toString(16);
			//从对应的缓存中查看是否已经缓存了该字节了，如果有就可以直接返回class文件
			Class<?> clazz = CLASS_CACHE.get(md5Str);
			if(clazz == null){
				//如果没有就在这里把源码解析成class文件
				clazz = groovyClassLoader.parseClass(codeSource);
				//键值对缓存到Map中
				CLASS_CACHE.putIfAbsent(md5Str, clazz);
			}
			//返回class文件
			return clazz;
		} catch (Exception e) {
			return groovyClassLoader.parseClass(codeSource);
		}
	}


	public void injectService(Object instance) {

	}

}
```

上面的这个类会在ExecutorBizImpl类中用到，并且是ExecutorBizImpl类的run方法中用到。下面我就把ExecutorBizImpl类中重构过的run方法给大家展示一下，当然，只展示部分，就不完全展示了。请看下面的代码块。
```java
public class ExecutorBizImpl implements ExecutorBiz {

     /**
     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/8
     * @Description:执行定时任务的方法，这里要再次强调一下，该方法是在用户定义的业务线程池中调用的
     */
    @Override
    public ReturnT<String> run(TriggerParam triggerParam) {
         //通过定时任务的ID从jobThreadRepository这个Map中获取一个具体的用来执行定时任务的线程
        JobThread jobThread = XxlJobExecutor.loadJobThread(triggerParam.getJobId());
        //判断该jobThread是否为空，不为空则说明该定时任务不是第一次执行了，也就意味着该线程已经分配了定时任务了，也就是这个jobHandler对象
        //如果为空，说明该定时任务是第一次执行，还没有分配jobThread
        IJobHandler jobHandler = jobThread!=null?jobThread.getHandler():null;
        //这个变量记录的是移除旧的工作线程的原因
        String removeOldReason = null;
        //得到定时任务的调度模式
        GlueTypeEnum glueTypeEnum = GlueTypeEnum.match(triggerParam.getGlueType());
        //如果为bean模式，就通过定时任务的名字，从jobHandlerRepository这个Map中获得jobHandler
        if (GlueTypeEnum.BEAN == glueTypeEnum) {
            //在这里获得定时任务对应的jobHandler对象，其实就是MethodJobHandler对象
            IJobHandler newJobHandler = XxlJobExecutor.loadJobHandler(triggerParam.getExecutorHandler());
            //这里会进行一下判断，如果上面得到的jobHandler并不为空，说明该定时任务已经执行过了，并且分配了对应的执行任务的线程
            //但是根据定时任务的名字，从jobHandlerRepository这个Map中得到封装定时任务方法的对象却和jobHandler不相同
            //说明定时任务已经改变了
            if (jobThread!=null && jobHandler != newJobHandler) {
                //走到这里就意味着定时任务已经改变了，要做出相应处理，需要把旧的线程杀死
                removeOldReason = "change jobhandler or glue type, and terminate the old job thread.";
                //执行定时任务的线程和封装定时任务方法的对象都置为null
                jobThread = null;
                jobHandler = null;
            }
            if (jobHandler == null) {
                //如果走到这里，就意味着jobHandler为null，这也就意味着上面得到的jobThread为null
                //这就说明，这次调度的定时任务是第一次执行，所以直接让jobHandler等于从jobHandlerRepository这个Map获得newJobHandler即可
                //然后，这个jobHandler会在下面创建JobThread的时候用到
                jobHandler = newJobHandler;
                if (jobHandler == null) {
                    //经过上面的赋值，
                    //走到这里如果jobHandler仍然为null，那只有一个原因，就是执行器这一端根本就没有对应的定时任务
                    //通过执行器的名字根本从jobHandlerRepository这个Map中找不到要被执行的定时任务
                    return new ReturnT<String>(ReturnT.FAIL_CODE, "job handler [" + triggerParam.getExecutorHandler() + "] not found.");
                }
            }
            //在这里就是匹配到了GLUE_GROOVY模式，也就是在线编辑的模式
            //在xxl-job中一般都是bean模式
        } else if (GlueTypeEnum.GLUE_GROOVY == glueTypeEnum) {
            //走到这里，说明是glue模式，在线编辑代码然后执行的
            //注意，这时候运行的事glue模式，就不能再使用MethodJobHandler了反射执行定时任务了，应该使用GlueJobHandler来执行任务
            //所以下面会先判断GlueJobHandler中的gule的更新时间，和本次要执行的任务的更新时间是否相等，如果不想等说明glue的源码可能改变了，要重新
            //创建handler和对应的工作线程
            if (jobThread != null &&
                    !(jobThread.getHandler() instanceof GlueJobHandler
                            && ((GlueJobHandler) jobThread.getHandler()).getGlueUpdatetime()==triggerParam.getGlueUpdatetime() )) {
                removeOldReason = "change job source or glue type, and terminate the old job thread.";
                jobThread = null;
                jobHandler = null;
            }
            if (jobHandler == null) {
                try {//下面就可以在创建新的handler了
                    IJobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
                    jobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    return new ReturnT<String>(ReturnT.FAIL_CODE, e.getMessage());
                }
            }
        }
        else {
            //如果没有合适的调度模式，就返回调用失败的信息
            return new ReturnT<String>(ReturnT.FAIL_CODE, "glueType[" + triggerParam.getGlueType() + "] is not valid.");
        }

        //剩下的部分逻辑省略
    }
}
```

在上面代码块中，如果匹配到了GLUE_GROOVY模式，程序就会执行第62，63行的代码，就是下面这两行代码。
```java
IJobHandler originJobHandler = GlueFactory.getInstance().loadNewInstance(triggerParam.getGlueSource());
jobHandler = new GlueJobHandler(originJobHandler, triggerParam.getGlueUpdatetime());
```

可以看到，GlueFactory调用了loadNewInstance把用户在线编辑的代码字符串转换成了Class文件。

当然，在loadNewInstance方法中，还调用了一个很重要的方法，那就是injectService方法，这个方法在GlueFactory中是空方法，需要子类来实现。

## 属性注入的功能，就有SpringGlueFactory子类
而实现的子类就是SpringGlueFactory。请看下面的代码块。
```java
public class SpringGlueFactory extends GlueFactory {

    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);


    @Override
    public void injectService(Object instance){
        if (instance==null) {
            return;
        }
        if (XxlJobSpringExecutor.getApplicationContext() == null) {
            return;
        }
        //得到该对象中的属性
        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            //如果是静态属性就跳过
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object fieldBean = null;
            //其实下面都是在做属性注入的工作了，这里就是看看该属性上有没有Resource注解
            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                try {//如果有就得到这个注解
                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                    //如果注解中有名称，就从容器中获得对应的对象
                    if (resource.name()!=null && resource.name().length()>0){
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
                    } else {
                        //否则就直接按照属性的名称从容器中获得对象
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
                    }
                } catch (Exception e) {
                }
                if (fieldBean==null ) {
                    //上面都赋值失败的话，就直接按照属性的类型从容器中获得对象
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }//判断是否有Autowired注解，逻辑和上面一样，就不再重复了
            else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
                } else {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }
            if (fieldBean!=null) {
                //设置可访问
                field.setAccessible(true);
                try {
                    //用反射给对象的属性赋值
                    field.set(instance, fieldBean);
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

}
```

从上面的代码块中可以看出，SpringGlueFactory这个类的主要功能就是提供属性注入的。因为用户在线编辑的代码也可能加了Spring的注解呀，也可能成为被容器管理的对象，也需要属性注入等等。

## 包装有glue实例的GlueJobHandler
那么属性注入的功能，就有SpringGlueFactory类来实现了。等这些工作都做好了，程序就会创建出一个GlueJobHandler，这个handler也继承了IJobHandler。就是用来在GLUE_GROOVY模式下执行定时任务的。请看下面的代码块。
```java
public class GlueJobHandler extends IJobHandler {

	private long glueUpdatetime;
	private IJobHandler jobHandler;

	public GlueJobHandler(IJobHandler jobHandler, long glueUpdatetime) {
		this.jobHandler = jobHandler;
		this.glueUpdatetime = glueUpdatetime;
	}
	public long getGlueUpdatetime() {
		return glueUpdatetime;
	}


	@Override
	public void execute() throws Exception {
		XxlJobHelper.log("----------- glue.version:"+ glueUpdatetime +" -----------");
		jobHandler.execute();
	}

	@Override
	public void init() throws Exception {
		this.jobHandler.init();
	}

	@Override
	public void destroy() throws Exception {
		this.jobHandler.destroy();
	}
}
```

剩下的逻辑就和执行普通定时任务没什么区别了，将创建的包装有glue实例的GlueJobHandler推送给工作线程队列，等待执行，大家简单看看源码就行。

## 本节测试

启动admin服务

启动sample服务

会发现执行器地址已经注册到执行器注册表`xxl_job_registry`中了
```sql
id;registry_group;registry_key;registry_value;update_time
1;EXECUTOR;xxl-job-executor-sample;http://:9999/;2025-05-09 14:07:27
5;EXECUTOR;xxl-job-executor-sample;http://10.77.182.251:9999/;2025-05-12 10:49:13
```

本节你可以删除xxl_job_group表中的address_list地址字段值，来验证调度中心能够自动的同步xxl_job_registry执行器的地址并注册到到xxl_job_group

在admin管理页面新增个定时任务，**任务类型为glue**，
注意修改默认的包名为：
- import com.cqfy.xxl.job.core.context.XxlJobHelper;
- import com.cqfy.xxl.job.core.handler.IJobHandler;
```java
package com.xxl.job.service.handler;

import com.cqfy.xxl.job.core.context.XxlJobHelper;
import com.cqfy.xxl.job.core.handler.IJobHandler;

public class DemoGlueJobHandler extends IJobHandler {

	@Override
	public void execute() throws Exception {
		XxlJobHelper.log("XXL-JOB, Hello World.");
	}

}

```

手动执行一次定时任务，触发的时候用的xxl_job_group中的数据，测试结果admin日志面板