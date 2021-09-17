# redis

## redis高效的核心要素

- 高速的存储介质：内存条
- 优良的底层数据结构：HashMap
- 高效的网络IO模型
- 高效的线程模型
- redis是单线程的
- redis是C语言写的

为什么redis要用单线程？为什么单线程还那么快？

​	(1) redis是基于内存操作的，CPU不是redis的性能瓶颈，redis的瓶颈是根据机器的内存和网络带宽，使用单线程实现即可。

​	(2) 误区1：高性能的服务器一定是多线程的；

​		 误区2：多线程（基于CPU上下文切换）一定比单线程效率高。

​	(3) redis是将所有的数据全部放在内存中的，所以说使用单线程去操作效率是最高的。对于内存系统来说，如果没有上下文切换，效率是最高的。多次读写都是在一个CPU上，在内存情况下，单线程就是最佳方案。

## 概述

底层存储数据结构使用的HashMap集合的hashtable来实现。数组 + 链表的形式。

数组中存索引值。链表中存键值对，以及下一个链接的键值对地址。

形如：![](pics\存储结构示意图.png)

如何使链表不会太长？

​	rehash的方式解决链表过长的问题。达到可扩容时，将原来的数组扩容2倍。

那么，什么时候进行扩容呢？

扩容后，数据访问和插入是怎么操作的？

​	扩容后，就会创建一个新的hashtable，旧的hashtable数据不是一次性就全部放进新的hashtable里面的，而是渐进式的，慢慢的迁移到新的hashtable里面。如果想要查询某一个数据，就先访问旧的，找不到在访问新的。在插入数据时，只能直接插入新的hashtable里面。

windows使用redis的方式。

下载地址：https://github.com/tporadowski/redis/releases

Redis 支持 32 位和 64 位。这个需要根据你系统平台的实际情况选择，这里我们下载 **Redis-x64-xxx.zip**压缩包到 C 盘，解压后，将文件夹重新命名为 **redis**。

> 直接在命令行使用的话，需要进行环境变量的配置。
>
> 服务器端：redis-server.exe
>
> 客户端：redis-cli.exe

## redis的键key类型

redis可以用作==数据库==、==缓存==和==消息中间件MQ==。

redis支持多种类型的数据结构，主要有5种：

​	》strings	字符串

​	》lists	列表

​	》hashes	HashMap

​	》sets	集合

​	》sorted sets	由于集合

还有三种不常用的：

​	》bitmaps

​	》hyperloglogs

​	》gespatial

>常用的命令

exists 键：判断当前的"键"是否存在。存在返回1；不存在返回0。

keys *：查看所有的key

move 键：移除当前的key

expire 键 时间(s)：设置该可以的过期时间，单位是秒

ttl 键：查看当前key的剩余时间

type 键：查看当前“键”的数据类型

append 旧键 "新值"：在已有的key后面追加新的字符串

查看所有命令：中文官网http://redis.cn/commands.html

### string

​	传入的可以是任意类型的数据，但redis会自动转换成string数据类型。

### 如何实现String类型的转换？

​	由于在C语言中，string类型的源代码里用的char[]数组来封装，且在字符数组的最后会默认加一个“\0”（\xxx 是一个字符）来作为该字符串的结束符。但是在redis中可以传入的是任意数据，在传入的数据中，就有可能包括 \0 这样的字符，所以不能直接引用C语言的string数据类型来作为redis的string类型。

​	因此，redis命名了一个SDS（Simple Dynamic String，简单动态字符串）的数据类型。

内存为当前字符串实际分配的空间capacity一般要高于实际字符串长度len。当字符串长度小于1M时，扩容都是加倍现有的空间，**如果超过1M，扩容时一次只会最多扩1M的空间。需要注意的是字符串的最大长度是512M。**

### SDS

SDS的数据结构

```c
sds:
	int len:
	char buf[];
```

对于上面的len的类型，不同版本有不同的定义。主要是因为int数据类型能代表的值太大了，没必要。

