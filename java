g1:
分为eden、survivor、old、humongous 4种region。每个region一个remeber set，记录外部指向本region的所有引用。card：固定大小，每个region分为多个card。优先回收几乎全是garbage的区域，建立stop模型决定每次gc回收哪些region，满足pause time。

《java核心技术36讲》
1.平台理解：
语言特性，如泛型、lambda。基础类库，如collection、io/nio、网络、并发、安全。类加载机制。GC原理。JDK工具。
1)write once,run anywhere 2)GC
JRE提供运行环境，包括jvm、类库。JDK：JRE超集，提供更多工具，包括compiler、诊断工具。
java是否解释执行？javac先编译为.class字节码，运行时jvm解释器转换为机器码。oracle hotspot JVM提供JIT编译器，运行时将热点bytecode编译为机器码，属于编译执行。故为编译和解释的混合模式：server 模式的jvm，收集上万次调用的信息进行高效编译，client 模式 1500次。hotspot内置两个JIT compiler，C1对应client模式，适合对启动速度敏感的桌面应用；C2对应server模式，适合长时间运行的server应用。默认采用分层编译。-Xint：只解释，不编译。-Xcomp：关闭解释器，由于JIT预热会导致jvm启动变慢。jdk9 AOT：ahead of time compilation，直接将bycode编译为机器码，避免JIT预热开销。

2.Exception vs Error
继承关系：Object <- Throwable <- Exception/Error。Throwable是可被catch或throw的基本类型，Exception是执行中可预料的意外，应该被catch并处理，Error是正常情况下不常出现的情况，会导致处于非正常、不可恢复状态，不需catch，如OutOfMemoryError。
Exception分为checked和unchecked异常。checked必须在code中显示catch，由javac检查，如IOException。unchecked是运行时异常，如NullPointerException、ArrayIndexOutOfBoundsException，是代码逻辑可避免，是否catch都可以，编译器不强制。
Error：LinkageError：包括NoClassDefFoundError、UnsatisfiedLinkError、ExceptionInitializerError。VirualMachineError：包括OutOfMemoryError、StackOverflowError。
NoClassDefFoundError vs ClassNotFoundException：exception是当代码中用Class.forName, ClassLoader.loadClass, ClassLoader.findSystemClass动态加载类到内存时，若类及其依赖包不在classpath中，或该类已经被加载，但另一个classloader又试图从同一包中加载时。发生在从外存加载时。该动态加载由代码控制，应该被catch。error是new对象，该类在编译时存在，但运行时找不到定义。可能是打包时漏掉了部分类，或jar包损坏/篡改。发生在内存连接时。是jvm引起的，不应被catch。
异常处理：尽量catch特定exception，而非通用的。不要swallow异常。e.printStackTrace()输出到std error，在生产环境很难找，改用errorlog。throw early：运行可能抛异常的代码前，先检查并抛异常，如Objects.requireNotNull(param)。catch late：若不知道如何处理，保留原cause，再抛出去，由上层业务处理。try-catch：会有额外性能开销，影响jvm优化，仅包住必要代码。不要用exception控制流程，他比if-else等低效。每实例化一个exception，要对当时stack快照，开销大。

3.final、finally、finalize
final：类/方法不能被修改，避免api修改基础功能，保证平台安全。变量/参数，保护只读数据，保证immutable，减少并发时的同步开销。但只能保证final引用不可变，若要对象也不可变，需要类支持。immutable类：final class；所有成员变量为private final，且无set()；构造器通过deep copy初始化，非直接赋值；若有方法可获取对象的内部状态，用copy-on-write，创建私有copy。
finally：用于try-fanally, try-catch-fanally，保证资源必须释放，如lock、db conn。但若在try中return或exit，不会执行finally。也可用try-with-resources减少代码量。
finalize：无法保证执行时机、执行是否符合预期，可能导致GC变慢、经过多个GC周期才能回收，导致死锁、挂起等。用System.runFinalization()也不可预测，回收时的异常会被swallow，程序无法感知。@deprecated。=》java.lang.ref.Cleaner：利用phantomReference，进行post mortem清理，比finalize轻量、可靠。且每个Cleaner的操作有独立线程运行，避免意外死锁。=》临时对象，用完置为null，加快gc。公用对象用资源池复用。用try-finally显式释放。

4.强引用、弱引用、软引用、幻象引用：会影响对象的可达性状态、gc
都是java.lang.ref.Reference的子类，提供get()返回原对象。
strong：普通对象引用。当引用超出作用域，或置为null，可被回收。
soft：oom前清理。用于内存敏感的cache：若有内存，保留cache。不足时清理。可和ReferenceQueue联合使用，若其引用的对象被gc，该ref加入refqueue，通过queue.poll检查对象是否被回收。
weak：不能使对象豁免gc，提供一种访问在弱引用状态下对象的途径，构建无特定约束的关系 。如维护非强制的映射关系，若试图获取对象时还在，就使用他，否则重新实例化。如ThreadLocal。gc扫描时，不管内存是否够，也回收。也可配合RefQueue。
phantom：不能通过他访问对象。对象被finalize后，可用于监控对象。必须配合RefQueue，gc回收某对象时，若还有phantom ref，将该ref加入queue。
若将soft、weak引用的对象，赋值给strong引用，会改变其可达性状态。如 weakref被赋值给static变量，可能mem leak。

5.String、StringBuffer、StringBuilder
StringBuffer：通过synchronized保证线程安全，可修改。StringBuilder非线程安全。二者底层都是byte[]，初始16，若可以预估大小，可先设好，避免频繁扩容。继承AbstractStringBuilder，操作都相同。
String.intern(str)：将str缓存，若常量池已有则直接使用。jdk6中缓存到perm gen，容易oom。jdk7放入heap,且perm gen用metaspace替代，更大。也可用-XX：+UseStringDeduplication，配合G1 GC，由jvm提供去重。
String:不可变 final private char[]，char占2B，jdk9用byte[]+coder编码说明（0表示单字节，1表示UTF-16），减少空间。
String s="abc"：jvm检查常量池是否存在，若有直接返回其引用，否则在常量池中创建，减少重复创建。String s = new String("abc")：编译器将abc放入常量结构，加载类时在常量池创建，new时调用String的构造函数在堆中创建对象，并引用常量池中的abc。new String(s)：在堆中创建。String s1=new String("abc").intern()，s2=new String("abc").intern()，s1/s2的char[]都引用常量池字符串，堆中创建的对象会被gc。但常量池存储的数据越多，遍历越慢。
s.split()：参数长度为1时不用正则。regexp匹配，强大但不稳定，若有回溯会使cpu load高。用indexOf()分割。

6.动态代理
反射：直接操作类、对象，如obj.getClass()，获取类声明的属性、方法，运行时修改类定义。java.lang.reflect包中：Class、Field、Method、Constructor等，可用于操作类/对象。AccessibleObject.setAccessible(bool)，用于开发、测试、依赖注入框架。框架为保证通用性，要根据配置文件加载不同对象，调用不同方法。如ORmapping框架，为obj自动生成get、set，进行加载、持久化数据等。也可用于绕过api访问控制，如nio.DirectBuffer。
动态代理：在运行时构建代理，实现动态的方法调用。用于rpc、aop。静态代理：rmi生成静态stub。每个业务类都要对于一个代理类，不灵活。动态代理：运行时生成代理对象，生成代理对象、调用代理方法要额外时间
jdk proxy:基于反射，业务类必须实现接口。
    interface MyInterface {
        void fun();
    }

    class MyClass implements MyInterface {//被调用者要实现接口

        @Override
        public void fun() {
            
        }
    }

    class MyHandler implements InvocationHandler {
        private Object target;
        public MyHandler(Object t) {
            target = t;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object res = method.invoke(target, args);
            return res;
        }
    }
    
    MyClass c = new MyClass();
    MyHandler handler = new MyHandler(c);
    MyClass proxy = (MyClass)Proxy.newProxyInstance(MyClass.class.getClassLoader(),MyClass.class.getInterfaces(), handler);
    proxy.fun();

cglib：基于asm，创建业务类的子类为代理类。性能高。
aop：oop不适合跨对象分散的逻辑。如不同模块的特定阶段，处理log、用户鉴权、全局性异常处理、性能监控、事务等。

