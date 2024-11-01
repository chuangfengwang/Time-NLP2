package com.helipy.text.time.nlp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * <p>
 * 新版时间表达式识别的主要工作类
 * <p>
 *
 * @author <a href="mailto:kexm@corp.21cn.com">kexm</a>
 * @since 2016年5月4日
 */
public class TimeNormalizer implements Serializable {
    private static final long serialVersionUID = 463541045644656392L;

    private Pattern patterns = null;
    private boolean isPreferFuture = true;

    private String timeBase;
    private String oldTimeBase;
    private String target;

    public TimeNormalizer() {
        try {
            InputStream in = getClass().getResourceAsStream("/TimeExp.m");
            ObjectInputStream objectInputStream = new ObjectInputStream(
                    new BufferedInputStream(new GZIPInputStream(in)));
            patterns = readModel(objectInputStream);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.print("Read model error!");
        }
    }

    /**
     * 参数为TimeExp.m文件路径
     *
     * @param path
     */
    public TimeNormalizer(String path) {
        try {
            patterns = readModel(path);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.print("Read TimeExp model error!");
        }
    }

    /**
     * 参数为TimeExp.m文件路径
     *
     * @param path
     */
    public TimeNormalizer(String path, boolean isPreferFuture) {
        this.isPreferFuture = isPreferFuture;
        try {
            patterns = readModel(path);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.err.print("Read model error!");
        }
    }

    public Pattern getPatterns() {
        return patterns;
    }

    /**
     * TimeNormalizer的构造方法，根据提供的待分析字符串和timeBase进行时间表达式提取
     * 在构造方法中已完成对待分析字符串的表达式提取工作
     *
     * @param target   待分析字符串
     * @param timeBase 给定的timeBase
     * @return 返回值  时间分析结果以TimeUnit组的形式给出
     */
    public TimeUnit[] parse(String target, String timeBase) {
        this.target = target;
        this.timeBase = timeBase;
        this.oldTimeBase = timeBase;
        // 字符串预处理
        preHandling();
        TimeUnit[] timeToken = timeExplain(this.target, timeBase);
        return timeToken;
    }

    /**
     * 同上的TimeNormalizer的构造方法，timeBase取默认的系统当前时间
     *
     * @param target 待分析字符串
     * @return 时间单元数组
     */
    public TimeUnit[] parse(String target) {
        this.target = target;
        this.timeBase = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        this.oldTimeBase = timeBase;
        preHandling(); // 字符串预处理
        TimeUnit[] timeToken = timeExplain(this.target, timeBase);
        return timeToken;
    }

    //

    /**
     * timeBase的get方法
     *
     * @return 返回值
     */
    public String getTimeBase() {
        return timeBase;
    }

    /**
     * oldTimeBase的get方法
     *
     * @return 返回值
     */
    public String getOldTimeBase() {
        return oldTimeBase;
    }

    public boolean isPreferFuture() {
        return isPreferFuture;
    }

    public void setPreferFuture(boolean isPreferFuture) {
        this.isPreferFuture = isPreferFuture;
    }

    /**
     * timeBase的set方法
     *
     * @param s timeBase
     */
    public void setTimeBase(String s) {
        timeBase = s;
    }

    /**
     * 重置timeBase为oldTimeBase
     */
    public void resetTimeBase() {
        timeBase = oldTimeBase;
    }

    /**
     * 待匹配字符串的清理空白符和语气助词以及大写数字转化的预处理
     */
    private void preHandling() {
        target = StringPreHandlingModule.delKeyword(target, "\\s+"); // 清理空白符
        target = StringPreHandlingModule.delKeyword(target, "[的]+"); // 清理语气助词
        target = StringPreHandlingModule.numberTranslator(target); // 大写数字转化
        // TODO 处理大小写标点符号
    }

    /**
     * 有基准时间输入的时间表达式识别
     * <p>
     * 这是时间表达式识别的主方法， 通过已经构建的正则表达式对字符串进行识别，并按照预先定义的基准时间进行规范化
     * 将所有别识别并进行规范化的时间表达式进行返回， 时间表达式通过TimeUnit类进行定义
     *
     * @param tar      输入文本字符串
     * @param timebase 输入基准时间
     * @return TimeUnit[] 时间表达式类型数组
     */
    private TimeUnit[] timeExplain(String tar, String timebase) {
        Matcher match;
        int startline = -1, endline = -1;

        String[] temp = new String[99];
        int rpointer = 0;// 计数器，记录当前识别到哪一个字符串了
        TimeUnit[] Time_Result = null;

        match = patterns.matcher(tar);
        boolean startmark = true;
        while (match.find()) {
            startline = match.start();
            if (endline == startline) { // 假如下一个识别到的时间字段和上一个是相连的 @author kexm
                rpointer--;
                temp[rpointer] = temp[rpointer] + match.group();// 则把下一个识别到的时间字段加到上一个时间字段去
            } else {
                if (!startmark) {
                    rpointer--;
                    rpointer++;
                }
                startmark = false;
                temp[rpointer] = match.group();// 记录当前识别到的时间字段，并把startmark开关关闭。这个开关貌似没用？
            }
            endline = match.end();
            rpointer++;
        }
        if (rpointer > 0) {
            rpointer--;
            rpointer++;
        }
        Time_Result = new TimeUnit[rpointer];
        // 时间上下文： 前一个识别出来的时间会是下一个时间的上下文，用于处理：周六3点到5点这样的多个时间的识别，第二个5点应识别到是周六的。
        TimePoint contextTp = new TimePoint();
        for (int j = 0; j < rpointer; j++) {
            Time_Result[j] = new TimeUnit(temp[j], this, contextTp);
            contextTp = Time_Result[j]._tp;
        }
        // 过滤无法识别的字段
        Time_Result = filterTimeUnit(Time_Result);
        return Time_Result;
    }

    /**
     * 过滤timeUnit中无用的识别词。无用识别词识别出的时间是1970.01.01 00:00:00(fastTime=-28800000)
     *
     * @param timeUnit
     * @return
     */
    public static TimeUnit[] filterTimeUnit(TimeUnit[] timeUnit) {
        if (timeUnit == null || timeUnit.length < 1) {
            return timeUnit;
        }
        List<TimeUnit> list = new ArrayList<>();
        for (TimeUnit t : timeUnit) {
            if (t.getTime().getTime() != -28800000) {
                list.add(t);
            }
        }
        TimeUnit[] newT = new TimeUnit[list.size()];
        newT = list.toArray(newT);
        return newT;
    }

    private Pattern readModel(String file) throws Exception {
        ObjectInputStream in;
        if (file.startsWith("jar:file") || file.startsWith("file:")) {
            in = new ObjectInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(
                                    new URL(file).openStream())));
        } else {
            in = new ObjectInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(
                                    new FileInputStream(file))));
        }
        return readModel(in);
    }

    /**
     * 读出 pattern
     *
     * @param in
     * @return
     * @throws Exception
     */
    public static Pattern readModel(ObjectInputStream in) throws Exception {
        Pattern p = (Pattern) in.readObject();
        return Pattern.compile(p.pattern());
    }

    /**
     * 写出 pattern 到文件
     *
     * @param p
     * @param path
     * @throws Exception
     */
    public static void writeModel(Object p, String path) throws Exception {
        ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(path))));
        out.writeObject(p);
        out.close();
    }


}
