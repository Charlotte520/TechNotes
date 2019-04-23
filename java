1. 线程池
线程开销：创建、销毁的时间开销；调度的上下文切换；内存（jvm堆中创建thread对象，os要分配对应的系统内存，默认最大1MB）
#线程：cpu密集型应用，为#core 1~2倍。IO密集：根据阻塞时长，或[min,max]可自动增减。tomcat默认200。
实现：接收任务（Runnable/Callable），放入BlockingQueue；thread从queue中取出，执行。没有任务时，thread阻塞；queue满时，任务拒绝策略（阻塞，抛出异常，返回特殊值，阻塞一段时间等）。
API：Executor.execute()； -> ExecutorService：加入callable、future、关闭方法。ForkJoinPool：支持forkjoin框架。ThreadPoolExecutor：标准实现。ScheduledExecutorService：定时任务执行。callable可有返回值，可抛异常。Future：cancel(),isDone(),get()，监听thread执行。
Executors工具类：不要用。queue、#thread大小无限制。
CountDownLatch：协同工具。所有线程同时开始。CylicBarrier

2. FutureTask
private void use() {
        Callable<JSONObject> c = () -> new JSONObject();
        FutureTask<JSONObject> f = new FutureTask<JSONObject>(c);//MyFutreTask
        new Thread(f).start();
    }

    // 泛型；异步调用call；构造函数；实现runnable，可作为thread参数；
    class MyFutureTask<T> implements Runnable {
        Callable<T> c;
        T result;
        volatile String state ="new"; //多线程间的线程可见性。其他状态：
        public MyFutureTask(Callable c) {
            this.c = c;
        }

        //子线程异步执行
        @Override
        public void run() {
            try {
                result = c.call();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                state = "done";
            }
            //执行完唤醒
            synchronized (this) {//每个object都有wait/notify方法，必须配合synchronized，若先notify再wait会一直等，活锁
                this.notifyAll();//LockSupport.park()/unpark()，不用synchronized，顺序无关
            }
        }

        // main线程执行
        T get() throws InterruptedException {
            if ("done".equals(state)) {
                return result;
            }
            //没完成需要阻塞
            synchronized (this) {
                wait();
            }
            return result;
        }
    }
