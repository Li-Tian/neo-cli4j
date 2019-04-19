package neo;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: UnhandledExceptionEventArgs
 * @Package neo
 * @Description: (用一句话描述该文件做什么)
 * @date Created in 11:09 2019/4/16
 */
public class UnhandledExceptionEventArgs {
    public Exception exceptionObject;

    public UnhandledExceptionEventArgs(Exception exceptionObject) {
        this.exceptionObject = exceptionObject;
    }
}