| 长度类型 | sdshdr5                                   | sdshdr8                      | sdshdr16                     | sdshdr32                     | sdshdr64                     |
| -------- | ----------------------------------------- | ---------------------------- | ---------------------------- | ---------------------------- | ---------------------------- |
| 容量     | [0, 2^5 - 1]                              | [2^5, 2^8 - 1]               | [2^8, 2^16 - 1]              | [2^16, 2^32 - 1]             | [2^32, 2^64 - 1]             |
| 解释     | 3 lsb of type, and 5 msb of string length | 3 lsb of type, 5 unused bits | 3 lsb of type, 5 unused bits | 3 lsb of type, 5 unused bits | 3 lsb of type, 5 unused bits |

那么。怎么调用这几种类呢？

首先看一个sdshdr5的数据结构和sdshdr8的数据结构（sdshdr16和sdshdr64和sdshdr8一样）

```
struct __attribute__ ((__packed__)) sdshdr5 {
    unsigned char flags; /* 3 lsb of type, and 5 msb of string length */
    char buf[];
};

struct __attribute__ ((__packed__)) sdshdr8 {
    uint8_t len; /* used ， 1byte */
    uint8_t alloc; /* excluding the header and null terminator  ， 1byte */
    unsigned char flags; /* 3 lsb of type, 5 unused bits  ， 1byte */
    char buf[]; // 为了兼容C语言的字符串，默认有一个'\0'字符，所以此时也是 1b
};
```

![](pics\sdshdr5.png)

\#define SDS_TYPE_5 0

\#define SDS_TYPE_8 1

\#define SDS_TYPE_16 2

\#define SDS_TYPE_32 3

\#define SDS_TYPE_64 4

以sdshdr5为例。这里的5就代表5个bit，它能代表的string类型长度范围为[0, 2^5 - 1]。

源码中有说明：sdshdr5不会被使用，仅仅只是用来指向flags字节。(Note: sdshdr5 is never used, we just access the flags byte directly)



redis源码中，databases数据库默认的个数为16。当然个数是可以修改的。

默认使用的是第0个数据库。可以用 "select 数据库序号" 来选择使用哪一个数据库。

使用dbsize命令，可以查看数据库使用了多少空间。

keys * 查看所有的key

flush 清空当前数据库

flushall 清空所有数据库

hashtable的数据结构

```c
/* This is our hash table structure. Every dictionary has two of this as we
 * implement incremental rehashing, for the old to the new table.
 * 
 */
typedef struct dictht {
    dictEntry **table;  // hashtable
    unsigned long size;	// hashtable size
    unsigned long sizemask; // size-1 求mod
    unsigned long used;  // hashtable有多少元素
} dictht;

typedef struct dict {
    dictType *type;
    void *privdata;
    dictht ht[2];  // 扩容为原来的两倍
    long rehashidx; /* rehashing not in progress if rehashidx == -1 */
    int16_t pauserehash; /* If >0 rehashing is paused (<0 indicates coding error) */
} dict;
```

dictEntry和dictType的数据结构

```c
typedef struct dictEntry {
    void *key;
    union {
        void *val;  // value 指向 redisObject数据结构
        uint64_t u64;
        int64_t s64;
        double d;
    } v;
    struct dictEntry *next;  // 建立链表之间的关系
} dictEntry;

typedef struct dictType {
    uint64_t (*hashFunction)(const void *key);
    void *(*keyDup)(void *privdata, const void *key);
    void *(*valDup)(void *privdata, const void *obj);
    int (*keyCompare)(void *privdata, const void *key1, const void *key2);
    void (*keyDestructor)(void *privdata, void *key);
    void (*valDestructor)(void *privdata, void *obj);
    int (*expandAllowed)(size_t moreMem, double usedRatio);
} dictType;
```

### embstr

使用 object encoding 键名 查看

通过数据结构redisObject里面的*ptr指针来查找需要找的value值。

嵌入string ，如果所写的字符串占用字节数<=44个（64 bytes - 16bytes - 4 bytes。其中16个字节是redisObject类中各个属性值所占的字节总数，4个bytes是所调用的sdshdr8结构所占的字节总数），就使用的是embstr数据类型，直接将该字符串嵌套在redisObject里面，而不是通过*ptr指针再去查找，因为查找会进行内存的IO操作，耗内存也耗时间。







# 尚硅谷课程

## Redis基本数据类型

包括五种基本数据类型：

- String
- List
- Hash
- Set
- Zset

### String数据类型