7.int vs Integer
primitive type：boolean、byte、short、char、int、long、float、double。包装类：包括一个private final int value字段存储数据（不可变），提供基本操作：计算、toString、valueOf。jdk可自动boxing/unboxing。Integer.valueOf()工厂方法，Flyweight模式，缓存-128~127间的值。
auto boxing：语法糖。在编译阶段进行，使不同的写法在运行时等价。如Integer i=1; javac自动将int装箱替换为Integer.valueOf()，int unbox=i++；拆箱替换为Integer.intValue()。Boolean：缓存Boolean.TRUE,FALSE。Short:-128~127。Byte：全部都缓存。Character：’\u0000‘~'\u007F'。在性能敏感的场景，创建10w int和Integer的内存（obj header）、速度（primitive存的是值，obj存的是reference，再根据ref得到值的地址）都不是一个数量级，要避免无意中的boxing/un。但obj可generic，更抽象、解决业务的效率高。obj包括：header、instance、padding。header：16B，包括hashcode、lock标志、线程持有的锁、偏向线程id、gc分代年龄等；指向class meta的类型指针。instance存数据，包括父类继承和子类定义的。padding 8B填充。
线程安全计数器：
AtomicLong counter = new AtomicLong();
counter.incrementAndGet();
=》primitive type：
volatile long counter;
AtomicLongFieldUpdater<MyClass> updater = AtomicLongFieldUpdater.newUpdater(MyClass.class,"counter");
updater.incrementAndGet(this);
Integer：cache上限调整：-XX:AutoBoxCacheMax=N，在内部类IntegerCache static代码块中，根据vm参数设置high。通过SIZE,BYTES等常量，使不同平台数据长度相同。
primitive：不能用于泛型。泛型在编译时将类型转为对应类型，必须要继承自Object。无法高效表达数据，如vector、tuple。

8.vector、ArrayList、LinkedList
vector：基于synchronized的线程安全动态数组，内部数组，扩容增加1倍。AL：非线程安全，扩容增加50%，手动缩容trimToSize()。LL：双向链表，非线程安全。可通过Collectins.synchronizedList(List<T> list)将其转为基于sync的线程安全容器。
默认排序算法：Arrays.sort()，Collections.sort()将collection转为Object[],再用Arrays.sort()。对primitive数据，用dual-pivot quickSort。对象类型，用TimSort，归并+二分插入结合。parallelSort：基于fork-join，利用多core cpu。jdk9:List.of()不可变，不能扩容，更紧凑。

Collection.Stream(),parallelStream()：可通过lamda表达式遍历集合，代码简洁，进行aggregate、bulk data operation。大数据量多cpu并行效率高，否则用传统方式。
操作：中间操作（只记录操作，返回stream，不计算），终结操作（计算）。中间操作/懒操作：stateless（元素处理不受之前元素影响，如filter，map，peek，unordered），stateful（要得到所有元素才能继续，如distinct，sorted，limit，skip）。终结操作：short circuit（遇到某些符合条件的元素即可得到结果，anyMatch，allMatch，findFirst，findAny，nonMatch），unshort circuit（全部处理完才能得到结果，forEach，toArray，reduce，collect，max，min，count）。 数据源+懒操作+终结操作 构成pipeline。
stream包：接口类，BaseStream, Stream。BaseStream定义流的基本接口：spliterator，isParallel。Stream定义流操作，map、filter等。ReferencePipeline：抽象类，内部类Head，StatelessOp，StatefulOp，实现Stream接口方法。Sink：接口，定义Stream操作间的协议，begin(),end,cancellationRequested,accept。ReferencePipeline将整个stream操作组装成调用链，链上各stream的上下关系通过sink接口实现。
Head类：定义数据源操作，list.stream()时，初次加载Head对象。再加载Stateful/StateOp，由AbstractPipeline生成stage链表。调用终结操作时，生成最终stage，触发之前的中间操作，从最后一个stage开始递归产生sink链。每个stage包括数据来源+操作+回调函数。

9.hashtable, hashmap, treemap
hashmap性能依赖于hashcode有效性：若equals相等，hashcode一定相等。重写hashcode，也要重写equals。内部Node<K,V>[] table + 链表。若链表长度>8，变成tree。构造函数只设置了capacity,loadFactor等初始值，没有对table初始化。在put()时，lazy load，若table为null，先resize()初始化table。放入新kv时，将hashcode高位移到低位异或，再&(n-1),忽略table容量以上的高位。若元素个数size>threshold，通过resize()扩容。threshold=loadFactor*capacity，默认为0.75*16。newThreshold=old<<1成倍增长。treefiBin()：当bin中节点数量（一边putval一边统计）>8时，若size<64，则resize扩容table。若size>64，进行树化改造。 可通过Collections.synchronizedMap()使其线程安全。
LinkedHashMap：kv维护双向链表，根据插入顺序遍历。也可根据访问顺序，实现LRU cache。

10.线程安全集合
ConcurrentHashmap：分段锁 + volatile value + CAS。Segment[]继承ReentrantLock，数量与entry一致，不再使用。初始化table，用volatile sizeCtl互斥，若发现有线程正在修改，Thread.yield()自旋等待，否则用cas修改sizeCtl，创建table。put(k,v)时，若entry为null，cas放入，否则用synchronized加锁，遍历链表数据，替换或增加新节点到链表中，若链表长度>8，变红黑树。size()，用LongAdd分别算不同entry元素个数，再求和。

11.io
5 io模型：阻塞（创建连接时需要线程处理，若数据未就绪，线程阻塞等待，在accept，connect，read/write都会阻塞），非阻塞（每步都不阻塞，线程轮询检查状态，cpu load高），io复用（select，poll，顺序扫描fd数组是否就绪，fd数量不能过大。epoll，事件驱动，fd放入红黑树，增删效率高），信号驱动（kernel为observer，信号回调为notification，发起io请求时给socket注册回调，不阻塞，内核数据就绪时生成sigio信号，通过回调通知进程操作，用于udp），异步（内核完成等待数据就绪、复制到用户空间后才通知进程，linux未实现）。 java nio用io复用selector实现非阻塞io，封装select/poll/epoll。
java.io：File抽象、Input/OutputStream（读写字节）、Reader/Writer(读写字符，加入字符编解码功能)、BufferedOutputStream（带缓冲区，flush时批处理）等。基于stream模型，同步、阻塞。java.net:Socket,ServerSocket,HttpURLConnection。
nio：channel（文件描述符，可通过DMA将数据在网卡和buffer中复制），selector（多路复用，检测注册其上的多个channel是否就绪，实现单线程对多chennel的管理，基于linux epoll），buffer（数据容器），多路复用、同步非阻塞io。4个监听事件：OP_ACCEPT,OP_CONNECT,OP_READ,OP_WRITE。先创建channel，监听socket连接。再创建多路复用器selector，并将channel注册到selector。通过selector轮询channel，发现某channel就绪，返回监听事件，程序匹配到事件，进行io操作。
aio：基于事件、回调的异步io。
file copy：内核态将数据从磁盘dma读到内核缓存，再切换到用户态，cpu将数据从内核缓存读取到用户缓存。nio transferTo：不需用户态参与，0copy。Files.copy()：用native方法实现用户态拷贝，不经过内核态。

线程模型优化：事件驱动
同步io：Reactor模型。io事件注册到多路复用器，触发io事件时，多路复用器将其分发到处理器。包括acceptor：接收连接请求；reactor：将连接注册到reactor，由selector监听并dispatch；handlers。
单线程reactor模型：所有io操作只有1线程。无法上万连接。
多线程reactor：单acceptor线程，worker线程池处理请求。
主从reactor：acceptor主线程监听连接事件，从线程监听io事件，worker线程池。主流。

12.synchronized
提供三种不同monitor实现：biased lock、轻量级锁、重量级锁。jvm检测到不同竞争状况，自动切换到对应锁实现，升级/降级。若无竞争，用biased，用cas在obj header中mark word设置threadId，表示该obj现在偏向当前线程。若有线程试图锁定已被偏斜过的obj，jvm撤销biased lock，切换到轻量锁。通过cas mark word试图获取锁，若成功则使用轻量锁，否则升级到重量锁。jvm进入safe point时，检查是否有闲置的monitor，降级。
自旋锁：while(true)+cas，竞争锁失败的线程，在os层面不真正挂起等待，jvm让线程做几个空循环等待（假设很快就能获取锁），若几次循环后能获取则进入临界区，否则os挂起。可减少线程阻塞，适合锁竞争不激烈、占用时间短的场景。但自旋会占用cpu。

13.死锁
jstack：获取线程栈，定位线程间的依赖关系。区分线程状态，查看waiting目标，对比monitor等持有状态。重启。
原因：互斥；不可抢占；循环等待。解决：一次性加锁；按顺序加锁；指定锁超时，得不到锁有相应退出逻辑。ReentrantLock.tryLock()：若对象恰好没加锁，直接获取锁。非公平插队。
若某线程死循环，占用锁，导致其他线程饥饿。可先找到占用cpu最多的线程，再jstack其线程栈，排查代码。

