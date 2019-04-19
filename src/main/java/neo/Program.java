package neo;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import neo.exception.AggregateException;
import neo.log.notr.TR;
import neo.services.ConsoleServiceBase;
import neo.smartcontract.EventHandler;
import neo.wallets.Wallet;

public class Program {

    static Wallet wallet;

/*    private static void currentDomainUnhandledException(Object sender,
                                                         UnhandledExceptionEventArgs e) {
        FileOutputStream fs = new FileStream("error.log", FileMode.Create, FileAccess.Write, FileShare.None))
        FileWriter w = new FileWriter(fs);
        if (e.exceptionObject instanceof Exception)
        {
            printErrorLogs(w,e.exceptionObject);
        }else{
            w.writeLine(e.exceptionObject.getType());
            w.writeLine(e.exceptionObject);
        }
    }*/

    public static void main(String[] args) {
        //AppDomain.CurrentDomain.UnhandledException += CurrentDomain_UnhandledException;
/*        int bufferSize = 1024 * 67 + 128;
        Stream inputStream = Console.OpenStandardInput(bufferSize);
        Console.SetIn(new StreamReader(inputStream, Console.InputEncoding, false, bufferSize));*/
        try {
            ConsoleServiceBase mainService = new MainService();
            mainService.run(args);
        } catch (Exception e) {
            try {
                File fs = new File("error.log");
                FileWriter w = new FileWriter(fs);
                if (e instanceof Exception) {
                    printErrorLogs(w, e);
                } else {
                    w.write(e.getClass() + "\r\n");
                    w.write(e + "\r\n");
                }
            } catch (IOException ex) {
                TR.warn(ex.getMessage());
                throw new RuntimeException(ex);
            }
        }

    }

    private static void printErrorLogs(Writer writer, Exception ex) throws IOException {
        writer.write(ex.getClass() + "\r\n");
        writer.write(ex.getMessage() + "\r\n");
        writer.write(ex.getStackTrace() + "\r\n");
        if (ex instanceof AggregateException) {
            for (Exception inner : ((AggregateException) ex).exceptionList) {
                writer.write("\r\n");
                printErrorLogs(writer, inner);
            }
        } else if (ex.getCause() != null) {
            if (ex.getCause() instanceof Exception) {
                writer.write("\r\n");
                printErrorLogs(writer, (Exception) ex.getCause());
            }
        }
    }
}
