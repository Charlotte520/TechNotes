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
JUC: 声明共享变量为volatile；用cas的原子条件更新实现线程间同步；volatile读写和cas的volatile读写实现通信。    
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

9.jvm内存模型
运行时数据区：线程私有的（程序计数器、虚拟机栈、本地方法栈）；共享的（堆、方法区、常量池、直接内存）。
PC：唯一不会OOM的区域。
虚拟机栈/本地方法栈：每个栈帧包括局部变量表、操作数栈、动态链接、方法返回值。局部变量表：基本数据类型和ref（对象起始地址的引用，或代表对象的句柄）。stackOverFlowError：若栈大小不允许动态扩展，请求栈深度超过max时。OOM：允许动态扩展，内存用完时。
堆：eden，s0,s1,老年代tentired。细致划分可更好的分配内存。先将obj分配到eden，第一次新生代gc后，若对象还存活，进入s0/s1，且age=1。age为15时进入老年代。
方法区：被jvm加载的类、常量、静态变量、JIT编译后的代码。jdk1.8前是堆的一部分，永久代，使jvm像heap一样管理这部分内存。1.8后，用直接内存。-XX:MetaspaceSize       若不指定大小，随着创建更多类，可能耗尽所有系统内存。
常量池：字面量（string、final、基本数据类型的值）；符号引用（类和结构的完全限定名、字段名、方法名）
直接内存：NIO，基于channel、buffer，直接用native函数库分配，通过java堆中的DirectByteBuffer对象引用操作，避免在java heap和native堆间复制数据。

对象创建：类加载检查；分配内存；初始化0;设置对象头；执行init()。
遇到new时，先检查常量池是否能找到类的符号引用，并检查其是否被加载、解析、初始化。
加载后，根据大小分配heap内存。两种分配方式：指针碰撞（当GC是serial、parnew时，mark-compact，用过、没用过的内存间有分界值指针，内存规整无碎片，沿没用过的内存方向将指针移动obj size即可）；空闲列表（GC是CMS，mark-delete，jvm维护列表记录哪些内存块可用，分配一块足够大的给obj）。如何保证多线程分配安全：CAS+失败重试；TLAB（每个线程预先在eden分配一块内存，先在tlab分配，不足时用cas）。
初始化：不包括obj header。使字段可不用赋值直接使用。
header：类型指针：obj是哪个class的实例、如何找到class meta。运行时数据：hashcode、gc年龄、是否用偏向锁、锁状态等。
对象访问：句柄：jvm划分一块句柄池，栈ref存储obj的句柄地址，其中包括obj和class地址，移动obj时修改句柄中的obj地址，ref不变。直接指针：栈ref存储obj地址，obj存储class地址。快。
String：”abc“存储在常量池中。new String("abc")，创建对象。String.intern() native方法，若常量池包含abc则返回常量池引用，否则在常量池创建字符串并返回引用。
基本类型实现常量池，Byte,Short,Integer,Long,Character,Boolean，[-128,127]缓存数据，超出则创建新对象。Float、Double无。

如何判断对象是否可被回收？
1)引用计数：增减频繁消耗cpu、计数器浪费存储空间、无法解决循环引用问题。实际没用
2)可达性分析：从GC roots开始（栈ref、jni ref、static ref、常量ref），遍历引用链。
引用：strong、soft、weak、phantom。强引用，Object obj = new Object()，只要引用在GC不会回收。soft：内存不足时回收，oom前清理。weak：只能生存到下次gc前。phantom：无法获取obj实例，当该obj被回收时收到系统通知。
可达性分析后，没有与roots相连的obj，会被第一次标记，并根据是否有必要执行finalize()筛选。若重写了finalize()，且没被调用过，将obj放入F-Queue队列，由低优先级的finalizer线程执行
WeakHashMap中Entry数组继承WeakReference，每个key对应一个ReferenceQueue。当key被GC时，Entry放入ReferenceQueue。put/get/remove等时，expungeStaleEntries(), weakhashmap从queue中取出相关entry，再到entry数组找到index，从链中去掉entry，value赋值为null。

并发编程：
线程通信：共享内存；消息传递。
线程同步：若共享内存，要显式指定代码段互斥执行；若消息传递，隐式同步。java并发用共享内存模型，隐式通信。

指令重排序：编译器优化（不改变但线程语义时）；指令级并行（cpu改变不存在数据依赖的指令执行顺序）；内存系统（cpu用缓存、读写缓冲区，使加载、存储看似乱序）。=》可能导致多线程的内存可见性问题。
写缓冲区：cpu不用停顿等内存写数据的延迟，保证指令流水线持续运行。且批处理刷新写缓冲，可合并对同一内存地址的多次写，减少对内存总线的占用。但每个cpu的写缓冲区仅对cpu可见，cpu执行内存操作的顺序与内存实际操作的顺序不一致。

10. 高并发分布式ID生成策略
全局唯一，趋势递增，效率高，并发控制。
1) UUID/GUID：按OSF制定的标准计算。用于MAC地址、纳秒级时间、芯片id、cookie中存放第一次访问server返回的jsessionid等。当前日期和时间+时钟序列+全局唯一的机器识别号(MAC地址)。UUID.randomUUID();36位字符串。 优点：简单。缺点：数据库索引效率低；无意义，用户不友好；字符串空间大；集群环境易重复。适合规模不大的单体应用。
2) db自增长：不同库用不同起始值，相同步长；或相同起始，不同步长。缺点：依赖db内部的自增锁，高并发影响性能；操作关联表时先插入父表；db单点故障。
3) snowflake：性能好，易调整；缺点：依赖机器时间，若时间回拨可能导致id重复。
4) redis自增：incr(key)，结合业务id+地区+自增值。优点：可结合业务方便扩展；原子操作保证并发不重复。缺点：第三方依赖；网络开销。

    class Snowflake {
        // 0 (id非负) | 41bit timestamp | 5bit dc | 5bit worker | 12bit seq
        long workerId; //机器id, 5bits，最多32台机器
        long datacenterId; //机房id，5bits，最多32机房
        long sequence; //1ms内生成的多个id的最新序号，12bits，最多4096个
        long seqMask = -1l ^ (-1l << 12);//低12bit为1，高位为0。-1的二进制为全1
        long lastTs = -1l;
        long twepoch = 1288834974657L;

        long workerIdShift = 12l;
        long dcIdShift = 17l;
        long timestampShift = 22l;
        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();//获取当前时间戳，ms
            if (timestamp == lastTs) {
                sequence = (sequence+1)&seqMask;
                if (sequence == 0) {
                    long tmp = System.currentTimeMillis();
                    while (tmp <= lastTs) {
                        tmp = System.currentTimeMillis();
                    }
                    timestamp = tmp;//超过范围，重新获取新时间戳
                }
            } else {
                sequence = 0;
            }
            lastTs = timestamp;
            return ((timestamp-twepoch)<<timestampShift) | (datacenterId << dcIdShift) | (workerId << workerId) | sequence;
        }
    }