由于在C语言中，string类型的源代码里用的char[]数组来封装，且在字符数组的最后会默认加一个“\0”（\xxx 是一个字符）来作为该字符串的结束符。但是在redis中可以传入的是任意数据，在传入的数据中，就有可能包括 \0 这样的字符，所以不能直接引用C语言的string数据类型来作为redis的string类型。

​	因此，redis命名了一个SDS（Simple Dynamic String，简单动态字符串）的数据类型。

内存为当前字符串实际分配的空间capacity一般要高于实际字符串长度len。当字符串长度小于1M时，扩容都是加倍现有的空间，**如果超过1M，扩容时一次只会最多扩1M的空间。需要注意的是字符串的最大长度是512M。**

### List数据类型

》单键多值。单键：一个key，多值：多个value，用链表存储。

》List的底层数据结构是**双向链表**，对两端的操作性都很高，通过索引下表的操作中间的节点性能会较差。

<img src="pics\redis列表List的数据结构示意图.png" style="zoom: 67%;" />

》常用的命令

- lpush/rpush <key> <value1> <value2> <value3> <value4>...... 。从左边/右边插入一个或多个值；
- lpop/rpop <key> 从左边/右边弹出一个值。 有值在，则键在，值没了，键没了。
- rpoplpush <key1> <key2>  从<key1>列表右边弹出值，插入到<key2>列表的左边。
- lrange <key> <start> <stop> 按照索引下表获取元素 （从左到右）
- lindex <key> <index> 查看key值对应的列表的第index个value值。

》List的底层数据结构是一个快速列表 quickList。首先在列表元素较少的情况下，会使用一块连续的内存存储，这个结构ziplist，即压缩列表。**它将所有元素紧挨着一起存储，分配的是一块连续的内存。当数据量比较多的时候才会改成quicklist。**

<img src="pics\redis列表List的ziplist.png" style="zoom:67%;" />

》使用场景：**微信公众号订阅消息**

```
1 大V作者PaperWeekly和小夕学算法发布了文章分别是 11 和 22 
2 小明关注了他们两个,只要他们发布了新文章,就会写进我的List列表中
   lpush likearticle:小明id    11 22
3 查看小明自己的号订阅的全部文章,类似分页,下面0~10就是一次显示10条
  lrange likearticle:小明id 0 9
```

### 集合Set

》set是可以自动去重的，不允许元素重复。Set的数据结构是dict字典，字典是用**哈希表实现的**。

》常用命令

| 指令                           | 解释                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| sadd key value1 value2         | 将一个或多个 member元素加入到集合 key 中，已经存在的 member 元素将被忽略 |
| smembers key                   | 取出该集合的所有值                                           |
| srem key value1 value2 …       | 删除集合中的某个元素（记忆：re : remove）                    |
| sismember key value            | 判断集合key是否为含有该value值，有1，没有0（记忆： is 是或不是） |
| scard key                      | 返回该集合的元素个数                                         |
| spop key                       | 随机从该集合中弹出一个值                                     |
| srandmember key n              | 随机从该集合中取出n个值。不会从集合中删除（只取不删）        |
| smove source destination value | 把集合中一个值从一个集合移动到另一个集合                     |
| sinter key1 key2               | 返回两个集合的交集（intersection）元素                       |
| sunion key1 key2               | 返回两个集合的并集（UNION）元素                              |
| sdiff key1 key2                | 返回两个集合的差集（difference）元素（key1中的，不包含key2中的） |

》应用场景1：微信抽奖小程序

| 用户ID，立即参与按钮              | 触发sadd key 用户ID                                          |
| --------------------------------- | ------------------------------------------------------------ |
| 显示已经有多少人参与了            | 使用scard key                                                |
| 抽奖 （从set中任意选取N个中奖人） | srandmember key 2   随机抽取两人，元素不删除                                                              spop key 3     随机抽奖3个人，元素会删除 |

》应用场景2：微信朋友圈点赞

| 新增点赞             | sadd pub:msgID 点赞用户ID1 点赞用户ID2 ...... |
| -------------------- | --------------------------------------------- |
| 取消点赞             | srem pub:msgID 点赞用户ID                     |
| 展现所有点赞过的用户 | smembers pub:msgID                            |
| 点赞用户数统计       | scard pub:msgID                               |

》应用场景3：微博好友关注社交关系





### 哈希 Hash

》Hash类型对应的数据结构是两种：ziplist（压缩列表），hashtable（哈希表）。

