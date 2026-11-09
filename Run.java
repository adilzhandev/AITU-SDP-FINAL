import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Dependency-free launcher: execute from the project directory with java Run.java. */
public final class Run {
    private Run() {
    }

    public static void main(String[] args) {
        int status;
        try {
            status = execute(args);
        } catch (IOException error) {
            System.err.println("Build or launch failed: " + error.getMessage());
            status = 1;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Launch interrupted.");
            status = 130;
        }
        if (status != 0) {
            System.exit(status);
        }
    }

    private static int execute(String[] args) throws IOException, InterruptedException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("A full JDK 17 or newer is required; this Java runtime has no compiler.");
            System.err.println("Put the JDK's bin directory on PATH and run java Run.java again.");
            return 2;
        }
        if (Runtime.version().feature() < 17) {
            System.err.println("JDK 17 or newer is required.");
            return 2;
        }

        String mode = args.length == 0 ? "" : args[0];
        boolean buildOnly = mode.equals("build");
        boolean tests = mode.equals("test");
        boolean demo = mode.equals("demo");
        boolean gui = mode.equals("gui");
        if ((buildOnly || tests || demo) && args.length != 1) {
            System.err.println("Launcher commands build, test and demo do not accept extra arguments.");
            System.err.println("Use java Run.java --help for application options.");
            return 2;
        }

        Path sourceDirectory = Path.of("src");
        if (!Files.isRegularFile(sourceDirectory.resolve("school/Main.java"))) {
            System.err.println("Run from the project directory containing src/school/Main.java.");
            return 2;
        }
        List<Path> sources = javaSources(sourceDirectory);
        if (tests) {
            if (!Files.isDirectory(Path.of("tests"))) {
                System.err.println("The tests directory is missing.");
                return 2;
            }
            sources.addAll(javaSources(Path.of("tests")));
            sources.sort(Path::compareTo);
        }
        Path output = Path.of("out");
        Files.createDirectories(output);
        List<String> options = List.of("--release", "17", "-encoding", "UTF-8", "-d", output.toString());
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            boolean compiled = Boolean.TRUE.equals(compiler.getTask(null, files, null, options, null,
                    files.getJavaFileObjectsFromPaths(sources)).call());
            if (!compiled) {
                System.err.println("Compilation failed; the application was not started.");
                return 1;
            }
        }
        System.out.println("Compiled " + sources.size() + " Java source files into out.");
        if (buildOnly) {
            return 0;
        }

        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp",
                output.toString(), tests ? "school.AllTests" : gui ? "school.GuiMain" : "school.Main"));
        if (demo) {
            command.add("--demo");
        } else if (gui) {
            command.addAll(List.of(args).subList(1, args.length));
        } else if (!tests) {
            command.addAll(List.of(args));
        }
        return new ProcessBuilder(command).inheritIO().start().waitFor();
    }

    private static List<Path> javaSources(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return new ArrayList<>(entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted().toList());
        }
    }
}