14.juc
CountDownLatch：线程A等其他线程完成后才执行。不可重置。countDown/await。调用await的线程阻塞等countDown为0。操作的是事件。
CyclicBarrier：一组线程互相等待至某状态，再同时执行。await，当所有线程都await达到某值，才继续并自动重置。操作的是线程。用于等并发线程结束。
Semaphore：限制多个同时工作的线程数。acquire，release。
为什么没有ConcurrentTreeMap？有序时用ConcurrentSkipListMap。TreeMap基于红黑树，插入/删除节点时要平衡操作，并发时很难控制粒度。SkipList通过层次提高效率，但便于并发控制。
ConcurrentLinkedQueue：concurrentxx基于cas+aqs，lock-free，吞吐较高。若遍历时发生修改，抛出ConcurrentModificationException，停止遍历。copyonwritexx：修改开销重，适合读多写少场景。遍历时若容器已修改可继续。size()不一定准确。
LinkedBlockingQueue：基于putlock+takelock+condition(not full/empty)，锁粒度比array细，并提供阻塞等待。有界。ArrayBlockingQueue。空间更紧凑。lock+putindex+takeindex。
线程池：Executors：提供各种静态工厂方法。newCachedThreadPool，处理大量短时间任务，内部用SynchronousQueue，先缓存thread并重用，若无thread，创建新worker thread，若thread>60s没有task，终止并移除cache。newFixedThreadPool，指定最多的#thread num，无界队列。newSingleThreadExecutor，worker thread1个，无界队列，保证任务按顺序执行。newSingleThreadScheduleExecutor，newScheduledThreadPool，创建ScheduledExecutorService，可定时/周期性调度。newWorkStealingPool，内部创建forkJoinPool，并行处理任务，不保证顺序。
Executor接口提供：execute(Runnable)提交任务。ExecutorService接口：继承Executor，并提供Future<T> submit(Callable<T>)，可获取返回值。实现类：ThreadPoolExecutor，ScheduledThreadPoolExecutor，ForJoinPool。
AtomicInteger：unsafe.cas() + volatile int value。业务代码如何cas：AtomicLongFieldUpdater<MyClass>，基于反射创建，AtomicLongFieldUpdater.newUpdater(MyClass.class, "fieldName")。用updater.compareAndSet(this, oldval, newval)。cas：适合短暂竞争，重试少的场景。否则要限制自旋次数，减少cpu消耗。ABA问题：用AtomicStampedReference，为对象引用增加stamp，保证cas正确。
AQS：AbstractQueuedSynchronizer。volatile int state 表示状态，FIFO等待线程队列(Node组成的双向链表)，cas基础操作方法，子类实现acquire/release。子类如ReentrantLock, Worker：通过state反应锁的持有情况。

ThreadPoolExecutor：任务队列+线程队列。状态：running（接受新任务，处理队列任务），shutdown（不接受新任务，处理队列），stop（不接受，不处理，中断正在进行的任务），tidying（所有任务都结束，线程队列为0）,terminated（清理）

23.类加载
加载、链接、初始化。load：从.jar、.class中读取ClassFile，可能ClassFormatError。link：验证，可能VerificationError；准备，为类/接口中的静态变量分配空间；解析，将常量池中的符号引用转为直接引用。init：静态字段赋值，执行static{}。
parent delegation model：bootstrap加载jre/lib下的jar。Extention：加载jre/lib/ext下的jar。application：加载classpath。

24.运行时动态生成java类


java并发编程：
分工：如何高效拆解任务并分配给线程 (Fork/Join)；同步：线程间如何协作 (CountDownLatch)；互斥：如何保证同一时刻只有一个线程访问共享资源(ReentrantLock)。
为了提高CPU性能：cpu增加cache；OS增加进程、线程，分时复用cpu；编译器优化指令执行次序，更有效利用cache。=》问题：cache导致可见性、编译优化导致有序性、线程切换导致原子性 =》java内存模型：按需禁用cache、编译优化（volatile变量），synchronized。底层通过memory barrier强制将cache刷新到memory。
死锁：互斥，占有且等待，不可抢占，循环等待 =》一次申请所有资源。
Object.wait()、Thread.sleep()：wait释放资源，sleep不释放；wait需要被唤醒；wait需要获取monitor，否则抛异常。
并发容器：非线程安全 ArrayList、HashMap；线程安全：ConcurrentHashMap

加锁时需要明确锁对象：synchronized func()：是对this加锁。synchronized static func()：是对A.class对象加锁。对同一对象加多个不同锁，相当于没加锁，编译优化时会去掉所有锁。临界区需要对多个对象加锁，需要定义private final Object lock = new Object()； synchronized(lock){}。

管程monitor：与semaphore等价，管理共享变量及其操作，使其支持并发。1)用synchronized、wait、notify(all)实现。由编译器自动生成加锁、解锁代码，仅支持一个条件变量。2)用lock+内部condition。可支持多condition，但要自己加解锁。  互斥：monitor X将共享变量queue和enq()，deq()操作封装起来，要访问queue只能通过enq、deq，这两个操作保证互斥，入口等待队列保证只有一个线程进入。同步：条件变量A，B，每个条件变量都有一个等待队列。若线程T1条件A不满足，则A.wait()进入A的条件队列，允许其他线程进入monitor。当T2使T1条件满足后，T2调用A.notify()通知A队列的线程，T1出条件队列，到入口等待队列重新排队。 timeout参数：若没有T2 notify，T1 timeout后直接到入口等待队列重新排队检查条件。

生命周期：通用：init(已创建但不能被分配cpu，如创建thread对象，但os还没创建对应的线程) -> runnable（t.start()后，os线程已创建，可分配cpu） -> running -> sleep（调用阻塞api，等待某事件，放弃cpu使用权） -> terminate（执行完，异常）. java：将runnable和running合并，细化sleep。new -> runnable -> (blocked,waiting,timed_waiting) -> terminated。线程等待synchronized隐式锁，进入blocked。调用阻塞api等io时仍为runnable。waiting：进入synchronized并调用Object.wait()；Thread.join()，T1调用T2.join()，T1进入waiting，等T2执行完，T1进入runnable；LockSupport.park()，当前T进入waiting，LockSupport.unpark(T2)，T2从waiting到runnable。timed_waiting：Thread.sleep(ms); synchronized中Object.wait(timeout)；LockSupport.parkNanos(ms)；LockSupport.parkUtil(ms)。terminated：执行完run()；stop()会杀死线程，不释放锁，Deprecated；interrupt()通知线程，T可通过捕获InterupttedException或isInterupted()主动检测，执行后续操作，如unlock()。

线程数：cpu密集：#T=#cpu+1，当T缺页失效，或其他原因阻塞时，执行额外T。io密集：#T=[（IO时间/cpu时间）+1]*#cpu core  压测：根据初始值逐步增加，开始吞吐增加，延迟缓慢增加。T增加到一定值，吞吐开始下降，延迟迅速增加，此时为max thread。nginx用非阻塞io，多进程单线程，是io密集型，但进程数=#cpu core。

并发策略：避免共享（利用thread本地存储，每个task分配独立thread）；不变模式（Actor模式、CSP模式、函数式编程）；monitor。

logger.debug("info"+info);  => logger.debug("info:{}", info); 嵌套调用时先计算参数，再将参数压栈，再执行方法。{}占位符只压栈不计算。
private final/static Object lock=new Object();不能用int/string，可能会变。且int会缓存-128-127间的值，string会缓存到常量池，会被重用，若其他线程也用到相同值，加锁不释放，则死锁。

并发工具类：
1.Lock：synchronized申请不到资源则阻塞，无法释放已有资源，不能破坏思索条件中的不可抢占。=》互斥锁：能响应中断（持有锁A的线程，再获取锁B失败，阻塞。发送中断信号，可唤醒线程并释放锁A）；可超时（若T一段时间没有获取锁B，不阻塞，返回error，也可释放锁A）；非阻塞获取锁（获取锁B失败，不阻塞，直接返回error）。避免不可抢占。即：Lock中的 lockInterruptibly() 可中断；tryLock(time)可超时；tryLock() 非阻塞。lock内部通过volatile state，happen-before规则保证变量的修改在锁释放后，一定能被其他T看到。
可重入锁：持有锁的线程可重复获取该锁。公平锁/非：唤醒入口等待队列中的线程时，根据等待时间，先入先出。非公平锁不保证，锁被释放时，若有线程来获取锁，则直接获取，不用排队，默认。
最佳实践：永远只在更新对象的成员变量时加锁;永远只在访问可变的成员变量时加锁;永远不在调用其他对象的方法时加锁。减少锁持有时间；减少锁粒度。
系统停止响应，cpu利用率低，大概率死锁。
2.condition：lock&condition实现的monitor中用await、signal(all)。synchronized实现的monitor用wait,notify(all)。

class BlockedQueue<T> {
    final Lock lock = new ReentrantLock();
    final Condition notFull = lock.newCondition();
    final Condition notEmpty = lock.newCondition();

    List<T> list = new ArrayList<>();
    int capacity = 10;