​	当field-value（字段值）长度较短且个数较少时，使用ziplist，否则使用hashtable （Map<String,Map<Object,Object>>）。

》常用命令

| 指令                                  | 解释                                                         |
| ------------------------------------- | ------------------------------------------------------------ |
| HSET key field value                  | 一次设置一个字段值                                           |
| HGET key field                        | 一次获取一个字段值                                           |
| hgetall key                           | 获取所有字段值                                               |
| hdel                                  | 删除一个key                                                  |
| HMSET key field value [field value …] | 一次设置多个字段值（批量设置）                               |
| HMGET key field [field …]             | 一次获取多个字段值（批量获取）                               |
| hlen key                              | 获取某个key内的全部数量                                      |
| hkeys key                             | 列出该hash集合的所有**field字段**                            |
| hvals key                             | 列出该hash集合的所有value                                    |
| hincrby key field increment           | 为哈希表 key 中的域field的值加上增量 1 -1                    |
| hsetnx key field value                | 将哈希表 key 中的域field的值设置为value,当且仅当域field不存在才设置 |

》应用场景：购物车模块

新增商品：使用hset shopcar:uid1314 112233 1

新增商品：使用hset shopcar:uid1314 445566 1

增加商品数量：hincreby shopcar:uid1314 445566 1

商品总数：hlen shopcar:uid1314

全部选择：hgetall shopcar:uid1314



### 有序集合 Zset

》Redis有序集合zset与普通集合set非常相似，是一个没有重复元素的字符串集合。**不同之处是有序集合的每个成员都关联了一个评分(score)**，这个评分(score)**被用来按照从最低分到最高分的方式排序集合中的成员**。集合的成员是唯一的，但是评分可以是重复的。

》常用命令

| 指令                                                         | 解释                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| zadd key score1 value1 score2 value2                         | 将一个或多个member元素及其score值加入到有序集key中           |
| zrange key start stop [withscores]                           | 返回有序集key中，下标在start - stop之间的元素。可选参数WITHSCORES，可以让分数一起和值返回到结果集 |
| zrem key value                                               | 删除该集合下，指定值的元素                                   |
| zrangebyscore key min max [withscores] [limitoffsetcount]    | 返回有序集key中，所有score值介于min和max之间（包括等于min或max）的成员。有序集成员按score值递增排列 |
| zrevrangebyscore key max min [withscores] [limitoffsetcount] | 同上。改为降序排列                                           |
| zincrby key increment value                                  | 为元素的score加上增量                                        |
| zcount key min max                                           | 统计该集合,分数区间内的元素个数                              |
| zrank key value                                              | 返回该值在集合中的排名,从0开始                               |

》应用场景1：如何利用Zset实现一个文章访问量的排行榜？

```
1. 下面这条指令记录了key为text的5个专栏的阅读量
	zadd text 1000 Java 100 Python 40 php 60 C 200 JQuery
2. 显示所有并且从小到大排序
	zrange text 0 -1
3. 根据所选范围从大到小排序
	zrevrangebyscore text 200 10
```

》应用场景扩展：根据商品销售对商品进行排序显示、热搜的设计



### 实例：实现验证码功能

实现步骤：

- 输入手机号，点击发送后，随机生成一个6位数字码，2分钟内有效；
- 输入验证码，点击验证，返回成功或失败；
- 每个手机号每天只能输入3次。

具体实现方式：

​	对于随机6位验证码的实现：使用 Random函数实现。

​	验证码在2分钟内有效：把验证码放到redis里面，设置过期时间120秒。

​	判断验证码是否一致：从redis获取验证码和输入的验证码进行比较。

​	每个手机号每天只能发送3次验证码： incr 每次发送后 + 1，大于2，提交不能发送。



## Redis的其他数据类型Bitmaps

### 概述

》Redis提供的Bitmaps“数据类型”可以实现对 位 的操作：

（1）Bitmaps本身不是一种数据类型，实际上它就是字符串（key-value），但是它可以对字符串的位进行操作。

（2）Bitmaps单独提供一套命令，所以在Redis使用Bitmaps和使用字符串的方式不太相同。可以把Bitmaps县县城一个以位位单位的数组，**数组的每个单元只能存储0和1**，**数组的下标**在Bitmaps中叫做**偏移量**。

<img src="pics\Bitmaps内部结构示意图.png" style="zoom:80%;" />

