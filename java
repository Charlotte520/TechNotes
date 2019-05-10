1. 线程池
线程开销：创建、销毁的时间开销；调度的上下文切换；内存（jvm堆中创建thread对象，os要分配对应的系统内存，默认最大1MB）
#线程：cpu密集型应用，为#core 1~2倍。IO密集：根据阻塞时长，或[min,max]可自动增减。tomcat默认200。
实现：接收任务（Runnable/Callable），放入BlockingQueue；thread从queue中取出，执行。没有任务时，thread阻塞；queue满时，任务拒绝策略（阻塞，抛出异常，返回特殊值，阻塞一段时间等）。
API：Executor.execute()； -> ExecutorService：加入callable、future、关闭方法。ForkJoinPool：支持forkjoin框架。ThreadPoolExecutor：标准实现。ScheduledExecutorService：定时任务执行。callable可有返回值，可抛异常。Future：cancel(),isDone(),get()，监听thread执行。
Executors工具类：不要用。queue、#thread大小无限制。
CountDownLatch：协同工具。所有线程同时开始。CylicBarrier
BlockingQueue：阻塞队列（空/满），线程安全。插入、移除、检查 3种方法。抛出异常：add,remove,element。返回特殊值：offer，poll，peek。阻塞：put，take。超时：offer(e,time)，poll。

    // 需要任务仓库；线程集合；工作线程；池的初始化（仓库大小、集合大小、线程就绪）；向仓库存放任务（阻塞/非）；关闭；
    class MyThreadPool {
        private BlockingQueue<Runnable> tasks;
        private List<Thread> workers;
        private volatile boolean isServing;//所有线程可见


        public MyThreadPool(int taskSize, int poolSize) {
            if (taskSize <= 0 || poolSize <= 0) {
                throw new IllegalArgumentException("非法参数");
            }
            tasks = new LinkedBlockingQueue<>(taskSize);
            workers = Collections.synchronizedList(new ArrayList<>()); //线程安全
            for (int i=0; i< poolSize; i++) {
                Worker worker = new Worker(this);
                workers.add(worker);
            }
            isServing = true;
        }
        //非阻塞
        boolean submit(Runnable task) {
            if (isServing) {
                return tasks.offer(task); //非阻塞方法
            } else {
                return false;
            }

        }
        //阻塞
        void execute(Runnable task) {
            if (isServing) {
                try {
                    tasks.put(task);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //关闭时，仓库不接受新任务；仓库中已有任务执行完；工作线程不阻塞；已经阻塞的线程要中断
        void shutDown() {
            isServing = false;
            //判断线程集合中的线程状态，中断
            for (Thread worker: workers) {
                if (worker.getState().equals(Thread.State.WAITING) || worker.getState().equals(Thread.State.BLOCKED)) {
                    worker.interrupt();//中断阻塞的线程
                }
            }
        }

        private class Worker extends Thread {
            private MyThreadPool pool;
            public Worker(MyThreadPool pool) {
                this.pool = pool;
            }

            @Override
            public void run() {
                while (isServing || pool.tasks.size() > 0) {//pool关闭，且队列为空后线程停止
                    Runnable task = null;
                    try {
                        if (pool.isServing) {
                            task = pool.tasks.take(); //拿不到时阻塞
                        } else {
                            task = pool.tasks.poll();//关闭时不阻塞获取任务，可能为空
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (task != null) {
                        task.run();//用run普通方法调用，不能用start。若用start会额外创建线程，异步执行，而无法由worker调度。
                        System.out.println("线程执行完毕："+Thread.currentThread().getName());
                    }
                }
            }
        }

    }


    private void use() {
        MyThreadPool pool = new MyThreadPool(10,3);
        for (int i = 0; i< 10; i++) {
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println("创建新线程并放入队列");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("线程被唤醒");
                    }
                }
            });
        }
        pool.shutDown();
    }


2. Future<T> 异步监听线程的运行情况，获取返回值
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

3.请求合并提高性能（hystrix）
每个用户请求对应一个线程，不直接调用服务接口，而是先放入队列。异步线程定时取request，转为batch请求服务接口。优点：减少网络/接口请求数；缺点：延迟。
    class BatchRequest {
        String id;//请求参数
        Future<String> future;//异步获取返回结果
    }

    class MyController {
        MyService service = new MyService();

        @Path("/movie")
        public String getMovie(String id) {
            return service.getMovie(id);
        }

    }

    class MyService {
        MyDao dao = new MyDao();
        LinkedBlockingQueue<BatchRequest> queue = new LinkedBlockingQueue<>();

        private void init() {
            ScheduledExecutorService exService = Executors.newScheduledThreadPool(1);//定时任务，线程数为1
            exService.scheduleAtFixedRate(()->{  //todo：捕获异常
                        int size = requests.size();
                        if (size == 0) return;
                        ArrayList<BatchRequest> requests = new ArrayList<BatchRequest>(); //取出queue中的请求，生成一次批量查询
                        List<String> ids = new ArrayList<>();
                        for (int i = 0; i< size; i++) {
                            requests.add(queue.poll());
                            ids.add(requests.get(i).id);
                        }
                        Map<String, String> responses = dao.getMovies(ids);//返回的是id->content
                        for(BatchRequest req: requests) {
                            req.future.complete(responses.get(req.id)); //将结果放入id对应的request的future中
                        }

                    },
                    0,10,TimeUnit.MILLISECONDS);
        }
        public String getMovie(String id) {
            // return dao.getMovie(id); //非批处理直接返回
            BatchRequest req = new BatchRequest(); //合并不同用户的同类型请求，减少接口调用
            req.id = id;
            CompletableFuture<String> future = new CompletableFuture<>();
            req.future = future;
            queue.add(req);
            return future.get(); //阻塞直到获取返回结果
        }
    }


4.NIO/BIO（socket编程）
java IO三种方式：BIO，NIO(同步非阻塞)，AIO（异步非阻塞）
BIO:
ServerSocket ss = new ServerSocket(port);
        while (true) {
            Socket s = ss.accept();//单线程阻塞等请求
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()), "utf-8");//阻塞等待输入
            process(reader.read());
            s.close();
        }
