package neo.services;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import neo.exception.FormatException;
import neo.log.notr.TR;
import neo.wallets.SQLite.Version;

/**
 * @author doubi.liu
 * @version V1.0
 * @Title: ConsoleServiceBase
 * @Package neo.services
 * @Description: 控制台基类
 * @date Created in 11:23 2019/3/29
 */
public abstract class ConsoleServiceBase {
    public boolean debugFlag = true;

    protected String getDepends() {
        return null;
    }

    protected String getPrompt() {
        return "service";
    }

    public abstract String getServiceName();

    protected boolean showPrompt=true;

    protected boolean onCommand(String[] args) {
        switch (args[0].toLowerCase()) {
            case "clear":
                ConsoleHelper.clear();
                return true;
            case "exit":
                return false;
            case "version":
                ConsoleHelper.writeLine("2.9.0");
                return true;
            default:
                ConsoleHelper.writeLine("error: command not found " + args[0]);
                return true;
        }
    }

    protected abstract void onStart(String[] args);

    protected abstract void onStop();

    private static String[] parseCommandLine(String line) {
        List<String> outputArgs = new ArrayList<String>();
        StringReader reader = new StringReader(line);

        while (true) {
            try {
                reader.mark(line.length());
                int c = reader.read();
                reader.reset();
                switch (c) {
                    case -1:
                        return outputArgs.toArray(new String[0]);
                    case ' ':
                        reader.read();
                        break;
                    case '\"':
                        outputArgs.add(parseCommandLineString(reader, line.length()));
                        break;
                    default:
                        outputArgs.add(parseCommandLineArgument(reader));
                        break;
                }
            } catch (IOException e) {
                TR.warn(e.getMessage());
                throw new RuntimeException(e);
            }
        }

    }