》用String类型作为底层数据结构实现的一种统计二值状态的数据类型，位图本质上是数组，它是基于String数据类型的按位的操作。

​	该数组由多个二进制位组成，每个二进制位都对应一个偏移量（可以称为一个索引或位格）。Bitmaps支持的最大位数是2^32，他可以极大的节约存储空间，使用512M内存就可以存储多达42.9亿的字节信息。

》由0和1状态表现的二进制位的bit 数组。

### Bitmaps的使用场景

- 用户是否登录过，比如京东每日签到送京豆
- 电影、广告是否被点击过
- 钉钉上下班打卡，签到统计
- 统计指定用户一年之中的登录天数
- 某用户按照一年365天算，哪几天登录过？哪几天没有登录？全年中登录的天数共计多少？

### 常用命令

| 命令                        | 作用                                                  | 时间复杂度 |
| --------------------------- | ----------------------------------------------------- | ---------- |
| setbit key offset val       | 给指定的key值的第offset（偏移量）赋值val              | O(1)       |
| getbit key offset           | 获取指定的第offset 位                                 | O(1)       |
| bitcount key start end      | 返回指定key中[start, end]中为1的数量                  | O(n)       |
| bitop operation destkey key | 对不同的二进制存储数据进行位运算（AND、OR、NOT、XOR） | O(n)       |

》解释

setbit key offset val（setbit 键 偏移位 只能是0或者1）。Bitmaps的偏移量是从0开始算的。

![](pics\BitmapsPics\pic01.png)

### Bitmaps的底层编码说明

#### get命令如何操作的？

​	实质上是二进制的ASCII 编码对应。Redis里用type命令查看Bitmaps的类型：String



## 事务

简单理解，可以认为redis事务是一系列redis命令的集合，并且有如下两个特点：

a）事务是一个单独的隔离操作：事务中的所有命令都会序列化、按顺序地执行。事务在执行的过程中，不会被其他客户端发送来的命令请求所打断。

b）事务是一个原子操作：事务中的命令要么全部被执行，要么全部都不执行。

![](pics\redis事务执行过程简图.png)

### Multi、Exec、discard

从输入Multi命令开始，输入的命令都会依次进入命令队列中，但不会执行，直到输入Exec后，Redis会将之前的命令队列中的命令依次执行。**Multi命令开始组队，Exec命令结束组队，开始执行队中的命令。**

组队的过程中，可以通过discard来放弃组队。

### 事务的错误处理

（1）**组队阶段**某个命令出现了报告错误，执行时**整个的所有队列都会被取消**。

（2）执行阶段有命令出现错误，只是出错的命令会执行不通过，其他命令照样会通过。

### 事务冲突以及锁机制

​	如果同时对某一数据进行操作时，就有可能出现事务冲突。怎么解决冲突发生呢？

#### 悲观锁

​	每次去拿数据的时候，都认为别人会修改，所以在拿数据的同时，会给数据上一把锁，这样过别人想拿该数据就会block，直到释放锁，才有可能拿到数据。传统的关系型数据库里面就用到了很多这种锁机制。比如行锁，表锁等，读锁，写锁等，都是在**操作之前先上锁**。

#### 乐观锁

​	 每次去拿数据时，都认为别人不会做修改，不会上锁，但是在更新的时候会判断一下在此期间别人有没有去更新这个数据，可以**使用版本号机制来做确定**。乐观锁**适用于多读的应用类型，这样可以提高吞吐量。**Redis就是利用这种 check-and-set机制实现事务的。

#### watch unwatch

​	watch 实现监视某key。被监视的key不可以对其做操作。



### Redis事务的三特性

》单独的隔离操作

​	事务中的所有命令都会序列化、按顺序的执行。事务在执行的过程中，不会被其他客户端发送来的命令请求所打断。

》没有隔离级别的概念

​	队列中的命令没有提交之前都不会实际被执行，因为事务提交前，任何指令都不会被实际执行。

》不保证原子性

​	事务中如果有一条命令执行失败，其它的命令任然会执行，没有事务回滚。

### 事务实例演示之秒杀活动

![](pics\事务实例演示之秒杀活动.png)



## Redis持久化

​	持久化有两种方式：

- RDB（Redis DataBase）
- AOF（Append Of File）

### RDB

