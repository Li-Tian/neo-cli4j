package neo;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: CliHelper
 * @Package neo
 * @Description: cli 字符串转换工具
 * @date Created in 11:08 2019/3/29
 */
public class CliHelper {
    /**
      * @Author:doubi.liu
      * @description:字符串转布尔值
      * @param input 输入
      * @date:2019/4/11
    */
    public static boolean toBoolean(String input)
    {
        input = input.toLowerCase();
        return input.equals("true") || input.equals("yes") || input.equals("1");
    }

    /**
      * @Author:doubi.liu
      * @description:获取系统版本
      * @date:2019/4/11
    */
    public static String getVersion()
    {
        return "2.9.4.0";
    }
}