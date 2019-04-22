1. 线程池
线程开销：创建、销毁的时间开销；调度的上下文切换；内存（jvm堆中创建thread对象，os要分配对应的系统内存，默认最大1MB）
#线程：cpu密集型应用，为#core 1~2倍。IO密集：根据阻塞时长，或[min,max]可自动增减。tomcat默认200。
实现：接收任务（Runnable/Callable），放入BlockingQueue；thread从queue中取出，执行。没有任务时，thread阻塞；queue满时，任务拒绝策略（阻塞，抛出异常，返回特殊值，阻塞一段时间等）。
API：Executor.execute()； -> ExecutorService：加入callable、future、关闭方法。ForkJoinPool：支持forkjoin框架。ThreadPoolExecutor：标准实现。ScheduledExecutorService：定时任务执行。callable可有返回值，可抛异常。Future：cancel(),isDone(),get()，监听thread执行。
Executors工具类：不要用。queue、#thread大小无限制。
CountDownLatch：协同工具。所有线程同时开始。CylicBarrier