    private static String parseCommandLineArgument(StringReader reader1) {
        BufferedReader reader = new BufferedReader(reader1);
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = 0;
            try {
                c = reader.read();
            } catch (IOException e) {
                TR.warn(e.getMessage());
                throw new RuntimeException(e);

            }
            switch (c) {
                case -1:
                case ' ':
                    return sb.toString();
                default:
                    sb.append((char) c);
                    break;
            }
        }
    }

    private static String parseCommandLineString(StringReader reader1, int length) {
        BufferedReader reader = new BufferedReader(reader1);
        try {
            if (reader.read() != '\"')
                throw new FormatException();
        } catch (IOException e) {
            TR.warn(e.getMessage());
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();
        while (true) {
            try {
                reader.mark(length);
                int c = 0;
                c = reader.read();
                reader.reset();
                switch (c) {
                    case '\"':
                        reader.read();
                        return sb.toString();
                    case '\\':
                        sb.append(parseEscapeCharacter(reader));
                        break;
                    default:
                        reader.read();
                        sb.append((char) c);
                        break;
                }
            } catch (IOException e) {
                TR.warn(e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private static char parseEscapeCharacter(BufferedReader reader) {
        try {
            if (reader.read() != '\\')
                throw new FormatException();
            int c = reader.read();
            switch (c) {
                case -1:
                    throw new FormatException();
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case 'x':
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 2; i++) {
                        int h = reader.read();
                        if (h >= '0' && h <= '9' || h >= 'A' && h <= 'F' || h >= 'a' && h <= 'f')
                            sb.append((char) h);
                        else
                            throw new FormatException();
                    }

                    return (char) (Byte.parseByte(sb.toString(), 16) & 0xff);
                default:
                    return (char) c;
            }
        } catch (IOException e) {
            TR.warn(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String readPassword(String prompt) {
        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.YELLOW);
        char[] result=ConsoleHelper.readPassWord(prompt);
        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.WHITE);
        ConsoleHelper.writeLine();
        return new String(result);
/*

        final String t = " !\"#$%&'()*+,-./0123456789:;" +
                "<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        StringBuilder sb = new StringBuilder();
        ConsoleKeyInfo key;
        ConsoleHelper.write(prompt);
        ConsoleHelper.write(": ");

        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.YELLOW);

        do {
            key = Console.ReadKey(true);
            if (t.indexOf(key.KeyChar) != -1) {
                sb.append(key.KeyChar);
                ConsoleHelper.write('*');
            } else if (key.Key == ConsoleKey.Backspace && sb.length() > 0) {
                sb.length--;
                ConsoleHelper.write(key.KeyChar);
                ConsoleHelper.write(' ');
                ConsoleHelper.write(key.KeyChar);
            }
        } while (key.Key != ConsoleKey.Enter);
        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.WHITE);
        ConsoleHelper.writeLine();
        return sb.toString();*/
    }

    //// TODO: 2019/4/16
    //安全字符处理
/*
    public static secureString readSecureString(String prompt)
    {
        final String t = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        SecureString securePwd = new SecureString();
        ConsoleKeyInfo key;
        Console.Write(prompt);
        Console.Write(": ");

        Console.ForegroundColor = ConsoleColor.Yellow;

        do
        {
            key = Console.ReadKey(true);
            if (t.IndexOf(key.KeyChar) != -1)
            {
                securePwd.AppendChar(key.KeyChar);
                Console.Write('*');
            }
            else if (key.Key == ConsoleKey.Backspace && securePwd.Length > 0)
            {
                securePwd.RemoveAt(securePwd.Length - 1);
                Console.Write(key.KeyChar);
                Console.Write(' ');
                Console.Write(key.KeyChar);
            }
        } while (key.Key != ConsoleKey.Enter);

        Console.ForegroundColor = ConsoleColor.White;
        Console.WriteLine();
        securePwd.MakeReadOnly();
        return securePwd;
    }
*/

    public void run(String[] args) {
/*        if (Environment.UserInteractive) {
            if (args.length > 0 && args[0] == "/install") {
                //if (System.getProperty("os.name") != PlatformID.Win32NT) {
                if (System.getProperties().getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1) {
                    ConsoleHelper.writeLine("Only support for installing services on Windows.");
                    return;
                }
                String arguments = String.format("create {0} start= auto binPath= \"{1}\"",
                        ServiceName, Process.GetCurrentProcess().MainModule.FileName);
                if (!String.IsNullOrEmpty(getDepends())) {
                    arguments += String.format(" depend= {0}", getDepends());
                }
                Process process = Process.Start(new ProcessStartInfo
                {
                    Arguments = arguments,
                            FileName = Path.Combine(Environment.SystemDirectory, "sc.exe"),
                            RedirectStandardOutput = true,
                            UseShellExecute = false
                });
                process.WaitForExit();
                ConsoleHelper.write(process.StandardOutput.ReadToEnd());
            } else if (args.length > 0 && args[0] == "/uninstall") {
                //if (Environment.OSVersion.Platform != PlatformID.Win32NT) {
                if (System.getProperties().getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1) {
                    ConsoleHelper.writeLine("Only support for installing services on Windows.");
                    return;
                }
                Process process = Process.Start(new ProcessStartInfo
                {
                    Arguments = string.Format("delete {0}", ServiceName),
                            FileName = Path.Combine(Environment.SystemDirectory, "sc.exe"),
                            RedirectStandardOutput = true,
                            UseShellExecute = false
                });
                process.WaitForExit();
                ConsoleHelper.write(process.StandardOutput.ReadToEnd());
            } else {
                onStart(args);
                runConsole();
                onStop();
            }
        } else {
            ServiceBase.Run(new ServiceProxy(this));
        }*/
        onStart(args);
        runConsole();
        onStop();
    }

    private void runConsole() {
        boolean running = true;
        //if (Environment.OSVersion.Platform == PlatformID.Win32NT)
        if (System.getProperties().getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1)
            ConsoleHelper.setTitle(getServiceName());
        //Console.OutputEncoding = Encoding.Unicode;
        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.DARKGREEN);
        Version ver = Version.parse("2.9.0");
        ConsoleHelper.writeLine(String.format("{0} Version: {1}", getServiceName(), ver
                .toString()));
        ConsoleHelper.writeLine();

        while (running) {
            if (showPrompt) {
                ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.GREEN);
                ConsoleHelper.write(String.format("{0}> ", getPrompt()));
            }

            ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.YELLOW);
            String line = ConsoleHelper.readLine();
            line = line == null ? null : line.trim();
            if (line == null) break;
            ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.WHITE);

            String[] args = parseCommandLine(line);
            if (args.length == 0)
                continue;
            try {
                running = onCommand(args);
            } catch (Exception ex) {
                if (debugFlag) {
                    ConsoleHelper.writeLine(String.format("error: {0}", ex.getMessage()));
                } else {
                    ConsoleHelper.writeLine("error");
                }
            }
        }

        ConsoleHelper.setForegroundColor(ConsoleHelper.ConsoleColor.DEFAULT);
    }
}