》Redis DataBase，**在指定的时间间隔内将内存中的数据集快照写入磁盘中**，也就是Snapshot快照，它恢复时是将快照文件直接读到内存里。



#### 如何触发RDB快照：save 和 bgsave

save：只管保存，其他全部阻塞。手动保存。不建议这样用。

**bgsave**：Redis会在后台异步进行快照操作，快照通常还可以响应客户端请求。

#### 备份是如何执行的

​	Redis会单独创建（fork）一个子进程来进行持久化，会先将数据写入到一个临时文件中，待持久化过程结束，再用这个临时文件替换上次持久化好的文件。整个过程中，**主进程是不进行任何IO操作的，这就保证了极高的性能，如果需要进行大规模数据的恢复，且对于数据恢复的完整性不是非常敏感，那RDB方式要比AOF方式更加高效**。

​	RDB的缺点是最后一次持久化后的数据可能丢失。

​	fork的作用是复制一个与当前进程一样的进程。新进程的所有数据（变量、环境变量、程序计数器等）数值都和原进程一致，但是是一个全新的进程，并作为原进程的子进程。

​	在Linux程序中，fork()会产生一个和父进程完全相同的子进程，但子进程在此后都会exec系统调用，出于效率考虑，Linux中引入了“写时复制技术”。

​	一般情况下，父进程和子进程会共用同一段物理内存，**只有进程空间的各段内容要发生变化时，才会将父进程的内容复制一份给子进程。**

#### RDB恢复备份

​	先通过config get dir 查询rdb文件的目录；

​	将 *.rdb 的文件拷贝到别的地方。

- 关闭Redis；
- 先把备份的文件拷贝到工作目录下；
- 启动Redis，备份数据会直接加载。

#### 优缺点

​	优势：

- 适合大规模的数据恢复；

- 对数据完整性和一致性要求不高，更适合使用RDB；

- 节省磁盘空间；

- 恢复速度快。

  

​    劣势：

- fork的时候，内存中的数据被克隆了一份，大致是2倍的膨胀空间，消耗内存；
- 虽然Redis在fork时使用了“写时拷贝技术”，但是如果数据庞大时，还是比较消耗性能；
- 在备份周期在一定间隔时间做一次备份，所以如果Redis意外关闭的话，就会丢失最后一次快照的所有修改。

<img src="pics\Redis持久化之RDB.png" style="zoom:67%;" />



### AOF

#### 是什么

- 全称 Append Only File。**以日志的形式来记录每个写操作**（增量保存），将Redis执行过的**所有写指令记录下来**，**只允许追加文件但不可以改写文件**，Redis启动之初会读取该文件重新构建数据。也就是说，**redis重启的话，就根据日志文件的内容将写指令从前到后执行一次，以完成数据的恢复工作**。 

#### AOF持久化流程

- 客户端的请求写命令会被append 追加到AOF缓冲区内；
- AOF缓冲区根据AOF持久化策略[always, everysec, no]将操作sync同步到磁盘的AOF文件中；
- AOF文件大小超过重写策略或手动重写时，会对AOF文件rewrite重写，压缩；
- Redis服务重启时，会重新load加载AOF文件中的写操作**达到数据恢复的目的**。

#### 手动启动

​	AOF默认是不开启的（appendonly no 改为 appendonly yes），可以在redis.conf 配置文件中配置AOF的名称。默认为 appendonly.aof 。

#### RDB和AOF同时开启，redis 会听谁的？

​	系统默认取AOF的数据（因为数据不会存在丢失的问题）。

#### AOF的启动 / 修复 / 恢复

- AOF的备份机制和性能虽然和RDB不同，但是备份和恢复的操作和RDB一样，都是拷贝备份文件，需要恢复时，在拷贝到Redis工作目录下，启动系统就会加载。
- 正常恢复
  - 修改默认的appendonly no，改为yes；
  - 将有数据的aof文件复制一份保存到对应的目录中（查看目录：config get dir）
  - 恢复。重启redis，然后重新加载。

- 异常恢复
  - 修改默认的appendonly no，改为 yes；
  - 如遇到AOF文件损坏，通过/usr/local/bin/redis-check-aof--fix appendonly.aof进行恢复；
  - 备份被写坏的AOF文件。
  - 恢复。重启redis，然后重新加载。

#### AOF的同步频率设置

