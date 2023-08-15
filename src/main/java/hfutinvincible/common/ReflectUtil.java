package hfutinvincible.common;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Java 反射工具
 */
public class ReflectUtil {

    /**
     * 扫描得到一个包路径下所有的 Class
     * @param basePackageName 要扫描的包路径
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Class<T>> scanAllClasses(
            final String basePackageName,
            final Predicate<Class<?>> classFilter
    ) throws IOException, ClassNotFoundException {
        // 将包名转为包路径
        String basePackagePath = basePackageName
                .replaceAll("\\.", "/");

        // 递归扫描
        Enumeration<URL> resources = Thread.currentThread()
                .getContextClassLoader()
                .getResources(basePackagePath);
        List<Class<T>> classList = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if ("file".equals(url.getProtocol())) {
                List<File> fileList = new ArrayList<>();
                listFiles(new File(url.getFile()), fileList);
                for (File file : fileList) {
                    String fPath = file.getAbsolutePath().replaceAll("\\\\","/") ;
                    String className = fPath.substring(fPath.lastIndexOf(basePackagePath))
                            .replace(".class","")
                            .replaceAll("/", ".");
                    // 根据名称加载类
                    Class<T> clazz = (Class<T>) Class.forName(className);
                    if (classFilter.test(clazz))
                        classList.add(clazz);
                }
            }
        }
        return classList;
    }

    /**
     * 递归扫描得到一个目录下的所有文件
     * @param dir 要扫描的目录
     * @param fileList 应该传入一个空 list
     */
    public static void listFiles(
            final File dir,
            List<File> fileList
    ) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                listFiles(f, fileList);
            }
        } else {
            if(dir.getName().endsWith(".class")) {
                fileList.add(dir);
            }
        }
    }
}
