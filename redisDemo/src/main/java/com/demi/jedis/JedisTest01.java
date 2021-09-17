package com.demi.jedis;

import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Set;

public class JedisTest01 {
    public static void main(String[] args) {
        // 与本地redis建立连接
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        // 测试
        String ping = jedis.ping();
        System.out.println(ping);
    }

    @Test
    public void test01() {
        Jedis jedis = new Jedis("127.0.0.1", 6379);

        // add
        jedis.set("k1", "v1");
        jedis.set("k2", "v2");
        jedis.set("k3", "v3");
        System.out.println(jedis.mget("k1", "k2"));
        // see all
        Set<String> keys = jedis.keys("*");
        System.out.println(keys);
        /*for (String key : keys) {
            System.out.println(key);
        }*/

    }
}
