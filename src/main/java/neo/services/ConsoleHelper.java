package neo.services;

import java.io.Console;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: ConsoleHelper
 * @Package neo.services
 * @Description: (用一句话描述该文件做什么)
 * @date Created in 14:42 2019/4/15
 */
public class ConsoleHelper {
    private static String title = "";
    private static String foregroundColor = ConsoleColor.WHITE;
    private static Console console = System.console();
    private static String colordetail = "\033[0m";

    public static String getTitle() {
        return title;
    }

    public static void setTitle(String title) {
        ConsoleHelper.title = title;
    }

    public static void setForegroundColor(String foregroundColor) {
        ConsoleHelper.foregroundColor = foregroundColor;
    }

    public static Console getConsole() {
        return console;
    }

    public static void setConsole(Console console) {
        ConsoleHelper.console = console;
    }

    public static void clear() {
        try {
            String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                Runtime.getRuntime().exec("cls");
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (Exception exception) {
            //  Handle exception.
            throw new RuntimeException(exception);
        }
    }

    public static void write(String msg) {
        console.writer().write(foregroundColor + msg + colordetail);
    }

    public static void write(char msg) {
        console.writer().write(foregroundColor + msg + colordetail);
    }

    public static void writeLine(String msg) {
        console.writer().write(foregroundColor+msg + "\n" + colordetail);
    }

    public static void writeLine() {
        console.writer().write("\n");
    }


    public static String readLine() {
        return console.readLine();
    }


    public static char[] readPassWord(String prompt){
        return console.readPassword(foregroundColor+prompt+colordetail);
    }
    class ConsoleColor {
        public final static String WHITE = "\033[30;4m";
        public final static String YELLOW = "\033[33;4m";
        public final static String GREEN = "\036[30;4m";
        public final static String DARKGREEN = "\032[30;4m";
        public final static String DEFAULT = "\030[30;4m";
    }
}