1)多线程
Socket s = ss.accept();
new Thread(new SocketPorcessor(s).start()); //并发量上万时，线程太多，内存不够
2)线程池
ExecutorService threadPool = Executors.newFixedThreadPool(100);//初始化
threadPool.execute(new SocketProcessor(s));//线程会阻塞等待客户端数据，并发量大时，导致没有线程处理请求，响应时间长，拒绝服务。
3)NIO 有数据才处理。阻塞、非阻塞两种工作方式。非阻塞时，可单/少量线程（#cpu core）处理大量IO连接。=》提高并发量；省硬件
框架：Netty。buffer优化：读写指针，不用flip，移动指针。
Selector：非阻塞模式，可检测多个SelectableChannel，事件机制通知channel处理请求。
Buffer: position, limit, capacity。用xxBuffer.allocate(int)创建buffer；用put写数据；用buffer.flip转为读模式；读取数据。buf.clear：pos=0,limit=capacity。buf.compact:将未读取的数据移到开始。

        Selector selector = Selector.open();

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);//非阻塞
        ssc.register(selector, SelectionKey.OP_ACCEPT);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        int connCnt = 0;
        while(true) {
            int readyChannelCnt = selector.select();//阻塞等待就绪的事件
            if (readyChannelCnt == 0)   continue;
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();
            while(iter.hasNext()) {
                SelectionKey key = iter.next();
                if (key.isAcceptable()) {
                    ServerSocketChannel ssssc = (ServerSocketChannel) key.channel();
                    SocketChannel cc = ssssc.accept();
                    cc.configureBlocking(false);
                    cc.register(selector, SelectionKey.OP_READ, ++connCnt);
                } else if(key.isConnectable()) {

                } else if(key.isReadable()) {
                    pool.execute(new SocketProcessor(key));
                    key.cancel();//取消注册，防止线程处理不及时，重复注册
                } else if (key.isWritable()) {

                }
            }
        }

5.缓存雪崩
只有拿到锁的线程可以访问db，其他等缓存重建

Object getObj(int id) {

    Object obj = redis.get(id);

    if (obj !=null) return obj;

   //缓存失效，高并发场景要容错。并发 --> 同步

    synchronized(Myservice.class) {//锁粒度太粗，阻塞请求其他id的线程

        Object obj = redis.get(id);//再次查询

        if (obj !=null) return obj;

        obj = dbDao.get(id);

        redis.put(id, obj);

    }

    return obj;

}



//记录缓存失效的瞬间，是否正在重建。性能高，且线程安全

ConcurrentHashMap<String, String> cachebuildflag = new ConcurrentHashMap<>();

boolean flag = false;

