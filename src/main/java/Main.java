import ccd.PropsLoader;
import ccd.WorkThread;
import util.FileUtil;
import util.RedisUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        System.out.println("AST转换开启时间: "+new SimpleDateFormat("yyy-MM-dd hh:mm:ss")
                .format(new Date(System.currentTimeMillis())));
        RedisUtil.init();//开启redis服务, 清空redis历史数据
//        RedisUtil.checkSet();
        String path = PropsLoader.getProperty("ccd.path");
        String fileType = PropsLoader.getProperty("ccd.fileType");
        int threadCount = Integer.valueOf(PropsLoader.getProperty("ccd.threadCount"));
        List<String> fileList = new FileUtil().readfile(path, fileType);
        List<String>[] taskListPerThread = distributeTasks(fileList, threadCount); //分发解析任务给多个线程, 并执行解析
        System.out.println("文件数目:"+fileList.size()+", 共有:"+taskListPerThread.length+"个线程开启.");
        for (int i = 0; i < taskListPerThread.length; i++) {
            Thread workThread = new WorkThread(taskListPerThread[i], i+1);
            workThread.start();
        }
    }

    private static List<String>[] distributeTasks(List<String> fileList, int threadCount) {
        int fileSize = fileList.size();
        int minTaskCount = fileList.size() / threadCount;
        int remainTaskCount = fileList.size() % threadCount;
        int actualThreadCount = minTaskCount > 0 ? threadCount:remainTaskCount;
        List<String>[] taskListPerThread = new List[actualThreadCount];
        int partition = fileSize / actualThreadCount;
        for (int i = 0; i < taskListPerThread.length; i++) {
            int index =  partition * i;
            taskListPerThread[i] = new ArrayList<>();
            taskListPerThread[i].addAll(fileList.subList(index, index + partition));
        }
        if(remainTaskCount > 0 && fileSize >= threadCount){
            taskListPerThread[taskListPerThread.length - 1].addAll(fileList.subList(fileSize-remainTaskCount, fileSize));
        }
        return taskListPerThread;
    }
}