    void enq(T x) {
        lock.lock();
        try {
            while (list.size() == capacity) { //用while，线程被notify后需要重新进入monitor等待队列，从wait的下一句继续执行，等执行时需要再次判断condition是否依旧满足
                notFull.await();
            }
            list.add(x);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    void deq() {
        lock.lock();
        try {
            while (list.size() == 0) {
                notEmpty.await();
            }
            list.get(list.size()-1);
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }
}

3. 异步转同步：RPC调用：同步。但底层TCP是异步的，不等返回结果。rpc框架将调用方阻塞为timed_waiting。，如dubbo通过DefaultFuture.get()。
class MyFuture {
    private final Lock lock = new ReentrantLock();
    private final Condition done = lock.newCondition();
    Object response = null;

    Object get(int timeout) { //调用方通过get()等待结果
        long start = System.currentTimeMillis();
        lock.lock();
        try {
            while (!isDone()) {
                done.await(timeout);
                long cur = System.currentTimeMillis();
                if (isDone() || cur-start > timeout) break;
            }
        } finally {
            lock.unlock();
        }
        if (!isDone()) throw new TimeoutException();
        return response;
    }

    boolean isDone() {
        return response != null;
    }

    void doReceived(Object res) { //rpc结果返回时调用
        lock.lock();
        try {
            response = res;
            if (done != null) done.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

4.semaphore ： PV原语。
计数器、等待队列。通过init(), down(),up()原子方法访问。init设置初始值；down使其-1，<0时当前T阻塞；up +1,若counter<=0，唤醒等待队列中一个T，从队列中移出。  java.util.concurrent.Semaphore, 用acquire(), release()
    int count;
    final Semaphore s = new Semaphore(1);
    void add() {
        s.acquire();
        try {
            count+=1;
        } finally {
            s.release();
        }
    }
用于允许多个线程访问临界资源，如connection pool。不能同时唤醒多个T竞争锁，只能唤醒一个。且没有condition，唤醒后的T直接运行，不检查临界条件。实现阻塞队列很麻烦。
class MyPool<T,R> {
    final List<T> pool;
    final Semaphore s;
    
    MyPool(int size, T t) {
        pool = new Vector<T>();//vector线程安全，不能用arrayList。多个线程进入临界区remove、add会并发。
        for (int i =0; i < size;i++) {
            pool.add(t);
        }
        s = new Semaphore(size);
    }
    
    R exec(Function<T,R> func) { //利用pool中对象调用func
        T t = null;
        s.acquire();
        try {
            t = pool.remove(0);
            return func.apply(t);
        } finally {
            pool.add(t);
            s.release();
        }
    }
}

5.ReadWriteLock：读多写少。多线程同时读，只有一个线程写；若一个线程正在写，不允许读。

    class Cache<K,V> {//cache与db的一致性问题：expire time；解析binlog，更新变化；MQ；双写。
        final Map<K,V> map = new HashMap<>();//非线程安全
        final ReadWriteLock lock = new ReentrantReadWriteLock();//优化：按key申请不同lock，减少冲突。

        V get(K k) {
            V v = null; 
            lock.readLock().lock();//writelock支持newCondition()，readlock不支持
            try {
                v = map.get(k); //不支持在此处readlock升级为writelock，必须先释放readlock。只能降级。
            } finally {
                lock.readLock().unlock();
            }
            
            if (v != null) {
                return v;
            }
            
            lock.writeLock().lock();//按需加载，加写锁后要再次检查，防止其他线程已经更新过
            try {
                v = map.get(k);
                if (v == null) {
                    //查询db得到kv
                    map.put(k,v);
                }
            } finally {
                lock.writeLock().lock();
            }
            return v;
        }

        void put(K k, V v) {
            lock.writeLock().lock();
            try {
                map.put(k,v);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

6.StampedLock：写锁，悲观读锁，乐观读（无锁）。比ReadWriteLock读性能更好。不可重入。不支持condition。
若线程阻塞在read/writelock，调用T.interrupt()，会导致cpu飙升。要中断用read/writeLockInterruptibly()
    int x;
    StampedLock s = new StampedLock();
    
    int add() {
        long stamp = s.tryOptimisticRead(); //先乐观读，读完检查x是否被修改，若修改则升级为悲观读锁
        int tmpx = x;
        if (!s.validate(stamp)) {
            stamp = s.readLock();
            try {
                tmpx = x;
            } finally {
                s.unlockRead(stamp);
            }
        }
        tmpx++;
        return tmpx; 
    }
7.CountDownLatch & CyclicBarrier
用线程池时，主线程无法通过t1.join()等T1退出 =》CDL用于一个T等待多个T的场景。CB用于一组线程间互相等待。CDL计数器不能循环利用，减为0后，再有线程调用await()，直接通过。CB计数器可循环利用，减为0后自动重置为初始值。可设置回调函数。
        Executor executor = Executors.newFixedThreadPool(2);//读订单、派送单并行，再串行执行process
        while (存在未对账订单) {
            CountDownLatch cdl = new CountDownLatch(2);
            executor.execute(() -> { //启动线程池中的一个线程读取未对账订单
                pos = getPOrders();
                cdl.countDown();
            });
            
            executor.execute(() -> {//启动线程池中的一个线程读取派送订单
                dos = getDOrders();
                cdl.countDown();
            });
            
            cdl.await(); //等待线程查询结果结束
            process(pos,dos);
        }

    Vector<P> pos;//订单、派送单队列，用于读取线程和处理线程传输数据
    Vector<D> dos;
    Executor executor = Executors.newFixedThreadPool(1); //1个线程处理队列消息，避免两个队列的读取错乱。使读订单、派送单，写process都并行
    CyclicBarrier cb = new CyclicBarrier(2, () -> {//该回调由最后一个执行cb.await()的线程t1/t2执行，同步调用process()，再开始第二回合。若该回调由另一线程异步执行，则t1,t2可立即开启下一回合。
        executor.execute(() -> process());
    });
        
    void process(){
        //回调函数
        executor.execute(() -> {
        P p = pos.remove(0);
        D d = dos.remove(0);
        process(p,d);

    }
                
    void check() {
        Thread t1 = new Thread(() -> {//线程不会反复创建，可不用线程池
            while (存在未对账订单) {
                pos.add(getPOrders());
                cb.await();
            }
        });
        t1.start();

        Thread t2 = new Thread(() -> {
            while (存在未对账订单) {
                dos.add(getDOrders());
                cb.await();
            }
        });
        t2.start();
    }
 
 8.并发容器
 容器：List、Map、Set、Queue。其中ArrayList、HashMap非线程安全。Collections类中提供包装类，可将所有方法加synchronized使其线程安全，称为同步容器。Vector、Stack、Hashtable不基于包装类，但也是基于synchronized实现，遍历时也需要加锁。 性能差。
         List list = Collections.synchronizedList(new ArrayList());//包装类型为SynchronizedList
        synchronized (list) {
            Iterator it = list.iterator(); //用iterator遍历容器非线程安全，需要加锁。在Collections.synchronizedList中加锁的mutex就是this，所以可以用list加同一把锁
            while (it.hasNext()) {
                process(it.next());
            }
        }
        
 并发容器：CopyOnWriteArrayList； ConcurrentHashMap, ConcurrentSkipListMap； ConcurrentSkipListSet, CopyOnWriteArraySet；BlockingDeque(LinkedBlockingDeque), BlockingQueue(ArrayBlockingQueue, LinkedBlockingQueue, SynchronousQueue, LinkedTransferQueue, PriorityBlockingQUeue, DelayQueue), ConcurrentLinkedQueue, ConcurrentLinkedDeque.
 
CopyOnWriteArrayList：写时将共享变量复制一份，可无锁读。内部array指向数组，读基于array，写时要加锁，使写操作互斥，将array复制一份，在新数组更新，再将array指向它。适合写很少，且能容忍短暂读写不一致。Iterator只读，不能增删改，因为遍历的是快照。
ConcurrentSkipListMap：key有序。ConcurrentHashMap：key无序。kv都不能为空。hashmap kv都可为null 非线程安全。treeMap k不能为null，v可null，非线程安全。hashtable、cskp、chp kv都不能为null，线程安全。hashmap 1.8以前，put会扩容，并发可能导致链表有环，cpu 100%。此时jstack查看方法调用栈会卡在hashmap的方法，或用dump线程栈分析。1.8后链表用红黑树可很大程度避免。
Queue：阻塞vs非阻塞，队满入队阻塞，队空出队阻塞，blocking。单端vs双端，单端queue只能队尾入，队首出，双端deque两端都可出入。要考虑queue是否支持capacity有界，防止oom。
单端阻塞队列：ArrayBlockingQueue 内部持有capacity数组，LinkedBlockingQueue 内部持有capacity链表，SynchronousQueue 内部无队列，生产者入队要等消费者出队，LinkedTransferQueue融合LinkedBlockingQueue和SynchronousQueue，性能更好。PriorityBlockingQUeue按优先级出队，DelayQueue 延时出队。
双端阻塞队列：LinkedBlockingDeque
单端非阻塞队列：ConcurrentLinkedQueue。双端非阻塞：ConcurrentLinkedDeque

9.原子类
互斥锁要加锁、解锁消耗性能，拿不到锁的线程进入阻塞状态，有线程切换开销。=》无锁：cas(memAddr,compareVal,newVal)。自旋：循环尝试。ABA问题：compareVal被其他线程多次更新又变成原值，更新对象可能改变属性。=》加递增version比较。性能好，无死锁问题，但自旋可能导致饥饿、活锁。但只有一个共享变量，多共享变量时用互斥锁。
基本类型：AtomicBoolean,AtomicInteger,AtomicLong. 引用：AtomicReference, AtomicStampedReference, AtomicMarkableReference，后两个可解决ABA问题. 数组：AtomicIntegerArray, AtomicLongArray, AtomicReferenceArray. 累加器：DoubleAccumulator, DoubleAdder,LongAccumulator,LongAdder.累加更快，但不支持compareAndSet。 对象属性更新：AtomicIntegerFieldUpdater,AtomicReferenceFieldUpdater。基于反射，属性必须是volatile。

10.线程池
创建对象，jvm在堆里分配一块内存。创建线程，要调用os api，分配一系列资源，是重量级对象，应避免频繁创建、销毁。
线程池设计：producer-consumer模式，使用者是prod，线程池本身是consumer 。
ThreadPoolExecutor：corePoolSize 最小线程数，maximumPoolSize 最大，keepAliveTime&unit 若某线程空闲了该时间没有任务，则回收。workQueue。threadFactory 可自定义如何创建线程。handler 自定义任务拒绝策略，当所有线程都忙，且workqueue已满，可指定CallerRunsPolicy 由提交任务的线程去执行该任务，AbortPolicy 默认，抛出RejectedExecutionException，DiscardPolicy 丢弃，DiscardOldestPolicy 丢弃最老，并加入新任务。  通过execute()提交的任务，若在运行时异常，会导致线程终止，且无法捕获异常。所以要cathch RuntimeException、Throwable。
Executors默认使用无界的LinkedBlockingQueue，易oom。
给线程池默认名称：pool-1-thread-2。自定义设置名称：
1)      ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("myname");
2)    class CustomThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread("myname");
            return t;
        }
    } 
 ThreadPoolExecutor pool = new ThreadPoolExecutor(100,120,TimeUnit.SECONDS,new LinkedBlockingQueue<>(), new CustomThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

11.future
ThreadPoolExecutor.execute()提交任务后无法获得返回值。Future<T> submit(Runnable/Callable)，其中Runnable无返回值，返回的future只能用于判断任务是否已完成，类似Thread.join()；Callable可通过call()获取返回值。Future接口提供：cancel()，isCancelled(), isDone()，阻塞 get(), get(timeout) .
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Result r = new Result();
        Future<Result> future = executor.submit(new Task(r), r);//Task implements Runnable，传入result对象，在run()中操作。可在主子线程间共享数据。
        Result fr = future.get();

FutureTask工具类：实现了Runnable、Future接口，可作为任务提交给ThreadPoolExecutor，或被Thread执行，也可通过get()获取结果.
        FutureTask<Integer> ft = new FutureTask<Integer>(() -> 1+2);
//        Thread t = new Thread(ft);
        ExecutorService es = Executors.newCachedThreadPool();
        es.submit(ft);
        Integer result = ft.get();

CompletableFuture:简化异步编程。4个静态方法，runAsync(Runnable，Executor) 无返回值, supplyAsync(Supplier<U>) get()返回值。默认用ForJoinPool线程池，创建线程数等于cpu核数，所有CompletableFuture共享同一线程池，若某些任务执行很慢的IO操作，会导致所有线程都阻塞在IO上，产生饥饿，要根据不同业务创建不同线程池。实现CompletionStage接口：描述任务间的时序关系，串行、并行、and汇聚、or汇聚。
        CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> {dosth1;}); //不能获取结果
        CompletableFuture<Void> f2 = CompletableFuture.supplyAsync(() -> {dosth2;}); //可获取结果
        CompletableFuture<Void> f3 = f1.thenCombine(f2, () -> {dosth3;}); //将f1，f2执行结果汇聚

CompletionService：批量执行异步任务。内部维护阻塞队列，任务结束后将结果的future对象加入队列。实现类ExecutorCompletionService。获取结果take()阻塞，poll非阻塞。
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CompletionService<Integer> cs = new ExecutorCompletionService<Integer>(executor);
        List<Future<Integer>> futures = new ArrayList<>();
        futures.add(cs.submit(() -> getFrom1()));//异步从3个服务提供商获取结果，只要有一个返回结果即可。类似dubbo forking cluster。
        futures.add(cs.submit(() -> getFrom2()));
        futures.add(cs.submit(() -> getFrom3()));
        
        Integer r = 0;
        try {
            for (int i = 0; i < 3; i++) {
                r = cs.take().get();
                if (r != null) {
                    break;
                }
            }
        } finally {
            for (Future<Integer> f: futures) {//取消其他异步线程
                f.cancel(true);
            }
        }
        return r;
简单的并行任务，用线程池+future。若任务间有聚合关系，completableFuture。批量并行，用completionservice。

12.fork/join：适合分治任务，fork分解，join合并。包括线程池ForkJoinPool，ForkJoinTask两部分。
ForkJoinTask抽象类：fork() 异步执行子任务,join() 阻塞当前线程等子任务结果。抽象子类RecursiveAction，RecursiveTask，递归处理子任务，其抽象方法Action.compute()无返回值，Task.compute()有返回值。
ForkJoinPool:内部有多个任务队列（deque），通过invoke/submit()提交任务时，根据一定路由规则提交到某一队列。若某任务执行时创建子任务，则放入当前线程的队列。若某workerThread队列为空，可从其他队列的另一端获取任务，
    class Fibonacci extends RecursiveTask<Integer> {
        final int n;
        Fibonacci(int n) {
            this.n = n;
        }

        @Override
        protected Integer compute() {
            if (n <= 1) return n;
            Fibonacci f1 = new Fibonacci(n-1);
            f1.fork();//异步执行子任务
            Fibonacci f2 = new Fibonacci(n-2);
            return f2.compute()+f1.join();//若用f1.fork,f2.fork，需要f2.join,f1.join，否则会有性能问题。若f1.join先于f2.compute，会先阻塞在join，等join完才compute，降低并行度
        }
    }

        ForkJoinPool pool = new ForkJoinPool(4);
        Fibonacci fib = new Fibonacci(30);
        Integer result = pool.invoke(fib);


并发设计模式
1.Immutability：不变性。
所有属性都设为final，只允许只读方法，final class不允许子类覆盖方法。若需要修改，则创建一个新不可变对象。如String、Long、Integer、Double。
创建对象太多，浪费内存=》Flyweight pattern：对象池，创建新对象前先检查池中是否存在，不存在再创建并放入。Long内部维护static cache，缓存[-128,127]间的数字，在jvm启动时创建。
final MyClass c; c不能修改，但c.field可以修改。
2.copy-on-write：延时策略，当真正需要复制时才按需复制。
适合对读性能要求高，读多写少，弱一致性场景。如os fork()，子进程不复制父进程整个地址空间，等父/子需要写入时才复制，使二者有独立地址空间。
3.ThreadLocal：避免共享
Thread类中有私有属性threadLocals，类型为ThreadLocalMap，其中key为ThreadLocal，value为该线程的数据。ThreadLocal内部不保存数据，可代理从Thread中获取数据。ThreadLocalMap对ThreadLocal的引用是weakReference，当Thread对象被回收时，ThreadLocalMap也能被回收。避免内存泄露。线程池中thread存活时间太长，导致ThreadLocal.map一直不被回收，且map.Entry对ThreadLocal是weakReference，故当ThreadLocal结束生命周期可被回收，但entry.value是强引用，不能被回收，导致内存泄露。=>try{}finally{}手动释放资源,threadlocal.remove()
子线程无法继承父线程的threadlocal，要用InheritableThreadLocal
    class TreadLocal<T> {
        class Entry extends WeakReference<ThreadLocal> {
            Object value;
        }
        
        class ThreadLocalMap {
            Entry[] table;
            
            Entry getEntry(ThreadLocal key) {
                
            }
        }
        
        T get() {
            ThreadLocalMap map = Thread.currentThread().threadLocals;
            Entry e = map.getEntry(this);
            return e.value;
        }
    }
    class Thread {
        ThreadLocal.ThreadLocalMap threadLocals;
    }

4.Guarded Suspension：等待唤醒，解决发送消息、处理结果的线程不是同一个
    class GuardedObject<T> { //异步转同步
        T obj;//受保护对象
        final Lock lock = new ReentrantLock();
        final Condition done = lock.newCondition();
        final int timeout = 1;
        final static Map<Object, GuardedObject> map = new ConcurrentHashMap<>();

        static GuardedObject creat(K key) {
            GuardedObject g = new GuardedObject();
            map.put(key,g);
        }
        
        static void fireEvent(K key, T obj) {
            GuardedObject g = map.remove(key);
            if (g != null) {
                g.onChange(obj);
            }
        }
        
        T get(Predicate<T> p) { //获取对象
            lock.lock();
            try {
                while (!p.test(obj)) { //检查条件是否满足，不满足则阻塞等待
                    done.await(timeout, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
            return obj;
        }

        void onChange(T obj) {
            lock.lock();
            try {
                this.obj = obj;
                done.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
使用：
        Message msg = new Message(id,content);
        GuardedObject<Message> go = GuardedObject.create(id);//生成msg，并放入发送队列
        send(msg);
        Message res = go.get(t -> t != null); //阻塞等待返回结果

        void onMessage(Message msg) { //当收到返回结果时，调用该回调函数，找到对应请求返回
            GuardedObject.fireEvent(msg.id, msg);
        }

5.balking：多线程版本if，用互斥锁实现，可用双重检查优化性能
    class AutoSaveEditor {
        boolean changed = false;//判断文件是否被修改过
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();//单线程任务池

        void startAutoSave() {
            s.scheduleWithFixedDelay(() -> {
                autoSave();//定时执行
            },5,5,TimeUnit.SECONDS);
        }

        void autoSave() {
            synchronized (this) {
                if (!changed) {//双重检查
                    return;
                }

                changed = false;
                //执行save操作
            }
        }

        void edit() {
            synchronized (this) {
                changed = true;
            }
        }
    }

6.thread-per-message模式：分工。
http server在主线程接收请求，委托子线程异步处理。
java线程与os线程一一对应，将线程调度权交给os，优：稳定、可靠，缺：创建成本高。=》线程池。另一方案：轻量级线程，go、lua。创建成本类似普通对象，速度快，内存小，可用来做thread-per-message。java轻量级线程fiber。

7.worker thread模式：避免重复创建线程
提交到相同线程池的任务要相互独立，不能有依赖关系，避免所有线程都阻塞，无法继续。
任务异常处理。ThreadLocal内存写了问题。有界队列。

8.两阶段终止：t1给t2发送终止指令;t2响应指令。
t.interrupt()：从休眠转换到runnable，设置标志位。t在合适时机检查标志，退出run().
pool.shutdown()/shutdownNow()。shutdown：拒绝接收新任务，等正在执行和队列中所有任务执行完，关闭线程池。shutdownNow：拒绝新任务，中断正在执行的任务，返回队列中不能执行的任务。

9.生产者消费者：解耦。异步，平衡二者速度差异。批量执行。分阶段提交：如log4j2中appender，可异步刷盘，error立即刷，数据500条立即刷，5s内没刷过则立即刷。
   class Logger {
        final BlockingQueue<LogMsg> q = new LinkedBlockingQueue<>();
        final int batchSize = 500;
        ExecutorService s = Executors.newFixedThreadPool(1);//单线程刷盘
        final LogMsg poisonPill = new LogMsg();//毒丸对象，producer要关闭时发送该对象，consumer读取消息，先判断，若是毒丸则自我销毁
        
        void start() {
            File logfile = File.createTempFile("mylog",".log");
            final FileWriter writer = new FileWriter(logfile);
            s.execute(() -> {
                flush();
            });
        }

        void flush(File logfile, FileWriter writer) {
            try {
                int curIdx = 0;
                long pretime = System.currentTimeMillis();
                while (true) {
                    LogMsg log = q.poll(5,TimeUnit.SECONDS);
                    if (log != null) {
                        if (poisonPill.equals(log)) break;
                        writer.write(log.toString()); //先暂存到内存
                        curIdx++;
                    }
                    if (curIdx <= 0) continue;//没有数据
                    if (log != null && log.level ==ERROR || curIdx == batchSize || System.currentTimeMillis()-pretime>5000) {
                        writer.flush();//刷盘
                        curIdx = 0;
                        pretime = System.currentTimeMillis();
                    }
                }
            } finally {
                try {
                    writer.flush();
                    writer.close();
                } catch (Exception e) {

                }
            }
        }
    }

框架：
1.guava ratelimiter：匀速
令牌桶算法token bucket：令牌以固定速率添加到桶中，若限流r/s，则令牌每1/rs产生一个。桶容量为b（允许的最大突发流量），若容量已满，则丢弃新令牌。请求通过limiter.acquire()获取令牌，否则阻塞。并发量很高时，定时器精度误差很大，guava不用定时器，记录并动态计算下一token发放的时间。支持warmup，初始流速r很小，但动态增长。
漏桶算法leaky bucket：请求注入桶，按一定速率流出。
class RateLimiter {
        long tokens = 0;//当前桶中令牌数
        long max = 3;//桶容量
        long next = System.nanoTime();//下次令牌产生时间
        long interval = 1000000000;//令牌发放间隔，ns

        void acquire() {
            long now = System.nanoTime();//申请令牌时间
            long at = reserve(now);//预占令牌
            long wait = Math.max(at-now,0);
            if (wait>0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(wait); //若尚无令牌发放，sleep等待
                } catch (InterruptedException e) {}
            }
        }

        synchronized long reserve(long now) {
            resync(now);
            long at = next;//可获取令牌的时间
            long fb = Math.min(1,tokens);//桶中能提供的令牌数
            long nr = 1-fb;//还需要多少令牌
            next = next+nr*interval;//重算下一令牌发放时间
            tokens -=fb;//重算桶中令牌数
            return at;
        }

        void resync(long now) {//若请求时间在下一令牌产生时间之后，则重算令牌数，并将下一令牌发放时间置为当前时间
            if (now>next) {
                long newToken = (now-next)/interval;//新令牌数
                tokens = Math.min(max, tokens+newToken);//新令牌加入桶中
                next=now;//下一令牌发放时间为现在
            }
        }
    }
    
    
2.netty
BIO模型：为每个socket分配一个线程（可用线程池），read、write会阻塞当前线程，直到io就绪。用于socket连接不很多的场景。=》server支持十万、百万连接，NIO：一个线程处理多个socket连接。
reactor模式：Handle：IO句柄，即网络连接。EventHandler：事件处理器，提供handle_event()处理io事件，get_handle() 获取该io的handle。Reactor：register_handler/remove_handler 注册/删除事件处理器，handle_events() 通过Synchronous Event Multiplexer中的select()监听网络事件，事件就绪后，遍历handler处理。
void Reactor::handle_events() {
    //通过select()监听事件
    select(handlers);
    for (h: handlers) {
        h.handle_event();
    }
}

main中循环执行：
while(true) {
    handle_events();
}
Netty中EventLoop即Reactor。一个网络连接对应一个eventLoop，一个eventLoop对应一个thread，避免并发问题。一组eventLoop组成eventLoopGroup，BossGroup处理连接请求，WorkerGroup处理读写请求，通过负载均衡（轮询）交给具体eventLoop执行。eventLoop个数：2*cpu core
其他优化：bytebuffer、0copy

3.有界队列：disruptor 内存消息队列，用于线程间传递消息
jdk中的ArrayBlockingQueue,LinkedBlockingQueue基于reentrantLock，效率不高。=》disruptor：用于log4j、hbase、storm等。无锁算法避免竞争；优化cpu性能。
    class MyEvent {//自定义event
        private long val;
        public void set(long value) {
            this.val = value;
        }
    }
    
    int bufferSize = 1024;//2^n
    Disruptor<MyEvent> disruptor = new Disruptor<>(MyEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);//根据EventFactory.newInstance()创建bufsize个MyEvent对象，地址连续
    disruptor.handleEventsWith((event, sequence, endOfBatch) -> System.out.println(event));//事件处理
    disruptor.start();

    RingBuffer<MyEvent> buf = disruptor.getRingBuffer();//向ringbuffer中生产数据
    ByteBuffer b = ByteBuffer.allocate(8);
    b.putLong(0,10);
    buf.publishEvent((event, sequence, buffer) -> event.set(b.getLong(0)), b);//写入数据不new，用set
    
内存用ringBuffer：循环顺序队列，增删元素时不用移动。初始化时创建全部数组元素,利用程序空间局部性原理，提升cache命中率；对象循环利用，避免频繁gc。ArrayBlockingQueue每增加一个元素，需要new Object，地址不连续。
避免伪共享，提高cache命中率：ArrayBlockingQueue中int takeIndex,int putIndex,int count，cpu加载时可能会将三个都加载到同一cache line（64B），入队修改putIndex会导致其他线程的takeIndex cache失效，要重新从内存加载。且用锁保证出入队互斥。false sharing：由于cache line导致cache无效。=》每个变量前后填充56B，使其独占一个cache line。  false sharing可用@sun.misc.Contented，会占更多内存。
无锁算法，避免加、解锁开销：入队不能覆盖未消费元素，出队不能读未写入元素。ringbuffer维护putindex，允许多个consumer同时消费，每个consumer一个takeindex，ringbuffer只维护最小的。入队：先加锁申请n个空闲单元，写入数据时不加锁。若没有足够空闲位置，用LockSupport.parkNanos()让出cpu x ns，再循环重新计算；否则用cas更新putindex。出队：加锁申请可读单元，读取时不加锁。consumer可无锁批量消费。

4.数据库连接池：HiKariCP
标准步骤：通过data source获取db conn；创建statement；执行sql；通过resultSet获取result；释放resultSet;释放statement；释放conn。
FastList:为避免用户只close conn，不close resultset、statement，在close conn时自动close resultset、statement。conn要跟踪创建的statement，放入ArrayList中，close时逆序将array中所有statement关闭。fastlist逆序查找，顺序删除。且保证不越界，不用检查index是否越界。
ConcurrentBag:若用两个blocking queue实现连接池，idle、busy。getConn()从idle移到busy，closeConn()从busy移到idle。并发时锁影响性能。concurrentbag用threadlocal避免并发。
    CopyOnWriteArrayList<T> sharedList;//所有db conn
    ThreadLocal<List<Object>> threadList;//线程本地存储中的db conn
    AtomicInteger waiters;//等待db conn的线程数
    SynchronousQueue<T> handoffQueue;//分配db conn的工具

    //创建db conn
    void add(final T entry) {
        sharedList.add(entry);
        while (waiters > 0 && entry.getState() == STATE_NOT_IN_USE && !handoffQueue.offer(entry)) {//若有等待连接的线程，通过handoffQueue分配给等待线程
            yield();
        }
    }

    //获取db conn
    T borrow(long timeout, TimeUnit tu) {
        List<Object> list = threadList.get();//若线程threadlocal中有空闲conn，直接返回
        for (int i = list.size()-1; i>=0;i--) {
            Object entry = list.remove(i);
            T bagEntry = weakThreadLocals ? ((WeakReference<T>) entry).get(): (T) entry;
            if (bagEntry != null && bagEntry.compareAndSet(STATE_NOT_IN_USE,STATE_IN_USE)) return bagEntry;
        }
        
        //线程本地无空闲conn，从共享队列获取
        final int waiting = waiters.incrementAndGet();
        try {
            for(T bagEntry: sharedList) {
                if (bagEntry.compareAndSet(STATE_NOT_IN_USE,STATE_IN_USE)) return bagEntry;
            }
            //共享队列无conn，等待
            timeout = tu.toNanos(timeout);
            do {
                final long start = System.currentTimeMillis();
                final T bagEntry = handoffQueue.poll(timeout, TimeUnit.NANOSECONDS);
                if (bagEntry == null || bagEntry.compareAndSet(STATE_NOT_IN_USE,STATE_IN_USE)) return bagEntry;
                timeout -= elapsedNanos(start);
            } while (timeout > 10000);
            return null;//超时未获取conn，返回null
        } finally {
            waiters.decrementAndGet();
        }
    }

    //释放db conn
    void requite(T bagEntry) {
        bagEntry.setState(STATE_NOT_IN_USE);
        for (int i=0; waiters.get()>0; i++) {
            if (bagEntry.getState(STATE_NOT_IN_USE)|| handoffQueue.offer(bagEntry)) return;//若有等待线程直接分配，不进队列
            else if ((i&0xff) == 0xff) parkNanos(MICROSECONDS.tonanos(10));
            else yield();
        }
        
        List<Object> threadlocallist = threadList.get();
        if (threadlocallist.size()<50) {
            threadlocallist.add(weakThreadLocals ? new WeakReference<>(bagEntry): bagEntry);
        }
    }

其他并发模型：
1.Actor：
所有计算都在Actor中执行，Actor间不共享变量。内部包括一个mailbox放发送的消息，单线程处理。异步，不保证顺序，可靠。
java Akka类库。spark、flink、play、erlang。
2.软件事务内存 STM
支持ACI。db MVCC在事务开启时打快照，本事务所有读写基于snapshot。若commit时，读写的数据在tx期间没有变化（通过version判断）则可提交，否则不能提交。类似stampedLock乐观锁。适合函数式语言，不变性。io很难回滚。
java Multivserse类库。
class VersionRef<T> {
        final T val;
        final long version;

        public VersionRef(T val, long version) {
            this.val = val;
            this.version = version;
        }
    }

    class TxnRef<T> {
        volatile VersionRef curRef;
        public TxnRef(T val) {
            this.curRef = new VersionRef(val,0l);
        }

        public T get(Txn txn) {
            return txn.get(this);
        }

        public void set(T val, Txn txn) {
            txn.set(this,val);
        }
    }

    interface Txn {//Txn用于事务内对数据的读写
        <T> T get(TxnRef<T> ref);
        <T> void set(TxnRef<T> ref, T val);
    }

    class STMTxn implements Txn {
        static AtomicLong txnSeq = new AtomicLong(0);
        Map<TxnRef, VersionRef> inTxnMap = new HashMap<>();//inTxnMap保存当前事务中所有读写的数据快照
        Map<TxnRef, Object> writeMap = new HashMap<>();//writeMap保存当前事务需要写入的数据
        long txnId;//全局唯一递增tx id

        STMTxn() {//自动生成当前tx id
            txnId = txnSeq.incrementAndGet();
        }
        @Override
        public <T> T get(TxnRef<T> ref) {//获取当前事务中的数据
            if (!inTxnMap.containsKey(ref)) {//将要读的数据加入intxnmap
                inTxnMap.put(ref, ref.curRef);
            }
            return (T) inTxnMap.get(ref).val;
        }

        @Override
        public <T> void set(TxnRef<T> ref, T val) {//在当前事务中修改数据
            if(!inTxnMap.containsKey(ref)) {//先将要修改的数据放入intxnmap
                inTxnMap.put(ref, ref.curRef);
            }
            writeMap.put(ref, val);
        }

        boolean commit() {
            synchronized (STM.commitLock) {
                boolean isValid = true;//是否有修改
                for (Map.Entry<TxnRef, VersionRef> entry: inTxnMap.entrySet()) {
                    VersionRef curRef = entry.getKey().curRef;
                    VersionRef readRef = entry.getValue();
                    if (curRef.version != readRef.version) {
                        isValid = false;
                        break;
                    }
                }
                if (isValid) {//若所有数据都没有修改过，则本事务修改生效
                    writeMap.forEach((k,v) -> {
                        k.curRef = new VersionRef(v, txnId);
                    });
                }
                return isValid;
            }
        }
    }
    
    //使用
        boolean committed = false;
        while (!committed) {
            STMTxn txn = new STMTxn();
            action.run(txn);//业务逻辑
            committed = txn.commit();
        }

3.协程 coroutine
轻量级线程：用户态调度，切换成本低。栈小，几十K(java栈1M)。支持语言：golang、python、lua、kotlin。
用于实现thread-per-message。异步转同步：java多线程io，用异步非阻塞，注册回调函数实现异步。协程可用同步非阻塞。OpenResty的cosocket。
func hello(msg string) {xxx}
main() {
    go hello("hi");//在新协程中执行
}

4.CSP模型
协程协作：共享内存方式（monitor、原子类，类似java）；消息传递CSP，避免共享。channel类似管道，生产者-消费者模型。
与Actor对比：1)Actor无channel，mailbox对程序员透明，属于某特定actor。actor间可通信，不需中介。csp：对程序员可见，是通信的中介。2)actor发消息非阻塞，不保证100%可达。csp阻塞，保证可达，但可能死锁。



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
            process(reader.read());//数据没有准备好，也阻塞
            s.close();
        }
1)多线程
Socket s = ss.accept();
new Thread(new SocketPorcessor(s).start()); //并发量上万时，线程太多，内存不够（64位系统默认线程栈最大1MB），切换开销大。
start()不能多次调用，会先判断status，不为0抛异常。if (threadStatus != 0)throw new IllegalThreadStateException();
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

分布式锁：
redis：单线程可用于串行化，setnx key v exp_time。缺：锁时间不可控；client-server无心跳，若连接出问题，client会被timeout；主从AP模型，若主的锁数据尚未同步到从，发生主从切换，可能两个线程同时执行。适合非强一致性场景。
zk：CP模型可保证锁在每个节点都存在，通过2PC提交写请求，集群规模大时瓶颈。若client挂、GC等，断开连接，临时节点删除，其他线程获取锁，导致两线程同时执行。
有惊群效应，多线程监听同一节点，同时被唤醒导致网络、zk性能开销
    class ZKLock implements Lock {
        String lockPath;
        ZKClient client;
        ZKLock(String path) {
            super();
            lockPath = path;
            client = new ZKClient(ip, port);
        }

        @Override
        public boolean tryLock() { //非阻塞，加锁不成功直接返回
            try {
                client.createEphemeral(lockPath, "");
                return true;
            } catch (NodeExistsException e) {
                return false;
            }

        }

        @Override
        public void lock() { //无返回值，不成功则阻塞
            if (!tryLock()) {
                waitForLock();
                lock();
            }
        }

        private void waitForLock() { 
            CountDownLatch cdl = new CountDownLatch(1); //用于阻塞
            IZkDataListener listener = new IZkDataListener() {
                @Override
                public void handleDataDeleted(String dataPath) throws Exception {
                    cdl.countDown();//唤醒
                }
            };
            client.registerDataDelete(lockPath, listener);//每次有新节点创建都要注册监听
            if (client.exists(lockPath)) { //先检查再加锁
                try {
                    cdl.await();
                } catch (InterruptedException e) {
                }
            }
            client.unregisterDataDelete(lockPath, listener);//唤醒后执行这句
        }

        @Override
        public void unlock() {
            client.delete(lockPath);
        }
    }

临时顺序节点：最小号获取锁，其他注册前一节点。每个节点名称为10位int，最大可有int.max个子节点。公平锁
    class ZKLock implements Lock {
        String lockPath;
        ZKClient client;
        String curPath;
        String prePath;

        ZKLock(String path) {
            super();
            lockPath = path;
            client = new ZKClient(ip, port);
            client.createPersistent(lockPath);//先创建父节点
        }

        @Override
        public boolean tryLock() { 
            if (curPath == null) { //不要重复创建
                curPath = client.createEphemeralSequential(lockPath +"/", "");
            }
            List<String> children = client.getChildrenNames(lockPath);
            Collections.sort(children);
            
            if (curPath.equals(lockPath +"/"+children.get(0))) {
                return true;
            } else {
                int curIndex = children.indexOf(curPath.substring(lockPath.length()+1));
                prePath = lockPath + "/" + children.get(curIndex-1);
                return false;
            }
        }

        @Override
        public void lock() { //无返回值，不成功则阻塞
            if (!tryLock()) {
                waitForLock();
                lock();
            }
        }

        private void waitForLock() {
            CountDownLatch cdl = new CountDownLatch(1); //用于阻塞
            IZkDataListener listener = new IZkDataListener() {
                @Override
                public void handleDataDeleted(String dataPath) throws Exception {
                    cdl.countDown();//唤醒
                }
            };
            client.registerDataDelete(prePath, listener);//监听前一节点
            if (client.exists(lockPath)) { //先检查再加锁
                try {
                    cdl.await();
                } catch (InterruptedException e) {
                }
            }
            client.unregisterDataDelete(prePath, listener);//唤醒后执行这句
        }

        @Override
        public void unlock() {
            client.delete(curPath);
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
PC：当前线程正在执行的java方法的jvm指令地址，即字节码行号。若执行native方法，为空。唯一不会OOM的区域。
虚拟机栈/本地方法栈：每个栈帧包括局部变量表、操作数栈、动态链接、方法返回值。局部变量表：基本数据类型和ref（对象起始地址的引用，或代表对象的句柄）。stackOverFlowError：若栈大小不允许动态扩展，请求栈深度超过max时。OOM：允许动态扩展，内存用完时。
堆：eden，s0,s1,老年代tentired。细致划分可更好的分配内存。先将obj分配到eden，第一次新生代gc后，若对象还存活，进入s0/s1，且age=1。age为15时进入老年代。为了避免多线程同时分配内存要加锁，影响分配速度：TLAB（thread local allocation buffer）。在TLAB中，[start,end)，top指向当前可分配的地址。普通obj先分配到TLAB，较大的分配到eden其他区域，更大的直接到老年代。eden不足时，触发minor gc，存活的对象放入survivor。如何避免过早full gc：survivor。为什么两个s：减少碎片，提高性能。minor gc时，将eden和s0中存活对象复制到s1。virtual space：内存从-Xms增长到-Xmx时，预留一部分等内存增长时，分配给新生代。-XX：NewSize：新生代大小。-XX：NewRatio：老与新的比例，通常2，老太大full gc时间长，太小full gc频繁。-XX：SurvivorRatio：eden与s的比例，通常8.
方法区：被jvm加载的类、常量、静态变量、JIT编译后的代码。jdk1.8前是堆的一部分，永久代，使jvm像heap一样管理这部分内存。1.8后，用直接内存。-XX:MetaspaceSize       若不指定大小，随着创建更多类，可能耗尽所有系统内存。oom:metaspace
常量池：字面量（string、final、基本数据类型的值）；符号引用（类和结构的完全限定名、字段名、方法名）
直接内存：NIO，基于channel、buffer，直接用native函数库分配，通过java堆中的DirectByteBuffer对象引用操作，避免在java heap和native堆间复制数据。受到物理内存的限制，可能oom。

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

新建对象：new、反射、Object.clone()、反序列化、Unsafe.allocateInstance()
new、反射用构造器初始化实例字段；Object.clone、反序列化通过复制已有数据初始化；unsafe不初始化。
压缩指针：每个obj header包括64bit标记字段（jvm关于该对象的信息，如hashcode、gc、锁）和64bit类型指针。默认，对象的起始地址要对齐至8N，即内存地址低3位总是0。将64bit指针压缩到32bit，可表示2^35（32GB）地址空间，超过32G时关闭压缩指针。内存对齐不仅在对象间，也在对象的字段间，如long、double、非压缩指针时的引用字段，要求地址为8N，避免跨缓存行的字段。

jvm问题定位：cpu：用top看load average；查看占用cpu的线程。java线程：查看高占用cpu的线程是什么：printf "%x" pid，将pid转为16进制；对比jstack获取线程栈的pid；或用vmstat看上下文切换的数量。内存：jstat，jmap，生成heapdump文件，用visualvm分析。jvm运行时监控：jconsole；jstack；gc log。
 
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

11.ThreadLocal
保存线程上下文；线程安全，避免考虑线程同步的性能损失。但无法解决共享变量的更新问题。eg.记录request id，可将多个请求关联起来。Spring事务管理，记录connection，多个dao可获取同一conn，便于rollback、commit等。
Thread类有属性变量threadLocals，类型为ThreadLocal.ThreadLocalMap。map为Entry[]，通过hashcode定位，线性探测再散列解决冲突。每个Entry为k:ThreadLocal对象，v：Object。多个Object需要多个ThreadLocal。Entry的key指向ThreadLocal为weakreference。jvm GC时，不管mem是否充足，若该对象只被weak ref，就要被回收。当ThreadLocal被GC后，map.Entry.key为null，但entry.val为object，没有回收，显式调用threadlocal.remove()回收。
    private static ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    threadLocal.set(i);
                    System.out.println(Thread.currentThread().getName() + threadLocal.get());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                threadLocal.remove();
            }
        }, "t1").start();

12.HashMap
通过数组存所有数据，初始大小为16，超过threshold时加倍扩容。hashtable用素数，不易冲突，但取模慢，hashmap可用&代替%。index=hash(key)&(len-1)。若hash(key)对应的元素个数<8时，用Node单链表；否则用TreeNode红黑树，空间为node的2倍，但查询速度快。当元素个数增减时，node和treenode可互相转换。resize:申请2倍数组，将原数组所有记录复制到新数组，再将原数组为null，便于GC。线程不安全：put，resize时get。允许存在一个null key，所以get(key)返回null时，有可能value为null，也可能key不存在，应用containsKey判断。
hash(key):将高16位移到低16位，再与低16位异或。
遍历：keySet:遍历2次，一次转为iterator对象，一次从map中取出key对应的value。entrySet：1次，将kv放入entry，效率高。jdk8用map.foreach，可结合lamda更方便。
LinkedHashmap:保持插入顺序。继承hashmap。
TreeMap：红黑树，o(logn)，比hashmap性能低。
HashSet:基于hashmap实现，value为new Object()
ConcurrentHashMap：继承hashmap。hashtable用synchronized互斥，get、put不能同时进行，其他线程阻塞或轮询。CHM用分段锁，不同段数据可并发。int transient volatile sizeCtl：共享变量，为负则正在init或resize。某线程要init/resize，先竞争sizeCtl，若不成功则自旋，若成功则用unsafe.cas将其置为-1。get：无lock，volatile entry[]保证可见。put：先计算index，若为null，用cas插入。否则用synchronized对index加锁，其他位置不影响。

13.annotation
xml描述元数据，难维护，与代码松耦合 =》annotation：
    @Target(ElementType.METHOD) 
    @Retention(RetentionPolicy.SOURCE) //编译阶段丢弃，不进入.class。CLASS：类加载时丢弃，处理.class时有用，默认。RUNTIME: 运行时可通过反射读取。
    public @interface MyAnnotation {
        public enum Priority {LOW,MEDIUM,HIGH}
        String author() default "a";
    }
    
    class MyClass {
        @MyAnnotation(Priority = MyAnnotation.Priority.MEDIUM, author = "b")
        public void method() {
        }    
    }
   
使用注解信息：
        Class clz = MyClass.class;
        for (Method method : clz.getMethods()) {
            MyAnnotation an = (MyAnnotation)method.getAnnotation(MyAnnotation.class);
            if (an != null) {
                System.out.println(an.author());
            }
        }