appendfsync **always**

始终同步，每次Redis的写入都会立即记入日志。性能较差但数据完整性比较好。

appendfsync **everysec**

每秒同步，每秒记入日志一次，如果宕机，本秒的数据可能会丢失。

appendfsync **no**

Redis不主动进行同步，把同步时机交给操作系统。

#### Rewrite 压缩

》是什么

​	AOF采用文件追加方式，**文件会越来越大**，为了**避免**出现这种情况，新增了**重写机制**，当AOF文件的大小超过所设定的阈值时，Redis就会启动AOF文件的内容压缩，只保留可以恢复数据的最小指令集。可以使用命令 **bgrewriteaof** 。

》重写原理

​	AOF文件持续增长而过大时，会fork出一条新进程来将文件重写（也是先写临时文件，最后在rename），Redis 4.0 版本后的重写，实际上就是**把 rdb 的快照，以二进制的形式附在新的aof头部，作为已有的历史数据，**替换掉原来的流水账操作。



#### 优缺点

优势

- 备份机制更稳健，丢失数据概率更低；
- 可读的日志文本，通过操作AOF文件，可以处理误操作。

劣势

- 比起RDB占用更多的磁盘空间；（多了一些操作的记录）
- 恢复备份速度更慢；（同上）
- 每次读写都同步的话，有一定的性能压力；
- 存在个别bug，造成不能恢复。



## Redis主从复制

### 是什么

​	主机数据更新后根据配置和策略，自动同步到备用机的 master/ slaver 机制，Master 以写为主 ，Slave以读为主。

<img src="pics\Redis主从复制.png" style="zoom:75%;" />

### 有什么用

​	读写分离，方便扩展。

​	容灾快速恢复。

### 实现流程

![](pics\Redis主从复制的操作流程图.png)

### 主从复制原理

<img src="pics\Redis主从复制原理.png" style="zoom:75%;" />



### 出现的问题

#### 一主两从

切入点问题：Slave是从头开始复制还是从切入点开始复制？比如从key4进来，那之前的key1，key2，key3是否也可以复制？

从机是否可以写？

主机shutdown后，从机是上位还是**原地待命**？



薪火相传 



反客为主：手动设置：slaveof no one



### 哨兵模式（Sentinel）

反客为主的自动版，能够后台监控主机是否故障，如果出故障，根据投票数自动将从库转换成主库。

#### 实现步骤

（1）调整为一主二仆的模式，6379带着6380和6381

（2）自定义的/myRedis目录下新建sentinel.conf文件，名字绝对不能错。

（3）配置哨兵，填写内容。sentinel monitor mymaster 127.0.0.1 6379 1

​		其中 mymaster为监控对象起的服务器名称，1为至少有1个哨兵同意迁移的数量。

（4）启动哨兵。redis-sentinel 文件目录+sentinel.conf

（5）当主机挂掉，从机采取  选举 的方式产生新的主机。根据优先级（slave-priority）来选举。源主机重启后，变成从机。

​		（大概10秒左右，可以看到哨兵窗口日志，切换了新的主机）

（6）复制延时

​		由于所有的写操作都是先在Master上操作，然后同步更新到Slave上，所以从Master同步到Slave及其上有一定的时延，当系统繁忙时，延迟问题也会加重，Slave及其数量的增加也会使这个问题更加严重。

#### 选举规则

选择条件依次（1不符合在用2，2不符合在用3）为：

（1）选择优先级靠前的。即选择优先级数字小的。

（2）选择偏移量最大的。即数据和主服务器相差最小的。

（3）选择runid最小的从服务。runid是redis启动后随机生成的一个40位的runid值

## 集群

### 什么是集群

​	Redis（无中心化）集群实现了对Redis的水平扩容，即启动N个redis节点，将整个数据库分布存储在这N个节点中，每个节点存储总数据的1/N。

​	Redis集群通过分区（partition）来提供一定程度的可用性（availability）：即使集群中有一部分节点失效或者无法进行通讯，集群也可以继续处理命令请求。



slot 插槽 0~16383

## 布隆过滤器 Bloom Filter

### 概述

》底层数据结构：一个很长的二进制组 + 一系列随机hash算法映射函数。

》作用：用于判断一个元素是否在集合中



## 缓存穿透

### 指的是

（1）应用服务器压力突然变大了；

