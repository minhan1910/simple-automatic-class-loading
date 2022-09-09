import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import annotations.InitializerClass;
import annotations.InitializerMethod;
import app.configs.ConfigsLoader;

public class Main {
    public static void main(String[] args)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, NoSuchMethodException, SecurityException, URISyntaxException, IOException {
        initialize("app", "app.configs", "app.databases", "app.http");
    }

    /**
     * 
     * @throws IOException
     * @throws URISyntaxException
     * @throws ClassNotFoundException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * 
     */

    public static void initialize(String... packageNames)
            throws ClassNotFoundException, URISyntaxException, IOException, InstantiationException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
            SecurityException {
        List<Class<?>> classes = getAllClasses(packageNames);

        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(InitializerClass.class))
                continue;

            List<Method> methods = getAllInitializingMethods(clazz);

            Object instance = clazz.getDeclaredConstructor().newInstance();

            for (Method method : methods)
                method.invoke(instance);
        }
    }

    public static List<Class<?>> getAllClasses(String... packageNames)
            throws URISyntaxException, ClassNotFoundException, IOException {
        List<Class<?>> classes = new ArrayList<>();

        for (String packageName : packageNames) {
            String packageRelativePath = packageName.replace('.', '/');

            URI packageUri = Main.class.getResource(packageRelativePath).toURI();

            if (packageUri.getScheme().equals("file")) {
                Path packageFullPath = Paths.get(packageUri);

                classes.addAll(getAllPackageNames(packageFullPath, packageName));
            }
        }

        return classes;
    }

    public static List<Method> getAllInitializingMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods())
            if (method.isAnnotationPresent(InitializerMethod.class))
                methods.add(method);

        return methods;
    }

    public static List<Class<?>> getAllPackageNames(Path packagePath, String packageName)
            throws IOException, ClassNotFoundException {
        if (!Files.exists(packagePath))
            return Collections.emptyList();

        List<Path> paths = Files.list(packagePath)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        List<Class<?>> classes = new ArrayList<>();

        for (Path path : paths) {
            String fileName = path.getFileName().toString();

            if (fileName.endsWith(".class")) {
                String classFullName = packageName + "." + fileName.replaceFirst("\\.class$", "");
                Class<?> clazz = Class.forName(classFullName);
                classes.add(clazz);
            }
        }

        return classes;
    }
}