try {

    flag = cachebuildflag.putIfAbsent(id, "true") == null;//原子操作，而不是先get再set

    if(flag) {//读db，set缓存}

    else{//不等待，则降级。返回固定值；隔一段时间后重试，sleep+getObj。

   

    }

} finally {

    if(flag) cachebuildflag.remove(id);//重建成功后要清除标记

}

高并发读：cache。写：batch、mq、cluster、load balance

6.CAS实现锁  AtomicInteger源码
    int i;//如何原子操作i++
    static Unsafe unsafe = null;//可修改对象值、属性、数组、class等
    static long valueOffset;
    static {
        //unsafe = Unsafe.getUnsafe();//不能直接用，抛SecurityException。改用反射
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);

            //通过unsafe调用底层的硬件原语。无法直接操作内存，通过对象属性的偏移量修改
            valueOffset = unsafe.objectFieldOffset(TEP.class.getDeclaredField("i"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void add() {//局部变量不用加锁，无线程安全问题
        boolean suc = false;
        do {
            int current = unsafe.getIntVolatile(this, valueOffset);//找到对象及其属性,native方法
            suc = unsafe.compareAndSwapInt(this, valueOffset, current, current+1);//cas    
        } while (!suc);//多次循环直到成功
    }
AbstractQueuedSynchronizer：同步组件的基础框架，以static形式出现在其他同步组件中。内部用Node类包装线程，包括pre,next,thread,waitStatus(cancelled,signal,condition,propagate)。head为队首，即持有锁的线程；tail队尾。volatile int state表示锁的状态，CAS()对其操作，0没占用，>0表示锁被当前线程重入的次数。exclusiveOwnerThread，当前锁线程。
ReentrantLock:独占式同步组件，仅一个线程获得锁。FairSync公平锁，用new ReentrantLock(true)（新线程，若锁没有被占，看queue是否有其他等待线程，若有则插入队尾，否则抢锁）；NonFairSync非公平锁。
Semaphore：共享式同步组件。有n个许可，申请线程数<n成功，否则失败。所有对AQS的访问都经过Sync内部类，安全。
伪唤醒：硬件原因导致obj.wait()会在除了obj.notify/notifyAll()外的其他情况被唤醒，但此时不应该被唤醒。用while代替if，while(condition) {obj.wait()}
stack：a、b pop，c push。a先获取锁，发现stack为空，进入waiting队列。c获取锁，放入元素。b获取锁失败，进入blocked队列。c notify。b优先执行，元素-1。a再执行，若没有再检查stack.size则抛异常。

Hashtable用synchronized实现线程安全；hashmap非线程安全；ConcurrentHashMap用juc lock实现。
        //如何让线程阻塞；释放锁后如何通知其他线程；
    class MyReentrantLock implements Lock {
        //Thread owner = null;
        AtomicReference<Thread> owner = null;
        ConcurrentHashMap<Thread, Object> waiters = new ConcurrentHashMap<>();
        @Override
        public void lock() {
            //if (owner == null) owner = Thread.currentThread();//多线程操作不安全,用cas
            while (!owner.compareAndSet(null, Thread.currentThread())) {
                //没获取成功，阻塞线程。wait/notify要与synchronized一起用。用LockSupport.park/unpark
                waiters.put(Thread.currentThread(), null);
                LockSupport.park();//伪唤醒，还没收到unpark就继续执行，所以要在lock中自己移出
                waiters.remove(Thread.currentThread());
            }
        }

        @Override
        public void unlock() {
            if (owner.compareAndSet(Thread.currentThread(), null)) {
                //释放通知
                for (Map.Entry<Thread, Object> entry: waiters.entrySet()) {
                    LockSupport.unpark(entry.getKey());
                }
            }
        }
    }

7.hashmap
数组+链表。key.hashCode()，若%length，效率低，用位计算得到index，&(len-1)。有冲突时头插法插入链表。
默认初始长度：16。必须取2^n,否则无法通过&(len-1)得到取模的效果，hash结果不均匀。
高并发死锁？
java8优化？

8.volatile 变量可见性
全局变量：属性（静态、非静态）；局部变量（本地变量，参数）。ThreadLocal是线程的本地变量。
如何在多线程间共享数据？全局变量：静态变量，或共享对象。
java内存模型：共享变量必须放在主内存中；线程只能操作自己的工作内存，要操作共享变量，要从主内存读到工作内存，修改后再同步到主内存。主内存：堆（共享对象）、方法区（静态变量）。工作内存：栈。

java同步协议中的8种原子操作：lock, unlock, read(从主内存到寄存器), load(寄存器到工作内存)，use（工作内存中使用），assign（修改工作内存的值）,store（工作内存到寄存器）,write（寄存器到主存）。从主内存到工作内存，用read+load，写回用store+write，不原子。
保证变量可见性的关键词：final 不可变，synchronized，volatile。
synchronized：进入同步块前，先清空工作内存的共享变量，从主存重新加载，不是使用时才读；解锁前把修改的共享变量写回主存，不是修改完立即写回。通过锁保护共享变量，不同线程的同步块用同一个锁，才可保证可见。保证安全和可见性。
volatile：线程修改完共享变量后立即写回主内存，其他线程使用前必须先从主存读。使用时加载，且read+load是连续的；修改后立即写回，且store+write连续。没有锁，只能保证可见，不能线程安全。使用比synchronized简单；性能稍好。用于限制局部代码指令重排序；一个线程修改，其他线程使用，如状态标识，数据定期发布有多个使用者。singleton将instance设为static volatile。




