import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import annotations.InitializerClass;
import annotations.InitializerMethod;
import annotations.RetryOperation;
import annotations.ScanPackages;

@ScanPackages({ "app", "app.configs", "app.databases", "app.http" })
public class App {
    public static void main(String[] args) throws Throwable {
        initialize();
    }

    public static void initialize(String... packageNames)
            throws Throwable {

        ScanPackages scanPackages = App.class.getAnnotation(ScanPackages.class);

        if (scanPackages == null || scanPackages.value().length == 0)
            return;

        List<Class<?>> classes = getAllClasses(scanPackages.value());

        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(InitializerClass.class))
                continue;

            List<Method> methods = getAllInitializingMethods(clazz);

            Object instance = clazz.getDeclaredConstructor().newInstance();

            for (Method method : methods)
                callInitializingMethod(instance, method);
        }
    }

    private static void callInitializingMethod(Object instance, Method method)
            throws Throwable {

        RetryOperation retryOperation = method.getAnnotation(RetryOperation.class);

        int numberOfRetries = retryOperation == null ? 0 : retryOperation.numberOfRetries();

        while (true) {
            try {
                method.invoke(instance);
                break;
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();

                boolean hasTargetException = Stream.of(retryOperation.retryExceptions())
                        .collect(Collectors.toCollection(HashSet::new))
                        .contains(targetException.getClass());

                if (numberOfRetries > 0 && hasTargetException) {

                    numberOfRetries--;

                    System.out.println("Retrying ...");
                    Thread.sleep(retryOperation.durationBetweenRetriesMs());
                } else if (retryOperation != null) {
                    throw new Exception(retryOperation.failureMessage(), targetException);
                } else {
                    throw targetException;
                }
            }
        }
    }

    private static List<Method> getAllInitializingMethods(Class<?> clazz) {
        List<Method> initializingMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods())
            if (method.isAnnotationPresent(InitializerMethod.class))
                initializingMethods.add(method);

        return initializingMethods;
    }

    public static List<Class<?>> getAllClasses(String... packageNames)
            throws URISyntaxException, ClassNotFoundException, IOException {
        List<Class<?>> allClasses = new ArrayList<>();

        for (String packageName : packageNames) {
            String packageRelativePath = packageName.replace('.', '/');

            // Cái này chỉ có path và scheme là file ko có các cái khác
            URI packageUri = App.class.getResource(packageRelativePath).toURI();

            if (packageUri.getScheme().equals("file")) {
                // Để loại bỏ cái scheme trong URI đi
                Path packageFullPath = Paths.get(packageUri);

                allClasses.addAll(getAllPackageClasses(packageFullPath, packageName));
            } else if (packageUri.getScheme().equals("jar")) {
                /**
                 * Này out trình chưa học tới để dùng java -jp ... thực thi file jar
                 * mà chưa học về nó nên cứ để đó dùng run bth để ra được scheme file:
                 */
                FileSystem fileSystem = FileSystems.newFileSystem(packageUri, Collections.emptyMap());

                Path packageFullPathInJar = fileSystem.getPath(packageRelativePath);
                allClasses.addAll(getAllPackageClasses(packageFullPathInJar, packageName));

                fileSystem.close();
            }
        }

        return allClasses;
    }

    private static List<Class<?>> getAllPackageClasses(Path packagePath, String packageName)
            throws IOException, ClassNotFoundException {
        if (!Files.exists(packagePath))
            return Collections.emptyList();

        // Nên dùng Set cho nó lẹ hơn
        List<Path> files = Files.list(packagePath)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        List<Class<?>> classes = new ArrayList<>();

        for (Path filePath : files) {
            String fileName = filePath.getFileName().toString();

            if (fileName.endsWith(".class")) {
                String classFullName = packageName + "." + fileName.replaceFirst("\\.class$", "");
                Class<?> clazz = Class.forName(classFullName);
                classes.add(clazz);
            }
        }

        return classes;
    }

}
