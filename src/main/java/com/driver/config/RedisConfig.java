package com.driver.config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisConfig {
    private static JedisPool pool;
    
    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        pool = new JedisPool(config, "localhost", 6379);
    }
    
    public static Jedis getConnection() {
        return pool.getResource();
    }
    
    public static void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}