（2）redis命中率降低了

### 怎么发现的

（1）redis查询不到数据库

（2）出现很多非正常url访问（恶意攻击）

### 解决方案

（1）**对空值缓存**。如果一个查询返回的数据为空（不管数据是否存在），我们仍然把这个空结果进行缓存。设置空结果的过期时间尽可能的短，最长不超过5分钟；

（2）**设置可访问的名单（白名单）**。使用bitmaps类型定义一个可访问的名单，名单id作为bitmaps的偏移量，每次访问和bitmap里面的id进行比较，如果访问id不在bitmaps里面，进行拦截，不允许访问。

（3）**采用布隆过滤器**。布隆过滤器实际上是一个很长的二进制向量和一系列随机映射函数（哈希函数）组成。布隆过滤器不会存储查询的key值，可以大大优化查询效率。可用于检索一个元素是否在集合中。缺点是有一定的命中误差，以及删除比较困难。

（4）**进行实时监控**。当发现Redis的命中率急速降低时，需要排查访问对象和访问的数据，和运维人员配合，可以设置黑名单来限制服务。



## 缓存击穿

### 是什么

（1）数据库访问压力瞬时增加；

（2）Redis里面没有出现大量key过期；

（3）Redis正常运行。

### 出现原因排查

（1）Redis某个 热点key 过期了，大量访问使用到了这个key。

### 解决方案

（1）**预先设置热门数据**。在Redis高峰访问之前，把一些热门数据提前存入到Redis里面，加大这些热门数据key的时长；

（2）**实时调整**。现场监控哪些数据热门，实时调整 key 的过期时长；

（3）**使用锁（排他锁）**。

​	a. 在缓存失效时（判断拿出来的值为空），不是立即去加载 数据库。

​	b. 先使用缓存工具的某些带成功操作返回值的操作去set一个mutex key。

​	c. 当操作返回成功时，在进行 load db 的操作，并回设缓存，最后删除 mutex key。

​	d. 当操作返回失败，证明有线程在load db，当前线程睡眠一段时间在重试整个get缓存的方法。



## 缓存雪崩

### 是什么

数据库压力变大，导致服务器崩溃。

### 排查原因

在极少时间段，查询**大量 key 的集中过期情况**。

### 解决方案

（1）**构建多级缓存架构**。nginx缓存 + redis缓存 + 其他缓存。

（2）**使用锁或者队列**。用加锁或者队列的方式保证不会有大量的线程对数据库一次性进行读写，从而避免失效时大量的并发请求落到底层存储系统上。不适用高并发的情况。

（3）**设置过期标志更新缓存**。记录缓存数据是否过期，如果过期会触发通知另外的线程去后台更新实际key的缓存。 

（4）**将缓存失效的时间分散开**。可以在原来的失效时间上增加一个随机值，这样的话，每一个缓存的过期时间的重复率就会降低，就不容易引起集体失效，导致缓存雪崩的情况。



## 分布式锁

分布式锁，主要应用于分布式集群之间的锁，对集群中所有的数据库都有效。	

### 使用redis实现分布式锁

setex key second value   给key上锁，通过expire key time 可以设置上锁时间，通过del key可以释放锁。

上面分开设置的方式如果出现异常，不能保证原子性操作，通过命令 set key value nx ex time 可以实现原子操作，即上锁的同时，设置过期时间。

### 误删锁的出现和解决方案

**怎么出现？**

​	当数据库A进行操作时，占用了分布式锁，没到过期时间，数据库A出现服务器卡顿，导致分布式锁自动释放了，数据库B得到了分布式锁，进行数据操作，此时数据库B也没有达到过期时间，而数据库A不卡顿，继续进行操作，发现没有数据可操作了，手动释放了分布式锁，而此时释放的是数据库B拿到的分布式锁。导致数据库B的操作没有完成就终止了，从而数据库A和B的数据操作都出错。

**怎么解决？**

​	（1）使用UUID工具类为集群中的每一个数据库都生成一个唯一的标识符。在释放分布式锁之前，首先判断当前的UUID标识符是否和需要释放的锁的UUID一样。

​	（2）使用lua脚本实现锁的原子性操作。



## Redis的ACL 访问控制列表

ACL（Access Control List），允许根据可以执行的命令和可以访问的键来限制某些连接。Redis 6 开始才能使用。 
