package com.demi.jedis;

import redis.clients.jedis.Jedis;

import java.util.Random;

public class PhoneCode {
    public static void main(String[] args) {
        // 模拟验证码发送
        String phone = "15908170794";
        // 发送验证码，并存储在redis中
        // verifyCode(phone);
        // 从redis中获取验证码进行校验
        getRedisCode(phone, "239410");
    }

    // 生成6位数字的随机验证码
    public static String getCode() {
        Random random = new Random();
        String s = "";
        for (int i = 0; i < 6; i++) {
            s += random.nextInt(10);
        }
        return s;
    }

    /**
     * 每个手机号只能发送3次。将随机生成的验证码放到redis中，设置过期时间
     * @param phone
     */
    public static void verifyCode(String phone) {
        // 创建Jedis对象
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        // 拼接手机发送次数key
        String countKey = "VerifyCode" + phone + ":count";

        // 拼接验证码key
        String codeKey = "VerifyCode" + phone + ":code";

        // 每个手机每天只能发送3次
        String count = jedis.get(countKey);
        if (count == null) {
            // 第一次发送， +1
            jedis.setex(countKey, 24 * 60 * 60, "1");
        } else if (Integer.parseInt(count) <= 2) {
            jedis.incr(countKey);
        } else if (Integer.parseInt(count) > 2) {
            System.out.println("发送次数已达上限：3次");
            jedis.close();
        }
        // 验证码放到redis里面，设置过期时间：120s
        jedis.setex(codeKey, 120, getCode());
        jedis.close();
    }

    /**
     * 验证填写验证码和存储的验证码是否一致
     * @param phone
     * @param code
     */
    public static void getRedisCode(String phone, String code) {
        // 创建Jedis对象
        Jedis jedis = new Jedis("127.0.0.1", 6379);
        //
        String codeKey = "VerifyCode" + phone + ":code";
        // 从redis获取验证码
        String redisCode = jedis.get(codeKey);
        // 判断
        if (redisCode.equals(code)) {
            System.out.println("验证成功！");
        } else {
            System.out.println("验证失败");
        }
        jedis.close();
    }
}
